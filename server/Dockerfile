# 构建：docker build -t goke-server ./server
FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/goke-server ./cmd/goke-server && mkdir /out/data

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/goke-server /goke-server
COPY --from=build --chown=65532:65532 /out/data /data
ENV GOKE_ADDR=:8080 GOKE_DB=/data/goke.db
VOLUME /data
EXPOSE 8080
USER nonroot
ENTRYPOINT ["/goke-server"]
