package dsr.practice.docseditor.service;

import dsr.practice.docseditor.dto.ActiveUserDto;
import dsr.practice.docseditor.dto.DocumentUpdateRequest;
import dsr.practice.docseditor.model.Document;
import dsr.practice.docseditor.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollaborationService {
    private final DocumentRepository documentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisCollaborationService redisCollaborationService;

    public synchronized boolean connectUserToDocument(UUID documentId, UUID userId) {
        return redisCollaborationService.connectUserToDocument(documentId, userId);
    }

    public synchronized void disconnectUserFromDocument(UUID documentId, UUID userId) {
        redisCollaborationService.disconnectUserFromDocument(documentId, userId);
    }

    public List<ActiveUserDto> getActiveUsers(UUID documentId, UUID currentUserId) {
        return redisCollaborationService.getActiveUsers(documentId, currentUserId);
    }

    public List<ActiveUserDto> getActiveUsersList(UUID documentId) {
        return redisCollaborationService.getActiveUsersList(documentId);
    }

    public void updateUserState(UUID documentId, UUID userId, Integer cursorPosition, Boolean isTyping) {
        redisCollaborationService.updateUserState(documentId, userId, cursorPosition, isTyping);
    }

    public Document handleDocumentUpdate(UUID documentId, DocumentUpdateRequest updateRequest, UUID userId) {
        Document document = documentRepository.findDocumentById(documentId);
        
        if (document == null) {
            log.error("Документ с ID {} не найден", documentId);
            throw new RuntimeException("Документ не найден");
        }

        if (updateRequest.getLastKnownUpdate() != null && 
            document.getUpdatedAt().isAfter(updateRequest.getLastKnownUpdate())) {
            log.info("Обнаружен конфликт версий документа {}. Клиент: {}, Последнее известное обновление клиента: {}, Текущее обновление сервера: {}", 
                    documentId, updateRequest.getClientId(), updateRequest.getLastKnownUpdate(), document.getUpdatedAt());

            notifyClientAboutConflict(documentId, document, updateRequest.getClientId());
            
            return document;
        }

        document.setContent(updateRequest.getContent());
        document.setUpdatedAt(LocalDateTime.now());
        
        Document updatedDocument = documentRepository.save(document);
        log.info("Документ {} успешно обновлен пользователем {}", documentId, userId);

        registerUserActivity(documentId, userId);

        UUID updatedBy = updateRequest.getUserId() != null ? updateRequest.getUserId() : userId;

        Map<String, Object> updateMessage = new HashMap<>();
        updateMessage.put("documentId", documentId);
        updateMessage.put("content", updatedDocument.getContent());
        updateMessage.put("updatedAt", updatedDocument.getUpdatedAt());
        updateMessage.put("updatedBy", updatedBy);
        updateMessage.put("clientId", updateRequest.getClientId());
        updateMessage.put("type", "DOCUMENT_UPDATE");
        
        try {
            messagingTemplate.convertAndSend(
                    "/topic/documents/" + documentId + "/updates",
                    updateMessage
            );
            log.info("Уведомление об обновлении документа {} успешно отправлено", documentId);
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления об обновлении документа {}: {}", documentId, e.getMessage(), e);
        }
        
        return updatedDocument;
    }

    public void notifyClientAboutConflict(UUID documentId, Document document, String clientId) {
        log.info("Отправка уведомления о конфликте версий клиенту {}", clientId);
        
        Map<String, Object> conflictMessage = new HashMap<>();
        conflictMessage.put("documentId", documentId);
        conflictMessage.put("content", document.getContent());
        conflictMessage.put("updatedAt", document.getUpdatedAt());
        conflictMessage.put("updatedBy", document.getCreatedBy());
        conflictMessage.put("clientId", "server-conflict-" + UUID.randomUUID());
        conflictMessage.put("type", "VERSION_CONFLICT");
        
        try {
            messagingTemplate.convertAndSend(
                    "/topic/documents/" + documentId + "/conflicts",
                    conflictMessage
            );

            messagingTemplate.convertAndSend(
                    "/topic/documents/" + documentId + "/updates",
                    conflictMessage
            );
            
            log.info("Уведомление о конфликте версий для документа {} успешно отправлено", documentId);
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления о конфликте версий для документа {}: {}", documentId, e.getMessage(), e);
        }
    }

    public void registerUserActivity(UUID documentId, UUID userId) {
        redisCollaborationService.registerUserActivity(documentId, userId);
    }

    public boolean isUserConnected(UUID documentId, UUID userId) {
        return redisCollaborationService.isUserConnected(documentId, userId);
    }

    public void notifyDocumentUpdate(UUID documentId, Document document, String clientId) {
        log.info("Отправка уведомления об обновлении документа {} всем пользователям, clientId: {}", documentId, clientId);
        
        Map<String, Object> updateMessage = new HashMap<>();
        updateMessage.put("documentId", documentId);
        updateMessage.put("content", document.getContent());
        updateMessage.put("updatedAt", document.getUpdatedAt());
        updateMessage.put("updatedBy", document.getCreatedBy());
        updateMessage.put("clientId", clientId);
        updateMessage.put("type", "DOCUMENT_UPDATE");
        
        try {
            messagingTemplate.convertAndSend(
                    "/topic/documents/" + documentId + "/updates",
                    updateMessage
            );
            log.info("Уведомление об обновлении документа {} успешно отправлено", documentId);
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления об обновлении документа {}: {}", documentId, e.getMessage(), e);
        }
    }

    public void notifyDocumentRestore(UUID documentId, Document document) {
        Map<String, Object> updateMessage = new HashMap<>();
        updateMessage.put("documentId", documentId);
        updateMessage.put("content", document.getContent());
        updateMessage.put("updatedAt", document.getUpdatedAt());
        updateMessage.put("updatedBy", document.getCreatedBy());
        updateMessage.put("clientId", "version-restore-" + UUID.randomUUID());
        updateMessage.put("type", "DOCUMENT_UPDATE");
        
        messagingTemplate.convertAndSend(
                "/topic/documents/" + documentId + "/updates",
                updateMessage
        );
    }

    public void notifyUserJoined(UUID documentId, UUID userId, String username) {
        log.info("Уведомление о подключении пользователя {} к документу {}", username, documentId);

        ActiveUserDto userDto = null;
        List<ActiveUserDto> allUsers = redisCollaborationService.getActiveUsersList(documentId);

        for (ActiveUserDto user : allUsers) {
            if (user.getUserId().equals(userId)) {
                userDto = user;
                break;
            }
        }
        
        if (userDto == null) {
            log.warn("Не удалось найти информацию о пользователе {} для уведомления", userId);
            return;
        }

        Map<String, Object> joinMessage = new HashMap<>();
        joinMessage.put("type", "USER_JOIN");
        joinMessage.put("userId", userId);
        joinMessage.put("username", username);
        joinMessage.put("color", userDto.getColor());
        joinMessage.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend(
                "/topic/documents/" + documentId + "/user-joined",
                joinMessage
        );
    }

    public void notifyUserLeft(UUID documentId, UUID userId, String username) {
        log.info("Уведомление об отключении пользователя {} от документа {}", username, documentId);

        Map<String, Object> leftMessage = new HashMap<>();
        leftMessage.put("type", "USER_LEAVE");
        leftMessage.put("userId", userId);
        leftMessage.put("username", username);
        leftMessage.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend(
                "/topic/documents/" + documentId + "/user-left",
                leftMessage
        );
    }

    public void handleDocumentDeleted(UUID documentId) {
        List<ActiveUserDto> activeUsers = getActiveUsersList(documentId);
        
        if (!activeUsers.isEmpty()) {
            Map<String, Object> deleteMessage = new HashMap<>();
            deleteMessage.put("documentId", documentId);
            deleteMessage.put("type", "DOCUMENT_DELETED");
            deleteMessage.put("timestamp", LocalDateTime.now());
            
            messagingTemplate.convertAndSend(
                    "/topic/documents/" + documentId + "/deleted",
                    deleteMessage
            );
        }

        redisCollaborationService.handleDocumentDeleted(documentId);
    }
}
