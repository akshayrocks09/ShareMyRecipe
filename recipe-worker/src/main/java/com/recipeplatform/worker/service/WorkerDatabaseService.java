package com.recipeplatform.worker.service;

import com.recipeplatform.worker.service.ImageProcessingService.ProcessedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Thin JDBC layer so the worker doesn't need the full JPA entity graph.
 * Writes processed image URLs back to recipe_images and updates
 * processing_state to reflect current status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerDatabaseService {

    private final JdbcTemplate jdbc;

    @Transactional
    public void markImageProcessing(UUID imageId) {
        int rows = jdbc.update(
                "UPDATE recipe_images SET processing_state = 'PROCESSING' WHERE id = ?",
                imageId);
        if (rows == 0) {
            log.warn("Image {} not found when marking PROCESSING", imageId);
        }
    }

    @Transactional
    public void saveProcessedImage(UUID imageId, ProcessedImage result) {
        int rows = jdbc.update(
                """
                UPDATE recipe_images
                   SET original_url     = ?,
                       thumb_url        = ?,
                       medium_url       = ?,
                       processing_state = 'DONE'
                 WHERE id = ?
                """,
                result.originalUrl(), result.thumbUrl(), result.mediumUrl(), imageId);

        if (rows == 0) {
            log.warn("Image {} not found when saving processed URLs", imageId);
        } else {
            log.info("Saved processed image URLs for {}", imageId);
        }
    }

    @Transactional
    public void markImageFailed(UUID imageId) {
        jdbc.update(
                "UPDATE recipe_images SET processing_state = 'FAILED' WHERE id = ?",
                imageId);
    }

    /** Returns the thumb_url of the first image for a recipe (for search index). */
    public String getFirstThumbUrl(UUID recipeId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT thumb_url FROM recipe_images
                     WHERE recipe_id = ? AND processing_state = 'DONE'
                     ORDER BY sort_order ASC
                     LIMIT 1
                    """,
                    String.class, recipeId);
        } catch (Exception e) {
            return null;
        }
    }
}
