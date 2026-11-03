CREATE TABLE conversation_summaries (
    user_id                      BIGINT       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    summary                      TEXT         NOT NULL,
    summarized_up_to_message_id  BIGINT       NOT NULL,
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now()
);