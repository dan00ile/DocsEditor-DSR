package dsr.practice.docseditor.service;

import dsr.practice.docseditor.dto.ActiveUserDto;
import dsr.practice.docseditor.dto.DocumentUpdateRequest;
import dsr.practice.docseditor.dto.EditOperation;
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

            long timeDifferenceSeconds = java.time.Duration.between(
                updateRequest.getLastKnownUpdate(), 
                document.getUpdatedAt()
            ).getSeconds();

            long timeDifferenceMillis = java.time.Duration.between(
                updateRequest.getLastKnownUpdate(), 
                document.getUpdatedAt()
            ).toMillis();
            
            log.debug("Время клиента: {}, Время сервера: {}, Разница: {} сек ({} мс)", 
                updateRequest.getLastKnownUpdate(), document.getUpdatedAt(), 
                timeDifferenceSeconds, timeDifferenceMillis);

            if (timeDifferenceSeconds > 3) {
                log.info("Обнаружен конфликт версий документа {}. Клиент: {}, Последнее известное обновление клиента: {}, Текущее обновление сервера: {}, Разница: {} сек", 
                        documentId, updateRequest.getClientId(), updateRequest.getLastKnownUpdate(), document.getUpdatedAt(), timeDifferenceSeconds);
                
                notifyClientAboutConflict(documentId, document, updateRequest.getClientId());
                
                return document;
            } else {
                log.debug("Игнорируем незначительное расхождение версий ({} сек) для документа {}. Клиент: {}", 
                        timeDifferenceSeconds, documentId, updateRequest.getClientId());
            }
        }

        String newContent = document.getContent();
        LocalDateTime updateTime = LocalDateTime.now();
        
        if (updateRequest.getOperations() != null && !updateRequest.getOperations().isEmpty()) {
            List<EditOperation> confirmedOperations = new ArrayList<>();
            
            for (EditOperation operation : updateRequest.getOperations()) {
                operation.setServerTimestamp(System.currentTimeMillis());
                operation.setUserId(userId);

                try {
                    switch (operation.getType()) {
                        case "insert" -> {
                            if (operation.getPosition() >= 0 && operation.getPosition() <= newContent.length()) {
                                newContent = newContent.substring(0, operation.getPosition()) +
                                        operation.getCharacter() +
                                        newContent.substring(operation.getPosition());
                                confirmedOperations.add(operation);
                            } else {
                                log.warn("Неверный индекс для операции вставки: {}", operation.getPosition());
                            }
                        }
                        case "delete" -> {
                            if (operation.getPosition() >= 0 && operation.getPosition() < newContent.length()) {
                                newContent = newContent.substring(0, operation.getPosition()) +
                                        newContent.substring(operation.getPosition() + 1);
                                confirmedOperations.add(operation);
                            } else {
                                log.warn("Неверный индекс для операции удаления: {}", operation.getPosition());
                            }
                        }
                        case "replace" -> {
                            log.info("Выполнили операцию замены");
                            newContent = operation.getCharacter();
                            confirmedOperations.add(operation);
                        }
                        case null, default -> log.warn("Неверный тип операции: {}", operation.getType());
                    }
                } catch (Exception e) {
                    log.error("Ошибка при обработке операции: {}", e.getMessage(), e);
                }
            }

            document.setContent(newContent);
            document.setUpdatedAt(updateTime);
            documentRepository.save(document);
            
            log.info("Документ {} успешно обновлен пользователем {}", documentId, userId);
            
            registerUserActivity(documentId, userId);

            for (EditOperation operation : confirmedOperations) {
                Map<String, Object> updateMessage = new HashMap<>();
                updateMessage.put("documentId", documentId);
                updateMessage.put("operation", operation);
                updateMessage.put("updatedAt", updateTime);
                updateMessage.put("updatedBy", userId);
                updateMessage.put("clientId", updateRequest.getClientId());
                updateMessage.put("type", "OPERATION_UPDATE");
                
                try {
                    messagingTemplate.convertAndSend(
                            "/topic/documents/" + documentId + "/updates",
                            updateMessage
                    );
                } catch (Exception e) {
                    log.error("Ошибка при отправке операции для документа {}: {}", documentId, e.getMessage(), e);
                }
            }
        }
        
        return document;
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
        conflictMessage.put("conflictThreshold", 3);
        
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

        try {
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
            
            log.debug("Уведомление о подключении пользователя {} успешно отправлено", username);
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления о подключении пользователя {}: {}", username, e.getMessage(), e);
        }
    }

    public void notifyUserLeft(UUID documentId, UUID userId, String username) {
        log.info("Уведомление об отключении пользователя {} от документа {}", username, documentId);

        try {
            Map<String, Object> leftMessage = new HashMap<>();
            leftMessage.put("type", "USER_LEAVE");
            leftMessage.put("userId", userId);
            leftMessage.put("username", username);
            leftMessage.put("timestamp", System.currentTimeMillis());
            
            messagingTemplate.convertAndSend(
                    "/topic/documents/" + documentId + "/user-left",
                    leftMessage
            );
            
            log.debug("Уведомление об отключении пользователя {} успешно отправлено", username);
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления об отключении пользователя {}: {}", username, e.getMessage(), e);
        }
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
