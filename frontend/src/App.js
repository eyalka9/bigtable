import React, { useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import DataTableContainer from './components/DataTable/DataTableContainer';
import ConnectionConfig from './components/ConnectionConfig/ConnectionConfig';
import { updateApiBaseUrl, tableAPI } from './services/api';
import './App.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30000,
      refetchOnWindowFocus: false,
    },
  },
});

function App() {
  const [isConnected, setIsConnected] = useState(false);
  const [selectedSession, setSelectedSession] = useState('');

  const handleConnectionChange = async (host, port) => {
    try {
      updateApiBaseUrl(host, port);
      await tableAPI.health();
      setIsConnected(true);
    } catch (error) {
      console.error('Connection failed:', error);
      setIsConnected(false);
    }
  };

  const handleSessionChange = (sessionId) => {
    setSelectedSession(sessionId);
  };

  return (
    <QueryClientProvider client={queryClient}>
      <div className="app">
        <header className="header">
          <h1>BigTable POC - Performance Comparison</h1>
          <p>Comparing H2 Database vs Apache Arrow for large dataset operations</p>
        </header>
        <ConnectionConfig 
          onConnectionChange={handleConnectionChange}
          onSessionChange={handleSessionChange}
          isConnected={isConnected}
        />
        {isConnected && selectedSession && <DataTableContainer sessionId={selectedSession} />}
      </div>
    </QueryClientProvider>
  );
}

export default App;