import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { apiService } from '../services/ApiService';
import { LoginRequest, RegisterRequest } from '../types';

interface AuthContextType {
  user: {
    id: string;
    username: string;
    email?: string;
    color?: string;
  } | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<any>;
  register: (data: RegisterRequest) => Promise<any>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | null>(null);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<AuthContextType['user']>(null);
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    setIsLoading(true);
    const token = localStorage.getItem('auth_token');
    const userId = localStorage.getItem('user_id');
    const username = localStorage.getItem('user_username');
    const color = localStorage.getItem('user_color');
    
    if (token && userId && username) {
      setUser({
        id: userId,
        username: username,
        color: color || undefined
      });
      setIsAuthenticated(true);
    }
    setIsLoading(false);
  }, []);

  const login = async (data: LoginRequest) => {
    try {
      const response = await apiService.login(data);
      
              console.log('Login response:', response);
        
        if (response && response.data) {
          let accessToken, refreshToken, userData;
          
          if (response.data.success && response.data.data) {
            accessToken = response.data.data.accessToken;
            refreshToken = response.data.data.refreshToken;
            userData = response.data.data.user;
          } else {
            accessToken = response.data.accessToken;
            refreshToken = response.data.refreshToken;
            userData = response.data.user;
          }
        
        if (!accessToken || !refreshToken) {
          throw new Error('Invalid response format: missing tokens');
        }

        localStorage.setItem('auth_token', accessToken);
        localStorage.setItem('refresh_token', refreshToken);

        if (userData && userData.id) {
          localStorage.setItem('user_id', userData.id);
          localStorage.setItem('user_username', userData.username || data.username);

          if (!localStorage.getItem('user_color')) {
            const colors = [
              '#3B82F6', '#10B981', '#F59E0B', '#EF4444', 
              '#8B5CF6', '#06B6D4', '#F97316', '#84CC16',
              '#EC4899', '#6366F1'
            ];
            const randomColor = colors[Math.floor(Math.random() * colors.length)];
            localStorage.setItem('user_color', randomColor);
          }
          
          setUser({
            id: userData.id,
            username: userData.username || data.username,
            email: userData.email,
            color: localStorage.getItem('user_color') || undefined
          });
          setIsAuthenticated(true);
        } else {
          throw new Error('User data not available in response');
        }
        
        return response;
      }
      
      throw new Error('Invalid response format');
    } catch (error) {
      console.error('Login error:', error);
      throw error;
    }
  };

  const register = async (data: RegisterRequest) => {
    try {
      const response = await apiService.register(data);
      
      if (response && response.data) {
        let accessToken, refreshToken, userData;
        
        if (response.data.success && response.data.data) {
          accessToken = response.data.data.accessToken;
          refreshToken = response.data.data.refreshToken;
          userData = response.data.data.user;
        } else {
          accessToken = response.data.accessToken;
          refreshToken = response.data.refreshToken;
          userData = response.data.user;
        }
        
        if (!accessToken || !refreshToken) {
          throw new Error('Invalid response format: missing tokens');
        }

        localStorage.setItem('auth_token', accessToken);
        localStorage.setItem('refresh_token', refreshToken);

        if (userData && userData.id) {
          localStorage.setItem('user_id', userData.id);
          localStorage.setItem('user_username', userData.username || data.username);

          const colors = [
            '#3B82F6', '#10B981', '#F59E0B', '#EF4444', 
            '#8B5CF6', '#06B6D4', '#F97316', '#84CC16',
            '#EC4899', '#6366F1'
          ];
          const randomColor = colors[Math.floor(Math.random() * colors.length)];
          localStorage.setItem('user_color', randomColor);
          
          setUser({
            id: userData.id,
            username: userData.username || data.username,
            email: userData.email || data.email,
            color: randomColor
          });
          setIsAuthenticated(true);
        } else {
          throw new Error('User data not available in response');
        }
        
        return response;
      }
      
      throw new Error('Invalid response format');
    } catch (error) {
      console.error('Registration error:', error);
      throw error;
    }
  };

  const logout = async () => {
    try {
      await apiService.logout();
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('refresh_token');
      localStorage.removeItem('user_id');
      localStorage.removeItem('user_username');
      
      setUser(null);
      setIsAuthenticated(false);
    }
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, isLoading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};