package com.recipeplatform.api.controller;

import com.recipeplatform.api.dto.response.PagedResponse;
import com.recipeplatform.api.dto.response.RecipeResponse;
import com.recipeplatform.api.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/v1/feed")
@RequiredArgsConstructor
public class FeedController {

    private final RecipeService recipeService;

    /**
     * GET /v1/feed
     * Returns published recipes from all chefs the caller follows.
     * Supports date-range filtering and pagination.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<RecipeResponse.Summary>> getFeed(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant publishedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant publishedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            Authentication auth) {

        UUID callerId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(
                recipeService.getFeed(callerId, publishedFrom, publishedTo, page, pageSize));
    }
}
