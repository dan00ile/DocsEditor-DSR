package dsr.practice.docseditor.service;

import dsr.practice.docseditor.config.AppProperties;
import dsr.practice.docseditor.dto.AuthResponse;
import dsr.practice.docseditor.dto.RegisterRequest;
import dsr.practice.docseditor.model.User;
import dsr.practice.docseditor.model.UserSession;
import dsr.practice.docseditor.repository.UserRepository;
import dsr.practice.docseditor.repository.UserSessionRepository;
import dsr.practice.docseditor.security.JwtTokenProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {
    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse createAuthResponse(String username, String ipAddress, String deviceInfo, String deviceId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Пользователь с email " + username + " не найден"));

        updateLastLogin(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, deviceInfo, ipAddress);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId().toString());
        userData.put("username", user.getUsername());
        userData.put("email", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn((int) (appProperties.getJwt().getAccessTokenExpiresMs() / 1000))
                .user(userData)
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken, String deviceInfo, String ipAddress) {
        UserSession session = userSessionRepository.findByToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный refresh-токен"));

        User user =session.getUser();

        userSessionRepository.deleteByToken(refreshToken);

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user, deviceInfo, ipAddress);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId().toString());
        userData.put("username", user.getUsername());
        userData.put("email", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .expiresIn((int) (appProperties.getJwt().getAccessTokenExpiresMs() / 1000))
                .user(userData)
                .build();
    }


    @Transactional
    public void logout(String refreshToken) {
        userSessionRepository.findByToken(refreshToken)
                .ifPresent(session -> userSessionRepository.deleteByToken(refreshToken));
    }

    @Transactional
    public String register(RegisterRequest registerRequest, String deviceInfo, String ipAdress) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь с таким email уже существует!");
        }

        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь с таким юзернеймом уже существует!");
        }

        User user =User.builder()
                .email(registerRequest.getEmail())
                .username(registerRequest.getUsername())
                .createdAt(LocalDateTime.now())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .build();

        User savedUser = userRepository.save(user);

        return savedUser.getId().toString();
    }
    @Transactional
    public void updateLastLogin(User user) {
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
    }
}
