import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import DataTableContainer from './components/DataTable/DataTableContainer';
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
  return (
    <QueryClientProvider client={queryClient}>
      <div className="app">
        <header className="header">
          <h1>BigTable POC - Performance Comparison</h1>
          <p>Comparing H2 Database vs Apache Arrow for large dataset operations</p>
        </header>
        <DataTableContainer />
      </div>
    </QueryClientProvider>
  );
}

export default App;