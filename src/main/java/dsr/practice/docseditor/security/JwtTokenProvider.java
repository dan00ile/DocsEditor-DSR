package dsr.practice.docseditor.security;

import dsr.practice.docseditor.config.AppProperties;
import dsr.practice.docseditor.model.User;
import dsr.practice.docseditor.model.UserSession;
import dsr.practice.docseditor.repository.UserSessionRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final AppProperties appProperties;
    private final UserSessionRepository userSessionRepository;
    private final UserDetailsService userDetailsService;

    public String generateAccessToken(User user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("email", user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + appProperties.getJwt().getAccessTokenExpiresMs()))
                .signWith(Keys.hmacShaKeyFor(appProperties.getJwt().getSecret().getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(User user, String deviceInfo, String ipAdress) {
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

    public boolean validateToken(String token) throws SignatureException, MalformedJwtException,
            ExpiredJwtException, UnsupportedJwtException,
            IllegalArgumentException {
        JwtParser jwtParser = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(appProperties.getJwt().getSecret().getBytes()))
                .build();

        jwtParser.parseClaimsJws(token);
        return isTokenActive(token);
    }

    public Authentication getAuthentication(String token) {
        try {
            if (validateToken(token)) {
                String username = extractUserName(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                return new UsernamePasswordAuthenticationToken(
                        userDetails, "", userDetails.getAuthorities());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
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
