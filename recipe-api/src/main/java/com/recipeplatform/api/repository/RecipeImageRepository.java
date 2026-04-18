package com.recipeplatform.api.repository;

import com.recipeplatform.api.entity.RecipeImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeImageRepository extends JpaRepository<RecipeImage, UUID> {
    List<RecipeImage> findByRecipeIdOrderBySortOrderAsc(UUID recipeId);
    void deleteByRecipeId(UUID recipeId);
}
