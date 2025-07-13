import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { DocumentList } from './DocumentList';

const DocumentsPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated, isLoading } = useAuth();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    if (!isLoading) {
      if (!isAuthenticated) {
        navigate('/');
        return;
      }
      setIsReady(true);
    }
  }, [isAuthenticated, isLoading, navigate]);

  if (isLoading || !isReady) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <DocumentList
      onSelectDocument={(documentId) => navigate(`/documents/${documentId}`)}
    />
  );
};

export default DocumentsPage; 