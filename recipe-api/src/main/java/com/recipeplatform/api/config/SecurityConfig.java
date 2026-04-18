package com.recipeplatform.api.config;

import com.recipeplatform.api.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(HttpMethod.POST, "/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/recipes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/chefs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Authoring — chef or admin only
                        .requestMatchers(HttpMethod.POST, "/v1/recipes").hasAnyRole("CHEF", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/v1/recipes/**").hasAnyRole("CHEF", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/recipes/**").hasAnyRole("CHEF", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/recipes/*/images").hasAnyRole("CHEF", "ADMIN")
                        // Follow/Feed — any authenticated user
                        .requestMatchers("/v1/feed/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/chefs/*/follow").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/chefs/*/follow").authenticated()
                        // Admin-only
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
