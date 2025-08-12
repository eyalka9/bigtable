import React, { useState, useEffect } from 'react';
import './ConnectionConfig.css';

const ConnectionConfig = ({ onConnectionChange, isConnected }) => {
  const [host, setHost] = useState('localhost');
  const [port, setPort] = useState('8080');
  const [isExpanded, setIsExpanded] = useState(false);

  useEffect(() => {
    onConnectionChange(host, port);
  }, []);

  const handleApply = () => {
    onConnectionChange(host, port);
    setIsExpanded(false);
  };

  const handleReset = () => {
    setHost('localhost');
    setPort('8080');
  };

  return (
    <div className="connection-config">
      <div className="connection-status">
        <div className={`status-indicator ${isConnected ? 'connected' : 'disconnected'}`}>
          {isConnected ? 'Connected' : 'Disconnected'}
        </div>
        <div className="connection-details">
          {host}:{port}
        </div>
        <button 
          className="config-toggle"
          onClick={() => setIsExpanded(!isExpanded)}
        >
          {isExpanded ? 'Hide' : 'Configure'}
        </button>
      </div>
      
      {isExpanded && (
        <div className="connection-form">
          <div className="form-group">
            <label htmlFor="host">Host:</label>
            <input
              id="host"
              type="text"
              value={host}
              onChange={(e) => setHost(e.target.value)}
              placeholder="localhost"
            />
          </div>
          <div className="form-group">
            <label htmlFor="port">Port:</label>
            <input
              id="port"
              type="number"
              value={port}
              onChange={(e) => setPort(e.target.value)}
              placeholder="8080"
              min="1"
              max="65535"
            />
          </div>
          <div className="form-actions">
            <button onClick={handleReset} className="reset-btn">
              Reset
            </button>
            <button onClick={handleApply} className="apply-btn">
              Apply
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default ConnectionConfig;