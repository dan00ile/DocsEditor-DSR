package dsr.practice.docseditor.controller;

import dsr.practice.docseditor.dto.ActiveUserDto;
import dsr.practice.docseditor.dto.DocumentUpdateRequest;
import dsr.practice.docseditor.model.Document;
import dsr.practice.docseditor.service.CollaborationService;
import dsr.practice.docseditor.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
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
            
            List<ActiveUserDto> activeUsers = collaborationService.getActiveUsers(documentId, userId);

            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/active-users",
                    activeUsers != null ? activeUsers : Collections.emptyList()
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

            String username = principal.getName();

            collaborationService.disconnectUserFromDocument(documentId, userId);

            collaborationService.notifyUserLeft(documentId, userId, username);
            
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
            
            log.debug("Событие ввода успешно обработано для пользователя {} в документе {}", userId, documentId);
        } catch (Exception e) {
            log.error("Ошибка при обработке события ввода для документа {}: {}", documentId, e.getMessage(), e);
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
