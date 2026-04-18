package com.recipeplatform.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public class RecipeRequest {

    @Data
    public static class Create {
        @NotBlank
        @Size(max = 200)
        private String title;

        @Size(max = 1000)
        private String summary;

        @Valid
        private List<IngredientDto> ingredients;

        @Valid
        private List<StepDto> steps;

        @Size(max = 20)
        private List<String> labels;

        // Optional: pre-uploaded S3 object keys from pre-signed upload
        private List<String> imageKeys;
    }

    @Data
    public static class Update {
        @Size(max = 200)
        private String title;

        @Size(max = 1000)
        private String summary;

        @Valid
        private List<IngredientDto> ingredients;

        @Valid
        private List<StepDto> steps;

        @Size(max = 20)
        private List<String> labels;
    }

    @Data
    public static class IngredientDto {
        @NotBlank
        private String name;

        @NotBlank
        private String quantity;

        private String unit;
    }

    @Data
    public static class StepDto {
        @NotNull
        private Integer stepNumber;

        @NotBlank
        private String instruction;
    }

    @Data
    public static class ImageUpload {
        @NotNull
        @Size(min = 1, max = 10, message = "Upload between 1 and 10 images")
        private List<String> imageKeys;  // S3 object keys from pre-signed upload
    }
}
