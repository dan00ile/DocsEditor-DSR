package dsr.practice.docseditor.utils;

import dsr.practice.docseditor.model.User;
import dsr.practice.docseditor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityUtils {
    private final UserRepository userRepository;

    public Optional<UUID> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(User::getId);
    }
    public UUID getCurrentUserIdOrThrow() {
        return getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Пользователь не аутентифицирован"));
    }
} 