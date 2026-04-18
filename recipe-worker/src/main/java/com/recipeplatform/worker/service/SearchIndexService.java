package com.recipeplatform.worker.service;

import com.recipeplatform.worker.dto.RecipeDocument;
import com.recipeplatform.worker.dto.RecipeEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private static final String INDEX = "recipes";

    private final ElasticsearchTemplate elasticsearchTemplate;

    /**
     * Creates or replaces the recipe document in the search index.
     * thumbUrl is passed in after image processing completes.
     */
    public void upsert(RecipeEventMessage msg, String thumbUrl) {
        RecipeDocument doc = RecipeDocument.builder()
                .id(msg.getRecipeId().toString())
                .title(msg.getTitle())
                .summary(msg.getSummary())
                .ingredientsText(msg.getIngredientsText())
                .stepsText(msg.getStepsText())
                .labels(msg.getLabels())
                .chefId(msg.getChefId().toString())
                .chefHandle(msg.getChefHandle())
                .chefDisplayName(msg.getChefDisplayName())
                .publishedAt(msg.getPublishedAt())
                .thumbUrl(thumbUrl)
                .build();

        IndexQuery query = new IndexQueryBuilder()
                .withId(doc.getId())
                .withObject(doc)
                .build();

        elasticsearchTemplate.index(query, IndexCoordinates.of(INDEX));
        log.info("Indexed recipe {} in Elasticsearch", msg.getRecipeId());
    }

    /**
     * Removes the recipe document from the search index (on delete/unpublish).
     */
    public void delete(String recipeId) {
        try {
            elasticsearchTemplate.delete(recipeId, IndexCoordinates.of(INDEX));
            log.info("Deleted recipe {} from Elasticsearch", recipeId);
        } catch (Exception e) {
            log.warn("Could not delete recipe {} from Elasticsearch: {}", recipeId, e.getMessage());
        }
    }
}
