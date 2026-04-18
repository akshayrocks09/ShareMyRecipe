package com.recipeplatform.api.repository;

import com.recipeplatform.api.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    // Find by chef
    Page<Recipe> findByChefIdAndState(UUID chefId, Recipe.RecipeState state, Pageable pageable);

    // Find published recipes for a set of followed chef IDs (the feed)
    @Query("SELECT r FROM Recipe r WHERE r.chef.id IN :chefIds AND r.state = 'PUBLISHED' " +
           "AND (:from IS NULL OR r.publishedAt >= :from) " +
           "AND (:to IS NULL OR r.publishedAt <= :to)")
    Page<Recipe> findFeedForChefs(
        @Param("chefIds") Set<UUID> chefIds,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable
    );

    // Public recipes with optional date range filter
    @Query("SELECT r FROM Recipe r WHERE r.state = 'PUBLISHED' " +
           "AND (:chefId IS NULL OR r.chef.id = :chefId) " +
           "AND (:from IS NULL OR r.publishedAt >= :from) " +
           "AND (:to IS NULL OR r.publishedAt <= :to)")
    Page<Recipe> findPublishedWithFilters(
        @Param("chefId") UUID chefId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable
    );

    Optional<Recipe> findByIdAndChefId(UUID id, UUID chefId);

    long countByChefId(UUID chefId);
}
