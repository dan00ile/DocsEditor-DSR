import React, { useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import { LoginRequest, RegisterRequest } from '../types';
import { FileText, LogIn, UserPlus, Eye, EyeOff } from 'lucide-react';

export const LoginForm: React.FC = () => {
  const { login, register } = useAuth();
  const [isLogin, setIsLogin] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: ''
  });

  const validateForm = () => {
    const errors: Record<string, string> = {};
    let isValid = true;

    setFieldErrors({});
    
    if (!formData.username.trim()) {
      errors.username = 'Имя пользователя обязательно';
      isValid = false;
    }
    
    if (!isLogin && !formData.email.trim()) {
      errors.email = 'Email обязателен';
      isValid = false;
    }
    
    if (!isLogin && formData.email.trim() && !formData.email.includes('@')) {
      errors.email = 'Введите корректный email';
      isValid = false;
    }
    
    if (!formData.password) {
      errors.password = 'Пароль обязателен';
      isValid = false;
    }
    
    if (!isLogin && formData.password !== formData.confirmPassword) {
      errors.confirmPassword = 'Пароли не совпадают';
      isValid = false;
    }
    
    setFieldErrors(errors);
    return isValid;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!validateForm()) {
      return;
    }
    
    setIsLoading(true);

    try {
      if (isLogin) {
        const credentials: LoginRequest = {
          username: formData.username,
          password: formData.password
        };
        await login(credentials);
      } else {
        const userData: RegisterRequest = {
          username: formData.username,
          email: formData.email,
          password: formData.password
        };
        await register(userData);
      }
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else if (err.response?.data?.errors) {
        const serverFieldErrors: Record<string, string> = {};
        err.response.data.errors.forEach((fieldError: any) => {
          serverFieldErrors[fieldError.field] = fieldError.message;
        });
        setFieldErrors(serverFieldErrors);
      } else if (err.response?.status === 401) {
        setError('Неверное имя пользователя или пароль');
      } else if (err.response?.status === 409) {
        setError('Пользователь с таким именем или email уже существует');
      } else {
        setError(err.message || 'Произошла ошибка при авторизации');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;

    if (fieldErrors[name]) {
      setFieldErrors(prev => ({
        ...prev,
        [name]: ''
      }));
    }
    
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-gray-200 p-8">
        
        {/* Header */}
        <div className="text-center mb-8">
          <div className="flex items-center justify-center mb-4">
            <div className="p-3 rounded-full bg-blue-500">
              <FileText className="w-8 h-8 text-white" />
            </div>
          </div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">
            CollabEdit
          </h1>
          <p className="text-sm text-gray-600">
            Редактор текста для совместной работы в реальном времени
          </p>
        </div>
        <div className="flex mb-6 bg-gray-100 rounded-lg p-1">
          <button
            type="button"
            onClick={() => {
              setIsLogin(true);
              setError('');
              setFieldErrors({});
            }}
            className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors ${
              isLogin 
                ? 'bg-white text-blue-600 shadow-sm' 
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            Вход
          </button>
          <button
            type="button"
            onClick={() => {
              setIsLogin(false);
              setError('');
              setFieldErrors({});
            }}
            className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors ${
              !isLogin 
                ? 'bg-white text-blue-600 shadow-sm' 
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            Регистрация
          </button>
        </div>
        <form onSubmit={handleSubmit} className="space-y-6">
          {isLogin ? (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Имя пользователя
              </label>
              <input
                type="text"
                name="username"
                value={formData.username}
                onChange={handleInputChange}
                className={`w-full px-4 py-3 rounded-lg border ${
                  fieldErrors.username ? 'border-red-500' : 'border-gray-300'
                } text-gray-900 placeholder-gray-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-opacity-20 transition-colors`}
                placeholder="Введите имя пользователя"
                required
              />
              {fieldErrors.username && (
                <p className="mt-1 text-sm text-red-600">{fieldErrors.username}</p>
              )}
            </div>
          ) :
            <>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Имя пользователя
                </label>
                <input
                  type="text"
                  name="username"
                  value={formData.username}
                  onChange={handleInputChange}
                  className={`w-full px-4 py-3 rounded-lg border ${
                    fieldErrors.username ? 'border-red-500' : 'border-gray-300'
                  } text-gray-900 placeholder-gray-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-opacity-20 transition-colors`}
                  placeholder="Введите имя пользователя"
                  pattern="^[a-zA-Z0-9_]+$"
                  title="Имя пользователя может содержать только буквы, цифры и знак подчеркивания"
                  minLength={3}
                  maxLength={50}
                  required
                />
                {fieldErrors.username && (
                  <p className="mt-1 text-sm text-red-600">{fieldErrors.username}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Email
                </label>
                <input
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleInputChange}
                  className={`w-full px-4 py-3 rounded-lg border ${
                    fieldErrors.email ? 'border-red-500' : 'border-gray-300'
                  } text-gray-900 placeholder-gray-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-opacity-20 transition-colors`}
                  placeholder="Введите email"
                  required
                />
                {fieldErrors.email && (
                  <p className="mt-1 text-sm text-red-600">{fieldErrors.email}</p>
                )}
              </div>
            </>
          }

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Пароль
            </label>
            <div className="relative">
              <input
                type={showPassword ? "text" : "password"}
                name="password"
                value={formData.password}
                onChange={handleInputChange}
                className={`w-full px-4 py-3 rounded-lg border ${
                  fieldErrors.password ? 'border-red-500' : 'border-gray-300'
                } text-gray-900 placeholder-gray-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-opacity-20 transition-colors`}
                placeholder="Введите пароль"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-500 hover:text-gray-700"
              >
                {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
              </button>
            </div>
            {fieldErrors.password && (
              <p className="mt-1 text-sm text-red-600">{fieldErrors.password}</p>
            )}
          </div>

          {!isLogin && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Подтверждение пароля
              </label>
              <div className="relative">
                <input
                  type={showConfirmPassword ? "text" : "password"}
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleInputChange}
                  className={`w-full px-4 py-3 rounded-lg border ${
                    fieldErrors.confirmPassword ? 'border-red-500' : 'border-gray-300'
                  } text-gray-900 placeholder-gray-500 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-opacity-20 transition-colors`}
                  placeholder="Подтвердите пароль"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-500 hover:text-gray-700"
                >
                  {showConfirmPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                </button>
              </div>
              {fieldErrors.confirmPassword && (
                <p className="mt-1 text-sm text-red-600">{fieldErrors.confirmPassword}</p>
              )}
            </div>
          )}

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={isLoading}
            className={`w-full py-3 px-4 rounded-lg font-medium transition-all duration-200 ${
              isLoading
                ? 'bg-gray-400 cursor-not-allowed'
                : 'bg-blue-600 hover:bg-blue-700 transform hover:scale-105'
            } text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50`}
          >
            {isLoading ? (
              <div className="flex items-center justify-center">
                <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white mr-2"></div>
                {isLogin ? 'Выполняется вход...' : 'Создание аккаунта...'}
              </div>
            ) : (
              <div className="flex items-center justify-center">
                {isLogin ? <LogIn className="w-5 h-5 mr-2" /> : <UserPlus className="w-5 h-5 mr-2" />}
                {isLogin ? 'Войти' : 'Создать аккаунт'}
              </div>
            )}
          </button>
        </form>

        {/* Features */}
        <div className="mt-8 pt-6 border-t border-gray-200">
          <div className="grid grid-cols-2 gap-4 text-center">
            <div>
              <div className="text-sm font-medium text-gray-700">
                Синхронизация в реальном времени
              </div>
              <div className="text-xs text-gray-500">
                Совместная работа онлайн
              </div>
            </div>
            <div>
              <div className="text-sm font-medium text-gray-700">
                Контроль версий
              </div>
              <div className="text-xs text-gray-500">
                Отслеживание истории документа
              </div>
            </div>
          </div>
        </div>
        
      </div>
    </div>
  );
};