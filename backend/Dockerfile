FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/nicetv-api ./cmd/server

FROM alpine:3.20
RUN adduser -D -H nicetv
USER nicetv
COPY --from=build /out/nicetv-api /usr/local/bin/nicetv-api
EXPOSE 8080
ENTRYPOINT ["nicetv-api"]
