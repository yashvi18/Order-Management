package com.ecommerce.oms.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // 401/403 are raised in the filter chain, before GlobalExceptionHandler can see them, so they are written here
    // in the same RFC 7807 shape as every other error.
    private static final AuthenticationEntryPoint UNAUTHORIZED = (request, response, ex) -> {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"oms\"");
        writeProblem(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    };

    private static final AccessDeniedHandler FORBIDDEN =
            (request, response, ex) -> writeProblem(response, HttpStatus.FORBIDDEN, "Access denied");

    @Bean
    public PasswordEncoder passwordEncoder(@Value("${oms.security.bcrypt-strength:10}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/warehouse/**").hasAnyRole("WAREHOUSE_STAFF", "ADMIN")
                        .requestMatchers("/api/cart/**", "/api/checkout/**", "/api/orders/**", "/api/notifications/**")
                                .hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(UNAUTHORIZED))
                .exceptionHandling(e -> e.authenticationEntryPoint(UNAUTHORIZED).accessDeniedHandler(FORBIDDEN));
        return http.build();
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
                .formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
