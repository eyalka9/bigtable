import React from 'react';

const DataTable = ({
  data,
  totalElements,
  totalPages,
  currentPage,
  pageSize,
  onPageChange,
  implementation,
  queryTime,
  queryParams,
  onQueryChange,
}) => {
  if (!data || data.length === 0) {
    return <div className="loading">No data available</div>;
  }

  // Get column names from first row
  const columns = Object.keys(data[0]).filter(key => !key.startsWith('_'));

  const handlePrevPage = () => {
    if (currentPage > 0) {
      onPageChange(currentPage - 1);
    }
  };

  const handleNextPage = () => {
    if (currentPage < totalPages - 1) {
      onPageChange(currentPage + 1);
    }
  };

  const handleSort = (columnName) => {
    const existingSort = queryParams.sorts?.find(sort => sort.column === columnName);
    let newSorts = [];
    
    if (existingSort) {
      // Toggle direction or remove if already DESC
      if (existingSort.direction === 'ASC') {
        newSorts = queryParams.sorts.map(sort => 
          sort.column === columnName 
            ? { ...sort, direction: 'DESC' }
            : sort
        );
      } else {
        // Remove sort
        newSorts = queryParams.sorts.filter(sort => sort.column !== columnName);
      }
    } else {
      // Add new sort
      newSorts = [...(queryParams.sorts || []), {
        column: columnName,
        direction: 'ASC',
        priority: (queryParams.sorts?.length || 0) + 1
      }];
    }
    
    onQueryChange({ sorts: newSorts });
  };

  const getSortDirection = (columnName) => {
    const sort = queryParams.sorts?.find(sort => sort.column === columnName);
    return sort?.direction;
  };

  const getSortIcon = (columnName) => {
    const direction = getSortDirection(columnName);
    if (direction === 'ASC') return ' ↑';
    if (direction === 'DESC') return ' ↓';
    return '';
  };

  return (
    <div className="table-container">
      <div style={{ padding: '10px', backgroundColor: '#f0f0f0', fontSize: '12px' }}>
        <strong>Implementation:</strong> {implementation} | 
        <strong> Query Time:</strong> {queryTime}ms | 
        <strong> Total Rows:</strong> {totalElements.toLocaleString()}
      </div>
      
      <table className="table">
        <thead>
          <tr>
            {columns.map(column => (
              <th 
                key={column} 
                onClick={() => handleSort(column)}
                style={{ 
                  cursor: 'pointer', 
                  userSelect: 'none',
                  backgroundColor: getSortDirection(column) ? '#e6f3ff' : 'inherit'
                }}
                title={`Click to sort by ${column}`}
              >
                {column}{getSortIcon(column)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row, index) => (
            <tr key={row._id || index}>
              {columns.map(column => (
                <td key={column}>{row[column]}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      
      <div className="pagination">
        <div>
          Showing {currentPage * pageSize + 1} to {Math.min((currentPage + 1) * pageSize, totalElements)} of {totalElements.toLocaleString()} entries
        </div>
        <div>
          <button onClick={handlePrevPage} disabled={currentPage === 0}>
            Previous
          </button>
          <span style={{ margin: '0 10px' }}>
            Page {currentPage + 1} of {totalPages}
          </span>
          <button onClick={handleNextPage} disabled={currentPage >= totalPages - 1}>
            Next
          </button>
        </div>
      </div>
    </div>
  );
};

export default DataTable;