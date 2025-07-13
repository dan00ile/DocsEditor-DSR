export interface User {
  id: string;
  username: string;
  email: string;
  color?: string;
  isActive: boolean;
  lastActive: Date;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user?: {
    id: string;
    username: string;
    email: string;
  };
}

export interface LoginRequest {
  username: string;
  password: string;
  deviceId?: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface Document {
  id: string;
  title: string;
  content: string;
  createdAt: Date;
  updatedAt: Date;
  createdBy: string;
  versions: DocumentVersion[];
  activeUsers?: ActiveUserDto[];
  versionCounter?: number;
}

export interface DocumentVersion {
  id: string;
  versionName: string;
  content: string;
  createdBy: string;
  createdAt: Date;
  authorName: string;
  createdByUsername?: string;
}

export interface CursorPosition {
  userId: string;
  username: string;
  position: number;
  color: string;
}

export interface CollaborationState {
  activeUsers: User[];
  cursors: CursorPosition[];
  lastUpdate: Date;
}

export interface WebSocketMessage {
  type: 'CURSOR_UPDATE' | 'CONTENT_UPDATE' | 'USER_JOIN' | 'USER_LEAVE';
  payload: any;
  userId: string;
  timestamp: Date;
}

export interface ContentUpdateMessage {
  content: string;
  position: number;
  userId: string;
  timestamp?: Date;
  clientId?: string;
}

export interface CursorUpdateMessage {
  userId: string;
  position: number;
  username?: string;
  color?: string;
}

export type OperationType = 'insert' | 'delete' | 'replace';

export interface EditOperation {
  documentId: string;
  position: number;
  type: OperationType;
  character: string;
  clientId: string;
  clientTimestamp: number;
  serverTimestamp?: number;
  userId?: string;
}

export interface DocumentUpdateRequest {
  operations: EditOperation[];
  lastKnownUpdate: string | null;
  clientId: string;
}

export interface SaveVersionRequest {
  versionName: string;
}

export interface ActiveUserDto {
  userId: string;
  username: string;
  color: string;
  isTyping: boolean;
  cursorPosition: number;
  lastActive: Date;
  isActive: boolean;
  connectedAt?: Date;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface ApiErrorResponse {
  success: boolean;
  message: string;
  errors?: Array<{
    field: string;
    message: string;
  }>;
}