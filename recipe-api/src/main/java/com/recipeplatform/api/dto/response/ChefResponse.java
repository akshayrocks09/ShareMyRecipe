package com.recipeplatform.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

public class ChefResponse {

    @Data
    @Builder
    public static class Brief {
        private UUID id;
        private String handle;
        private String displayName;
        private String avatarUrl;
    }

    @Data
    @Builder
    public static class Detail {
        private UUID id;
        private String handle;
        private String displayName;
        private String bio;
        private String avatarUrl;
        private long recipeCount;
        private long followerCount;
        private long followingCount;
        private boolean isFollowedByMe;
        private Instant createdAt;
    }
}
