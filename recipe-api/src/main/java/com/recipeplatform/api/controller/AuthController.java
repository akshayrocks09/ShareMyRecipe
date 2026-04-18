package com.recipeplatform.api.controller;

import com.recipeplatform.api.dto.request.AuthRequest;
import com.recipeplatform.api.dto.response.AuthResponse;
import com.recipeplatform.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse.TokenPair> signUp(@Valid @RequestBody AuthRequest.SignUp req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signUp(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse.TokenPair> login(@Valid @RequestBody AuthRequest.Login req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse.AccessToken> refresh(@Valid @RequestBody AuthRequest.Refresh req) {
        return ResponseEntity.ok(authService.refresh(req));
    }
}
