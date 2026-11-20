FROM postgres:16-alpine

RUN apk add --no-cache bash dos2unix

COPY postgresql.conf /etc/postgresql/postgresql.conf
COPY entrypoint-replica.sh /usr/local/bin/entrypoint-replica.sh

RUN dos2unix /usr/local/bin/entrypoint-replica.sh && \
    chmod +x /usr/local/bin/entrypoint-replica.sh

ENTRYPOINT ["/usr/local/bin/entrypoint-replica.sh"]
