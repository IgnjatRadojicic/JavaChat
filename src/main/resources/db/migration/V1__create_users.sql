CREATE TABLE users (
    id             BIGSERIAL     PRIMARY KEY,
    google_sub     VARCHAR(255)  NOT NULL UNIQUE,
    email          VARCHAR(320)  NOT NULL,
    name           VARCHAR(255),
    picture_url    TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_login_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);