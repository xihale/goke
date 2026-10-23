# 构建：docker build -t mintcal-server ./server
FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/mintcal-server ./cmd/mintcal-server && mkdir /out/data

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/mintcal-server /mintcal-server
COPY --from=build --chown=65532:65532 /out/data /data
ENV MINTCAL_ADDR=:8080 MINTCAL_DB=/data/mintcal.db
VOLUME /data
EXPOSE 8080
USER nonroot
ENTRYPOINT ["/mintcal-server"]
