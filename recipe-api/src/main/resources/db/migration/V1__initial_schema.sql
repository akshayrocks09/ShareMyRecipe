-- V1__initial_schema.sql
-- Recipe Platform — initial database schema

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── Users ───────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    role             VARCHAR(20)  NOT NULL DEFAULT 'USER'
                         CHECK (role IN ('USER', 'CHEF', 'ADMIN')),
    email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    verification_token VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ─── Chef Profiles ───────────────────────────────────────────────────────────
CREATE TABLE chef_profiles (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    handle       VARCHAR(30) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    bio          TEXT,
    avatar_url   VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chef_profiles_handle  ON chef_profiles(handle);
CREATE INDEX idx_chef_profiles_user_id ON chef_profiles(user_id);

-- ─── Chef Follows ────────────────────────────────────────────────────────────
CREATE TABLE chef_follows (
    follower_id  UUID        NOT NULL REFERENCES chef_profiles(id) ON DELETE CASCADE,
    followee_id  UUID        NOT NULL REFERENCES chef_profiles(id) ON DELETE CASCADE,
    followed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT no_self_follow CHECK (follower_id <> followee_id)
);

CREATE INDEX idx_chef_follows_follower ON chef_follows(follower_id);
CREATE INDEX idx_chef_follows_followee ON chef_follows(followee_id);

-- ─── Recipes ─────────────────────────────────────────────────────────────────
CREATE TABLE recipes (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    chef_id      UUID         NOT NULL REFERENCES chef_profiles(id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    summary      TEXT,
    ingredients  JSONB        NOT NULL DEFAULT '[]',
    steps        JSONB        NOT NULL DEFAULT '[]',
    labels       JSONB        NOT NULL DEFAULT '[]',
    state        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                     CHECK (state IN ('DRAFT', 'PUBLISHED')),
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipes_chef_id     ON recipes(chef_id);
CREATE INDEX idx_recipes_state       ON recipes(state);
CREATE INDEX idx_recipes_published_at ON recipes(published_at DESC)
    WHERE state = 'PUBLISHED';
CREATE INDEX idx_recipes_labels      ON recipes USING GIN(labels);
CREATE INDEX idx_recipes_ingredients ON recipes USING GIN(ingredients);

-- ─── Recipe Images ────────────────────────────────────────────────────────────
CREATE TABLE recipe_images (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id        UUID        NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    original_key     VARCHAR(500),
    original_url     VARCHAR(500),
    thumb_url        VARCHAR(500),
    medium_url       VARCHAR(500),
    sort_order       INT         NOT NULL DEFAULT 0,
    processing_state VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (processing_state IN ('PENDING','PROCESSING','DONE','FAILED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipe_images_recipe_id ON recipe_images(recipe_id);

-- ─── Refresh Tokens ───────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- ─── Auto-update updated_at ───────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_updated_at_users
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION trigger_set_updated_at();

CREATE TRIGGER set_updated_at_recipes
    BEFORE UPDATE ON recipes
    FOR EACH ROW EXECUTE FUNCTION trigger_set_updated_at();
