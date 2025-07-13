export const API_CONFIG = {
  BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  WS_URL: import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws',
  ENDPOINTS: {
    AUTH: {
      LOGIN: '/auth/login',
      REGISTER: '/auth/register',
      REFRESH: '/auth/refresh'
    },
    USERS: {
      ACTIVE: '/users/active'
    },
    DOCUMENTS: {
      LIST: '/documents',
      GET: (id: string) => `/documents/${id}`,
      CREATE: '/documents',
      UPDATE_CONTENT: (id: string) => `/documents/${id}/content`,
      DELETE: (id: string) => `/documents/${id}`,
      ACTIVE_USERS: (id: string) => `/documents/${id}/active-users`,
      HEARTBEAT: (id: string) => `/documents/${id}/heartbeat`,
      VERSIONS: (id: string) => `/documents/${id}/versions`,
      SAVE_VERSION: (id: string) => `/documents/${id}/versions`,
      RESTORE_VERSION: (id: string, versionId: string) => `/documents/${id}/versions/${versionId}/restore`
    },
    VERSIONS: {
      LIST: (documentId: string) => `/documents/${documentId}/versions`,
      CREATE: (documentId: string) => `/documents/${documentId}/versions`,
      RESTORE: (documentId: string, versionId: string) => `/documents/${documentId}/versions/${versionId}/restore`
    }
  }
};