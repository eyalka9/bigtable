import React, { useState } from 'react';

const Filters = ({ queryParams, onQueryChange, schema }) => {
  const [showFilters, setShowFilters] = useState(false);
  const [newFilter, setNewFilter] = useState({
    column: '',
    operation: 'EQUALS',
    value: '',
    logicalOperator: 'AND'
  });

  const filterOperations = [
    { value: 'EQUALS', label: 'Equals' },
    { value: 'NOT_EQUALS', label: 'Not Equals' },
    { value: 'CONTAINS', label: 'Contains' },
    { value: 'STARTS_WITH', label: 'Starts With' },
    { value: 'ENDS_WITH', label: 'Ends With' },
    { value: 'GREATER_THAN', label: 'Greater Than' },
    { value: 'GREATER_THAN_OR_EQUAL', label: 'Greater Than or Equal' },
    { value: 'LESS_THAN', label: 'Less Than' },
    { value: 'LESS_THAN_OR_EQUAL', label: 'Less Than or Equal' },
    { value: 'IS_NULL', label: 'Is Null' },
    { value: 'IS_NOT_NULL', label: 'Is Not Null' }
  ];

  // Get unique column names from schema or first data row
  const getAvailableColumns = () => {
    if (schema && schema.length > 0) {
      return schema.filter(col => col.filterable).map(col => col.name);
    }
    return [];
  };

  const availableColumns = getAvailableColumns();

  const handleAddFilter = () => {
    if (!newFilter.column) return;

    const filter = {
      column: newFilter.column,
      operation: newFilter.operation,
      values: [newFilter.value],
      logicalOperator: newFilter.logicalOperator
    };

    const newFilters = [...(queryParams.filters || []), filter];
    onQueryChange({ filters: newFilters });

    // Reset form
    setNewFilter({
      column: '',
      operation: 'EQUALS',
      value: '',
      logicalOperator: 'AND'
    });
  };

  const handleRemoveFilter = (index) => {
    const newFilters = queryParams.filters.filter((_, i) => i !== index);
    onQueryChange({ filters: newFilters });
  };

  const handleClearAllFilters = () => {
    onQueryChange({ filters: [] });
  };

  if (availableColumns.length === 0) {
    return null;
  }

  return (
    <div style={{ marginBottom: '20px', border: '1px solid #ddd', borderRadius: '4px', padding: '10px' }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: '10px' }}>
        <button 
          onClick={() => setShowFilters(!showFilters)}
          style={{ marginRight: '10px' }}
        >
          {showFilters ? 'Hide Filters' : 'Show Filters'}
        </button>
        {queryParams.filters && queryParams.filters.length > 0 && (
          <span style={{ fontSize: '12px', color: '#666' }}>
            {queryParams.filters.length} filter(s) active
          </span>
        )}
      </div>

      {showFilters && (
        <div>
          {/* Active Filters */}
          {queryParams.filters && queryParams.filters.length > 0 && (
            <div style={{ marginBottom: '15px' }}>
              <h4 style={{ margin: '0 0 10px 0', fontSize: '14px' }}>Active Filters:</h4>
              {queryParams.filters.map((filter, index) => (
                <div key={index} style={{ display: 'flex', alignItems: 'center', marginBottom: '5px', padding: '5px', backgroundColor: '#f9f9f9', borderRadius: '3px' }}>
                  <span style={{ marginRight: '10px', fontSize: '12px' }}>
                    {filter.column} {filter.operation.toLowerCase().replace('_', ' ')} "{filter.values?.[0]}"
                  </span>
                  <button 
                    onClick={() => handleRemoveFilter(index)}
                    style={{ fontSize: '12px', padding: '2px 6px' }}
                  >
                    Remove
                  </button>
                </div>
              ))}
              <button 
                onClick={handleClearAllFilters}
                style={{ fontSize: '12px', marginTop: '5px' }}
              >
                Clear All Filters
              </button>
            </div>
          )}

          {/* Add New Filter */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr auto', gap: '10px', alignItems: 'end' }}>
            <div>
              <label style={{ display: 'block', fontSize: '12px', marginBottom: '2px' }}>Column:</label>
              <select 
                value={newFilter.column}
                onChange={(e) => setNewFilter({...newFilter, column: e.target.value})}
                style={{ width: '100%', padding: '4px' }}
              >
                <option value="">Select column...</option>
                {availableColumns.map(column => (
                  <option key={column} value={column}>{column}</option>
                ))}
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '12px', marginBottom: '2px' }}>Operation:</label>
              <select 
                value={newFilter.operation}
                onChange={(e) => setNewFilter({...newFilter, operation: e.target.value})}
                style={{ width: '100%', padding: '4px' }}
              >
                {filterOperations.map(op => (
                  <option key={op.value} value={op.value}>{op.label}</option>
                ))}
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '12px', marginBottom: '2px' }}>Value:</label>
              <input
                type="text"
                value={newFilter.value}
                onChange={(e) => setNewFilter({...newFilter, value: e.target.value})}
                placeholder="Enter value..."
                style={{ width: '100%', padding: '4px' }}
                disabled={newFilter.operation === 'IS_NULL' || newFilter.operation === 'IS_NOT_NULL'}
              />
            </div>

            <button 
              onClick={handleAddFilter}
              disabled={!newFilter.column || (!newFilter.value && newFilter.operation !== 'IS_NULL' && newFilter.operation !== 'IS_NOT_NULL')}
              style={{ padding: '5px 10px' }}
            >
              Add Filter
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default Filters;