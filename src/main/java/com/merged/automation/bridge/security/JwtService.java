package com.merged.automation.bridge.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    
    @Autowired
    private SecurityConfig securityConfig;
    
    private SecretKey getSigningKey() {
        String secret = securityConfig.getJwtSecret();
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateToken(String clientId, String clientType, Map<String, Object> claims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + securityConfig.getJwtExpiration());
        
        return Jwts.builder()
                .subject(clientId)
                .claim("type", clientType)
                .claim("iat", now)
                .claims(claims)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }
    
    public String generateToken(String clientId, String clientType) {
        return generateToken(clientId, clientType, Map.of());
    }
    
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            logger.warn("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }
    
    public String getClientIdFromToken(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.getSubject() : null;
    }
    
    public String getClientTypeFromToken(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.get("type", String.class) : null;
    }
    
    public boolean isTokenExpired(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return true;
        }
        return claims.getExpiration().before(new Date());
    }
    
    public boolean validateTokenForClient(String token, String expectedClientId) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return false;
        }
        
        String tokenClientId = claims.getSubject();
        return expectedClientId.equals(tokenClientId) && !isTokenExpired(token);
    }
}