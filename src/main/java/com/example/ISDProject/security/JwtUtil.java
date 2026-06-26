package com.example.ISDProject.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    // Chiave derivata da un secret configurabile (jwt.secret / JWT_SECRET): resta stabile tra
    // riavvii e tra istanze, così i token restano validi. Richiede almeno 32 byte (HS256).
    private final Key secretKey;

    private final long accessExpirationMs;
    private final String issuer;
    private final String audience;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms:900000}") long accessExpirationMs, // 15 minuti
            @Value("${jwt.issuer:helios-auth}") String issuer, // identifica il server che ha emesso il token
            @Value("${jwt.audience:helios-dashboard}") String audience) // identifica il destinatario previsto del token
    {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "jwt.secret mancante o troppo corto: servono almeno 32 caratteri (imposta JWT_SECRET).");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.issuer = issuer;
        this.audience = audience;
    }

    /**
     * Access token a vita breve. Include issuer e audience (verificati in validazione) e i ruoli
     * dell'utente, così il filtro può popolare le authorities per l'autorizzazione.
     */
    public String generateAccessToken(String username, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .setIssuer(issuer)
                .setAudience(audience)
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = getClaims(token).get("roles");
        return roles instanceof List ? (List<String>) roles : List.of();
    }

    // Controlla se il token è valido: firma corretta, non scaduto, issuer e audience attesi.
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Estrae i claims verificando anche issuer e audience: un token con iss/aud diversi è rifiutato.
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
