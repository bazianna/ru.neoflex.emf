FROM maven:3.6.3-openjdk-8-slim
RUN apt-get update -y
RUN apt-get install -y git

COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]