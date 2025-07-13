import { User } from '../types';
import { v4 as uuidv4 } from 'uuid';

const USER_COLORS = [
  '#3B82F6', '#10B981', '#F59E0B', '#EF4444', 
  '#8B5CF6', '#06B6D4', '#F97316', '#84CC16',
  '#EC4899', '#6366F1'
];

export class AuthService {
  private static readonly STORAGE_KEY = 'collabedit_user';

  static login(user: User): void {
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(user));
  }

  static logout(): void {
    localStorage.removeItem(this.STORAGE_KEY);
  }

  static getCurrentUser(): User | null {
    const userData = localStorage.getItem(this.STORAGE_KEY);
    if (userData) {
      const user = JSON.parse(userData);
      user.lastActive = new Date(user.lastActive);
      return user;
    }
    return null;
  }

  static createUser(username: string, email: string): User {
    return {
      id: uuidv4(),
      username,
      email,
      color: USER_COLORS[Math.floor(Math.random() * USER_COLORS.length)],
      isActive: true,
      lastActive: new Date()
    };
  }

  static validateCredentials(username: string, email: string): boolean {
    return username.trim().length >= 3 && email.includes('@');
  }
}