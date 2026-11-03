CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE vector_store (
    id         UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    content    TEXT          NOT NULL,
    metadata   JSON,
    embedding  VECTOR(1536)
);

CREATE INDEX idx_vector_store_embedding ON vector_store USING hnsw (embedding vector_cosine_ops);