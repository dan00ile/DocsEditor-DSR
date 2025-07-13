import { Client, IFrame } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_CONFIG } from '../config/api';
import { WebSocketMessage, ContentUpdateMessage, DocumentVersion, User, EditOperation, OperationType } from '../types';
import { apiService } from './ApiService';

class WebSocketService {
  private static instance: WebSocketService;
  private client: Client | null = null;
  private documentId: string | null = null;
  private userId: string | null = null;
  private username: string | null = null;
  private userColor: string | null = null;
  private messageHandlers: Map<string, (message: any) => void> = new Map();
  private lastKnownUpdate: Date = new Date();
  private clientId: string = Math.random().toString(36).substring(2, 15);
  private operationTimeoutRef: NodeJS.Timeout | null = null;
  private operationDebounceTime = 50;
  private isConnecting: boolean = false;
  private connectionPromise: Promise<void> | null = null;
  private reconnectAttempts: number = 0;
  private maxReconnectAttempts: number = 5;
  private pendingOperations: EditOperation[] = [];
  
  private shadowContent: string = '';

  private constructor() {}

  public static getInstance(): WebSocketService {
    if (!WebSocketService.instance) {
      WebSocketService.instance = new WebSocketService();
    }
    return WebSocketService.instance;
  }


  connect(documentId: string, userId: string): Promise<void> {
    if (!userId) {
      console.warn('Не указан ID пользователя для WebSocket подключения');
    }

    if (this.isConnecting && this.connectionPromise) {
      console.log('WebSocket подключение уже выполняется, ожидаем завершения');
      return this.connectionPromise;
    }

    if (this.client && this.client.connected && this.documentId === documentId) {
      console.log('WebSocket уже подключен к тому же документу, используем текущее соединение');
      return Promise.resolve();
    }

    if (this.client && this.client.connected && this.documentId !== documentId) {
      console.log('Смена документа, отключаем текущий WebSocket');
      this.disconnect();
    }
    
    this.isConnecting = true;
    this.reconnectAttempts = 0;
    
    this.connectionPromise = new Promise((resolve, reject) => {
      try {
        this.documentId = documentId;
        this.userId = userId;

        this.username = localStorage.getItem('user_username') || 'Неизвестный';

        this.userColor = localStorage.getItem('user_color');
        if (!this.userColor) {
          const colors = [
            '#3B82F6', '#10B981', '#F59E0B', '#EF4444', 
            '#8B5CF6', '#06B6D4', '#F97316', '#84CC16',
            '#EC4899', '#6366F1'
          ];
          this.userColor = colors[Math.floor(Math.random() * colors.length)];
          localStorage.setItem('user_color', this.userColor);
        }

        const authToken = localStorage.getItem('auth_token');
        if (!authToken) {
          console.error('Токен авторизации отсутствует');
          this.isConnecting = false;
          reject(new Error('Токен авторизации отсутствует'));
          return;
        }

        const sockjsUrl = `${API_CONFIG.WS_URL}?token=${encodeURIComponent(authToken)}`;
        
        console.log('Подключение к WebSocket по URL:', sockjsUrl);

        this.client = new Client({
          webSocketFactory: () => {
            const socket = new SockJS(sockjsUrl);

            socket.onopen = () => {
              console.log('SockJS соединение открыто');
            };
            
            socket.onclose = (event) => {
              console.log('SockJS соединение закрыто', event);
            };
            
            socket.onerror = (error) => {
              console.error('SockJS ошибка:', error);
            };
            
            return socket;
          },
          connectHeaders: {
            Authorization: `Bearer ${authToken}`,
            userId: userId,
            documentId: documentId
          },
          debug: (str) => {
            console.log('STOMP Debug:', str);
          },
          reconnectDelay: 5000,
          heartbeatIncoming: 10000,
          heartbeatOutgoing: 10000,
        });

        const originalPublish = this.client.publish;
        this.client.publish = function(parameters: any) {
          if (!parameters.headers) {
            parameters.headers = {};
          }
          parameters.headers['Authorization'] = `Bearer ${authToken}`;
          console.log('Отправка сообщения с заголовками:', parameters.headers);
          return originalPublish.call(this, parameters);
        };

        this.client.onConnect = (frame) => {
          console.log('WebSocket успешно подключен', frame);

          this.client?.subscribe('/user/queue/errors', (message) => {
            try {
              const errorData = JSON.parse(message.body);
              console.error('Получена ошибка от сервера:', errorData);
            } catch (e) {
              console.error('Не удалось разобрать сообщение об ошибке:', message.body);
            }
          });
          
          this.subscribeToDocument();
          this.isConnecting = false;
          this.reconnectAttempts = 0;
          resolve();
        };

        this.client.onStompError = (frame) => {
          console.error('STOMP ошибка:', frame);
          this.isConnecting = false;

          this.handleReconnect(documentId, userId, resolve);
        };

        this.client.onWebSocketError = (error) => {
          console.error('WebSocket ошибка:', error);
          this.isConnecting = false;

          this.handleReconnect(documentId, userId, resolve);
        };

        this.client.activate();
      } catch (error) {
        console.error('Ошибка при настройке WebSocket соединения:', error);
        this.isConnecting = false;

        this.handleReconnect(documentId, userId, resolve);
      }
    });
    
    return this.connectionPromise;
  }

  disconnect(): void {
    if (!this.client) {
      console.log('Нет WebSocket клиента для отключения');
      return;
    }
    
    if (!this.client.connected) {
      console.log('WebSocket уже отключен');
      this.client = null;
      this.messageHandlers.clear();
      return;
    }
    
    if (this.client.connected && this.documentId) {
      try {
        console.log('Отправка сообщения об отключении на сервер');
        this.sendMessage(`/app/documents/${this.documentId}/disconnect`, {});
      } catch (error) {
        console.error('Ошибка отправки сообщения об отключении:', error);
      }
    }
    
    try {
      console.log('Деактивация WebSocket клиента');
      this.client.deactivate();
    } catch (error) {
      console.error('Ошибка деактивации WebSocket клиента:', error);
    }
    
    this.client = null;
    this.messageHandlers.clear();
    this.isConnecting = false;
    this.connectionPromise = null;

    if (this.operationTimeoutRef) {
      clearTimeout(this.operationTimeoutRef);
      this.operationTimeoutRef = null;
    }
    
    console.log('WebSocket успешно отключен');
  }

  private subscribeToDocument(): void {
    if (!this.client || !this.documentId) return;

    this.client.subscribe(`/topic/documents/${this.documentId}/updates`, (message) => {
      const data = JSON.parse(message.body);
      console.log('Received document update:', data);

      const isOwnUpdate = data.clientId === this.clientId;

      if (data.updatedAt) {
        console.log('Received server date string:', data.updatedAt);
        
        this.lastKnownUpdate = typeof data.updatedAt === 'string' ? 
          this.parseDateFromServer(data.updatedAt) : data.updatedAt;
        
        console.log('Updated lastKnownUpdate to:', this.lastKnownUpdate, 
          'formatted:', this.formatDateForServer(this.lastKnownUpdate));
      }

      if (data.type === "OPERATION_UPDATE") {
        console.log('Received operation update:', data.operation);
        
        if (isOwnUpdate) {
          console.log('Ignoring own operation update from server (already applied locally)');
          this.notifyHandler('OPERATION_UPDATE', {
            operation: data.operation,
            content: this.shadowContent,
            userId: data.updatedBy,
            timestamp: data.updatedAt ? 
              (typeof data.updatedAt === 'string' ? this.parseDateFromServer(data.updatedAt) : data.updatedAt) : 
              new Date(),
            clientId: data.clientId
          });
        } else {
          console.log('Applying remote operation from user:', data.updatedBy);
          
          this.applyOperation(data.operation);
          
          this.notifyHandler('OPERATION_UPDATE', {
            operation: data.operation,
            content: this.shadowContent,
            userId: data.updatedBy,
            timestamp: data.updatedAt ? 
              (typeof data.updatedAt === 'string' ? this.parseDateFromServer(data.updatedAt) : data.updatedAt) : 
              new Date(),
            clientId: data.clientId
          });
        }
      } else if (data.content !== undefined) {
        console.log('Received full document update');
        
        this.shadowContent = data.content;
        
        this.notifyHandler('CONTENT_UPDATE', {
          content: data.content,
          userId: data.updatedBy,
          timestamp: data.updatedAt ? 
            (typeof data.updatedAt === 'string' ? this.parseDateFromServer(data.updatedAt) : data.updatedAt) : 
            new Date(),
          clientId: data.clientId
        });
      } else {
        console.warn('Received unknown update type');
      }
    });

    this.client.subscribe(`/topic/documents/${this.documentId}/conflicts`, (message) => {
      const data = JSON.parse(message.body);
      console.log('Received version conflict notification:', data);

      if (data.updatedAt) {
        this.lastKnownUpdate = typeof data.updatedAt === 'string' ? 
          this.parseDateFromServer(data.updatedAt) : data.updatedAt;
        console.log('Updated lastKnownUpdate from conflict notification to:', this.lastKnownUpdate);
      }

      if (data.conflictThreshold) {
        console.log(`Note: Server is configured to only report conflicts when the time difference exceeds ${data.conflictThreshold} seconds`);
      }

      console.log('Ignoring content update from conflict notification');
    });

    this.client.subscribe(`/topic/documents/${this.documentId}/user-joined`, (message) => {
      const data = JSON.parse(message.body);
      console.log('User joined:', data);
      this.notifyHandler('USER_JOIN', data);
    });

    this.client.subscribe(`/topic/documents/${this.documentId}/user-left`, (message) => {
      const data = JSON.parse(message.body);
      console.log('User left:', data);
      this.notifyHandler('USER_LEAVE', data);
    });

    this.client.subscribe('/user/queue/document-update-result', (message) => {
      const result = JSON.parse(message.body);
      console.log('Document update result:', result);
      if (result.status === 'success' && result.updatedAt) {
        this.lastKnownUpdate = typeof result.updatedAt === 'string' ? 
          this.parseDateFromServer(result.updatedAt) : result.updatedAt;
        console.log('Updated lastKnownUpdate from update result to:', this.lastKnownUpdate);
      }
    });

    this.client.subscribe(`/topic/documents/${this.documentId}/restore`, (message) => {
      const data = JSON.parse(message.body);
      console.log('Document restored:', data);

      if (data.updatedAt) {
        this.lastKnownUpdate = typeof data.updatedAt === 'string' ? 
          this.parseDateFromServer(data.updatedAt) : data.updatedAt;
        console.log('Updated lastKnownUpdate from restore to:', this.lastKnownUpdate);
      }
      
      this.notifyHandler('CONTENT_UPDATE', {
        content: data.content,
        userId: data.updatedBy
      });
    });

    this.client.subscribe(`/topic/documents/${this.documentId}/active-users`, (message) => {
      try {
        const users = JSON.parse(message.body);
        console.log('Broadcasted active users received:', users);
        const safeUsers = Array.isArray(users) ? users : [];
        this.notifyHandler('USER_PRESENCE', { users: safeUsers });
      } catch (error) {
        console.error('Error parsing broadcasted active users data:', error);
        this.notifyHandler('USER_PRESENCE', { users: [] });
      }
    });

    this.sendMessage(`/app/documents/${this.documentId}/connect`, {});
  }

  private notifyHandler(type: string, data: any): void {
    const handler = this.messageHandlers.get(type);
    if (handler) {
      try {
        if (type === 'CONTENT_UPDATE' && (!data || typeof data.content !== 'string')) {
          console.error(`Invalid content update data:`, data);
          return;
        }

        handler(data);
      } catch (error) {
        console.error(`Error in handler for message type ${type}:`, error);
      }
    } else {
      console.warn(`No handler registered for message type: ${type}`);
    }
  }
  
  private applyOperation(operation: EditOperation): void {
    if (!operation) return;
    
    try {
      console.log(`Applying operation: type=${operation.type}, position=${operation.position}, character=${operation.character ? (operation.character.length > 10 ? operation.character.substring(0, 10) + '...' : operation.character) : ''}`);
      console.log(`Current shadow content length: ${this.shadowContent.length}`);
      
      if (operation.type === 'insert') {
        const safePosition = Math.min(Math.max(0, operation.position), this.shadowContent.length);
        
        if (safePosition !== operation.position) {
          console.warn(`Adjusted insert position from ${operation.position} to ${safePosition} (content length: ${this.shadowContent.length})`);
        }
        
        this.shadowContent = 
          this.shadowContent.substring(0, safePosition) + 
          operation.character + 
          this.shadowContent.substring(safePosition);
          
        console.log(`After insert: shadow content length = ${this.shadowContent.length}`);
      } else if (operation.type === 'delete') {
        const safePosition = Math.min(Math.max(0, operation.position), this.shadowContent.length - 1);
        
        if (operation.position >= 0 && operation.position < this.shadowContent.length) {
          this.shadowContent = 
            this.shadowContent.substring(0, safePosition) + 
            this.shadowContent.substring(safePosition + 1);
            
          console.log(`After delete: shadow content length = ${this.shadowContent.length}`);
        } else {
          console.warn(`Skipping invalid delete operation at position ${operation.position} (content length: ${this.shadowContent.length})`);
        }
      } else if (operation.type === 'replace') {
        console.log('Applying replace operation, setting shadow content to new value');
        this.shadowContent = operation.character;
        console.log(`After replace: shadow content length = ${this.shadowContent.length}`);
      }
    } catch (error) {
      console.error('Error applying operation:', error);
    }
  }
  
  sendOperation(type: OperationType, position: number, character: string = ''): void {
    if (!this.client || !this.documentId || !this.isConnected()) {
      console.warn('WebSocket not connected, can\'t send operation');
      return;
    }
    
    const operation: EditOperation = {
      documentId: this.documentId,
      position,
      type,
      character,
      clientId: this.clientId,
      clientTimestamp: Date.now()
    };
    
    this.applyOperation(operation);
    
    this.pendingOperations.push(operation);
    
    if (this.operationTimeoutRef) {
      clearTimeout(this.operationTimeoutRef);
    }
    
    this.operationTimeoutRef = setTimeout(() => {
      this.flushPendingOperations();
    }, 100);
  }
  
  private flushPendingOperations(): void {
    if (!this.pendingOperations.length) return;
    
    try {
      const formattedDate = this.formatDateForServer(this.lastKnownUpdate);
      
      console.log('Original date:', this.lastKnownUpdate);
      console.log('Timezone offset (minutes):', this.lastKnownUpdate.getTimezoneOffset());
      console.log('Formatted date for server:', formattedDate);
      
      const batchRequest = {
        operations: [...this.pendingOperations],
        lastKnownUpdate: formattedDate,
        clientId: this.clientId
      };
      
      console.log('Sending with lastKnownUpdate:', batchRequest.lastKnownUpdate);
      
      this.sendMessage(`/app/documents/${this.documentId}/batch-operations`, batchRequest);
      
      console.log(`Sent batch of ${this.pendingOperations.length} operations`);
      this.pendingOperations = [];
    } catch (error) {
      console.error('Error sending operations:', error);
    }
  }
  
  private formatDateForServer(date: Date): string {
    if (!date) return '';
    
    try {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      const milliseconds = String(date.getMilliseconds()).padStart(3, '0');
      
      return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}.${milliseconds}`;
    } catch (error) {
      console.error('Error formatting date for server:', error);
      return '';
    }
  }
  
  sendContentUpdate(content: string): void {
    console.warn('Using legacy full-content update. Consider using operation-based updates.');
  }

  sendBulkContentUpdate(content: string): void {
    if (!this.client || !this.documentId || !this.isConnected()) {
      console.warn('WebSocket not connected, can\'t send bulk update');
      return;
    }

    this.shadowContent = content;
    
    const replaceOperation: EditOperation = {
      type: 'replace' as OperationType,
      position: 0,
      character: content,
      clientId: this.clientId,
      documentId: this.documentId || '',
      clientTimestamp: Date.now()
    };
    
    const updateRequest = {
      operations: [replaceOperation],
      lastKnownUpdate: this.formatDateForServer(this.lastKnownUpdate),
      clientId: this.clientId
    };
    
    console.log('Sending bulk content update as replace operation');
    
    this.sendMessage(`/app/documents/${this.documentId}/update`, updateRequest);
  }

  private sendMessage(destination: string, body: any): void {
    if (this.client && this.client.connected) {
      this.client.publish({
        destination,
        body: JSON.stringify(body),
      });
    }
  }

  onContentUpdate(handler: (data: ContentUpdateMessage) => void): void {
    console.log('Registering CONTENT_UPDATE handler');
    
    const wrappedHandler = (data: ContentUpdateMessage) => {
      try {
        handler(data);
      } catch (error) {
        console.error('Error in content update handler:', error);
      }
    };
    
    this.messageHandlers.set('CONTENT_UPDATE', wrappedHandler);
  }

  onOperationUpdate(handler: (data: any) => void): void {
    console.log('Registering OPERATION_UPDATE handler');
    
    const operationHandler = (data: any) => {
      try {
        handler(data);
      } catch (error) {
        console.error('Error in operation update handler:', error);
      }
    };
    
    this.messageHandlers.set('OPERATION_UPDATE', operationHandler);
  }

  onUserPresence(handler: (data: any) => void): void {
    console.log('Registering USER_PRESENCE handler');
    
    const userPresenceHandler = (data: any) => {
      try {
        handler(data);
      } catch (error) {
        console.error('Error in user presence handler:', error);
      }
    };
    
    this.messageHandlers.set('USER_PRESENCE', userPresenceHandler);
    
    this.messageHandlers.set('USER_JOIN', (data: any) => {
      console.log('User joined event received:', data);
    });
    
    this.messageHandlers.set('USER_LEAVE', (data: any) => {
      console.log('User left event received:', data);
    });
    
    this.messageHandlers.set('VERSION_CONFLICT', (data) => {
      console.log('Version conflict detected:', data);
      try {
        if (data && data.content) {
          handler({ content: data.content });
        }
      } catch (error) {
        console.error('Error handling version conflict:', error);
      }
    });
  }

  isConnected(): boolean {
    return this.client !== null && this.client.connected;
  }

  getClientId(): string {
    return this.clientId;
  }

  private handleReconnect(documentId: string, userId: string, resolve: () => void): void {
    this.reconnectAttempts++;
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts - 1), 30000);
      console.log(`Will attempt to reconnect in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
      
      setTimeout(() => {
        try {
          if (this.client) {
            this.client.deactivate();
            this.client = null;
          }
          
          this.isConnecting = false;
          this.connectionPromise = null;

          this.connect(documentId, userId).catch(err => {
            console.error('Reconnection attempt failed:', err);
          });
        } catch (error) {
          console.error('Error during reconnection:', error);
        }
      }, delay);
    } else {
      console.error(`Failed to reconnect after ${this.maxReconnectAttempts} attempts`);
    }

    resolve();
  }

  setShadowContent(content: string): void {
    this.shadowContent = content;
  }
  
  getShadowContent(): string {
    return this.shadowContent;
  }

  private parseDateFromServer(dateStr: string): Date {
    try {
      console.log('Parsing server date string:', dateStr);
      
      const regex = /(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})\.?(\d*)/;
      const match = dateStr.match(regex);
      
      if (match) {
        const year = parseInt(match[1], 10);
        const month = parseInt(match[2], 10) - 1;
        const day = parseInt(match[3], 10);
        const hour = parseInt(match[4], 10);
        const minute = parseInt(match[5], 10);
        const second = parseInt(match[6], 10);
        
        let milliseconds = 0;
        if (match[7]) {
          const msStr = match[7].substring(0, 3).padEnd(3, '0');
          milliseconds = parseInt(msStr, 10);
        }
        
        console.log(`Creating date with components: ${year}-${month+1}-${day} ${hour}:${minute}:${second}.${milliseconds}`);
        
        const date = new Date(year, month, day, hour, minute, second, milliseconds);
        console.log('Parsed date:', date);
        return date;
      }
      
      console.log('Regex parsing failed, trying standard Date constructor');
      const date = new Date(dateStr);
      
      if (!isNaN(date.getTime())) {
        console.log('Successfully parsed date with standard constructor:', date);
        return date;
      }
      
      console.warn('Failed to parse date string:', dateStr);
      return new Date();
    } catch (error) {
      console.error('Error parsing date from server:', error);
      return new Date();
    }
  }

  verifyContentSync(serverContent: string): boolean {
    const localContent = this.shadowContent;
    
    if (localContent === serverContent) {
      console.log('Content is synchronized with server');
      return true;
    }
    
    console.warn('Content is NOT synchronized with server');
    console.log('Local content length:', localContent.length);
    console.log('Server content length:', serverContent.length);
    
    let diffIndex = -1;
    const minLength = Math.min(localContent.length, serverContent.length);
    
    for (let i = 0; i < minLength; i++) {
      if (localContent[i] !== serverContent[i]) {
        diffIndex = i;
        break;
      }
    }
    
    if (diffIndex >= 0) {
      console.log(`Content differs starting at position ${diffIndex}`);
      console.log('Local content around diff:', localContent.substring(Math.max(0, diffIndex - 10), diffIndex + 10));
      console.log('Server content around diff:', serverContent.substring(Math.max(0, diffIndex - 10), diffIndex + 10));
    } else if (localContent.length !== serverContent.length) {
      console.log('Content lengths differ, but all common characters match');
    }
    
    return false;
  }

  syncWithServerContent(serverContent: string): void {
    console.log('Forcing content sync with server');
    this.shadowContent = serverContent;
  }
}

export const webSocketService = WebSocketService.getInstance();