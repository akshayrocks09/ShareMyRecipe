package com.recipeplatform.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthRequest {

    @Data
    public static class SignUp {
        @Email(message = "Must be a valid email address")
        @NotBlank
        private String email;

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        @NotBlank
        @Size(min = 3, max = 30, message = "Handle must be 3–30 characters")
        private String handle;

        @NotBlank
        @Size(max = 80)
        private String displayName;

        private String bio;
    }

    @Data
    public static class Login {
        @Email
        @NotBlank
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    public static class Refresh {
        @NotBlank
        private String refreshToken;
    }
}
