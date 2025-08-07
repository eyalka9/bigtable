import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import DataTable from './DataTable';
import TableControls from './TableControls';
import Filters from '../Filters/Filters';
import PerformanceMetrics from '../Performance/PerformanceMetrics';
import { tableAPI } from '../../services/api';
import { generateSampleData } from '../../utils/sampleData';

const DataTableContainer = () => {
  const [sessionId] = useState('default-session'); // Use default session that backend pre-loads
  const [queryParams, setQueryParams] = useState({
    filters: [],
    sorts: [],
    searchTerm: '',
    page: 0,
    pageSize: 100,
  });
  const [isDataLoaded, setIsDataLoaded] = useState(false);

  // Query for table data
  const { data: tableData, isLoading, error, refetch } = useQuery({
    queryKey: ['tableData', sessionId, queryParams],
    queryFn: () => tableAPI.queryData({ sessionId, ...queryParams }),
    enabled: isDataLoaded,
  });

  // Query for session status to check if data is pre-loaded
  const { data: sessionStatus } = useQuery({
    queryKey: ['sessionStatus', sessionId],
    queryFn: () => tableAPI.getSessionStatus(sessionId),
    refetchInterval: 5000, // Check periodically during startup
    refetchIntervalInBackground: false,
  });

  // Query for schema
  const { data: schema } = useQuery({
    queryKey: ['schema', sessionId],
    queryFn: () => tableAPI.getSchema(sessionId),
    enabled: isDataLoaded,
  });

  // Query for performance metrics
  const { data: metrics } = useQuery({
    queryKey: ['metrics', sessionId],
    queryFn: () => tableAPI.getMetrics(sessionId),
    enabled: isDataLoaded,
    refetchInterval: 5000,
  });

  // Check if data is already loaded when component mounts or session status changes
  useEffect(() => {
    if (sessionStatus?.hasData && !isDataLoaded) {
      setIsDataLoaded(true);
    }
  }, [sessionStatus, isDataLoaded]);

  const handleLoadSampleData = async () => {
    try {
      const { data, schema } = generateSampleData(100000, 40);
      await tableAPI.uploadData(sessionId, data, schema);
      setIsDataLoaded(true);
      refetch();
    } catch (error) {
      console.error('Error loading sample data:', error);
    }
  };

  const handleQueryChange = (newParams) => {
    setQueryParams(prev => ({ ...prev, ...newParams, page: 0 }));
  };

  const handlePageChange = (newPage) => {
    setQueryParams(prev => ({ ...prev, page: newPage }));
  };

  if (error) {
    return (
      <div className="error">
        <h3>Error loading data</h3>
        <p>{error.message}</p>
        <button onClick={() => refetch()}>Retry</button>
      </div>
    );
  }

  return (
    <div>
      <TableControls
        onLoadSampleData={handleLoadSampleData}
        onQueryChange={handleQueryChange}
        queryParams={queryParams}
        isDataLoaded={isDataLoaded}
        sessionStatus={sessionStatus}
      />

      {isDataLoaded && schema && (
        <Filters
          queryParams={queryParams}
          onQueryChange={handleQueryChange}
          schema={schema}
        />
      )}
      
      {isLoading && <div className="loading">Loading data...</div>}
      
      {tableData && (
        <DataTable
          data={tableData.data}
          totalElements={tableData.totalElements}
          totalPages={tableData.totalPages}
          currentPage={tableData.currentPage}
          pageSize={tableData.pageSize}
          onPageChange={handlePageChange}
          implementation={tableData.implementation}
          queryTime={tableData.queryTimeMs}
          queryParams={queryParams}
          onQueryChange={handleQueryChange}
        />
      )}
      
      {metrics && <PerformanceMetrics metrics={metrics} />}
    </div>
  );
};

export default DataTableContainer;