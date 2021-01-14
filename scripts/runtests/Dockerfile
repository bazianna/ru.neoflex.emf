FROM alpine:3.10.4
RUN apk add --no-cache git maven 'openjdk8=~8.252' 'bash=~5.0'

COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]