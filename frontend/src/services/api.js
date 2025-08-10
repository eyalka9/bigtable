import axios from 'axios';

const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

export const tableAPI = {
  uploadData: async (sessionId, data, schema) => {
    const response = await api.post(`/sessions/${sessionId}/data`, {
      data,
      schema,
    });
    return response.data;
  },

  queryData: async (queryRequest) => {
    const response = await api.post(`/sessions/${queryRequest.sessionId}/query`, queryRequest);
    return response.data;
  },

  getSchema: async (sessionId) => {
    const response = await api.get(`/sessions/${sessionId}/schema`);
    return response.data;
  },

  clearData: async (sessionId) => {
    const response = await api.delete(`/sessions/${sessionId}/data`);
    return response.data;
  },

  getMetrics: async (sessionId) => {
    const response = await api.get(`/sessions/${sessionId}/metrics`);
    return response.data;
  },

  getSessionStatus: async (sessionId) => {
    const response = await api.get(`/sessions/${sessionId}/status`);
    return response.data;
  },

  health: async () => {
    const response = await api.get('/sessions/health');
    return response.data;
  },

  exportTable: async (sessionId) => {
    const response = await api.post(`/sessions/${sessionId}/export`);
    return response.data;
  },
};

export default api;