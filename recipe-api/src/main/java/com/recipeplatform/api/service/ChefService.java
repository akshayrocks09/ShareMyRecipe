package com.recipeplatform.api.service;

import com.recipeplatform.api.dto.response.ChefResponse;
import com.recipeplatform.api.dto.response.PagedResponse;
import com.recipeplatform.api.entity.ChefProfile;
import com.recipeplatform.api.exception.ApiException;
import com.recipeplatform.api.repository.ChefProfileRepository;
import com.recipeplatform.api.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChefService {

    private final ChefProfileRepository chefProfileRepository;
    private final RecipeRepository recipeRepository;

    public ChefResponse.Detail getByHandle(String handle, UUID callerId) {
        ChefProfile chef = chefProfileRepository.findByHandle(handle)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef not found: " + handle));
        return toDetail(chef, callerId);
    }

    public ChefResponse.Detail getById(UUID chefId, UUID callerId) {
        ChefProfile chef = chefProfileRepository.findById(chefId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef not found"));
        return toDetail(chef, callerId);
    }

    public PagedResponse<ChefResponse.Brief> getFollowers(UUID chefId, int page, int pageSize) {
        ChefProfile chef = chefProfileRepository.findById(chefId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef not found"));

        List<ChefResponse.Brief> followers = chef.getFollowers().stream()
                .map(this::toBrief)
                .toList();

        // Manual pagination on the in-memory set
        int start = Math.min((page - 1) * pageSize, followers.size());
        int end   = Math.min(start + pageSize, followers.size());
        return PagedResponse.of(followers.subList(start, end), page, pageSize, followers.size());
    }

    public PagedResponse<ChefResponse.Brief> getFollowing(UUID chefId, int page, int pageSize) {
        ChefProfile chef = chefProfileRepository.findById(chefId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef not found"));

        List<ChefResponse.Brief> following = chef.getFollowing().stream()
                .map(this::toBrief)
                .toList();

        int start = Math.min((page - 1) * pageSize, following.size());
        int end   = Math.min(start + pageSize, following.size());
        return PagedResponse.of(following.subList(start, end), page, pageSize, following.size());
    }

    @Transactional
    public void follow(UUID callerId, UUID targetChefId) {
        ChefProfile follower = chefProfileRepository.findByUserId(callerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef profile not found"));

        ChefProfile target = chefProfileRepository.findById(targetChefId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Target chef not found"));

        if (follower.getId().equals(target.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot follow yourself");
        }

        if (follower.getFollowing().contains(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "Already following this chef");
        }

        follower.getFollowing().add(target);
        target.getFollowers().add(follower);
        chefProfileRepository.save(follower);
        chefProfileRepository.save(target);
    }

    @Transactional
    public void unfollow(UUID callerId, UUID targetChefId) {
        ChefProfile follower = chefProfileRepository.findByUserId(callerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef profile not found"));

        ChefProfile target = chefProfileRepository.findById(targetChefId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Target chef not found"));

        if (!follower.getFollowing().contains(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "Not following this chef");
        }

        follower.getFollowing().remove(target);
        target.getFollowers().remove(follower);
        chefProfileRepository.save(follower);
        chefProfileRepository.save(target);
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private ChefResponse.Brief toBrief(ChefProfile c) {
        return ChefResponse.Brief.builder()
                .id(c.getId())
                .handle(c.getHandle())
                .displayName(c.getDisplayName())
                .avatarUrl(c.getAvatarUrl())
                .build();
    }

    private ChefResponse.Detail toDetail(ChefProfile c, UUID callerId) {
        boolean isFollowed = false;
        if (callerId != null) {
            isFollowed = chefProfileRepository.findByUserId(callerId)
                    .map(caller -> chefProfileRepository.isFollowing(caller.getId(), c.getId()))
                    .orElse(false);
        }

        long recipeCount = recipeRepository.countByChefId(c.getId());

        return ChefResponse.Detail.builder()
                .id(c.getId())
                .handle(c.getHandle())
                .displayName(c.getDisplayName())
                .bio(c.getBio())
                .avatarUrl(c.getAvatarUrl())
                .recipeCount(recipeCount)
                .followerCount(c.getFollowers().size())
                .followingCount(c.getFollowing().size())
                .isFollowedByMe(isFollowed)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
