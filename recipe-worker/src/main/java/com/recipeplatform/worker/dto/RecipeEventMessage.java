package com.recipeplatform.worker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeEventMessage {

    public enum EventType {
        PUBLISHED, UPDATED, DELETED
    }

    private EventType eventType;
    private UUID recipeId;
    private UUID chefId;
    private String chefHandle;
    private String chefDisplayName;
    private String title;
    private String summary;
    private String ingredientsText;
    private String stepsText;
    private List<String> labels;
    private Instant publishedAt;
    private List<ImageKey> imageKeys;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImageKey {
        private UUID imageId;
        private String s3Key;
        private int sortOrder;
    }
}
