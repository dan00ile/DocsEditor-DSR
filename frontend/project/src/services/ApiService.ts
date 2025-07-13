import axios, { AxiosInstance, AxiosResponse, AxiosError } from 'axios';
import { API_CONFIG } from '../config/api';
import { AuthResponse, LoginRequest, RegisterRequest, User, Document, DocumentVersion, DocumentUpdateRequest, SaveVersionRequest } from '../types';

class ApiService {
  private api: AxiosInstance;
  private isRefreshing = false;
  private refreshAttempts = 0;
  private maxRefreshAttempts = 1;

  constructor() {
    this.api = axios.create({
      baseURL: API_CONFIG.BASE_URL,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    this.api.interceptors.request.use(
      (config) => {
        const token = localStorage.getItem('auth_token');
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    this.api.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config;
        
        if (
          error.response?.status === 401 &&
          !originalRequest._retry &&
          !this.isRefreshing &&
          this.refreshAttempts < this.maxRefreshAttempts &&
          originalRequest.url !== API_CONFIG.ENDPOINTS.AUTH.REFRESH
        ) {
          originalRequest._retry = true;
          this.isRefreshing = true;
          this.refreshAttempts++;

          try {
            const refreshToken = localStorage.getItem('refresh_token');
            if (!refreshToken) {
              throw new Error('Токен обновления отсутствует');
            }

            const response = await this.api.post(API_CONFIG.ENDPOINTS.AUTH.REFRESH, {
              refreshToken,
            });

            if (response.data.success && response.data.data) {
              const { token, refreshToken: newRefreshToken } = response.data.data;
              
              localStorage.setItem('auth_token', token);
              localStorage.setItem('refresh_token', newRefreshToken);
              
              originalRequest.headers.Authorization = `Bearer ${token}`;
              
              this.isRefreshing = false;
              this.refreshAttempts = 0;
              
              return this.api(originalRequest);
            } else {
              throw new Error('Не удалось обновить токен');
            }
          } catch (refreshError) {
            this.isRefreshing = false;
            
            localStorage.removeItem('auth_token');
            localStorage.removeItem('refresh_token');
            localStorage.removeItem('user_id');
            localStorage.removeItem('user_username');
            
            return Promise.reject(refreshError);
          }
        }
        
        return Promise.reject(error);
      }
    );
  }

  async login(credentials: LoginRequest): Promise<AxiosResponse<any>> {
    try {
      this.refreshAttempts = 0;
      console.log('Sending login request:', credentials);
      const response = await this.api.post(API_CONFIG.ENDPOINTS.AUTH.LOGIN, credentials);
      console.log('Login response:', response);
      return response;
    } catch (error: any) {
      console.error('Login error:', error);
      if (error.response?.status === 401) {
        error.message = 'Неверное имя пользователя или пароль';
      } else if (!error.response) {
        error.message = 'Не удалось подключиться к серверу';
      }
      throw error;
    }
  }

  async register(userData: RegisterRequest): Promise<AxiosResponse<any>> {
    try {
      this.refreshAttempts = 0;
      const response = await this.api.post(API_CONFIG.ENDPOINTS.AUTH.REGISTER, userData);
      return response;
    } catch (error: any) {
      console.error('Register error:', error);
      if (error.response?.status === 409) {
        error.message = 'Пользователь с таким именем или email уже существует';
      } else if (!error.response) {
        error.message = 'Не удалось подключиться к серверу';
      }
      throw error;
    }
  }

  async refreshToken(refreshToken: string): Promise<AxiosResponse<any>> {
    return this.api.post(API_CONFIG.ENDPOINTS.AUTH.REFRESH, { refreshToken });
  }

  async getDocuments(): Promise<AxiosResponse<any>> {
    return this.api.get(API_CONFIG.ENDPOINTS.DOCUMENTS.LIST);
  }

  async getDocument(id: string): Promise<AxiosResponse<any>> {
    return this.api.get(API_CONFIG.ENDPOINTS.DOCUMENTS.GET(id));
  }

  async createDocument(title: string): Promise<AxiosResponse<any>> {
    return this.api.post(API_CONFIG.ENDPOINTS.DOCUMENTS.CREATE, { title });
  }

  async updateDocument(id: string, request: DocumentUpdateRequest): Promise<AxiosResponse<any>> {
    return this.api.put(API_CONFIG.ENDPOINTS.DOCUMENTS.UPDATE_CONTENT(id), request);
  }

  async deleteDocument(id: string): Promise<AxiosResponse<any>> {
    return this.api.delete(API_CONFIG.ENDPOINTS.DOCUMENTS.DELETE(id));
  }

  async getVersions(documentId: string): Promise<AxiosResponse<any>> {
    return this.api.get(API_CONFIG.ENDPOINTS.VERSIONS.LIST(documentId));
  }

  async createVersion(documentId: string, request: SaveVersionRequest): Promise<AxiosResponse<any>> {
    return this.api.post(API_CONFIG.ENDPOINTS.VERSIONS.CREATE(documentId), request);
  }

  async restoreVersion(documentId: string, versionId: string): Promise<AxiosResponse<any>> {
    return this.api.post(API_CONFIG.ENDPOINTS.VERSIONS.RESTORE(documentId, versionId));
  }

  async getActiveUsers(documentId: string): Promise<AxiosResponse<any>> {
    return this.api.get(API_CONFIG.ENDPOINTS.DOCUMENTS.ACTIVE_USERS(documentId));
  }

  async sendHeartbeat(documentId: string): Promise<AxiosResponse<any>> {
    return this.api.post(API_CONFIG.ENDPOINTS.DOCUMENTS.HEARTBEAT(documentId));
  }
}

export const apiService = new ApiService();