package com.recipeplatform.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipeplatform.api.dto.request.AuthRequest;
import com.recipeplatform.api.dto.response.AuthResponse;
import com.recipeplatform.api.entity.ChefProfile;
import com.recipeplatform.api.entity.User;
import com.recipeplatform.api.repository.ChefProfileRepository;
import com.recipeplatform.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ChefProfileRepository chefProfileRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Clean state handled by @Transactional rollback
    }

    @Test
    @DisplayName("POST /v1/auth/signup — creates user and returns token pair")
    void signUp_success() throws Exception {
        AuthRequest.SignUp req = new AuthRequest.SignUp();
        req.setEmail("chef@example.com");
        req.setPassword("password123");
        req.setHandle("testchef");
        req.setDisplayName("Test Chef");

        MvcResult result = mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.chef.handle").value("testchef"))
                .andReturn();

        // Verify persisted
        assertThat(userRepository.findByEmail("chef@example.com")).isPresent();
        assertThat(chefProfileRepository.findByHandle("testchef")).isPresent();
    }

    @Test
    @DisplayName("POST /v1/auth/signup — duplicate email returns 409")
    void signUp_duplicateEmail_conflict() throws Exception {
        // Seed existing user
        User existing = User.builder()
                .email("dup@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .role(User.Role.CHEF)
                .build();
        userRepository.save(existing);

        AuthRequest.SignUp req = new AuthRequest.SignUp();
        req.setEmail("dup@example.com");
        req.setPassword("password123");
        req.setHandle("newhandle");
        req.setDisplayName("New Chef");

        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /v1/auth/login — valid credentials return token pair")
    void login_success() throws Exception {
        // Seed user
        User user = User.builder()
                .email("login@example.com")
                .passwordHash(passwordEncoder.encode("mypassword"))
                .role(User.Role.CHEF)
                .build();
        user = userRepository.save(user);
        ChefProfile chef = ChefProfile.builder()
                .user(user)
                .handle("loginchef")
                .displayName("Login Chef")
                .build();
        chefProfileRepository.save(chef);

        AuthRequest.Login req = new AuthRequest.Login();
        req.setEmail("login@example.com");
        req.setPassword("mypassword");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /v1/auth/login — wrong password returns 401")
    void login_wrongPassword_unauthorized() throws Exception {
        User user = User.builder()
                .email("bad@example.com")
                .passwordHash(passwordEncoder.encode("correctpassword"))
                .role(User.Role.CHEF)
                .build();
        userRepository.save(user);

        AuthRequest.Login req = new AuthRequest.Login();
        req.setEmail("bad@example.com");
        req.setPassword("wrongpassword");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /v1/auth/signup — invalid email returns 400 with field errors")
    void signUp_invalidEmail_badRequest() throws Exception {
        AuthRequest.SignUp req = new AuthRequest.SignUp();
        req.setEmail("not-an-email");
        req.setPassword("password123");
        req.setHandle("handle");
        req.setDisplayName("Name");

        mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
    }
}
