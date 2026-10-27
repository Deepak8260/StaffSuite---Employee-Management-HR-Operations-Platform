package com.staffsuite.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestErrorWriter errorWriter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                    errorWriter.write(req, res, HttpStatus.UNAUTHORIZED, "Authentication required"))
                .accessDeniedHandler((req, res, ex) ->
                    errorWriter.write(req, res, HttpStatus.FORBIDDEN, "Access denied")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/api/v1/auth/login",
                        "/api/v1/auth/reset-password-request",
                        "/api/v1/auth/reset-password-confirm").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Role checks at URL level run before request-body validation, so a caller
                // without the role always gets 403 rather than a 400 about their payload.
                // @PreAuthorize on the controllers remains as a second layer.
                .requestMatchers("/api/v1/users/**").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/employees").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/employees/{id}").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/employees/{id}/offboard").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/employees/{id}/reassign").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/departments", "/api/v1/designations").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/departments/{id}", "/api/v1/designations/{id}").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/departments/{id}").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/leave-types", "/api/v1/public-holidays",
                        "/api/v1/leave-balances/initialize").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/leave-types/{id}", "/api/v1/public-holidays/{id}")
                        .hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/public-holidays/{id}").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/leave-requests/{id}/approve", "/api/v1/leave-requests/{id}/reject")
                        .hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN", "MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/v1/documents/categories").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/documents/categories/{id}").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/documents/{id}").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/documents/expiring").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/attendance-corrections/{id}/approve",
                        "/api/v1/attendance-corrections/{id}/reject").hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN", "MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/v1/attendance/summary")
                        .hasAnyRole("SYSTEM_ADMIN", "HR_ADMIN", "MANAGER")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Authentication is done by the JWT filter; this stops Boot generating a default user. */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Not supported");
        };
    }
}
