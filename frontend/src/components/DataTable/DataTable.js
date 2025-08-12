import React, { useState } from 'react';

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
  onUpdateField,
  sessionId,
}) => {
  const [editingCell, setEditingCell] = useState(null);
  const [editValue, setEditValue] = useState('');

  if (!data || data.length === 0) {
    return <div className="loading">No data available</div>;
  }

  // Get column names from first row
  const columns = Object.keys(data[0]).filter(key => !key.startsWith('_'))

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

  const handleCellDoubleClick = (rowIndex, columnName, currentValue) => {
    const row = data[rowIndex];
    const recordId = row.id || row._id;
    if (!recordId) {
      console.warn('No ID found for row:', row);
      return;
    }
    
    setEditingCell(`${recordId}-${columnName}`);
    setEditValue(currentValue || '');
  };

  const handleCellEdit = (e) => {
    if (e.key === 'Enter') {
      saveCellEdit();
    } else if (e.key === 'Escape') {
      cancelCellEdit();
    }
  };

  const saveCellEdit = async () => {
    if (!editingCell) return;
    
    const [recordId, fieldName] = editingCell.split('-');
    
    // Basic validation
    if (editValue.trim() === '') {
      alert('Value cannot be empty');
      return;
    }
    
    try {
      if (onUpdateField) {
        await onUpdateField(sessionId, recordId, fieldName, editValue.trim());
      }
      setEditingCell(null);
      setEditValue('');
    } catch (error) {
      console.error('Failed to update field:', error);
      let errorMessage = 'Failed to update field';
      
      if (error.response?.data?.message) {
        errorMessage = error.response.data.message;
      } else if (error.message) {
        errorMessage = error.message;
      }
      
      alert(`Update failed: ${errorMessage}`);
    }
  };

  const cancelCellEdit = () => {
    setEditingCell(null);
    setEditValue('');
  };

  const renderCell = (row, column, rowIndex) => {
    const recordId = row.id || row._id;
    const cellKey = `${recordId}-${column}`;
    const isEditing = editingCell === cellKey;
    const cellValue = row[column];

    if (isEditing) {
      return (
        <input
          type="text"
          value={editValue}
          onChange={(e) => setEditValue(e.target.value)}
          onKeyDown={handleCellEdit}
          onBlur={saveCellEdit}
          autoFocus
          style={{
            width: '100%',
            border: '1px solid #007bff',
            padding: '2px 4px',
            fontSize: 'inherit'
          }}
        />
      );
    }

    return (
      <span
        onDoubleClick={() => handleCellDoubleClick(rowIndex, column, cellValue)}
        style={{ cursor: 'pointer', display: 'block', padding: '4px' }}
        title="Double-click to edit"
      >
        {cellValue}
      </span>
    );
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
                <td key={column}>
                  {renderCell(row, column, index)}
                </td>
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