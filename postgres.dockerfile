# Extend the official PostgreSQL 14.1 image
FROM postgres:14.1

# Install necessary dependencies for building pgvector
RUN apt-get update && apt-get install -y \
    build-essential \
    postgresql-server-dev-14 \
    git \
    clang-11 \
    llvm-11 \
    ca-certificates \  
    && update-ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Set clang as the default compiler
ENV CC=clang-11
ENV CXX=clang++-11

# Clone and build pgvector
WORKDIR /tmp
RUN git clone https://github.com/pgvector/pgvector.git

WORKDIR /tmp/pgvector
RUN make
RUN make install

# Verify pgvector installation
RUN ls -la /usr/lib/postgresql/14/lib/vector* && \
    ls -la /usr/share/postgresql/14/extension/vector*

# Configure pgvector after PostgreSQL initialization
RUN mkdir -p /docker-entrypoint-initdb.d && \
    echo "CREATE EXTENSION IF NOT EXISTS vector;" > /docker-entrypoint-initdb.d/vector.sql

# Enable pgvector in PostgreSQL configuration
RUN echo "shared_preload_libraries = 'vector'" >> /usr/share/postgresql/postgresql.conf.sample
