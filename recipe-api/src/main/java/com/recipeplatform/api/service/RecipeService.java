package com.recipeplatform.api.service;

import com.recipeplatform.api.config.RabbitMQConfig;
import com.recipeplatform.api.dto.RecipeEventMessage;
import com.recipeplatform.api.dto.RecipeEventMessage.ImageKey;
import com.recipeplatform.api.dto.request.RecipeRequest;
import com.recipeplatform.api.dto.response.ChefResponse;
import com.recipeplatform.api.dto.response.PagedResponse;
import com.recipeplatform.api.dto.response.RecipeResponse;
import com.recipeplatform.api.entity.ChefProfile;
import com.recipeplatform.api.entity.Recipe;
import com.recipeplatform.api.entity.Recipe.IngredientItem;
import com.recipeplatform.api.entity.Recipe.RecipeState;
import com.recipeplatform.api.entity.Recipe.StepItem;
import com.recipeplatform.api.entity.RecipeImage;
import com.recipeplatform.api.exception.ApiException;
import com.recipeplatform.api.repository.ChefProfileRepository;
import com.recipeplatform.api.repository.RecipeImageRepository;
import com.recipeplatform.api.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RecipeRepository recipeRepository;
    private final RecipeImageRepository recipeImageRepository;
    private final ChefProfileRepository chefProfileRepository;
    private final RabbitTemplate rabbitTemplate;

    // ── Public / Feed queries ────────────────────────────────────────────────

    public PagedResponse<RecipeResponse.Summary> listPublic(
            String q, Instant from, Instant to, UUID chefId, String chefHandle,
            int page, int pageSize) {

        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "publishedAt"));

        // If a keyword is provided, delegate to Elasticsearch (handled at controller level)
        // Here we handle the DB-only path (no keyword)
        UUID resolvedChefId = chefId;
        if (resolvedChefId == null && StringUtils.hasText(chefHandle)) {
            resolvedChefId = chefProfileRepository.findByHandle(chefHandle)
                    .map(ChefProfile::getId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "Chef not found: " + chefHandle));
        }

        Page<Recipe> resultPage = recipeRepository.findPublishedWithFilters(
                resolvedChefId, from, to, pageable);

        return PagedResponse.of(resultPage.map(this::toSummary));
    }

    public PagedResponse<RecipeResponse.Summary> getFeed(
            UUID callerId, Instant from, Instant to, int page, int pageSize) {

        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);

        ChefProfile caller = chefProfileRepository.findByUserId(callerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef profile not found"));

        var followedIds = caller.getFollowing().stream()
                .map(ChefProfile::getId)
                .collect(Collectors.toSet());

        if (followedIds.isEmpty()) {
            return PagedResponse.of(Page.empty(PageRequest.of(page - 1, pageSize)));
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "publishedAt"));

        Page<Recipe> resultPage = recipeRepository.findFeedForChefs(followedIds, from, to, pageable);
        return PagedResponse.of(resultPage.map(this::toSummary));
    }

    public RecipeResponse.Detail getById(UUID id) {
        Recipe recipe = recipeRepository.findById(id)
                .filter(r -> r.getState() == RecipeState.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recipe not found"));
        return toDetail(recipe);
    }

    // ── Authoring ────────────────────────────────────────────────────────────

    @Transactional
    public RecipeResponse.Detail create(UUID userId, RecipeRequest.Create req) {
        ChefProfile chef = chefProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef profile not found"));

        Recipe recipe = Recipe.builder()
                .chef(chef)
                .title(req.getTitle())
                .summary(req.getSummary())
                .ingredients(mapIngredients(req.getIngredients()))
                .steps(mapSteps(req.getSteps()))
                .labels(req.getLabels() != null ? req.getLabels() : new ArrayList<>())
                .state(RecipeState.DRAFT)
                .build();

        recipe = recipeRepository.save(recipe);

        // Register any pre-uploaded image keys
        if (req.getImageKeys() != null && !req.getImageKeys().isEmpty()) {
            attachImages(recipe, req.getImageKeys());
        }

        return toDetail(recipe);
    }

    @Transactional
    public RecipeResponse.Detail update(UUID userId, UUID recipeId, RecipeRequest.Update req, boolean isAdmin) {
        Recipe recipe = findOwned(userId, recipeId, isAdmin);

        if (StringUtils.hasText(req.getTitle()))   recipe.setTitle(req.getTitle());
        if (StringUtils.hasText(req.getSummary())) recipe.setSummary(req.getSummary());
        if (req.getIngredients() != null)           recipe.setIngredients(mapIngredients(req.getIngredients()));
        if (req.getSteps() != null)                 recipe.setSteps(mapSteps(req.getSteps()));
        if (req.getLabels() != null)                recipe.setLabels(req.getLabels());

        recipe = recipeRepository.save(recipe);

        // If already published, push an update event so the search index stays fresh
        if (recipe.getState() == RecipeState.PUBLISHED) {
            publishEvent(recipe, RecipeEventMessage.EventType.UPDATED);
        }

        return toDetail(recipe);
    }

    @Transactional
    public RecipeResponse.Detail publish(UUID userId, UUID recipeId, boolean isAdmin) {
        Recipe recipe = findOwned(userId, recipeId, isAdmin);

        if (recipe.getState() == RecipeState.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "Recipe is already published");
        }

        recipe.setState(RecipeState.PUBLISHED);
        recipe.setPublishedAt(Instant.now());
        recipe = recipeRepository.save(recipe);

        // Emit to queue — worker does image processing + search indexing async
        publishEvent(recipe, RecipeEventMessage.EventType.PUBLISHED);

        log.info("Recipe {} published by chef {}, event dispatched to queue", recipeId, userId);
        return toDetail(recipe);
    }

    @Transactional
    public RecipeResponse.Detail unpublish(UUID userId, UUID recipeId, boolean isAdmin) {
        Recipe recipe = findOwned(userId, recipeId, isAdmin);

        if (recipe.getState() == RecipeState.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Recipe is already a draft");
        }

        recipe.setState(RecipeState.DRAFT);
        recipe = recipeRepository.save(recipe);

        publishEvent(recipe, RecipeEventMessage.EventType.DELETED); // remove from search index
        return toDetail(recipe);
    }

    @Transactional
    public void delete(UUID userId, UUID recipeId, boolean isAdmin) {
        Recipe recipe = findOwned(userId, recipeId, isAdmin);
        publishEvent(recipe, RecipeEventMessage.EventType.DELETED);
        recipeRepository.delete(recipe);
    }

    @Transactional
    public List<RecipeResponse.ImageDto> addImages(UUID userId, UUID recipeId,
                                                   RecipeRequest.ImageUpload req, boolean isAdmin) {
        Recipe recipe = findOwned(userId, recipeId, isAdmin);
        List<RecipeImage> images = attachImages(recipe, req.getImageKeys());

        // Re-emit a publish event so the worker processes the new images
        if (recipe.getState() == RecipeState.PUBLISHED) {
            publishEvent(recipe, RecipeEventMessage.EventType.UPDATED);
        }

        return images.stream().map(RecipeResponse::fromEntity).toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Recipe findOwned(UUID userId, UUID recipeId, boolean isAdmin) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recipe not found"));

        if (!isAdmin) {
            ChefProfile chef = chefProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Not a chef account"));
            if (!recipe.getChef().getId().equals(chef.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You do not own this recipe");
            }
        }
        return recipe;
    }

    private List<RecipeImage> attachImages(Recipe recipe, List<String> keys) {
        int existingCount = recipe.getImages().size();
        List<RecipeImage> saved = new ArrayList<>();

        for (int i = 0; i < keys.size(); i++) {
            RecipeImage img = RecipeImage.builder()
                    .recipe(recipe)
                    .originalKey(keys.get(i))
                    .sortOrder(existingCount + i)
                    .processingState(RecipeImage.ProcessingState.PENDING)
                    .build();
            saved.add(recipeImageRepository.save(img));
        }

        recipe.getImages().addAll(saved);
        return saved;
    }

    private void publishEvent(Recipe recipe, RecipeEventMessage.EventType eventType) {
        List<ImageKey> imageKeys = recipe.getImages().stream()
                .filter(img -> img.getProcessingState() == RecipeImage.ProcessingState.PENDING
                            || eventType == RecipeEventMessage.EventType.UPDATED)
                .map(img -> ImageKey.builder()
                        .imageId(img.getId())
                        .s3Key(img.getOriginalKey())
                        .sortOrder(img.getSortOrder())
                        .build())
                .toList();

        String ingredientsText = recipe.getIngredients() == null ? "" :
                recipe.getIngredients().stream()
                        .map(IngredientItem::getName)
                        .collect(Collectors.joining(" "));

        String stepsText = recipe.getSteps() == null ? "" :
                recipe.getSteps().stream()
                        .map(StepItem::getInstruction)
                        .collect(Collectors.joining(" "));

        RecipeEventMessage message = RecipeEventMessage.builder()
                .eventType(eventType)
                .recipeId(recipe.getId())
                .chefId(recipe.getChef().getId())
                .chefHandle(recipe.getChef().getHandle())
                .chefDisplayName(recipe.getChef().getDisplayName())
                .title(recipe.getTitle())
                .summary(recipe.getSummary())
                .ingredientsText(ingredientsText)
                .stepsText(stepsText)
                .labels(recipe.getLabels())
                .publishedAt(recipe.getPublishedAt())
                .imageKeys(imageKeys)
                .build();

        String routingKey = switch (eventType) {
            case PUBLISHED -> RabbitMQConfig.ROUTING_PUBLISHED;
            case UPDATED   -> RabbitMQConfig.ROUTING_UPDATED;
            case DELETED   -> RabbitMQConfig.ROUTING_DELETED;
        };

        rabbitTemplate.convertAndSend(RabbitMQConfig.RECIPE_EXCHANGE, routingKey, message);
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private List<IngredientItem> mapIngredients(List<RecipeRequest.IngredientDto> dtos) {
        if (dtos == null) return new ArrayList<>();
        return dtos.stream()
                .map(d -> new IngredientItem(d.getName(), d.getQuantity(), d.getUnit()))
                .toList();
    }

    private List<StepItem> mapSteps(List<RecipeRequest.StepDto> dtos) {
        if (dtos == null) return new ArrayList<>();
        return dtos.stream()
                .map(d -> new StepItem(d.getStepNumber(), d.getInstruction()))
                .toList();
    }

    private ChefResponse.Brief toChefBrief(ChefProfile c) {
        return ChefResponse.Brief.builder()
                .id(c.getId())
                .handle(c.getHandle())
                .displayName(c.getDisplayName())
                .avatarUrl(c.getAvatarUrl())
                .build();
    }

    private RecipeResponse.Summary toSummary(Recipe r) {
        String thumbUrl = r.getImages().isEmpty() ? null
                : r.getImages().get(0).getThumbUrl();
        return RecipeResponse.Summary.builder()
                .id(r.getId())
                .title(r.getTitle())
                .summary(r.getSummary())
                .labels(r.getLabels())
                .state(r.getState().name())
                .publishedAt(r.getPublishedAt())
                .createdAt(r.getCreatedAt())
                .chef(toChefBrief(r.getChef()))
                .thumbUrl(thumbUrl)
                .build();
    }

    private RecipeResponse.Detail toDetail(Recipe r) {
        return RecipeResponse.Detail.builder()
                .id(r.getId())
                .title(r.getTitle())
                .summary(r.getSummary())
                .ingredients(r.getIngredients())
                .steps(r.getSteps())
                .labels(r.getLabels())
                .state(r.getState().name())
                .publishedAt(r.getPublishedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .chef(toChefBrief(r.getChef()))
                .images(r.getImages().stream().map(RecipeResponse::fromEntity).toList())
                .build();
    }
}
