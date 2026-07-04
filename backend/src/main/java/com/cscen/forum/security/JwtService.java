package com.cscen.forum.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtService {

    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final SecretKey key;

    public JwtService(Environment env) {
        String secret = env.getProperty("JWT_SECRET", "dev-secret-change-me");
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 needs a 256-bit key; stretch short dev secrets
            try {
                bytes = MessageDigest.getInstance("SHA-256").digest(bytes);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String sign(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TOKEN_TTL.toMillis()))
                .signWith(key)
                .compact();
    }

    /** Returns the user id, or null for missing/invalid/expired tokens. */
    public String verify(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
