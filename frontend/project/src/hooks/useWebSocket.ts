import { useEffect, useState, useRef } from 'react';
import { webSocketService } from '../services/WebSocketService';
import { ContentUpdateMessage } from '../types';

interface UseWebSocketProps {
  documentId: string;
  userId: string;
  onContentUpdate: (content: string, data?: ContentUpdateMessage) => void;
  onUserPresence: (users: any) => void;
}

export const useWebSocket = ({
  documentId,
  userId,
  onContentUpdate,
  onUserPresence
}: UseWebSocketProps) => {
  const [isConnected, setIsConnected] = useState(false);
  const hasSetupHandlers = useRef(false);
  const componentMountedRef = useRef(true);
  const connectionCheckRef = useRef<NodeJS.Timeout>();

  useEffect(() => {
    componentMountedRef.current = true;

    if (!documentId || !userId) return;

    let isMounted = true;
    
    const setupConnection = async () => {
      try {
        console.log(`Setting up WebSocket connection for document ${documentId} and user ${userId}`);
        await webSocketService.connect(documentId, userId);

        if (!hasSetupHandlers.current) {
          console.log('Setting up WebSocket event handlers');
          
          webSocketService.onContentUpdate((data: ContentUpdateMessage) => {
            if (!componentMountedRef.current) {
              console.log('Component unmounted, ignoring update');
              return;
            }

            if (data && typeof data.content === 'string') {
              try {
                onContentUpdate(data.content, data);
              } catch (error) {
                console.error('Error in onContentUpdate callback:', error);
              }
            } else {
              console.error('Received invalid content update:', data);
            }
          });

          webSocketService.onOperationUpdate((data: any) => {
            if (!componentMountedRef.current) {
              console.log('Component unmounted, ignoring operation update');
              return;
            }

            try {
              const shadowContent = webSocketService.getShadowContent();
              onContentUpdate(shadowContent, {
                content: shadowContent,
                userId: data.userId || data.updatedBy,
                timestamp: data.timestamp || 
                  (data.updatedAt ? 
                    (typeof data.updatedAt === 'string' ? new Date(data.updatedAt) : data.updatedAt) : 
                    new Date()),
                clientId: data.clientId,
                position: data.operation?.position
              });
            } catch (error) {
              console.error('Error in onOperationUpdate callback:', error);
            }
          });

          webSocketService.onUserPresence((data: any) => {
            if (componentMountedRef.current && data) {
              console.log('User presence update received:', data);
              onUserPresence(data);
            }
          });
          
          hasSetupHandlers.current = true;
          console.log('WebSocket handlers setup complete');
        }

        if (isMounted) {
          setIsConnected(true);
          console.log('WebSocket connection state updated to connected');
        }
      } catch (error) {
        console.error('Failed to setup WebSocket connection:', error);
        if (isMounted) {
          setIsConnected(false);
        }
      }
    };

    setupConnection();

    if (!connectionCheckRef.current) {
      connectionCheckRef.current = setInterval(() => {
        if (componentMountedRef.current) {
          const connected = webSocketService.isConnected();
          setIsConnected(connected);

          if (!connected) {
            console.log('WebSocket connection lost, attempting to reconnect...');
            setupConnection();
          }
        }
      }, 10000);
    }

    return () => {
      console.log(`Cleaning up WebSocket resources for document ${documentId}`);
      isMounted = false;
      componentMountedRef.current = false;

      if (connectionCheckRef.current) {
        clearInterval(connectionCheckRef.current);
        connectionCheckRef.current = undefined;
      }

      if (!documentId) {
        hasSetupHandlers.current = false;
      }
    };
  }, [documentId, userId, onContentUpdate, onUserPresence]);

  return {
    isConnected,
    sendOperation: (type: string, position: number, character: string = '') => {
      webSocketService.sendOperation(type as any, position, character);
    },
    sendContentUpdate: (content: string) => {
      webSocketService.sendContentUpdate(content);
    }
  };
};