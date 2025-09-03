import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import DataTable from './DataTable';
import TableControls from './TableControls';
import Filters from '../Filters/Filters';
import PerformanceMetrics from '../Performance/PerformanceMetrics';
import { tableAPI } from '../../services/api';
import { generateSampleData } from '../../utils/sampleData';

const DataTableContainer = ({ sessionId = 'default-session' }) => {
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

  const handleExportTable = async () => {
    try {
      const result = await tableAPI.exportTable(sessionId);
      alert(`Export successful!\nFile: ${result.fileName}\nPath: ${result.filePath}\nFormat: ${result.format}`);
    } catch (error) {
      console.error('Error exporting data:', error);
      alert(`Export failed: ${error.response?.data?.message || error.message}`);
    }
  };

  const handleUpdateField = async (sessionId, recordId, fieldName, newValue) => {
    try {
      await tableAPI.updateField(sessionId, recordId, fieldName, newValue);
      refetch(); // Refresh the data to show the updated value
    } catch (error) {
      console.error('Error updating field:', error);
      throw error;
    }
  };

  const handleDeleteByQuery = async () => {
    try {
      const confirmed = window.confirm(
        `Are you sure you want to delete all records matching the current query?\n\n` +
        `Filters: ${queryParams.filters.length > 0 ? JSON.stringify(queryParams.filters, null, 2) : 'None'}\n` +
        `Search: ${queryParams.searchTerm || 'None'}\n\n` +
        `This action cannot be undone.`
      );
      
      if (confirmed) {
        const result = await tableAPI.deleteByQuery({ sessionId, ...queryParams });
        alert(`Delete successful!\nDeleted ${result.deletedCount} records.`);
        refetch(); // Refresh the data to show updated results
      }
    } catch (error) {
      console.error('Error deleting records:', error);
      alert(`Delete failed: ${error.response?.data?.message || error.message}`);
    }
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
        onExportTable={handleExportTable}
        onDeleteByQuery={handleDeleteByQuery}
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
          onUpdateField={handleUpdateField}
          sessionId={sessionId}
        />
      )}
      
      {metrics && <PerformanceMetrics metrics={metrics} />}
    </div>
  );
};

export default DataTableContainer;