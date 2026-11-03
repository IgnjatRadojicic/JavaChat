# Official Postgres on Alpine, with the pgvector extension compiled in.
FROM postgres:18-alpine

ARG PGVECTOR_VERSION=0.8.7

RUN apk add --no-cache --virtual .build-deps git build-base \
    && git clone --branch v${PGVECTOR_VERSION} --depth 1 https://github.com/pgvector/pgvector.git /tmp/pgvector \
    && cd /tmp/pgvector \
    # OPTFLAGS="" keeps the build portable (no -march=native); with_llvm=no skips JIT bitcode (no clang needed)
    && make OPTFLAGS="" with_llvm=no \
    && make install with_llvm=no \
    && cd / && rm -rf /tmp/pgvector \
    && apk del .build-deps

# Runs once, on first start with an empty data volume
COPY init/ /docker-entrypoint-initdb.d/
