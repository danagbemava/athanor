package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/url"
	"strings"
	"time"

	"github.com/athanor/apps/worker/internal/contracts"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"github.com/redis/go-redis/v9"
)

type RedisConfig struct {
	Address             string
	DispatchStream      string
	DispatchConsumerGrp string
	DispatchConsumer    string
	EventStream         string
	CacheDir            string
	ObjectStore         ObjectStoreConfig
}

type ObjectStoreConfig struct {
	Endpoint  string
	Region    string
	Bucket    string
	AccessKey string
	SecretKey string
	UseSSL    bool
}

func Serve(config RedisConfig) error {
	if config.Address == "" {
		return errors.New("redis address is required")
	}
	client := redis.NewClient(&redis.Options{Addr: config.Address})
	bundleReader, err := newS3BundleReader(config.ObjectStore)
	if err != nil {
		return err
	}
	runtime := NewRuntime(config.CacheDir, bundleReader)
	publisher := redisEventPublisher{client: client, stream: config.EventStream}
	context := context.Background()

	if err := client.XGroupCreateMkStream(
		context,
		config.DispatchStream,
		config.DispatchConsumerGrp,
		"$",
	).Err(); err != nil && !strings.Contains(err.Error(), "BUSYGROUP") {
		return fmt.Errorf("failed to create dispatch consumer group: %w", err)
	}

	for {
		streams, err := client.XReadGroup(
			context,
			&redis.XReadGroupArgs{
				Group:    config.DispatchConsumerGrp,
				Consumer: config.DispatchConsumer,
				Streams:  []string{config.DispatchStream, ">"},
				Block:    time.Second,
				Count:    1,
			},
		).Result()
		if err != nil {
			if isIdleRedisRead(err) {
				continue
			}
			return fmt.Errorf("failed to read worker dispatch stream: %w", err)
		}

		for _, stream := range streams {
			for _, message := range stream.Messages {
				dispatchRequest, err := dispatchRequestFromMessage(message)
				if err != nil {
					_ = publisher.PublishFailure(fmt.Sprint(message.Values["run_id"]), err.Error())
					continue
				}
				if err := runtime.ProcessDispatch(dispatchRequest, publisher); err == nil {
					_ = client.XAck(context, config.DispatchStream, config.DispatchConsumerGrp, message.ID).Err()
				}
			}
		}
	}
}

func isIdleRedisRead(err error) bool {
	if errors.Is(err, redis.Nil) {
		return true
	}
	var netErr net.Error
	return errors.As(err, &netErr) && netErr.Timeout()
}

func dispatchRequestFromMessage(message redis.XMessage) (DispatchRequest, error) {
	runID := fmt.Sprint(message.Values["run_id"])
	bundleHash := fmt.Sprint(message.Values["bundle_hash"])
	requestJSON := fmt.Sprint(message.Values["request_json"])
	if runID == "" || bundleHash == "" || requestJSON == "" {
		return DispatchRequest{}, errors.New("dispatch message missing fields")
	}
	var request contracts.ExecutionRequest
	if err := json.Unmarshal([]byte(requestJSON), &request); err != nil {
		return DispatchRequest{}, fmt.Errorf("invalid request_json: %w", err)
	}
	return DispatchRequest{
		RunID:      runID,
		BundleHash: bundleHash,
		Request:    request,
	}, nil
}

type redisEventPublisher struct {
	client *redis.Client
	stream string
}

func (publisher redisEventPublisher) PublishProgress(runID string, completedRuns int, totalRuns int) error {
	payload, err := json.Marshal(map[string]int{
		"completedRuns": completedRuns,
		"totalRuns":     totalRuns,
	})
	if err != nil {
		return err
	}
	return publisher.publish(runID, "progress", payload)
}

func (publisher redisEventPublisher) PublishCompletion(runID string, result contracts.ExecutionResult) error {
	payload, err := json.Marshal(result)
	if err != nil {
		return err
	}
	return publisher.publish(runID, "complete", payload)
}

func (publisher redisEventPublisher) PublishFailure(runID string, message string) error {
	payload, err := json.Marshal(map[string]string{"error": message})
	if err != nil {
		return err
	}
	return publisher.publish(runID, "failed", payload)
}

func (publisher redisEventPublisher) publish(runID string, eventType string, payload []byte) error {
	return publisher.client.XAdd(
		context.Background(),
		&redis.XAddArgs{
			Stream: publisher.stream,
			Values: map[string]any{
				"run_id":       runID,
				"type":         eventType,
				"payload_json": string(payload),
			},
		},
	).Err()
}

type s3BundleReader struct {
	client *minio.Client
	bucket string
}

func newS3BundleReader(config ObjectStoreConfig) (BundleReader, error) {
	if config.Endpoint == "" || config.Bucket == "" {
		return nil, errors.New("object store endpoint and bucket are required")
	}
	endpoint, secure := normalizeEndpoint(config.Endpoint, config.UseSSL)
	client, err := minio.New(
		endpoint,
		&minio.Options{
			Creds:  credentials.NewStaticV4(config.AccessKey, config.SecretKey, ""),
			Secure: secure,
			Region: config.Region,
		},
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create object store client: %w", err)
	}
	return &s3BundleReader{client: client, bucket: config.Bucket}, nil
}

func (reader *s3BundleReader) ReadBundle(bundleHash string) ([]byte, error) {
	object, err := reader.client.GetObject(
		context.Background(),
		reader.bucket,
		"bundles/"+bundleHash,
		minio.GetObjectOptions{},
	)
	if err != nil {
		return nil, err
	}
	defer object.Close()
	data, err := io.ReadAll(object)
	if err != nil {
		return nil, err
	}
	return data, nil
}

func normalizeEndpoint(raw string, fallbackSecure bool) (string, bool) {
	parsed, err := url.Parse(raw)
	if err == nil && parsed.Host != "" {
		return parsed.Host, parsed.Scheme == "https"
	}
	return strings.TrimPrefix(strings.TrimPrefix(raw, "https://"), "http://"), fallbackSecure
}
