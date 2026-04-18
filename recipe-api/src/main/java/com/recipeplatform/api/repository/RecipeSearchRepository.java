package com.recipeplatform.api.repository;

import com.recipeplatform.api.config.RecipeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeSearchRepository extends ElasticsearchRepository<RecipeDocument, String> {

    // Multi-field full-text search across title, summary, ingredients, steps
    @Query("""
        {
          "bool": {
            "must": [
              {
                "multi_match": {
                  "query": "?0",
                  "fields": ["title^3", "summary^2", "ingredientsText", "stepsText"],
                  "type": "best_fields",
                  "fuzziness": "AUTO"
                }
              }
            ]
          }
        }
        """)
    Page<RecipeDocument> searchByKeyword(String keyword, Pageable pageable);

    Page<RecipeDocument> findByChefId(String chefId, Pageable pageable);

    Page<RecipeDocument> findByChefHandle(String chefHandle, Pageable pageable);

    Page<RecipeDocument> findByLabelsContaining(String label, Pageable pageable);

    List<RecipeDocument> findByChefIdIn(List<String> chefIds, Pageable pageable);
}
