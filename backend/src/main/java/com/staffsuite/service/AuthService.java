package com.staffsuite.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.staffsuite.config.AppProperties;
import com.staffsuite.dto.AuthDtos.LoginRequest;
import com.staffsuite.dto.AuthDtos.LoginResponse;
import com.staffsuite.dto.UserResponse;
import com.staffsuite.entity.User;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.UserRepository;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ENTITY = "USER";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final PasswordResetNotifier resetNotifier;
    private final AppProperties props;

    public LoginResponse login(LoginRequest request, String ip) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        boolean valid = user != null && user.isActive()
                && passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!valid) {
            if (user != null) {
                auditService.log(user.getId(), "LOGIN_FAILED", ENTITY, user.getId(), ip);
            }
            throw new BadCredentialsException("Invalid email or password");
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditService.log(user.getId(), "LOGIN", ENTITY, user.getId(), ip);
        return new LoginResponse(jwtService.generateToken(user), "Bearer", jwtService.getExpiryMs(),
                UserResponse.from(user));
    }

    /** JWTs are stateless, so logout only records the event; the client discards its token. */
    public void logout(AuthenticatedUser principal, String ip) {
        auditService.log(principal.userId(), "LOGOUT", ENTITY, principal.userId(), ip);
    }

    public void changePassword(AuthenticatedUser principal, String currentPassword, String newPassword, String ip) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must differ from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetTokenHash(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        auditService.log(user.getId(), "PASSWORD_CHANGED", ENTITY, user.getId(), ip);
    }

    /** Always succeeds from the caller's point of view so account existence is not revealed. */
    public void requestPasswordReset(String email, String ip) {
        userRepository.findByEmailIgnoreCase(email.trim()).filter(User::isActive).ifPresent(user -> {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            user.setResetTokenHash(sha256(token));
            user.setResetTokenExpiry(Instant.now().plus(Duration.ofMinutes(props.passwordReset().expiryMinutes())));
            userRepository.save(user);
            auditService.log(user.getId(), "PASSWORD_RESET_REQUESTED", ENTITY, user.getId(), ip);
            resetNotifier.send(user.getEmail(), token);
        });
    }

    public void confirmPasswordReset(String token, String newPassword, String ip) {
        User user = userRepository.findByResetTokenHash(sha256(token.trim()))
                .filter(u -> u.getResetTokenExpiry() != null && u.getResetTokenExpiry().isAfter(Instant.now()))
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Reset token is invalid or has expired"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetTokenHash(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        auditService.log(user.getId(), "PASSWORD_RESET", ENTITY, user.getId(), ip);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
