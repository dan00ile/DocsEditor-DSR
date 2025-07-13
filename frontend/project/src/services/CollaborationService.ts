import { User, CursorPosition, CollaborationState } from '../types';

export class CollaborationService {
  private static activeUsers: Map<string, User> = new Map();
  private static cursors: Map<string, CursorPosition> = new Map();
  private static listeners: Set<(state: CollaborationState) => void> = new Set();

  static addUser(user: User): void {
    user.isActive = true;
    user.lastActive = new Date();
    this.activeUsers.set(user.id, user);
    this.notifyListeners();

    this.simulateCollaborativeUsers();
  }

  static removeUser(userId: string): void {
    this.activeUsers.delete(userId);
    this.cursors.delete(userId);
    this.notifyListeners();
  }

  static updateCursor(userId: string, position: number): void {
    const user = this.activeUsers.get(userId);
    if (user) {
      this.cursors.set(userId, {
        userId,
        username: user.username,
        position,
        color: user.color
      });
      this.notifyListeners();
    }
  }

  static subscribe(callback: (state: CollaborationState) => void): () => void {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }

  private static notifyListeners(): void {
    const state: CollaborationState = {
      activeUsers: Array.from(this.activeUsers.values()),
      cursors: Array.from(this.cursors.values()),
      lastUpdate: new Date()
    };
    this.listeners.forEach(listener => listener(state));
  }

  private static simulateCollaborativeUsers(): void {
    const simulatedUsers = [
      { username: 'Alex Chen', email: 'alex@example.com', color: '#10B981' },
      { username: 'Sarah Johnson', email: 'sarah@example.com', color: '#F59E0B' },
      { username: 'Mike Rodriguez', email: 'mike@example.com', color: '#8B5CF6' }
    ];

    simulatedUsers.forEach((userData, index) => {
      setTimeout(() => {
        if (Math.random() > 0.3) {
          const simulatedUser: User = {
            id: `sim-${index}`,
            username: userData.username,
            email: userData.email,
            color: userData.color,
            isActive: true,
            lastActive: new Date()
          };
          this.activeUsers.set(simulatedUser.id, simulatedUser);
          this.notifyListeners();

          this.simulateCursorMovement(simulatedUser);
        }
      }, (index + 1) * 2000);
    });
  }

  private static simulateCursorMovement(user: User): void {
    const updateCursor = () => {
      if (this.activeUsers.has(user.id)) {
        const position = Math.floor(Math.random() * 1000);
        this.updateCursor(user.id, position);
        setTimeout(updateCursor, 3000 + Math.random() * 5000);
      }
    };
    setTimeout(updateCursor, 1000);
  }
}