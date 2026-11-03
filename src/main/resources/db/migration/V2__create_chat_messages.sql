CREATE TABLE chat_messages (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role        VARCHAR(20)  NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_user_id ON chat_messages (user_id, id DESC);