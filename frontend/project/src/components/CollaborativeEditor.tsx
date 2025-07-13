import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Document, User, DocumentVersion, DocumentUpdateRequest, SaveVersionRequest, ContentUpdateMessage, EditOperation, OperationType } from '../types';
import { apiService } from '../services/ApiService';
import { useWebSocket } from '../hooks/useWebSocket';
import { useAuth } from '../hooks/useAuth';
import { webSocketService } from '../services/WebSocketService';
import {
  FileText,
  Users,
  LogOut,
  Save,
  Clock,
  GitBranch,
  Plus,
  ArrowLeft,
  RotateCcw,
  X,
  Link,
  Check
} from 'lucide-react';

interface CollaborativeEditorProps {
  documentId: string;
  onBack: () => void;
  onError?: (errorMessage: string) => void;
}

export const CollaborativeEditor: React.FC<CollaborativeEditorProps> = ({
                                                                          documentId,
                                                                          onBack,
                                                                          onError
                                                                        }) => {
  const { user, logout } = useAuth();
  const [document, setDocument] = useState<Document | null>(null);
  const [activeUsers, setActiveUsers] = useState<User[]>([]);
  const [versions, setVersions] = useState<DocumentVersion[]>([]);
  const [showVersionPanel, setShowVersionPanel] = useState(false);
  const [showVersionDialog, setShowVersionDialog] = useState(false);
  const [versionName, setVersionName] = useState('');
  const [isAutoSaving, setIsAutoSaving] = useState(false);
  const [error, setError] = useState('');
  const [heartbeatError, setHeartbeatError] = useState(false);
  const [linkCopied, setLinkCopied] = useState(false);
  const [syncStatus, setSyncStatus] = useState<'synced' | 'unsynced' | 'checking'>('checking');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const heartbeatIntervalRef = useRef<NodeJS.Timeout>();
  const heartbeatErrorCountRef = useRef<number>(0);
  const maxHeartbeatErrors = 3;
  const documentLoadedRef = useRef<boolean>(false);
  const updateTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const lastSentContentRef = useRef<string>('');
  const localContentRef = useRef<string>('');
  const activeUsersIntervalRef = useRef<NodeJS.Timeout>();
  const syncCheckIntervalRef = useRef<NodeJS.Timeout>();

  const cursorPositionRef = useRef<number>(0);
  const selectionStartRef = useRef<number>(0);
  const selectionEndRef = useRef<number>(0);
  const isComposingRef = useRef<boolean>(false);

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    e.preventDefault();
    
    const pastedText = e.clipboardData.getData('text');
    if (!pastedText) return;

    const textarea = e.target as HTMLTextAreaElement;
    const cursorPos = textarea.selectionStart;
    const selectionEnd = textarea.selectionEnd;

    const currentContent = document?.content || '';

    const newContent = 
      currentContent.substring(0, cursorPos) + 
      pastedText + 
      currentContent.substring(selectionEnd);
    
    console.log('Paste detected, using bulk update for pasted content');

    webSocketService.sendBulkContentUpdate(newContent);

    setDocument(prev => {
      if (!prev) return null;
      return { 
        ...prev, 
        content: newContent
      };
    });

    localContentRef.current = newContent;

    setTimeout(() => {
      if (textareaRef.current) {
        const newPosition = cursorPos + pastedText.length;
        textareaRef.current.selectionStart = newPosition;
        textareaRef.current.selectionEnd = newPosition;

        cursorPositionRef.current = newPosition;
        selectionStartRef.current = newPosition;
        selectionEndRef.current = newPosition;
      }
    }, 0);

    if (updateTimeoutRef.current) {
      clearTimeout(updateTimeoutRef.current);
    }
    
    setIsAutoSaving(true);
    updateTimeoutRef.current = setTimeout(() => {
      setIsAutoSaving(false);
    }, 500);
  };

  const handleContentChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const newContent = e.target.value;
    const textarea = e.target;

    const prevContent = document?.content || '';
    const shadowContent = webSocketService.getShadowContent();
    const currentLength = shadowContent.length;

    if (Math.abs(newContent.length - prevContent.length) > 100) {
      console.log('Large content change detected, using bulk update');
      webSocketService.sendBulkContentUpdate(newContent);

      setDocument(prev => {
        if (!prev) return null;
        return { 
          ...prev, 
          content: newContent
        };
      });
      return;
    }
    
    if (newContent.length > prevContent.length) {
      const diffIndex = findDiffIndex(prevContent, newContent);
      if (diffIndex >= 0 && diffIndex <= currentLength) {
        const insertedChar = newContent.charAt(diffIndex);
        sendInsertOperation(diffIndex, insertedChar);
      } else {
        console.warn(`Invalid insert position: ${diffIndex}, current length: ${currentLength}, using safe position`);
        const safePosition = Math.min(Math.max(0, diffIndex), currentLength);
        const insertedChar = newContent.charAt(diffIndex) || '';
        if (insertedChar) {
          sendInsertOperation(safePosition, insertedChar);
        }
      }
    } else if (newContent.length < prevContent.length) {
      const selectionLength = selectionEndRef.current - selectionStartRef.current;
      
      if (selectionLength > 1 && 
          prevContent.length - newContent.length === selectionLength) {
        console.log(`Deleting selection of ${selectionLength} characters at position ${selectionStartRef.current}`);

        for (let i = selectionEndRef.current - 1; i >= selectionStartRef.current; i--) {
          const deletedChar = prevContent.charAt(i);
          sendDeleteOperation(i, deletedChar);
        }
      } else {
        const diffIndex = findDiffIndex(prevContent, newContent);
        if (diffIndex >= 0 && diffIndex < currentLength) {
          const deletedChar = prevContent.charAt(diffIndex);
          sendDeleteOperation(diffIndex, deletedChar);
        } else {
          console.warn(`Invalid delete position: ${diffIndex}, current length: ${currentLength}, using safe position`);
          const safePosition = Math.min(Math.max(0, diffIndex), currentLength - 1);
          if (safePosition >= 0) {
            const deletedChar = prevContent.charAt(safePosition) || '';
            if (deletedChar) {
              sendDeleteOperation(safePosition, deletedChar);
            }
          }
        }
      }
    }

    cursorPositionRef.current = textarea.selectionStart;
    selectionStartRef.current = textarea.selectionStart;
    selectionEndRef.current = textarea.selectionEnd;

    setDocument(prev => {
      if (!prev) return null;
      return { 
        ...prev, 
        content: newContent
      };
    });

    localContentRef.current = newContent;

    if (updateTimeoutRef.current) {
      clearTimeout(updateTimeoutRef.current);
    }
    
    setIsAutoSaving(true);
    updateTimeoutRef.current = setTimeout(() => {
      setIsAutoSaving(false);
    }, 500);
  };

  const findDiffIndex = (oldStr: string, newStr: string): number => {
    if (!oldStr) oldStr = '';
    if (!newStr) newStr = '';
    
    const minLength = Math.min(oldStr.length, newStr.length);
    
    for (let i = 0; i < minLength; i++) {
      if (oldStr[i] !== newStr[i]) {
        return i;
      }
    }
    
    return minLength;
  };

  const sendInsertOperation = (position: number, character: string): void => {
    webSocketService.sendOperation('insert', position, character);
  };

  const sendDeleteOperation = (position: number, character: string): void => {
    webSocketService.sendOperation('delete', position, character);
  };

  const handleRemoteContentUpdate = (content: string, data?: ContentUpdateMessage) => {
    try {
      if (data?.clientId === webSocketService.getClientId()) {
        console.log('Received own update confirmation from server');
        
        if (data?.timestamp) {
          const timestamp = typeof data.timestamp === 'string' ? new Date(data.timestamp) : data.timestamp;
          setDocument(prev => {
            if (!prev) return prev;
            return {
              ...prev,
              updatedAt: timestamp
            };
          });
        }
        return;
      }

      if (localContentRef.current === content) {
        console.log('Remote content is identical to local content, skipping update');
        return;
      }

      console.log('Applying remote content update from user:', data?.userId);

      const textarea = textareaRef.current;
      const selectionStart = textarea?.selectionStart || 0;
      const selectionEnd = textarea?.selectionEnd || 0;
      const scrollTop = textarea?.scrollTop || 0;

      setDocument(prev => {
        if (!prev) return prev;
        return {
          ...prev,
          content: content,
          updatedAt: data?.timestamp ? 
            (typeof data.timestamp === 'string' ? new Date(data.timestamp) : data.timestamp) : 
            new Date()
        };
      });

      localContentRef.current = content;

      requestAnimationFrame(() => {
        if (textarea) {
          const maxPos = textarea.value.length;
          const newSelectionStart = Math.min(selectionStart, maxPos);
          const newSelectionEnd = Math.min(selectionEnd, maxPos);

          textarea.selectionStart = newSelectionStart;
          textarea.selectionEnd = newSelectionEnd;
          textarea.scrollTop = scrollTop;
        }
      });
    } catch (error) {
      console.error('Error handling remote content update:', error);
    }
  };

  const handleSelection = (e: React.SyntheticEvent<HTMLTextAreaElement>) => {
    const textarea = e.target as HTMLTextAreaElement;
    cursorPositionRef.current = textarea.selectionStart;
    selectionStartRef.current = textarea.selectionStart;
    selectionEndRef.current = textarea.selectionEnd;
  };

  const handleCompositionStart = () => {
    isComposingRef.current = true;
  };

  const handleCompositionEnd = () => {
    isComposingRef.current = false;
  };

  const debouncedSendContentUpdate = useCallback((content: string) => {
    if (updateTimeoutRef.current) {
      clearTimeout(updateTimeoutRef.current);
    }

    setIsAutoSaving(true);

    lastSentContentRef.current = content;

    updateTimeoutRef.current = setTimeout(() => {
      setIsAutoSaving(false);
    }, 500);
  }, []);

  const { isConnected, sendOperation } = useWebSocket({
    documentId: documentId,
    userId: user?.id || '',
    onContentUpdate: handleRemoteContentUpdate,
    onUserPresence: (data) => {
      console.log('User presence update received:', data);
      
      let users = [];
      
      if (Array.isArray(data)) {
        users = data;
      } else if (data && Array.isArray(data.users)) {
        users = data.users;
      } else if (data && typeof data === 'object') {
        users = Object.values(data);
      } else {
        console.warn('Received unexpected user presence data format:', data);
        users = [];
      }
      
      const processedUsers = users.map((u: any) => ({
        ...u,
        lastActive: u.lastActive ? new Date(u.lastActive) : new Date(),
        connectedAt: u.connectedAt ? new Date(u.connectedAt) : new Date(),
        isActive: u.isActive !== undefined ? u.isActive : true,
        color: u.color || '#3B82F6'
      }));
      
      console.log('Setting active users:', processedUsers);
      setActiveUsers(processedUsers);
    },
  });

  const startHeartbeat = () => {
    heartbeatIntervalRef.current = setInterval(async () => {
      try {
        await apiService.sendHeartbeat(documentId);
        heartbeatErrorCountRef.current = 0;
        if (heartbeatError) {
          setHeartbeatError(false);
        }
      } catch (err) {
        console.error('Heartbeat failed:', err);
        heartbeatErrorCountRef.current++;

        if (heartbeatErrorCountRef.current >= maxHeartbeatErrors) {
          setHeartbeatError(true);
        }
      }
    }, 30000);
  };

  const copyDocumentLink = () => {
    const url = `${window.location.origin}/documents/${documentId}`;
    navigator.clipboard.writeText(url).then(
        () => {
          setLinkCopied(true);
          setTimeout(() => setLinkCopied(false), 2000);
        },
        (err) => {
          console.error('Could not copy link: ', err);
          setError('Failed to copy link to clipboard');
        }
    );
  };

  const loadDocument = async () => {
    if (document) {
      console.log('Document already loaded, skipping fetch');
      return;
    }

    console.log(`Loading document ${documentId}`);
    try {
      const response = await apiService.getDocument(documentId);
      if (response.data.success && response.data.data) {
        const doc = response.data.data;
        setDocument({
          ...doc,
          createdAt: new Date(doc.createdAt),
          updatedAt: new Date(doc.updatedAt)
        });

        webSocketService.setShadowContent(doc.content);
        localContentRef.current = doc.content;

        if (doc.activeUsers && Array.isArray(doc.activeUsers)) {
          setActiveUsers(doc.activeUsers.map((u: any) => ({
            ...u,
            lastActive: u.lastActive ? new Date(u.lastActive) : new Date(),
            connectedAt: u.connectedAt ? new Date(u.connectedAt) : new Date(),
            isActive: u.isActive !== undefined ? u.isActive : true,
            color: u.color || '#3B82F6'
          })));
        } else {
          setActiveUsers([]);
        }

        setError('');
        setHeartbeatError(false);
        heartbeatErrorCountRef.current = 0;
        console.log(`Document ${documentId} loaded successfully`);
        
        if (isConnected) {
          setSyncStatus('synced');
        }
      }
    } catch (err: any) {
      console.error('Failed to load document:', err);

      const errorMessage = err.response?.data?.message || 'Не удалось загрузить документ. Пожалуйста, попробуйте позже.';
      setError(errorMessage);

      if (onError) {
        onError(errorMessage);
      }

      if (err.response?.status === 401) {
        console.error('Authentication error when loading document');
      } else if (err.response?.status === 403) {
        const errorMsg = 'У вас нет доступа к этому документу';
        setError(errorMsg);
        if (onError) onError(errorMsg);
      } else if (err.response?.status === 404) {
        const errorMsg = 'Документ не найден';
        setError(errorMsg);
        if (onError) onError(errorMsg);
      }
    }
  };

  const loadVersions = async () => {
    try {
      const response = await apiService.getVersions(documentId);
      if (response.data.success && response.data.data) {
        setVersions(response.data.data.map((v: DocumentVersion) => ({
          ...v,
          createdAt: new Date(v.createdAt)
        })));
      }
    } catch (err) {
      console.error('Failed to load versions:', err);
    }
  };

  const startSyncCheck = () => {
    if (syncCheckIntervalRef.current) {
      clearInterval(syncCheckIntervalRef.current);
    }

    syncCheckIntervalRef.current = setInterval(async () => {
      try {
        if (!documentId || !document) return;
        
        if (!isConnected) {
          setSyncStatus('unsynced');
          return;
        }
        
        setSyncStatus('checking');
        console.log('Performing sync check with server...');
        
        const response = await apiService.getDocument(documentId);
        if (response.data.success && response.data.data) {
          const serverDoc = response.data.data;
          
          const isSynced = webSocketService.verifyContentSync(serverDoc.content);
          
          if (!isSynced) {
            console.warn('Content is out of sync with server!');
            setSyncStatus('unsynced');
          } else {
            setSyncStatus('synced');
          }
        }
      } catch (err) {
        console.error('Sync check failed:', err);
        setSyncStatus('unsynced');
      }
    }, 30000);
  };

  useEffect(() => {
    if (!documentLoadedRef.current) {
      console.log(`Initial loading of document ${documentId}`);
      loadDocument();
      documentLoadedRef.current = true;
    }

    loadVersions();
    startHeartbeat();
    startSyncCheck();

    const handleBeforeUnload = () => {
      console.log('Page is being unloaded, disconnecting from document');
      if (webSocketService.isConnected()) {
        webSocketService.disconnect();
      }
    };

    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      console.log(`Cleaning up document ${documentId} resources`);
      
      window.removeEventListener('beforeunload', handleBeforeUnload);
      
      if (heartbeatIntervalRef.current) {
        clearInterval(heartbeatIntervalRef.current);
        heartbeatIntervalRef.current = undefined;
      }
      if (syncCheckIntervalRef.current) {
        clearInterval(syncCheckIntervalRef.current);
        syncCheckIntervalRef.current = undefined;
      }
      if (updateTimeoutRef.current) {
        clearTimeout(updateTimeoutRef.current);
        updateTimeoutRef.current = null;
      }
      documentLoadedRef.current = false;
    };
  }, [documentId]);

  useEffect(() => {
    if (!isConnected) {
      setSyncStatus('unsynced');
    } else if (document) {
      const checkSync = async () => {
        try {
          const response = await apiService.getDocument(documentId);
          if (response.data.success && response.data.data) {
            const serverDoc = response.data.data;
            const isSynced = webSocketService.verifyContentSync(serverDoc.content);
            setSyncStatus(isSynced ? 'synced' : 'unsynced');
          }
        } catch (err) {
          console.error('Initial sync check failed:', err);
          setSyncStatus('unsynced');
        }
      };
      
      checkSync();
    }
  }, [isConnected, document, documentId]);

  useEffect(() => {
    if (textareaRef.current && document?.content) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${textareaRef.current.scrollHeight}px`;
    }
  }, [document?.content]);

  const handleCreateVersion = async () => {
    if (!versionName.trim() || !document) return;

    try {
      const request: SaveVersionRequest = {
        versionName: versionName.trim()
      };

      const response = await apiService.createVersion(document.id, request);
      if (response.data.success && response.data.data) {
        setVersions(prev => [response.data.data, ...prev]);
        setVersionName('');
        setShowVersionDialog(false);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create version');
    }
  };

  const handleLegacyContentUpdate = (content: string) => {
    if (!document) return;
    
    const currentContent = document.content;
    
    setDocument(prev => {
      if (!prev) return null;
      return { ...prev, content };
    });
    
    localContentRef.current = content;
    
    webSocketService.setShadowContent(content);
    
    setIsAutoSaving(true);
    if (updateTimeoutRef.current) {
      clearTimeout(updateTimeoutRef.current);
    }
    
    updateTimeoutRef.current = setTimeout(() => {
      setIsAutoSaving(false);
    }, 500);
  };

  const handleRestoreVersion = async (versionId: string) => {
    if (!document) return;

    try {
      const response = await apiService.restoreVersion(document.id, versionId);
      if (response.data.success && response.data.data) {
        const restoredDoc = response.data.data;
        setDocument({
          ...restoredDoc,
          createdAt: new Date(restoredDoc.createdAt),
          updatedAt: new Date(restoredDoc.updatedAt)
        });
        handleLegacyContentUpdate(restoredDoc.content);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to restore version');
    }
  };

  const handleLogout = async () => {
    try {
      await logout();
      onBack();
    } catch (err) {
      console.error('Logout failed:', err);
    }
  };

  if (!document) {
    return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center">
          {error ? (
              <div className="bg-red-50 border border-red-200 text-red-700 px-6 py-4 rounded-lg mb-6 max-w-md w-full">
                <div className="font-medium">Ошибка</div>
                <div>{error}</div>
                <button
                    onClick={onBack}
                    className="mt-4 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
                >
                  Вернуться к документам
                </button>
              </div>
          ) : (
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
          )}
        </div>
    );
  }

  return (
      <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <header className="h-16 bg-white border-b border-gray-200">
          <div className="flex items-center justify-between h-full px-6">
            {/* Left Section */}
            <div className="flex items-center space-x-4">
              <button
                  onClick={onBack}
                  className="p-2 rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-700 transition-colors"
              >
                <ArrowLeft className="w-5 h-5" />
              </button>

              <div className="flex items-center space-x-2">
                <FileText className="w-6 h-6 text-blue-600" />
                <h1 className="text-xl font-semibold text-gray-900">
                  {document.title}
                </h1>
              </div>

              <button
                  onClick={copyDocumentLink}
                  className="flex items-center space-x-2 px-3 py-2 rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-700 transition-colors"
                  title="Скопировать ссылку в буфер обмена"
              >
                {linkCopied ? (
                    <>
                      <Check className="w-4 h-4 text-green-500" />
                      <span className="text-sm text-green-500">Скопировано!</span>
                    </>
                ) : (
                    <>
                      <Link className="w-4 h-4" />
                      <span className="text-sm">Скопировать ссылку</span>
                    </>
                )}
              </button>
            </div>

            <div className="flex items-center space-x-2">
              {isAutoSaving ? (
                  <div className="flex items-center space-x-2 text-green-500">
                    <Save className="w-4 h-4 animate-pulse" />
                    <span className="text-sm">Сохранение...</span>
                  </div>
              ) : (
                  <div className="flex items-center space-x-2 text-gray-600">
                    <Clock className="w-4 h-4" />
                    <span className="text-sm">
                  Последнее сохранение: {document.updatedAt ? new Intl.DateTimeFormat('ru-RU', {
                      hour: '2-digit',
                      minute: '2-digit',
                      second: '2-digit',
                      hour12: false
                    }).format(document.updatedAt) : 'Н/Д'}
                </span>
                  </div>
              )}

              {/* Индикатор синхронизации */}
              <div className={`flex items-center space-x-1 ml-4 ${
                syncStatus === 'synced' ? 'text-green-600' : 
                syncStatus === 'checking' ? 'text-blue-600' : 'text-amber-600'
              }`}>
                <div className={`w-2 h-2 rounded-full ${
                  syncStatus === 'synced' ? 'bg-green-500' : 
                  syncStatus === 'checking' ? 'bg-blue-500' : 'bg-amber-500'
                }`}></div>
                <span className="text-xs">
                  {syncStatus === 'synced' ? 'Синхронизировано' : 
                   syncStatus === 'checking' ? 'Проверка синхронизации...' : 'Не синхронизировано'}
                </span>
                {syncStatus === 'unsynced' && (
                  <button 
                    onClick={async () => {
                      try {
                        setSyncStatus('checking');
                        const response = await apiService.getDocument(documentId);
                        if (response.data.success && response.data.data) {
                          const serverDoc = response.data.data;
                          webSocketService.syncWithServerContent(serverDoc.content);
                          setDocument(prev => {
                            if (!prev) return null;
                            return {...prev, content: serverDoc.content};
                          });
                          setSyncStatus('synced');
                        }
                      } catch (err) {
                        console.error('Sync failed:', err);
                        setSyncStatus('unsynced');
                      }
                    }}
                    className="text-xs underline hover:text-blue-700"
                  >
                    Синхронизировать
                  </button>
                )}
              </div>
            </div>

            <div className="flex items-center space-x-3">
              <button
                  onClick={() => setShowVersionDialog(true)}
                  className="flex items-center space-x-2 px-3 py-2 rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                <span className="text-sm">Сохранить версию</span>
              </button>

              <button
                  onClick={() => setShowVersionPanel(!showVersionPanel)}
                  className="flex items-center space-x-2 px-3 py-2 rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-700 transition-colors"
              >
                <GitBranch className="w-4 h-4" />
                <span className="text-sm">Версии ({versions.length})</span>
              </button>

              <div className="flex items-center space-x-3">
                <div className="text-sm text-gray-700">
                  {user?.username}
                </div>
                <div
                    className="w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-medium"
                    style={{ backgroundColor: user?.color || '#3B82F6' }}
                >
                  {user?.username.charAt(0).toUpperCase()}
                </div>
              </div>

              <button
                  onClick={handleLogout}
                  className="p-2 rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-700 transition-colors"
                  title="Logout"
              >
                <LogOut className="w-5 h-5" />
              </button>
            </div>
          </div>
        </header>

        {error && (
            <div className="bg-red-50 border-b border-red-200 text-red-700 px-6 py-3">
              {error}
              <button
                  onClick={() => setError('')}
                  className="ml-2 text-red-500 hover:text-red-700"
              >
                ×
              </button>
            </div>
        )}

        {heartbeatError && (
            <div className="bg-yellow-50 border-b border-yellow-200 text-yellow-700 px-6 py-3">
              Обнаружены проблемы с подключением. Ваши изменения могут не сохраниться.
              <button
                  onClick={loadDocument}
                  className="ml-2 text-blue-600 hover:text-blue-800 underline"
              >
                Обновить
              </button>
            </div>
        )}

        <div className="flex h-[calc(100vh-4rem)]">
          <div className="flex-1 flex flex-col">
            <div className="flex-1 relative">
              <div className="h-full border-r border-gray-200">
                <div className="relative h-full">
                <textarea
                    ref={textareaRef}
                    value={document?.content || ''}
                    onChange={handleContentChange}
                    onSelect={handleSelection}
                    onCompositionStart={handleCompositionStart}
                    onCompositionEnd={handleCompositionEnd}
                    onPaste={handlePaste}
                    className="w-full min-h-full p-6 resize-none focus:outline-none bg-white text-gray-900 placeholder-gray-400 text-lg leading-relaxed"
                    placeholder="Начните вводить текст документа здесь..."
                    style={{
                      fontFamily: 'ui-monospace, SFMono-Regular, "SF Mono", Consolas, "Liberation Mono", Menlo, monospace',
                      lineHeight: '1.7'
                    }}
                />
                </div>
              </div>
            </div>
          </div>

          <div className="flex">
            <div className="w-64 bg-white border-r border-gray-200">
              <div className="p-4 border-b border-gray-200">
                <div className="flex items-center space-x-2">
                  <Users className="w-5 h-5 text-gray-600" />
                  <h3 className="font-semibold text-gray-900">
                    Активные пользователи ({activeUsers.length})
                  </h3>
                </div>
              </div>

              <div className="p-4 space-y-3 max-h-96 overflow-y-auto">
                {Array.isArray(activeUsers) ? activeUsers.map((activeUser) => (
                    <div
                        key={activeUser.userId || activeUser.id}
                        className={`flex items-center space-x-3 p-3 rounded-lg ${
                            (activeUser.userId || activeUser.id) === user?.id
                                ? 'bg-blue-50 border border-blue-200'
                                : 'bg-gray-50'
                        }`}
                    >
                      <div className="relative">
                        <div
                            className="w-10 h-10 rounded-full flex items-center justify-center text-white font-medium"
                            style={{ backgroundColor: activeUser.color || '#3B82F6' }}
                        >
                          {activeUser.username.charAt(0).toUpperCase()}
                        </div>
                        {activeUser.isActive && (
                            <div className="absolute -bottom-1 -right-1 w-3 h-3 bg-green-500 rounded-full border-2 border-white"></div>
                        )}
                      </div>

                      <div className="flex-1 min-w-0">
                        <div className="font-medium text-gray-900 truncate">
                          {activeUser.username}
                          {(activeUser.userId || activeUser.id) === user?.id && (
                              <span className="text-xs ml-2 text-blue-600">
                          (You)
                        </span>
                          )}
                        </div>
                        <div className="text-xs flex items-center space-x-1 text-gray-500">
                          <Clock className="w-3 h-3" />
                          <span>
                        {activeUser.isActive ? 'Активен сейчас' : (activeUser.lastActive ? 
                          `Был в сети ${new Intl.DateTimeFormat('ru-RU', {
                            hour: '2-digit',
                            minute: '2-digit',
                            second: '2-digit',
                            hour12: false
                          }).format(new Date(activeUser.lastActive))}` : 'Неизвестно')}
                      </span>
                        </div>
                      </div>
                    </div>
                )) : (
                    <div className="p-8 text-center text-gray-500">
                      <Users className="w-12 h-12 mx-auto mb-3 text-gray-300" />
                      <p className="text-sm">Нет данных об активных пользователях</p>
                    </div>
                )}
              </div>

              {Array.isArray(activeUsers) && activeUsers.length === 0 && (
                  <div className="p-8 text-center text-gray-500">
                    <Users className="w-12 h-12 mx-auto mb-3 text-gray-300" />
                    <p className="text-sm">Нет других пользователей онлайн</p>
                  </div>
              )}
            </div>

            {showVersionPanel && (
                <div className="w-80 bg-white border-l border-gray-200">
                  <div className="p-4 border-b border-gray-200 flex items-center justify-between">
                    <div className="flex items-center space-x-2">
                      <GitBranch className="w-5 h-5 text-gray-600" />
                      <h3 className="font-semibold text-gray-900">
                        История версий
                      </h3>
                    </div>
                    <button
                        onClick={() => setShowVersionPanel(false)}
                        className="p-1 rounded-lg hover:bg-gray-100 text-gray-600"
                    >
                      <X className="w-5 h-5" />
                    </button>
                  </div>

                  <div className="p-4 space-y-3 max-h-96 overflow-y-auto">
                    {versions.length === 0 ? (
                        <div className="text-center py-8 text-gray-500">
                          <Clock className="w-12 h-12 mx-auto mb-3 text-gray-300" />
                          <p className="text-sm">Нет сохраненных версий</p>
                          <p className="text-xs mt-1">Создайте версию, чтобы увидеть ее здесь</p>
                        </div>
                    ) : (
                        versions.map((version) => (
                            <div
                                key={version.id}
                                className="p-4 rounded-lg border border-gray-200 bg-gray-50 hover:bg-gray-100 transition-colors"
                            >
                              <div className="flex items-start justify-between mb-2">
                                <h4 className="font-medium text-gray-900">
                                  {version.versionName}
                                </h4>
                                <button
                                    onClick={() => handleRestoreVersion(version.id)}
                                    className="p-1 rounded transition-colors hover:bg-gray-200 text-gray-600 hover:text-blue-600"
                                    title="Восстановить эту версию"
                                >
                                  <RotateCcw className="w-4 h-4" />
                                </button>
                              </div>

                              <div className="text-xs space-y-1 text-gray-500">
                                <div className="flex items-center space-x-1">
                                  <Users className="w-3 h-3" />
                                  <span>{version.createdByUsername || version.authorName}</span>
                                </div>
                                <div className="flex items-center space-x-1">
                                  <Clock className="w-3 h-3" />
                                  <span>{version.createdAt.toLocaleString()}</span>
                                </div>
                              </div>

                              <div className="mt-3 p-2 rounded text-xs max-h-20 overflow-y-auto bg-white text-gray-600">
                                {version.content.substring(0, 200)}
                                {version.content.length > 200 && '...'}
                              </div>
                            </div>
                        ))
                    )}
                  </div>
                </div>
            )}
          </div>
        </div>

        {showVersionDialog && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
              <div className="w-full max-w-md bg-white p-6 rounded-lg">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Создать новую версию
                </h3>

                <input
                    type="text"
                    value={versionName}
                    onChange={(e) => setVersionName(e.target.value)}
                    placeholder="Введите название версии..."
                    className="w-full px-4 py-2 rounded-lg border border-gray-300 text-gray-900 placeholder-gray-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-opacity-20 mb-4"
                    autoFocus
                    maxLength={100}
                />

                <div className="flex justify-end space-x-3">
                  <button
                      onClick={() => setShowVersionDialog(false)}
                      className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition-colors"
                  >
                    Отмена
                  </button>
                  <button
                      onClick={handleCreateVersion}
                      disabled={!versionName.trim()}
                      className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg disabled:opacity-50 transition-colors"
                  >
                    Создать версию
                  </button>
                </div>
              </div>
            </div>
        )}
      </div>
  );
};