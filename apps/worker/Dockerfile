FROM golang:1.22-alpine AS dev
WORKDIR /workspace
COPY go.mod go.sum ./
RUN go mod download
CMD ["go", "run", "./cmd/worker", "serve", "--cache-dir", ".worker-cache"]

FROM golang:1.22-alpine AS build
WORKDIR /workspace
COPY go.mod go.sum ./
RUN go mod download
COPY cmd cmd
COPY internal internal
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o /out/worker ./cmd/worker

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/worker /worker
ENTRYPOINT ["/worker"]
CMD ["serve", "--cache-dir", "/var/cache/athanor/worker"]
