package dsr.practice.docseditor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dsr.practice.docseditor.dto.ActiveUserDto;
import dsr.practice.docseditor.dto.EditOperation;
import dsr.practice.docseditor.model.User;
import dsr.practice.docseditor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCollaborationService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final String DOCUMENT_USERS_PREFIX = "document:users:";
    private static final String USER_STATE_PREFIX = "document:user:state:";

    private static final long DATA_TTL_HOURS = 24;

    public boolean connectUserToDocument(UUID documentId, UUID userId) {
        String documentUsersKey = DOCUMENT_USERS_PREFIX + documentId;

        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(documentUsersKey, userId.toString()))) {
            log.debug("Пользователь {} уже подключен к документу {}", userId, documentId);
            return false;
        }

        log.info("Подключение пользователя {} к документу {}", userId, documentId);

        redisTemplate.opsForSet().add(documentUsersKey, userId.toString());
        redisTemplate.expire(documentUsersKey, DATA_TTL_HOURS, TimeUnit.HOURS);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        ActiveUserDto activeUserDto = ActiveUserDto.builder()
                .userId(userId)
                .username(user.getUsername())
                .cursorPosition(0)
                .isTyping(false)
                .color(generateRandomColor())
                .lastActive(LocalDateTime.now())
                .isActive(true)
                .build();

        String userStateKey = getUserStateKey(documentId, userId);
        redisTemplate.opsForValue().set(userStateKey, activeUserDto);
        redisTemplate.expire(userStateKey, DATA_TTL_HOURS, TimeUnit.HOURS);
        
        log.info("Пользователь {} подключен к документу {}", userId, documentId);
        return true;
    }

    public void disconnectUserFromDocument(UUID documentId, UUID userId) {
        String documentUsersKey = DOCUMENT_USERS_PREFIX + documentId;
        String userStateKey = getUserStateKey(documentId, userId);

        if (!Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(documentUsersKey, userId.toString()))) {
            log.debug("Пользователь {} не подключен к документу {}", userId, documentId);
            return;
        }
        
        log.info("Отключение пользователя {} от документа {}", userId, documentId);

        redisTemplate.opsForSet().remove(documentUsersKey, userId.toString());

        redisTemplate.delete(userStateKey);
        
        log.info("Пользователь {} отключен от документа {}", userId, documentId);
    }

    public List<ActiveUserDto> getActiveUsers(UUID documentId, UUID currentUserId) {
        String documentUsersKey = DOCUMENT_USERS_PREFIX + documentId;

        Set<Object> userIds = redisTemplate.opsForSet().members(documentUsersKey);
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        return userIds.stream()
                .map(Object::toString)
                .map(UUID::fromString)
                .filter(userId -> !userId.equals(currentUserId))
                .map(userId -> {
                    String userStateKey = getUserStateKey(documentId, userId);
                    Object userStateObj = redisTemplate.opsForValue().get(userStateKey);
                    return convertToActiveUserDto(userStateObj);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<ActiveUserDto> getActiveUsersList(UUID documentId) {
        String documentUsersKey = DOCUMENT_USERS_PREFIX + documentId;

        Set<Object> userIds = redisTemplate.opsForSet().members(documentUsersKey);
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        return userIds.stream()
                .map(Object::toString)
                .map(UUID::fromString)
                .map(userId -> {
                    String userStateKey = getUserStateKey(documentId, userId);
                    Object userStateObj = redisTemplate.opsForValue().get(userStateKey);
                    return convertToActiveUserDto(userStateObj);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ActiveUserDto convertToActiveUserDto(Object userStateObj) {
        if (userStateObj == null) {
            return null;
        }
        
        try {
            if (userStateObj instanceof ActiveUserDto) {
                return (ActiveUserDto) userStateObj;
            } else if (userStateObj instanceof Map) {
                // Преобразуем Map в ActiveUserDto
                return objectMapper.convertValue(userStateObj, ActiveUserDto.class);
            }
        } catch (Exception e) {
            log.error("Ошибка при преобразовании объекта в ActiveUserDto", e);
        }
        
        return null;
    }

    public void updateUserState(UUID documentId, UUID userId, Integer cursorPosition, Boolean isTyping) {
        String userStateKey = getUserStateKey(documentId, userId);

        Object userStateObj = redisTemplate.opsForValue().get(userStateKey);
        ActiveUserDto userState = convertToActiveUserDto(userStateObj);
        
        if (userState != null) {
            if (cursorPosition != null) {
                userState.setCursorPosition(cursorPosition);
            }
            if (isTyping != null) {
                userState.setIsTyping(isTyping);
            }

            userState.setLastActive(LocalDateTime.now());
            userState.setIsActive(true);

            redisTemplate.opsForValue().set(userStateKey, userState);
            redisTemplate.expire(userStateKey, DATA_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    public boolean isUserConnected(UUID documentId, UUID userId) {
        String documentUsersKey = DOCUMENT_USERS_PREFIX + documentId;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(documentUsersKey, userId.toString()));
    }

    public void registerUserActivity(UUID documentId, UUID userId) {
        if (!isUserConnected(documentId, userId)) {
            connectUserToDocument(documentId, userId);
        } else {
            String documentUsersKey = DOCUMENT_USERS_PREFIX + documentId;
            String userStateKey = getUserStateKey(documentId, userId);

            Object userStateObj = redisTemplate.opsForValue().get(userStateKey);
            ActiveUserDto userState = convertToActiveUserDto(userStateObj);
            
            if (userState != null) {
                userState.setLastActive(LocalDateTime.now());
                userState.setIsActive(true);
                redisTemplate.opsForValue().set(userStateKey, userState);
            }
            
            redisTemplate.expire(documentUsersKey, DATA_TTL_HOURS, TimeUnit.HOURS);
            redisTemplate.expire(userStateKey, DATA_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    public void handleDocumentDeleted(UUID documentId) {
        String documentUsersKey = DOCUMENT_USERS_PREFIX + documentId;

        Set<Object> userIds = redisTemplate.opsForSet().members(documentUsersKey);
        if (userIds != null && !userIds.isEmpty()) {
            userIds.stream()
                    .map(Object::toString)
                    .map(UUID::fromString)
                    .forEach(userId -> {
                        String userStateKey = getUserStateKey(documentId, userId);
                        redisTemplate.delete(userStateKey);
                    });
        }

        redisTemplate.delete(documentUsersKey);
    }

    private String getUserStateKey(UUID documentId, UUID userId) {
        return USER_STATE_PREFIX + documentId + ":" + userId;
    }

    private String generateRandomColor() {
        String[] colors = {
            "#3B82F6", "#10B981", "#F59E0B", "#EF4444", 
            "#8B5CF6", "#06B6D4", "#F97316", "#84CC16",
            "#EC4899", "#6366F1"
        };
        return colors[(int) (Math.random() * colors.length)];
    }
}