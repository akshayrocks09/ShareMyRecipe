package com.recipeplatform.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

public class AuthResponse {

    @Data
    @Builder
    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private long expiresInSeconds;
        private ChefResponse.Brief chef;
    }

    @Data
    @Builder
    public static class AccessToken {
        private String accessToken;
        private String tokenType;
        private long expiresInSeconds;
    }
}
