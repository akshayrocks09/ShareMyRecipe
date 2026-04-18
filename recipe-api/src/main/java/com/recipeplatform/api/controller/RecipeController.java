package com.recipeplatform.api.controller;

import com.recipeplatform.api.dto.request.RecipeRequest;
import com.recipeplatform.api.dto.response.PagedResponse;
import com.recipeplatform.api.dto.response.RecipeResponse;
import com.recipeplatform.api.service.RecipeService;
import com.recipeplatform.api.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final StorageService storageService;

    // ── Public ────────────────────────────────────────────────────────────────

    /**
     * GET /v1/recipes
     * Public listing with filters, keyword search, pagination.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<RecipeResponse.Summary>> listRecipes(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant publishedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant publishedTo,
            @RequestParam(required = false) UUID chefId,
            @RequestParam(required = false) String chefHandle,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {

        return ResponseEntity.ok(recipeService.listPublic(
                q, publishedFrom, publishedTo, chefId, chefHandle, page, pageSize));
    }

    /**
     * GET /v1/recipes/{id}
     * Public recipe detail.
     */
    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse.Detail> getRecipe(@PathVariable UUID id) {
        return ResponseEntity.ok(recipeService.getById(id));
    }

    // ── Authoring (CHEF or ADMIN) ─────────────────────────────────────────────

    /**
     * POST /v1/recipes
     * Create a new recipe as a draft.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CHEF','ADMIN')")
    public ResponseEntity<RecipeResponse.Detail> createRecipe(
            @Valid @RequestBody RecipeRequest.Create req,
            Authentication auth) {

        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recipeService.create(userId, req));
    }

    /**
     * PATCH /v1/recipes/{id}
     * Update recipe fields (draft or published).
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN')")
    public ResponseEntity<RecipeResponse.Detail> updateRecipe(
            @PathVariable UUID id,
            @Valid @RequestBody RecipeRequest.Update req,
            Authentication auth) {

        UUID userId = (UUID) auth.getPrincipal();
        boolean isAdmin = isAdmin(auth);
        return ResponseEntity.ok(recipeService.update(userId, id, req, isAdmin));
    }

    /**
     * PATCH /v1/recipes/{id}/publish
     * Transition recipe from DRAFT → PUBLISHED and emit to queue.
     */
    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN')")
    public ResponseEntity<RecipeResponse.Detail> publishRecipe(
            @PathVariable UUID id,
            Authentication auth) {

        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.accepted()  // 202 — async processing in progress
                .body(recipeService.publish(userId, id, isAdmin(auth)));
    }

    /**
     * PATCH /v1/recipes/{id}/unpublish
     * Revert a published recipe back to DRAFT.
     */
    @PatchMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN')")
    public ResponseEntity<RecipeResponse.Detail> unpublishRecipe(
            @PathVariable UUID id,
            Authentication auth) {

        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(recipeService.unpublish(userId, id, isAdmin(auth)));
    }

    /**
     * DELETE /v1/recipes/{id}
     * Hard-delete a recipe.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN')")
    public ResponseEntity<Void> deleteRecipe(@PathVariable UUID id, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        recipeService.delete(userId, id, isAdmin(auth));
        return ResponseEntity.noContent().build();
    }

    // ── Image management ──────────────────────────────────────────────────────

    /**
     * GET /v1/recipes/{id}/images/upload-url?filename=photo.jpg
     * Returns a pre-signed S3 PUT URL. Client uploads directly to S3,
     * then passes the returned key to POST /images.
     */
    @GetMapping("/{id}/images/upload-url")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN')")
    public ResponseEntity<Map<String, String>> getUploadUrl(
            @PathVariable UUID id,
            @RequestParam String filename) {

        return ResponseEntity.ok(storageService.generateUploadUrl(id, filename));
    }

    /**
     * POST /v1/recipes/{id}/images
     * Register S3 keys for images that were directly uploaded by the client.
     * The worker will pick them up and resize them asynchronously.
     */
    @PostMapping("/{id}/images")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN')")
    public ResponseEntity<List<RecipeResponse.ImageDto>> addImages(
            @PathVariable UUID id,
            @Valid @RequestBody RecipeRequest.ImageUpload req,
            Authentication auth) {

        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recipeService.addImages(userId, id, req, isAdmin(auth)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
