package com.recipeplatform.api.controller;

import com.recipeplatform.api.entity.ChefProfile;
import com.recipeplatform.api.entity.User;
import com.recipeplatform.api.repository.ChefProfileRepository;
import com.recipeplatform.api.repository.UserRepository;
import com.recipeplatform.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChefControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ChefProfileRepository chefProfileRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired PasswordEncoder passwordEncoder;

    private String followerToken;
    private ChefProfile followerProfile;
    private ChefProfile targetProfile;

    @BeforeEach
    void setUp() {
        // Follower
        User followerUser = User.builder()
                .email("follower@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .role(User.Role.CHEF)
                .build();
        followerUser = userRepository.save(followerUser);
        followerProfile = ChefProfile.builder()
                .user(followerUser)
                .handle("follower")
                .displayName("Follower Chef")
                .build();
        followerProfile = chefProfileRepository.save(followerProfile);
        followerToken = "Bearer " + jwtTokenProvider.generateAccessToken(
                followerUser.getId(), followerUser.getEmail(), followerUser.getRole().name());

        // Target
        User targetUser = User.builder()
                .email("target@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .role(User.Role.CHEF)
                .build();
        targetUser = userRepository.save(targetUser);
        targetProfile = ChefProfile.builder()
                .user(targetUser)
                .handle("target")
                .displayName("Target Chef")
                .build();
        targetProfile = chefProfileRepository.save(targetProfile);
    }

    @Test
    @DisplayName("GET /v1/chefs/{id} — returns chef detail with follow count")
    void getChefById_returnsDetail() throws Exception {
        mockMvc.perform(get("/v1/chefs/" + targetProfile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("target"))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followingCount").value(0));
    }

    @Test
    @DisplayName("GET /v1/chefs/handle/{handle} — resolves chef by handle")
    void getChefByHandle_found() throws Exception {
        mockMvc.perform(get("/v1/chefs/handle/target"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("target"));
    }

    @Test
    @DisplayName("GET /v1/chefs/handle/{handle} — unknown handle returns 404")
    void getChefByHandle_notFound() throws Exception {
        mockMvc.perform(get("/v1/chefs/handle/doesnotexist"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /v1/chefs/{id}/follow — creates follow relationship")
    void follow_success() throws Exception {
        mockMvc.perform(post("/v1/chefs/" + targetProfile.getId() + "/follow")
                        .header("Authorization", followerToken))
                .andExpect(status().isNoContent());

        // Verify via GET
        mockMvc.perform(get("/v1/chefs/" + targetProfile.getId())
                        .header("Authorization", followerToken))
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.isFollowedByMe").value(true));
    }

    @Test
    @DisplayName("POST /v1/chefs/{id}/follow — follow same chef twice returns 409")
    void follow_duplicate_conflict() throws Exception {
        mockMvc.perform(post("/v1/chefs/" + targetProfile.getId() + "/follow")
                        .header("Authorization", followerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/chefs/" + targetProfile.getId() + "/follow")
                        .header("Authorization", followerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /v1/chefs/{id}/follow — removes follow relationship")
    void unfollow_success() throws Exception {
        // First follow
        mockMvc.perform(post("/v1/chefs/" + targetProfile.getId() + "/follow")
                        .header("Authorization", followerToken))
                .andExpect(status().isNoContent());

        // Then unfollow
        mockMvc.perform(delete("/v1/chefs/" + targetProfile.getId() + "/follow")
                        .header("Authorization", followerToken))
                .andExpect(status().isNoContent());

        // Verify count dropped
        mockMvc.perform(get("/v1/chefs/" + targetProfile.getId()))
                .andExpect(jsonPath("$.followerCount").value(0));
    }

    @Test
    @DisplayName("POST /v1/chefs/{id}/follow — unauthenticated returns 403")
    void follow_unauthenticated_forbidden() throws Exception {
        mockMvc.perform(post("/v1/chefs/" + targetProfile.getId() + "/follow"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/chefs/{id}/followers — returns paginated follower list")
    void getFollowers_paginatedList() throws Exception {
        // Follow the target
        mockMvc.perform(post("/v1/chefs/" + targetProfile.getId() + "/follow")
                        .header("Authorization", followerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/chefs/" + targetProfile.getId() + "/followers")
                        .param("page", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].handle").value("follower"))
                .andExpect(jsonPath("$.meta.total").value(1));
    }
}
