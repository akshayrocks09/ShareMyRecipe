package com.recipeplatform.worker.service;

import com.recipeplatform.worker.dto.RecipeEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RecipeIngestionServiceTest {

    @Mock ImageProcessingService imageProcessingService;
    @Mock WorkerDatabaseService   workerDatabaseService;
    @Mock SearchIndexService       searchIndexService;

    @InjectMocks RecipeIngestionService ingestionService;

    private RecipeEventMessage publishedMsg;
    private UUID recipeId;
    private UUID imageId;

    @BeforeEach
    void setUp() {
        recipeId = UUID.randomUUID();
        imageId  = UUID.randomUUID();

        publishedMsg = RecipeEventMessage.builder()
                .eventType(RecipeEventMessage.EventType.PUBLISHED)
                .recipeId(recipeId)
                .chefId(UUID.randomUUID())
                .chefHandle("testchef")
                .chefDisplayName("Test Chef")
                .title("Tasty Dish")
                .summary("A great recipe")
                .ingredientsText("flour eggs butter")
                .stepsText("mix bake serve")
                .labels(List.of("baking"))
                .publishedAt(Instant.now())
                .imageKeys(List.of(
                        RecipeEventMessage.ImageKey.builder()
                                .imageId(imageId)
                                .s3Key("uploads/recipes/recipe-id/photo.jpg")
                                .sortOrder(0)
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("handlePublished — processes images then indexes recipe")
    void handlePublished_fullPipeline() throws Exception {
        ImageProcessingService.ProcessedImage processed =
                new ImageProcessingService.ProcessedImage(
                        "http://cdn/original.jpg",
                        "http://cdn/thumb.jpg",
                        "http://cdn/medium.jpg"
                );
        given(imageProcessingService.process(eq(imageId), anyString())).willReturn(processed);
        given(workerDatabaseService.getFirstThumbUrl(recipeId)).willReturn("http://cdn/thumb.jpg");

        ingestionService.handlePublished(publishedMsg);

        then(workerDatabaseService).should().markImageProcessing(imageId);
        then(workerDatabaseService).should().saveProcessedImage(imageId, processed);
        then(searchIndexService).should().upsert(eq(publishedMsg), eq("http://cdn/thumb.jpg"));
        then(workerDatabaseService).should(never()).markImageFailed(any());
    }

    @Test
    @DisplayName("handlePublished — image processing failure marks image FAILED but still indexes")
    void handlePublished_imageFailure_continuesIndexing() throws Exception {
        given(imageProcessingService.process(eq(imageId), anyString()))
                .willThrow(new RuntimeException("S3 timeout"));
        given(workerDatabaseService.getFirstThumbUrl(recipeId)).willReturn(null);

        ingestionService.handlePublished(publishedMsg);

        then(workerDatabaseService).should().markImageFailed(imageId);
        // Indexing still proceeds with null thumbUrl
        then(searchIndexService).should().upsert(eq(publishedMsg), isNull());
    }

    @Test
    @DisplayName("handlePublished — recipe with no images skips image processing")
    void handlePublished_noImages_skipsProcessing() throws Exception {
        publishedMsg.setImageKeys(List.of());
        given(workerDatabaseService.getFirstThumbUrl(recipeId)).willReturn(null);

        ingestionService.handlePublished(publishedMsg);

        then(imageProcessingService).should(never()).process(any(), any());
        then(searchIndexService).should().upsert(eq(publishedMsg), isNull());
    }

    @Test
    @DisplayName("handleUpdated — re-processes images and refreshes index")
    void handleUpdated_refreshesPipeline() throws Exception {
        ImageProcessingService.ProcessedImage processed =
                new ImageProcessingService.ProcessedImage(
                        "http://cdn/original.jpg",
                        "http://cdn/thumb.jpg",
                        "http://cdn/medium.jpg"
                );
        publishedMsg.setEventType(RecipeEventMessage.EventType.UPDATED);
        given(imageProcessingService.process(eq(imageId), anyString())).willReturn(processed);
        given(workerDatabaseService.getFirstThumbUrl(recipeId)).willReturn("http://cdn/thumb.jpg");

        ingestionService.handleUpdated(publishedMsg);

        then(searchIndexService).should().upsert(eq(publishedMsg), anyString());
    }

    @Test
    @DisplayName("handleDeleted — removes recipe from search index only")
    void handleDeleted_removesFromIndex() {
        publishedMsg.setEventType(RecipeEventMessage.EventType.DELETED);

        ingestionService.handleDeleted(publishedMsg);

        then(searchIndexService).should().delete(recipeId.toString());
        then(imageProcessingService).shouldHaveNoInteractions();
        then(workerDatabaseService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handlePublished — image with null S3 key is skipped gracefully")
    void handlePublished_nullS3Key_skipped() throws Exception {
        publishedMsg.setImageKeys(List.of(
                RecipeEventMessage.ImageKey.builder()
                        .imageId(imageId)
                        .s3Key(null)   // no key
                        .sortOrder(0)
                        .build()
        ));
        given(workerDatabaseService.getFirstThumbUrl(recipeId)).willReturn(null);

        ingestionService.handlePublished(publishedMsg);

        then(imageProcessingService).should(never()).process(any(), any());
        then(searchIndexService).should().upsert(any(), isNull());
    }
}
