import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useParams, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './hooks/useAuth';
import { LoginForm } from './components/LoginForm';
import { CollaborativeEditor } from './components/CollaborativeEditor';
import DocumentsPage from './components/DocumentsPage';
import { webSocketService } from './services/WebSocketService';

const DocumentEditor: React.FC = () => {
  const { documentId } = useParams<{ documentId: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [hasCheckedAuth, setHasCheckedAuth] = useState(false);

  React.useEffect(() => {
    if (!isAuthLoading) {
      setHasCheckedAuth(true);

      if (!isAuthenticated) {
        navigate('/');
        return;
      }

      setIsLoading(false);
    }
  }, [isAuthenticated, isAuthLoading, navigate]);

  React.useEffect(() => {
    return () => {
      if (documentId) {
        console.log(`Leaving document page ${documentId}, disconnecting WebSocket`);
        webSocketService.disconnect();
      }
    };
  }, [documentId]);

  if (!documentId) {
    return <Navigate to="/documents" />;
  }

  if (isAuthLoading || !hasCheckedAuth) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/" />;
  }

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="bg-red-50 border border-red-200 text-red-700 px-6 py-4 rounded-lg mb-6 max-w-md w-full">
          <div className="font-medium">Ошибка</div>
          <div>{error}</div>
          <button
            onClick={() => navigate('/documents')}
            className="mt-4 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
          >
            Вернуться к списку документов
          </button>
        </div>
      </div>
    );
  }

  return (
    <CollaborativeEditor
      documentId={documentId}
      onBack={() => navigate('/documents')}
      onError={(errorMessage) => {
        setError(errorMessage);
      }}
    />
  );
};

const AppContent: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <LoginForm />;
  }

  return <Navigate to="/documents" />;
};

function App() {
  useEffect(() => {
    return () => {
      console.log('App unmounting, cleaning up WebSocket connections');
      webSocketService.disconnect();
    };
  }, []);
  
  return (
    <AuthProvider>
      <Router>
        <Routes>
          <Route path="/" element={<AppContent />} />
          <Route path="/documents" element={<DocumentsPage />} />
          <Route path="/documents/:documentId" element={<DocumentEditor />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Router>
    </AuthProvider>
  );
}

export default App;