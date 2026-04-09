FROM golang:1.25 AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o crowd-mock-server .

FROM alpine:3.21
COPY --from=builder /app/crowd-mock-server /usr/local/bin/
EXPOSE 8095
CMD ["crowd-mock-server"]
