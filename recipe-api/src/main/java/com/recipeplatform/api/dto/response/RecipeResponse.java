package com.recipeplatform.api.dto.response;

import com.recipeplatform.api.entity.Recipe;
import com.recipeplatform.api.entity.RecipeImage;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RecipeResponse {

    @Data
    @Builder
    public static class Summary {
        private UUID id;
        private String title;
        private String summary;
        private List<String> labels;
        private String state;
        private Instant publishedAt;
        private Instant createdAt;
        private ChefResponse.Brief chef;
        private String thumbUrl;
    }

    @Data
    @Builder
    public static class Detail {
        private UUID id;
        private String title;
        private String summary;
        private List<Recipe.IngredientItem> ingredients;
        private List<Recipe.StepItem> steps;
        private List<String> labels;
        private String state;
        private Instant publishedAt;
        private Instant createdAt;
        private Instant updatedAt;
        private ChefResponse.Brief chef;
        private List<ImageDto> images;
    }

    @Data
    @Builder
    public static class ImageDto {
        private UUID id;
        private String originalUrl;
        private String thumbUrl;
        private String mediumUrl;
        private int sortOrder;
        private String processingState;
    }

    public static ImageDto fromEntity(RecipeImage img) {
        return ImageDto.builder()
                .id(img.getId())
                .originalUrl(img.getOriginalUrl())
                .thumbUrl(img.getThumbUrl())
                .mediumUrl(img.getMediumUrl())
                .sortOrder(img.getSortOrder())
                .processingState(img.getProcessingState().name())
                .build();
    }
}
