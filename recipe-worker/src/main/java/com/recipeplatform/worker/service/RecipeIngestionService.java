package com.recipeplatform.worker.service;

import com.recipeplatform.worker.dto.RecipeEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full ingestion pipeline for a recipe event:
 *   1. For each image key → download, resize, upload, write URLs to DB
 *   2. Fetch the resulting thumb URL
 *   3. Upsert or delete the Elasticsearch document
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeIngestionService {

    private final ImageProcessingService imageProcessingService;
    private final WorkerDatabaseService  workerDatabaseService;
    private final SearchIndexService     searchIndexService;

    public void handlePublished(RecipeEventMessage msg) {
        log.info("Ingesting published recipe {}", msg.getRecipeId());
        processImages(msg);
        String thumbUrl = workerDatabaseService.getFirstThumbUrl(msg.getRecipeId());
        searchIndexService.upsert(msg, thumbUrl);
    }

    public void handleUpdated(RecipeEventMessage msg) {
        log.info("Re-ingesting updated recipe {}", msg.getRecipeId());
        processImages(msg);
        String thumbUrl = workerDatabaseService.getFirstThumbUrl(msg.getRecipeId());
        searchIndexService.upsert(msg, thumbUrl);
    }

    public void handleDeleted(RecipeEventMessage msg) {
        log.info("Removing deleted recipe {} from index", msg.getRecipeId());
        searchIndexService.delete(msg.getRecipeId().toString());
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void processImages(RecipeEventMessage msg) {
        if (msg.getImageKeys() == null || msg.getImageKeys().isEmpty()) {
            log.debug("No images to process for recipe {}", msg.getRecipeId());
            return;
        }

        for (RecipeEventMessage.ImageKey imageKey : msg.getImageKeys()) {
            if (imageKey.getS3Key() == null || imageKey.getS3Key().isBlank()) {
                log.warn("Skipping image {} — missing S3 key", imageKey.getImageId());
                continue;
            }

            try {
                workerDatabaseService.markImageProcessing(imageKey.getImageId());

                ImageProcessingService.ProcessedImage result =
                        imageProcessingService.process(imageKey.getImageId(), imageKey.getS3Key());

                workerDatabaseService.saveProcessedImage(imageKey.getImageId(), result);

            } catch (Exception e) {
                log.error("Failed to process image {} for recipe {}: {}",
                        imageKey.getImageId(), msg.getRecipeId(), e.getMessage(), e);
                workerDatabaseService.markImageFailed(imageKey.getImageId());
                // Do not rethrow — continue with other images; the consumer handles DLQ routing
            }
        }
    }
}
