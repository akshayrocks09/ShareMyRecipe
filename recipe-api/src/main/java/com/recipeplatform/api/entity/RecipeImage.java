package com.recipeplatform.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipe_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    // Key in S3 for the raw upload (temp prefix)
    @Column(name = "original_key")
    private String originalKey;

    // CDN URLs written back by the worker after resize
    @Column(name = "original_url")
    private String originalUrl;

    @Column(name = "thumb_url")
    private String thumbUrl;

    @Column(name = "medium_url")
    private String mediumUrl;

    @Column(name = "sort_order")
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_state")
    private ProcessingState processingState = ProcessingState.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum ProcessingState {
        PENDING, PROCESSING, DONE, FAILED
    }
}
