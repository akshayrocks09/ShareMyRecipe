package com.recipeplatform.api.controller;

import com.recipeplatform.api.dto.response.ChefResponse;
import com.recipeplatform.api.dto.response.PagedResponse;
import com.recipeplatform.api.service.ChefService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/chefs")
@RequiredArgsConstructor
public class ChefController {

    private final ChefService chefService;

    /** GET /v1/chefs/{id} — public chef profile by UUID */
    @GetMapping("/{id}")
    public ResponseEntity<ChefResponse.Detail> getChefById(
            @PathVariable UUID id,
            Authentication auth) {

        UUID callerId = auth != null ? (UUID) auth.getPrincipal() : null;
        return ResponseEntity.ok(chefService.getById(id, callerId));
    }

    /** GET /v1/chefs/handle/{handle} — public chef profile by handle */
    @GetMapping("/handle/{handle}")
    public ResponseEntity<ChefResponse.Detail> getChefByHandle(
            @PathVariable String handle,
            Authentication auth) {

        UUID callerId = auth != null ? (UUID) auth.getPrincipal() : null;
        return ResponseEntity.ok(chefService.getByHandle(handle, callerId));
    }

    /** GET /v1/chefs/{id}/followers */
    @GetMapping("/{id}/followers")
    public ResponseEntity<PagedResponse<ChefResponse.Brief>> getFollowers(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {

        return ResponseEntity.ok(chefService.getFollowers(id, page, pageSize));
    }

    /** GET /v1/chefs/{id}/following */
    @GetMapping("/{id}/following")
    public ResponseEntity<PagedResponse<ChefResponse.Brief>> getFollowing(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {

        return ResponseEntity.ok(chefService.getFollowing(id, page, pageSize));
    }

    /** POST /v1/chefs/{id}/follow — follow a chef (authenticated) */
    @PostMapping("/{id}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> follow(@PathVariable UUID id, Authentication auth) {
        UUID callerId = (UUID) auth.getPrincipal();
        chefService.follow(callerId, id);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /v1/chefs/{id}/follow — unfollow a chef (authenticated) */
    @DeleteMapping("/{id}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unfollow(@PathVariable UUID id, Authentication auth) {
        UUID callerId = (UUID) auth.getPrincipal();
        chefService.unfollow(callerId, id);
        return ResponseEntity.noContent().build();
    }
}
