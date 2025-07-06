package com.ewallet.dom.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.security.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.security.KeyPairGenerator.getInstance;

@Component
public class JwtUtil {

    @Value("${jwt.expiration}") // Token expiration time in milliseconds
    private long EXPIRATION_TIME; // e.g., 864_000_000 (10 days)

    static PublicKey PUBLIC_KEY;
    static PrivateKey PRIVATE_KEY;

    static {

        // 1. Create a KeyPairGenerator instance for RSA algorithm
        KeyPairGenerator keyPairGenerator ;
        try {
            keyPairGenerator = getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        // 2. Initialize the KeyPairGenerator with a key size (e.g., 2048 bits)
        // A larger key size provides higher security but may impact performance.
        keyPairGenerator.initialize(2048);

        // 3. Generate the KeyPair
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // 4. Extract the Public and Private Keys from the KeyPair
        PUBLIC_KEY = keyPair.getPublic();
        PRIVATE_KEY = keyPair.getPrivate();

    }


    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // You can add custom claims here if needed
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Assert.notNull(PRIVATE_KEY,"Private should not be null.");
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // Set expiration
                .signWith(PRIVATE_KEY)
                .compact();
    }

    // Add methods for validation, extraction etc. if not already present
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        Assert.notNull(PUBLIC_KEY,"PUBLIC_KEY should not be null.");
        return Jwts.parser().verifyWith(PUBLIC_KEY).build().parseSignedClaims(token).getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
