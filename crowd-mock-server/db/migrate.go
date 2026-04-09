package db

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5/pgxpool"
)

const schema = `
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    name          TEXT NOT NULL UNIQUE,
    key           TEXT NOT NULL UNIQUE,
    first_name    TEXT NOT NULL DEFAULT '',
    last_name     TEXT NOT NULL DEFAULT '',
    display_name  TEXT NOT NULL DEFAULT '',
    email         TEXT NOT NULL DEFAULT '',
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    password      TEXT NOT NULL DEFAULT '',
    created_date  BIGINT NOT NULL,
    updated_date  BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS groups (
    id            BIGSERIAL PRIMARY KEY,
    name          TEXT NOT NULL UNIQUE,
    description   TEXT NOT NULL DEFAULT '',
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    type          TEXT NOT NULL DEFAULT 'GROUP',
    created_date  BIGINT NOT NULL,
    updated_date  BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS user_attributes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attr_name  TEXT NOT NULL,
    attr_value TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_attr_user_id ON user_attributes(user_id);

CREATE TABLE IF NOT EXISTS group_attributes (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    attr_name  TEXT NOT NULL,
    attr_value TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_group_attr_group_id ON group_attributes(group_id);

CREATE TABLE IF NOT EXISTS user_group_memberships (
    user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, group_id)
);

CREATE TABLE IF NOT EXISTS group_group_memberships (
    parent_group_id BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    child_group_id  BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    PRIMARY KEY (parent_group_id, child_group_id)
);
`

func RunMigrations(ctx context.Context, pool *pgxpool.Pool) error {
	_, err := pool.Exec(ctx, schema)
	if err != nil {
		return fmt.Errorf("failed to run migrations: %w", err)
	}
	return nil
}

func TruncateAll(ctx context.Context, pool *pgxpool.Pool) error {
	_, err := pool.Exec(ctx, `
		TRUNCATE TABLE user_group_memberships, group_group_memberships,
		               user_attributes, group_attributes,
		               users, groups CASCADE
	`)
	if err != nil {
		return fmt.Errorf("failed to truncate tables: %w", err)
	}
	return nil
}
