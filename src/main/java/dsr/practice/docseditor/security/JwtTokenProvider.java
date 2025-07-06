package dsr.practice.docseditor.security;

import dsr.practice.docseditor.config.AppProperties;
import dsr.practice.docseditor.model.User;
import dsr.practice.docseditor.model.UserSession;
import dsr.practice.docseditor.repository.UserSessionRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final AppProperties appProperties;
    private final UserSessionRepository userSessionRepository;

    private String generateAccessToken(User user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("email", user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + appProperties.getJwt().getAccessTokenExpiresMs()))
                .signWith(Keys.hmacShaKeyFor(appProperties.getJwt().getSecret().getBytes()), SignatureAlgorithm.HS512)
                .compact();
    }

    private String generateRefreshToken(User user, String deviceInfo, String ipAdress) {
        String token = UUID.randomUUID().toString();

        UserSession userSession = UserSession.builder()
                .user(user)
                .token(token)
                .deviceInfo(deviceInfo)
                .ipAddress(ipAdress)
                .expiresAt(LocalDateTime.now().plusSeconds(appProperties.getJwt().getRefreshTokenExpiresMs() / 1000))
                .createdAt(LocalDateTime.now())
                .build();

        userSessionRepository.save(userSession);

        return token;
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUserName(token);
        return (username.equals(userDetails.getUsername()) && isTokenActive(token));
    }

    /**
     * Валидирует JWT токен без проверки UserDetails
     *
     * @param token JWT токен для проверки
     * @return true если токен валидный, false в противном случае
     * @throws SignatureException       если подпись токена неверна
     * @throws MalformedJwtException    если формат токена неверен
     * @throws ExpiredJwtException      если срок действия токена истек
     * @throws UnsupportedJwtException  если токен не поддерживается
     * @throws IllegalArgumentException если токен не содержит claims
     */
    public boolean validateToken(String token) throws SignatureException, MalformedJwtException,
            ExpiredJwtException, UnsupportedJwtException,
            IllegalArgumentException {
        JwtParser jwtParser = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(appProperties.getJwt().getSecret().getBytes()))
                .build();

        jwtParser.parseClaimsJws(token);
        return isTokenActive(token);
    }

    private boolean isTokenActive(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return !expiration.before(new Date());
    }
    public String extractUserName(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }
    private Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
