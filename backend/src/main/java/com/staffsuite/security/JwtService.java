package com.staffsuite.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.staffsuite.config.AppProperties;
import com.staffsuite.entity.Role;
import com.staffsuite.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMs;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expiryMs = props.jwt().expiryMs();
    }

    public long getExpiryMs() {
        return expiryMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("userId", user.getId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("employeeId", user.getEmployeeId())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(key)
                .compact();
    }

    /** Returns the principal encoded in the token, or empty if it is invalid or expired. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Number userId = claims.get("userId", Number.class);
            Number employeeId = claims.get("employeeId", Number.class);
            return Optional.of(new AuthenticatedUser(
                    userId.longValue(),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class)),
                    employeeId == null ? null : employeeId.longValue()));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
