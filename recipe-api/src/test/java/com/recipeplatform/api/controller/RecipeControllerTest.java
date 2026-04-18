package com.recipeplatform.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipeplatform.api.dto.request.RecipeRequest;
import com.recipeplatform.api.entity.ChefProfile;
import com.recipeplatform.api.entity.Recipe;
import com.recipeplatform.api.entity.User;
import com.recipeplatform.api.repository.ChefProfileRepository;
import com.recipeplatform.api.repository.RecipeRepository;
import com.recipeplatform.api.repository.UserRepository;
import com.recipeplatform.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecipeControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ChefProfileRepository chefProfileRepository;
    @Autowired RecipeRepository recipeRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired PasswordEncoder passwordEncoder;

    // Mock out RabbitMQ so tests don't need a broker
    @MockBean RabbitTemplate rabbitTemplate;

    private String chefToken;
    private ChefProfile chefProfile;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .email("chef@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .role(User.Role.CHEF)
                .build();
        user = userRepository.save(user);

        chefProfile = ChefProfile.builder()
                .user(user)
                .handle("testchef")
                .displayName("Test Chef")
                .build();
        chefProfile = chefProfileRepository.save(chefProfile);

        chefToken = "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
    }

    @Test
    @DisplayName("POST /v1/recipes — creates draft recipe for authenticated chef")
    void createRecipe_success() throws Exception {
        RecipeRequest.Create req = new RecipeRequest.Create();
        req.setTitle("Spaghetti Carbonara");
        req.setSummary("Classic Italian pasta dish");
        req.setLabels(List.of("italian", "pasta"));
        req.setIngredients(List.of(
                buildIngredient("spaghetti", "400", "g"),
                buildIngredient("eggs", "4", null)
        ));
        req.setSteps(List.of(
                buildStep(1, "Boil salted water and cook pasta"),
                buildStep(2, "Mix eggs and cheese")
        ));

        mockMvc.perform(post("/v1/recipes")
                        .header("Authorization", chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Spaghetti Carbonara"))
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.chef.handle").value("testchef"));
    }

    @Test
    @DisplayName("POST /v1/recipes — unauthenticated request returns 403")
    void createRecipe_unauthenticated_forbidden() throws Exception {
        RecipeRequest.Create req = new RecipeRequest.Create();
        req.setTitle("Test Recipe");

        mockMvc.perform(post("/v1/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /v1/recipes/{id}/publish — transitions recipe to PUBLISHED and returns 202")
    void publishRecipe_success() throws Exception {
        // Create a draft recipe directly
        Recipe draft = Recipe.builder()
                .chef(chefProfile)
                .title("Draft Recipe")
                .summary("A draft")
                .state(Recipe.RecipeState.DRAFT)
                .build();
        draft = recipeRepository.save(draft);

        mockMvc.perform(patch("/v1/recipes/" + draft.getId() + "/publish")
                        .header("Authorization", chefToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /v1/recipes — public listing returns paginated results with meta")
    void listRecipes_publicEndpoint_hasMeta() throws Exception {
        // Seed a published recipe
        Recipe published = Recipe.builder()
                .chef(chefProfile)
                .title("Published Recipe")
                .summary("Visible to everyone")
                .state(Recipe.RecipeState.PUBLISHED)
                .build();
        recipeRepository.save(published);

        mockMvc.perform(get("/v1/recipes")
                        .param("page", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(10))
                .andExpect(jsonPath("$.meta.total").isNumber())
                .andExpect(jsonPath("$.meta.totalPages").isNumber());
    }

    @Test
    @DisplayName("DELETE /v1/recipes/{id} — chef cannot delete another chef's recipe")
    void deleteRecipe_notOwner_forbidden() throws Exception {
        // Create another chef
        User otherUser = User.builder()
                .email("other@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .role(User.Role.CHEF)
                .build();
        otherUser = userRepository.save(otherUser);
        ChefProfile otherChef = ChefProfile.builder()
                .user(otherUser)
                .handle("otherchef")
                .displayName("Other Chef")
                .build();
        otherChef = chefProfileRepository.save(otherChef);

        Recipe otherRecipe = Recipe.builder()
                .chef(otherChef)
                .title("Other's Recipe")
                .state(Recipe.RecipeState.DRAFT)
                .build();
        otherRecipe = recipeRepository.save(otherRecipe);

        mockMvc.perform(delete("/v1/recipes/" + otherRecipe.getId())
                        .header("Authorization", chefToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /v1/recipes/{id} — partial update only changes provided fields")
    void updateRecipe_partialUpdate() throws Exception {
        Recipe draft = Recipe.builder()
                .chef(chefProfile)
                .title("Original Title")
                .summary("Original Summary")
                .state(Recipe.RecipeState.DRAFT)
                .build();
        draft = recipeRepository.save(draft);

        RecipeRequest.Update update = new RecipeRequest.Update();
        update.setTitle("Updated Title");
        // summary NOT set — should remain unchanged

        mockMvc.perform(patch("/v1/recipes/" + draft.getId())
                        .header("Authorization", chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.summary").value("Original Summary"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RecipeRequest.IngredientDto buildIngredient(String name, String qty, String unit) {
        RecipeRequest.IngredientDto d = new RecipeRequest.IngredientDto();
        d.setName(name);
        d.setQuantity(qty);
        d.setUnit(unit);
        return d;
    }

    private RecipeRequest.StepDto buildStep(int num, String instruction) {
        RecipeRequest.StepDto d = new RecipeRequest.StepDto();
        d.setStepNumber(num);
        d.setInstruction(instruction);
        return d;
    }
}
