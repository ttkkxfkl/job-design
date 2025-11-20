import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
apiClient.interceptors.request.use(
  (config) => {
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
apiClient.interceptors.response.use(
  (response) => {
    // 如果是 ApiResponse 格式，返回 data 字段
    if (response.data && typeof response.data === 'object' && 'code' in response.data) {
      if (response.data.code === 200) {
        return response.data.data;
      } else {
        return Promise.reject(new Error(response.data.message || '请求失败'));
      }
    }
    return response.data;
  },
  (error) => {
    console.error('API Error:', error);
    return Promise.reject(error);
  }
);

export interface Task {
  id: number;
  taskName: string;
  taskType: string;
  cronExpression: string;
  status: 'PENDING' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED';
  priority: number;
  createdAt: string;
  updatedAt: string;
  parameters?: Record<string, any>;
}

export interface CreateTaskRequest {
  taskName: string;
  taskType: string;
  cronExpression?: string;
  executeTime?: string;
  priority?: number;
  taskData?: Record<string, any>;
  maxRetryCount?: number;
  executionTimeout?: number;
}

export interface TaskStatistics {
  pendingCount: number;
  executingCount: number;
  successCount: number;
  failedCount: number;
  cancelledCount: number;
  pausedCount: number;
  timeoutCount: number;
  totalCount: number;
  successRate: number;
  avgExecutionDuration?: number;
  maxExecutionDuration?: number;
  minExecutionDuration?: number;
}

export const taskApi = {
  // 获取任务列表
  getTasks: (params?: { status?: string }) =>
    apiClient.get<any, Task[]>('/tasks', { params }),

  // 获取单个任务
  getTask: (id: number) =>
    apiClient.get<any, Task>(`/tasks/${id}`),

  // 创建任务
  createTask: (data: CreateTaskRequest) =>
    apiClient.post<any, Task>('/tasks', data),

  // 删除任务
  deleteTask: (id: number) =>
    apiClient.delete(`/tasks/${id}`),

  // 暂停任务
  pauseTask: (id: number) =>
    apiClient.put(`/tasks/${id}/pause`),

  // 恢复任务
  resumeTask: (id: number) =>
    apiClient.put(`/tasks/${id}/resume`),

  // 重试任务
  retryTask: (id: number) =>
    apiClient.post(`/tasks/${id}/retry`),

  // 获取任务类型
  getTaskTypes: () =>
    apiClient.get<any, { type: string; name: string; description: string }[]>('/tasks/types'),

  // 获取统计数据
  getStatistics: () =>
    apiClient.get<any, TaskStatistics>('/tasks/statistics/summary'),

  // 获取每日统计
  getDailyStatistics: (days: number = 7) =>
    apiClient.get('/tasks/statistics/daily', { params: { days } }),

  // 获取类型分布
  getTypeDistribution: () =>
    apiClient.get('/tasks/statistics/type-distribution'),

  // 获取状态分布
  getStatusDistribution: () =>
    apiClient.get<any, Record<string, number>>('/tasks/statistics/status-distribution'),

  // 获取执行日志
  getTaskLogs: (taskId: number) =>
    apiClient.get(`/tasks/${taskId}/logs`),

  // 获取调度器状态
  getSchedulerStatus: () =>
    apiClient.get('/tasks/scheduler/status'),
};

export default apiClient;
