package com.recipeplatform.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recipes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chef_id", nullable = false)
    private ChefProfile chef;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // Stored as JSON array of {name, quantity, unit}
    @Convert(converter = RecipeJsonConverters.IngredientListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<IngredientItem> ingredients = new ArrayList<>();

    // Stored as JSON array of {stepNumber, instruction}
    @Convert(converter = RecipeJsonConverters.StepListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<StepItem> steps = new ArrayList<>();

    // Stored as JSON array of strings
    @Convert(converter = RecipeJsonConverters.LabelListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> labels = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipeState state = RecipeState.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<RecipeImage> images = new ArrayList<>();

    public enum RecipeState {
        DRAFT, PUBLISHED
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientItem {
        private String name;
        private String quantity;
        private String unit;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepItem {
        private int stepNumber;
        private String instruction;
    }
}
