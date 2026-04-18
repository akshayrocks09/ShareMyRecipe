package com.recipeplatform.api.service;

import com.recipeplatform.api.dto.request.AuthRequest;
import com.recipeplatform.api.dto.response.AuthResponse;
import com.recipeplatform.api.dto.response.ChefResponse;
import com.recipeplatform.api.entity.ChefProfile;
import com.recipeplatform.api.entity.RefreshToken;
import com.recipeplatform.api.entity.User;
import com.recipeplatform.api.exception.ApiException;
import com.recipeplatform.api.repository.ChefProfileRepository;
import com.recipeplatform.api.repository.RefreshTokenRepository;
import com.recipeplatform.api.repository.UserRepository;
import com.recipeplatform.api.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ChefProfileRepository chefProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.refresh-token-ttl-days:7}")
    private int refreshTokenTtlDays;

    @Value("${app.jwt.access-token-ttl-ms:900000}")
    private long accessTokenTtlMs;

    @Transactional
    public AuthResponse.TokenPair signUp(AuthRequest.SignUp req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (chefProfileRepository.existsByHandle(req.getHandle())) {
            throw new ApiException(HttpStatus.CONFLICT, "Handle already taken");
        }

        User user = User.builder()
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(User.Role.CHEF)  // direct sign-up creates a chef account
                .build();
        user = userRepository.save(user);

        ChefProfile chef = ChefProfile.builder()
                .user(user)
                .handle(req.getHandle())
                .displayName(req.getDisplayName())
                .bio(req.getBio())
                .build();
        chef = chefProfileRepository.save(chef);

        return buildTokenPair(user, chef);
    }

    @Transactional
    public AuthResponse.TokenPair login(AuthRequest.Login req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        ChefProfile chef = chefProfileRepository.findByUserId(user.getId()).orElse(null);
        return buildTokenPair(user, chef);
    }

    @Transactional
    public AuthResponse.AccessToken refresh(AuthRequest.Refresh req) {
        RefreshToken rt = refreshTokenRepository.findByTokenAndRevokedFalse(req.getRefreshToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));

        if (rt.getExpiresAt().isBefore(Instant.now())) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        User user = rt.getUser();
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());

        return AuthResponse.AccessToken.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(accessTokenTtlMs / 1000)
                .build();
    }

    // ---- helpers ----

    private AuthResponse.TokenPair buildTokenPair(User user, ChefProfile chef) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());

        String rawRefresh = UUID.randomUUID().toString();
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .token(rawRefresh)
                .expiresAt(Instant.now().plus(refreshTokenTtlDays, ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(rt);

        ChefResponse.Brief chefBrief = chef == null ? null : ChefResponse.Brief.builder()
                .id(chef.getId())
                .handle(chef.getHandle())
                .displayName(chef.getDisplayName())
                .avatarUrl(chef.getAvatarUrl())
                .build();

        return AuthResponse.TokenPair.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefresh)
                .tokenType("Bearer")
                .expiresInSeconds(accessTokenTtlMs / 1000)
                .chef(chefBrief)
                .build();
    }
}
