package com.supplychainsentinel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychainsentinel.dto.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Instant;

/**
 * Central Spring Security configuration.
 *
 * <ul>
 *   <li>Stateless JWT authentication (no server-side session)</li>
 *   <li>BCrypt password hashing</li>
 *   <li>Method-level @PreAuthorize for RBAC</li>
 *   <li>Public auth endpoints; everything else requires authentication</li>
 *   <li>Consistent ApiError JSON for 401 / 403</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final IngestionKeyFilter ingestionKeyFilter;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            IngestionKeyFilter ingestionKeyFilter,
            CustomUserDetailsService userDetailsService,
            ObjectMapper objectMapper
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.ingestionKeyFilter = ingestionKeyFilter;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {})  // uses CorsConfigurationSource bean
            .csrf(csrf -> csrf.disable())  // stateless JWT, refresh via SameSite cookie
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/ping",
                    "/api/v1/integrations/**",
                    "/actuator/health",
                    "/swagger-ui/**",
                    "/api-docs/**",
                    "/v3/api-docs/**"
                ).permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ApiError body = new ApiError(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized",
                        "Authentication is required to access this resource",
                        request.getRequestURI()
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
                .accessDeniedHandler((request, response, ex) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ApiError body = new ApiError(
                        HttpStatus.FORBIDDEN.value(),
                        "Forbidden",
                        "You do not have permission to perform this action",
                        request.getRequestURI()
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(ingestionKeyFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider::authenticate;
    }
}
