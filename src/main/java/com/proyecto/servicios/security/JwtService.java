package com.proyecto.servicios.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@SuppressWarnings("java:S2143")
public class JwtService {

    private static final String CLAIM_TOKEN_VERSION = "ver";
    private static final String DEFAULT_JWT_ISSUER = "sistema-bancario-core";
    private static final long DEFAULT_JWT_EXPIRATION_MS = 900_000L;
    private static final long MILLIS_PER_SECOND = 1_000L;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration:900000}")
    private long jwtExpiration = DEFAULT_JWT_EXPIRATION_MS;

    @Value("${jwt.issuer:sistema-bancario-core}")
    private String jwtIssuer = DEFAULT_JWT_ISSUER;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Long extractTokenVersion(String token) {
        return extractClaim(token, this::resolveTokenVersion);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public long getJwtExpirationSeconds() {
        return Math.max(1L, jwtExpiration / MILLIS_PER_SECOND);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Map<String, Object> claims = extraClaims != null ? new HashMap<>(extraClaims) : new HashMap<>();
        if (userDetails instanceof CustomUserDetails customUserDetails
                && !claims.containsKey(CLAIM_TOKEN_VERSION)
                && customUserDetails.getUsuario() != null) {
            claims.put(CLAIM_TOKEN_VERSION, customUserDetails.getUsuario().getTokenVersion());
        }

        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(jwtExpiration)));

        if (jwtIssuer != null && !jwtIssuer.isBlank()) {
            builder.issuer(jwtIssuer.trim());
        }

        return builder
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (token == null || token.isBlank() || userDetails == null) {
            return false;
        }
        try {
            Claims claims = extractAllClaims(token);
            String username = claims.getSubject();
            if (username == null || !username.equals(userDetails.getUsername())) {
                return false;
            }
            if (isClaimsExpired(claims) || !isIssuerValid(claims)) {
                return false;
            }
            return isTokenVersionValid(claims, userDetails);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isClaimsExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration == null || expiration.toInstant().isBefore(Instant.now());
    }

    private boolean isIssuerValid(Claims claims) {
        if (jwtIssuer == null || jwtIssuer.isBlank() || claims.getIssuer() == null) {
            return true;
        }
        return jwtIssuer.trim().equals(claims.getIssuer());
    }

    private boolean isTokenVersionValid(Claims claims, UserDetails userDetails) {
        if (!(userDetails instanceof CustomUserDetails customUserDetails)) {
            return true;
        }
        if (customUserDetails.getUsuario() == null) {
            return false;
        }
        Long tokenVersion = resolveTokenVersion(claims);
        return tokenVersion != null && tokenVersion.equals(customUserDetails.getUsuario().getTokenVersion());
    }

    private Long resolveTokenVersion(Claims claims) {
        Object rawVersion = claims.get(CLAIM_TOKEN_VERSION);
        if (rawVersion instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
