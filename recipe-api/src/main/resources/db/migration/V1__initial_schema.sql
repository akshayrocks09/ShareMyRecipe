-- V1__initial_schema.sql
-- Recipe Platform — initial database schema (MySQL)

-- ─── Users ───────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                 CHAR(36)      NOT NULL PRIMARY KEY,
    email              VARCHAR(255)  NOT NULL UNIQUE,
    password_hash      VARCHAR(255)  NOT NULL,
    role               VARCHAR(20)   NOT NULL DEFAULT 'USER',
    email_verified     TINYINT(1)    NOT NULL DEFAULT 0,
    verification_token VARCHAR(255),
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'CHEF', 'ADMIN'))
);

CREATE INDEX idx_users_email ON users(email);

-- ─── Chef Profiles ───────────────────────────────────────────────────────────
CREATE TABLE chef_profiles (
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    user_id      CHAR(36)     NOT NULL UNIQUE,
    handle       VARCHAR(30)  NOT NULL UNIQUE,
    display_name VARCHAR(80)  NOT NULL,
    bio          TEXT,
    avatar_url   VARCHAR(500),
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_chef_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_chef_profiles_handle  ON chef_profiles(handle);
CREATE INDEX idx_chef_profiles_user_id ON chef_profiles(user_id);

-- ─── Chef Follows ────────────────────────────────────────────────────────────
CREATE TABLE chef_follows (
    follower_id  CHAR(36)    NOT NULL,
    followee_id  CHAR(36)    NOT NULL,
    followed_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT fk_chef_follows_follower FOREIGN KEY (follower_id) REFERENCES chef_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_chef_follows_followee FOREIGN KEY (followee_id) REFERENCES chef_profiles(id) ON DELETE CASCADE,
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> followee_id)
);

CREATE INDEX idx_chef_follows_follower ON chef_follows(follower_id);
CREATE INDEX idx_chef_follows_followee ON chef_follows(followee_id);

-- ─── Recipes ─────────────────────────────────────────────────────────────────
CREATE TABLE recipes (
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    chef_id      CHAR(36)     NOT NULL,
    title        VARCHAR(200) NOT NULL,
    summary      TEXT,
    ingredients  JSON         NOT NULL,
    steps        JSON         NOT NULL,
    labels       JSON         NOT NULL,
    state        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6),
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_recipes_chef  FOREIGN KEY (chef_id) REFERENCES chef_profiles(id) ON DELETE CASCADE,
    CONSTRAINT chk_recipes_state CHECK (state IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX idx_recipes_chef_id      ON recipes(chef_id);
CREATE INDEX idx_recipes_state        ON recipes(state);
CREATE INDEX idx_recipes_published_at ON recipes(published_at DESC);

-- ─── Recipe Images ────────────────────────────────────────────────────────────
CREATE TABLE recipe_images (
    id               CHAR(36)    NOT NULL PRIMARY KEY,
    recipe_id        CHAR(36)    NOT NULL,
    original_key     VARCHAR(500),
    original_url     VARCHAR(500),
    thumb_url        VARCHAR(500),
    medium_url       VARCHAR(500),
    sort_order       INT         NOT NULL DEFAULT 0,
    processing_state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_recipe_images_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
    CONSTRAINT chk_processing_state CHECK (processing_state IN ('PENDING','PROCESSING','DONE','FAILED'))
);

CREATE INDEX idx_recipe_images_recipe_id ON recipe_images(recipe_id);

-- ─── Refresh Tokens ───────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id         CHAR(36)     NOT NULL PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at DATETIME(6)  NOT NULL,
    revoked    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
