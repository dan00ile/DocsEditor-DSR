package dsr.practice.docseditor.controller;

import dsr.practice.docseditor.dto.ApiResponse;
import dsr.practice.docseditor.dto.AuthResponse;
import dsr.practice.docseditor.dto.LoginRequest;
import dsr.practice.docseditor.dto.RefreshTokenRequest;
import dsr.practice.docseditor.dto.RegisterRequest;
import dsr.practice.docseditor.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerUser(@RequestBody RegisterRequest registerRequest,
                                                            HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        String deviceInfo = request.getHeader("User-Agent");

        String userId = userService.register(registerRequest, ipAddress, deviceInfo);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("message", "Регистрация успешно завершена.");
        responseData.put("userId", userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(responseData));
    }

    @PostMapping("login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String ipAddress = request.getRemoteAddr();
        String deviceInfo = request.getHeader("User-Agent");

        AuthResponse authResponse = userService.createAuthResponse(
                loginRequest.getUsername(),
                ipAddress,
                deviceInfo,
                loginRequest.getDeviceId()
        );

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest refreshTokenRequest,
                                                HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        String deviceInfo = request.getHeader("User-Agent");

        AuthResponse authResponse = userService.refreshToken(
                refreshTokenRequest.getRefreshToken(),
                ipAddress,
                deviceInfo
        );

        return ResponseEntity.ok(authResponse);
    }
}
