package com.recipeplatform.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Serialized into the RabbitMQ message body.
 * The worker deserializes this to drive its processing pipeline.
 */
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
    private String ingredientsText;   // pre-flattened for search indexing
    private String stepsText;         // pre-flattened for search indexing
    private List<String> labels;
    private Instant publishedAt;

    // S3 keys for images that need processing
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
