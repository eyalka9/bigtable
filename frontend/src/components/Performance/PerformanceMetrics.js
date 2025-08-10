import React from 'react';

const PerformanceMetrics = ({ metrics }) => {
  if (!metrics || Object.keys(metrics).length === 0) {
    return null;
  }

  const formatMetric = (key, value) => {
    if (typeof value === 'number') {
      if (key.includes('Time') || key.includes('Ms')) {
        return `${value}ms`;
      }
      if (key.includes('Memory') && key.includes('MB')) {
        if (key === 'arrowLimitMB' && value === -1) {
          return 'Unlimited';
        }
        return `${value}MB`;
      }
      if (key.includes('Percent')) {
        return `${value}%`;
      }
      if (key === 'rowCount' || key === 'totalQueries') {
        return value.toLocaleString();
      }
    }
    return value;
  };

  const getMetricLabel = (key) => {
    const labels = {
      // Query performance
      'loadTimeMs': 'Load Time',
      'avgQueryTimeMs': 'Avg Query Time',
      'stdDevQueryTimeMs': 'Query Time Std Dev',
      'minQueryTimeMs': 'Min Query Time',
      'maxQueryTimeMs': 'Max Query Time',
      'totalQueries': 'Total Queries',
      'rowCount': 'Row Count',
      'implementation': 'Implementation',
      // JVM Memory metrics
      'totalMemoryMB': 'JVM Total Memory',
      'usedMemoryMB': 'JVM Used Memory',
      'freeMemoryMB': 'JVM Free Memory',
      'maxMemoryMB': 'JVM Max Memory',
      'memoryUsagePercent': 'JVM Memory Usage',
      // Arrow off-heap memory metrics
      'arrowAllocatedMB': 'Arrow Allocated',
      'arrowPeakMB': 'Arrow Peak',
      'arrowLimitMB': 'Arrow Limit',
      'totalMemoryUsedMB': 'Total Memory Used'
    };
    return labels[key] || key;
  };

  const getMetricCategory = (key) => {
    if (key.includes('Memory') || key.includes('memoryUsage') || key.includes('arrow')) return 'memory';
    if (key.includes('Time') || key.includes('Ms') || key === 'totalQueries') return 'performance';
    return 'general';
  };

  const categorizedMetrics = Object.entries(metrics).reduce((acc, [key, value]) => {
    const category = getMetricCategory(key);
    if (!acc[category]) acc[category] = [];
    acc[category].push([key, value]);
    return acc;
  }, {});

  const categoryTitles = {
    general: 'General',
    performance: 'Query Performance',
    memory: 'Memory Statistics (JVM + Arrow Off-Heap)'
  };

  return (
    <div className="metrics">
      <h3>Performance Metrics</h3>
      {Object.entries(categorizedMetrics).map(([category, items]) => (
        <div key={category} style={{ marginBottom: '20px' }}>
          <h4 style={{ marginBottom: '10px', color: '#666' }}>{categoryTitles[category]}</h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '10px' }}>
            {items.map(([key, value]) => (
              <div key={key} style={{ 
                padding: '8px', 
                border: '1px solid #ddd', 
                borderRadius: '4px',
                backgroundColor: key.includes('arrow') ? '#ffe6e6' : category === 'memory' ? '#f8f9ff' : '#fff'
              }}>
                <strong>{getMetricLabel(key)}:</strong> {formatMetric(key, value)}
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
};

export default PerformanceMetrics;