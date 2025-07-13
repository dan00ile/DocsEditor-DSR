package dsr.practice.docseditor.controller;

import dsr.practice.docseditor.dto.ActiveUserDto;
import dsr.practice.docseditor.dto.DocumentUpdateRequest;
import dsr.practice.docseditor.dto.EditOperation;
import dsr.practice.docseditor.model.Document;
import dsr.practice.docseditor.service.CollaborationService;
import dsr.practice.docseditor.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CollaborationWebSocketController {
    private final SecurityUtils securityUtils;
    private final SimpMessagingTemplate messagingTemplate;
    private final CollaborationService collaborationService;

    @MessageMapping("/documents/{documentId}/connect")
    @SendToUser("/queue/document-connection")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> connectToDocument(@DestinationVariable UUID documentId,
                                  Principal principal) {
        try {
            log.debug("Обработка запроса на подключение к документу: {}, пользователь: {}", 
                    documentId, principal != null ? principal.getName() : "unknown");
                    
            UUID userId = securityUtils.getCurrentUserIdOrThrow();
            log.debug("Получен ID пользователя: {}", userId);
            
            boolean isNewConnection = collaborationService.connectUserToDocument(documentId, userId);
            
            List<ActiveUserDto> allActiveUsers = collaborationService.getActiveUsersList(documentId);

            messagingTemplate.convertAndSend(
                    "/topic/documents/" + documentId + "/active-users",
                    allActiveUsers != null ? allActiveUsers : Collections.emptyList()
            );

            if (isNewConnection) {
                String username = principal.getName();
                collaborationService.notifyUserJoined(documentId, userId, username);
            }
            
            log.debug("Пользователь {} успешно подключен к документу {}", userId, documentId);
            return Map.of(
                "status", "connected",
                "documentId", documentId
            );

        } catch (Exception e) {
            log.error("Ошибка при подключении пользователя к документу {}: {}", documentId, e.getMessage(), e);
            return Map.of(
                "status", "error",
                "message", e.getMessage()
            );
        }
    }

    @MessageMapping("/documents/{documentId}/disconnect")
    @PreAuthorize("isAuthenticated()")
    public void disconnectFromDocument(@DestinationVariable UUID documentId,
                                       Principal principal) {
        try {
            log.debug("Обработка запроса на отключение от документа: {}, пользователь: {}", 
                    documentId, principal != null ? principal.getName() : "unknown");
                    
            UUID userId = securityUtils.getCurrentUserIdOrThrow();
            log.debug("Получен ID пользователя для отключения: {}", userId);

            String username = principal.getName();
            log.debug("Отключаем пользователя {} ({}) от документа {}", username, userId, documentId);

            List<ActiveUserDto> usersBeforeDisconnect = collaborationService.getActiveUsersList(documentId);
            log.debug("Пользователей в документе до отключения: {}", usersBeforeDisconnect.size());

            collaborationService.disconnectUserFromDocument(documentId, userId);
            log.debug("Пользователь {} успешно отключен от документа {}", userId, documentId);

            try {
                collaborationService.notifyUserLeft(documentId, userId, username);
                log.debug("Уведомление об отключении пользователя {} отправлено", username);
            } catch (Exception e) {
                log.error("Ошибка при отправке уведомления об отключении пользователя {}: {}", username, e.getMessage(), e);
            }

            List<ActiveUserDto> remainingUsers = collaborationService.getActiveUsersList(documentId);
            log.debug("Пользователей в документе после отключения: {}", remainingUsers.size());

            try {
                messagingTemplate.convertAndSend(
                        "/topic/documents/" + documentId + "/active-users",
                        remainingUsers
                );
                log.debug("Обновленный список пользователей отправлен всем клиентам документа {}", documentId);
            } catch (Exception e) {
                log.error("Ошибка при отправке обновленного списка пользователей для документа {}: {}", documentId, e.getMessage(), e);
            }
            
            log.debug("Пользователь {} успешно отключен от документа {}", userId, documentId);
        } catch (Exception e) {
            log.error("Ошибка при отключении от документа {}: {}", documentId, e.getMessage(), e);
        }
    }

    @MessageMapping("/documents/{documentId}/typing")
    @PreAuthorize("isAuthenticated()")
    public void handleTyping(@DestinationVariable UUID documentId,
                             @Payload Map<String, Object> payload,
                             Principal principal) {
        try {
            log.debug("Обработка события ввода для документа: {}, пользователь: {}", 
                    documentId, principal != null ? principal.getName() : "unknown");
                    
            UUID userId = securityUtils.getCurrentUserIdOrThrow();

            Integer cursorPosition = payload.containsKey("cursorPosition") ? 
                    (Integer) payload.get("cursorPosition") : null;
            Boolean isTyping = payload.containsKey("isTyping") ? 
                    (Boolean) payload.get("isTyping") : null;

            collaborationService.updateUserState(documentId, userId, cursorPosition, isTyping);

            Map<String, Object> typingMessage = new HashMap<>();
            typingMessage.put("userId", userId);
            typingMessage.put("isTyping", isTyping != null ? isTyping : false);
            typingMessage.put("cursorPosition", cursorPosition != null ? cursorPosition : 0);
            typingMessage.put("type", "CURSOR_UPDATE");
            typingMessage.put("timestamp", System.currentTimeMillis());
            
            messagingTemplate.convertAndSend(
                    "/topic/documents/" + documentId + "/typing",
                    typingMessage
            );
            
            log.debug("Ввод успешно обработан для пользователя {} в документе {}", userId, documentId);
        } catch (Exception e) {
            log.error("Ошибка при обработке ввода для документа {}: {}", documentId, e.getMessage(), e);
        }
    }

    @MessageMapping("/documents/{documentId}/update")
    @SendToUser("/queue/document-update-result")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> handleDocumentUpdate(
            @DestinationVariable UUID documentId,
            @Payload DocumentUpdateRequest updateRequest,
            Principal principal) {
        try {
            log.debug("Обработка запроса на обновление документа: {}, пользователь: {}, clientId: {}", 
                    documentId, principal != null ? principal.getName() : "unknown", updateRequest.getClientId());
                    
            UUID userId = securityUtils.getCurrentUserIdOrThrow();
            log.debug("Получен ID пользователя: {}", userId);

            Document updatedDocument = collaborationService.handleDocumentUpdate(
                    documentId, updateRequest, userId);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("documentId", documentId);
            result.put("updatedAt", updatedDocument.getUpdatedAt());
            result.put("content", updatedDocument.getContent());
            result.put("clientId", updateRequest.getClientId());
            
            log.debug("Документ {} успешно обновлен пользователем {}", documentId, userId);
            return result;
            
        } catch (Exception e) {
            log.error("Ошибка при обновлении документа {}: {}", documentId, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", e.getMessage());
            errorResult.put("clientId", updateRequest.getClientId());
            return errorResult;
        }
    }
    
    @MessageMapping("/documents/{documentId}/operation")
    @SendToUser("/queue/operation-result")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> handleOperation(
            @DestinationVariable UUID documentId,
            @Payload EditOperation operation,
            Principal principal) {
        try {
            log.debug("Обработка операции для документа: {}, пользователь: {}, clientId: {}, тип: {}", 
                    documentId, principal != null ? principal.getName() : "unknown", 
                    operation.getClientId(), operation.getType());
            
            UUID userId = securityUtils.getCurrentUserIdOrThrow();
            operation.setUserId(userId);
            operation.setDocumentId(documentId);
            operation.setServerTimestamp(System.currentTimeMillis());

            DocumentUpdateRequest updateRequest = new DocumentUpdateRequest();
            updateRequest.setOperations(Collections.singletonList(operation));
            updateRequest.setClientId(operation.getClientId());
            
            Document updatedDocument = collaborationService.handleDocumentUpdate(
                    documentId, updateRequest, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("documentId", documentId);
            result.put("operation", operation);
            result.put("updatedAt", updatedDocument.getUpdatedAt());
            result.put("clientId", operation.getClientId());
            
            return result;
            
        } catch (Exception e) {
            log.error("Ошибка при обработке операции для документа {}: {}", documentId, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", e.getMessage());
            errorResult.put("clientId", operation.getClientId());
            return errorResult;
        }
    }
    
    @MessageMapping("/documents/{documentId}/batch-operations")
    @SendToUser("/queue/document-update-result")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> handleBatchOperations(
            @DestinationVariable UUID documentId,
            @Payload DocumentUpdateRequest updateRequest,
            Principal principal) {
        try {
            log.debug("Обработка пакета операций для документа: {}, пользователь: {}, clientId: {}, количество операций: {}", 
                    documentId, principal != null ? principal.getName() : "unknown", 
                    updateRequest.getClientId(), updateRequest.getOperations().size());

            UUID userId = securityUtils.getCurrentUserIdOrThrow();
            
            long timestamp = System.currentTimeMillis();
            for (EditOperation operation : updateRequest.getOperations()) {
                operation.setUserId(userId);
                operation.setDocumentId(documentId);
                operation.setServerTimestamp(timestamp);
            }
            
            Document updatedDocument = collaborationService.handleDocumentUpdate(
                    documentId, updateRequest, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("documentId", documentId);
            result.put("operationsCount", updateRequest.getOperations().size());
            result.put("updatedAt", updatedDocument.getUpdatedAt());
            result.put("clientId", updateRequest.getClientId());
            
            log.debug("Пакет операций успешно обработан для документа {}", documentId);
            return result;
            
        } catch (Exception e) {
            log.error("Ошибка при обработке пакета операций для документа {}: {}", documentId, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", e.getMessage());
            errorResult.put("clientId", updateRequest.getClientId());
            return errorResult;
        }
    }
    
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, Object> handleException(Exception exception) {
        log.error("Ошибка при обработке WebSocket сообщения: {}", exception.getMessage(), exception);
        Map<String, Object> errorMessage = new HashMap<>();
        errorMessage.put("status", "error");
        errorMessage.put("message", exception.getMessage());
        errorMessage.put("timestamp", System.currentTimeMillis());
        return errorMessage;
    }
}
