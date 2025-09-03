import React, { useState } from 'react';

const TableControls = ({ onLoadSampleData, onQueryChange, queryParams, isDataLoaded, sessionStatus, onExportTable, onDeleteByQuery }) => {
  const [searchTerm, setSearchTerm] = useState(queryParams.searchTerm);

  const handleSearchChange = (e) => {
    const value = e.target.value;
    setSearchTerm(value);
    onQueryChange({ searchTerm: value });
  };

  const handlePageSizeChange = (e) => {
    const value = parseInt(e.target.value);
    onQueryChange({ pageSize: value });
  };

  const getButtonText = () => {
    if (isDataLoaded) {
      return `Data Loaded (${sessionStatus?.implementation || 'Unknown'})`;
    }
    if (sessionStatus?.hasData) {
      return 'Loading...';
    }
    return 'Load Sample Data (100k rows)';
  };

  return (
    <div className="controls">
      <button onClick={onLoadSampleData} disabled={isDataLoaded}>
        {getButtonText()}
      </button>
      
      {isDataLoaded && (
        <>
          <div>
            <label>Search: </label>
            <input
              type="text"
              value={searchTerm}
              onChange={handleSearchChange}
              placeholder="Search across all columns..."
              style={{ width: '300px', padding: '5px' }}
            />
          </div>
          
          <div>
            <label>Page Size: </label>
            <select value={queryParams.pageSize} onChange={handlePageSizeChange}>
              <option value={50}>50</option>
              <option value={100}>100</option>
              <option value={200}>200</option>
              <option value={500}>500</option>
            </select>
          </div>
          
          <button onClick={onExportTable} style={{ backgroundColor: '#28a745' }}>
            Export Table
          </button>
          
          <button 
            onClick={onDeleteByQuery} 
            style={{ 
              backgroundColor: '#dc3545', 
              color: 'white',
              marginLeft: '10px'
            }}
          >
            Delete Current Query
          </button>
        </>
      )}
    </div>
  );
};

export default TableControls;