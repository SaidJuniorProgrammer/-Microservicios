FROM postgres:16-alpine

RUN apk add --no-cache bash dos2unix

COPY postgresql.conf /etc/postgresql/postgresql.conf
COPY init-primary.sh /docker-entrypoint-initdb.d/init-primary.sh

RUN dos2unix /docker-entrypoint-initdb.d/init-primary.sh && \
    chmod +x /docker-entrypoint-initdb.d/init-primary.sh

CMD ["postgres", "-c", "config_file=/etc/postgresql/postgresql.conf"]
