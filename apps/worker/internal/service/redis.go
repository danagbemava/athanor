package service

import (
	"bytes"
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

const runStatusKeyPrefix = "athanor.worker.run.status:"
const runClaimKeyPrefix = "athanor.worker.run.claim:"
const runStatusTTL = 24 * time.Hour
const runClaimTTL = 30 * time.Minute

type runStateStore interface {
	Get(ctx context.Context, key string) (string, error)
	SetNX(ctx context.Context, key string, value string, expiration time.Duration) (bool, error)
	Del(ctx context.Context, keys ...string) error
}

type redisRunStateStore struct {
	client *redis.Client
}

func (store redisRunStateStore) Get(ctx context.Context, key string) (string, error) {
	return store.client.Get(ctx, key).Result()
}

func (store redisRunStateStore) SetNX(
	ctx context.Context,
	key string,
	value string,
	expiration time.Duration,
) (bool, error) {
	return store.client.SetNX(ctx, key, value, expiration).Result()
}

func (store redisRunStateStore) Del(ctx context.Context, keys ...string) error {
	return store.client.Del(ctx, keys...).Err()
}

func Serve(config RedisConfig) error {
	if config.Address == "" {
		return errors.New("redis address is required")
	}
	consumerClient := redis.NewClient(&redis.Options{
		Addr:         config.Address,
		ReadTimeout:  0,
		WriteTimeout: 30 * time.Second,
	})
	publisherClient := redis.NewClient(&redis.Options{
		Addr:         config.Address,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
	})
	bundleReader, err := newS3BundleReader(config.ObjectStore)
	if err != nil {
		return err
	}
	resultStore, err := newS3ExecutionResultStore(config.ObjectStore)
	if err != nil {
		return err
	}
	runtime := NewRuntime(config.CacheDir, bundleReader)
	publisher := redisEventPublisher{
		client:      publisherClient,
		stream:      config.EventStream,
		resultStore: resultStore,
	}
	context := context.Background()
	stateStore := redisRunStateStore{client: publisherClient}

	if err := consumerClient.XGroupCreateMkStream(
		context,
		config.DispatchStream,
		config.DispatchConsumerGrp,
		"$",
	).Err(); err != nil && !strings.Contains(err.Error(), "BUSYGROUP") {
		return fmt.Errorf("failed to create dispatch consumer group: %w", err)
	}

	for {
		streams, err := consumerClient.XReadGroup(
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
					_ = consumerClient.XAck(context, config.DispatchStream, config.DispatchConsumerGrp, message.ID).Err()
					continue
				}
				// Redis delivers dispatch messages at least once. Claim the run first,
				// then check for a completed marker so redeliveries are skipped safely.
				claimed, err := claimRunExecution(
					context,
					stateStore,
					dispatchRequest.RunID,
					config.DispatchConsumer,
				)
				if err != nil {
					return fmt.Errorf("failed to claim run execution: %w", err)
				}
				if !claimed {
					_ = consumerClient.XAck(context, config.DispatchStream, config.DispatchConsumerGrp, message.ID).Err()
					continue
				}
				if err := runtime.ProcessDispatch(dispatchRequest, publisher); err == nil {
					_ = consumerClient.XAck(context, config.DispatchStream, config.DispatchConsumerGrp, message.ID).Err()
				} else {
					_ = releaseRunExecutionClaim(context, stateStore, dispatchRequest.RunID)
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

func hasTerminalRunState(
	ctx context.Context,
	store runStateStore,
	runID string,
) (bool, error) {
	status, err := store.Get(ctx, runStatusKey(runID))
	if err != nil {
		if errors.Is(err, redis.Nil) {
			return false, nil
		}
		return false, err
	}
	return isTerminalRunStatus(status), nil
}

func isTerminalRunStatus(status string) bool {
	return status == "completed"
}

func runStatusKey(runID string) string {
	return runStatusKeyPrefix + runID
}

func runClaimKey(runID string) string {
	return runClaimKeyPrefix + runID
}

func claimRunExecution(
	ctx context.Context,
	store runStateStore,
	runID string,
	consumer string,
) (bool, error) {
	claimed, err := store.SetNX(ctx, runClaimKey(runID), consumer, runClaimTTL)
	if err != nil {
		return false, err
	}
	if !claimed {
		return false, nil
	}

	completed, err := hasTerminalRunState(ctx, store, runID)
	if err != nil {
		_ = store.Del(ctx, runClaimKey(runID))
		return false, err
	}
	if completed {
		if err := store.Del(ctx, runClaimKey(runID)); err != nil {
			return false, err
		}
		return false, nil
	}
	return true, nil
}

func releaseRunExecutionClaim(
	ctx context.Context,
	store runStateStore,
	runID string,
) error {
	return store.Del(ctx, runClaimKey(runID))
}

type redisEventPublisher struct {
	client      *redis.Client
	stream      string
	resultStore ExecutionResultStore
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
	if publisher.resultStore == nil {
		return errors.New("execution result store is not configured")
	}
	resultKey, err := publisher.resultStore.Store(runID, result)
	if err != nil {
		return err
	}
	payload, err := json.Marshal(completionPayload(result, resultKey, time.Now()))
	if err != nil {
		return err
	}
	return publisher.publish(runID, "complete", payload, "completed")
}

func (publisher redisEventPublisher) PublishFailure(runID string, message string) error {
	payload, err := json.Marshal(map[string]string{"error": message})
	if err != nil {
		return err
	}
	return publisher.publish(runID, "failed", payload)
}

func (publisher redisEventPublisher) publish(runID string, eventType string, payload []byte, terminalStatus ...string) error {
	pipe := publisher.client.TxPipeline()
	pipe.XAdd(
		context.Background(),
		&redis.XAddArgs{
			Stream: publisher.stream,
			Values: map[string]any{
				"run_id":       runID,
				"type":         eventType,
				"payload_json": string(payload),
			},
		},
	)
	if len(terminalStatus) > 0 {
		pipe.Set(
			context.Background(),
			runStatusKey(runID),
			terminalStatus[0],
			runStatusTTL,
		)
	}
	if eventType == "complete" || eventType == "failed" {
		pipe.Del(context.Background(), runClaimKey(runID))
	}
	_, err := pipe.Exec(context.Background())
	return err
}

type s3BundleReader struct {
	client *minio.Client
	bucket string
}

type ExecutionResultStore interface {
	Store(runID string, result contracts.ExecutionResult) (string, error)
}

type s3ExecutionResultStore struct {
	client *minio.Client
	bucket string
}

func newS3BundleReader(config ObjectStoreConfig) (BundleReader, error) {
	if config.Endpoint == "" || config.Bucket == "" {
		return nil, errors.New("object store endpoint and bucket are required")
	}
	client, err := newObjectStoreClient(config)
	if err != nil {
		return nil, err
	}
	return &s3BundleReader{client: client, bucket: config.Bucket}, nil
}

func newS3ExecutionResultStore(config ObjectStoreConfig) (ExecutionResultStore, error) {
	client, err := newObjectStoreClient(config)
	if err != nil {
		return nil, err
	}
	return &s3ExecutionResultStore{client: client, bucket: config.Bucket}, nil
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

func (store *s3ExecutionResultStore) Store(
	runID string,
	result contracts.ExecutionResult,
) (string, error) {
	payload, err := json.Marshal(result)
	if err != nil {
		return "", err
	}
	resultKey := executionResultObjectKey(runID)
	_, err = store.client.PutObject(
		context.Background(),
		store.bucket,
		resultKey,
		bytes.NewReader(payload),
		int64(len(payload)),
		minio.PutObjectOptions{ContentType: "application/json"},
	)
	if err != nil {
		return "", fmt.Errorf("failed to store execution result: %w", err)
	}
	return resultKey, nil
}

func newObjectStoreClient(config ObjectStoreConfig) (*minio.Client, error) {
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
	return client, nil
}

func executionResultObjectKey(runID string) string {
	return "simulation-results/" + runID + ".json"
}

func normalizeEndpoint(raw string, fallbackSecure bool) (string, bool) {
	parsed, err := url.Parse(raw)
	if err == nil && parsed.Host != "" {
		return parsed.Host, parsed.Scheme == "https"
	}
	return strings.TrimPrefix(strings.TrimPrefix(raw, "https://"), "http://"), fallbackSecure
}
