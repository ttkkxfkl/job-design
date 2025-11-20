import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { Separator } from './ui/separator';
import { 
  Clock, 
  Calendar, 
  Activity, 
  CheckCircle2, 
  XCircle, 
  Pause, 
  Play, 
  RotateCcw, 
  Trash2,
  ArrowLeft,
  AlertCircle
} from 'lucide-react';
import { taskApi, Task as ApiTask } from '@/lib/api';

interface Task extends ApiTask {
  executionCount?: number;
  successCount?: number;
  failureCount?: number;
  nextExecutionTime?: string;
  lastExecutionTime?: string;
}

interface ExecutionLog {
  id: number;
  taskId: number;
  executionTime: string;
  status: string;
  duration: number;
  errorMessage?: string;
}

export default function TaskDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [task, setTask] = useState<Task | null>(null);
  const [logs, setLogs] = useState<ExecutionLog[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadTaskDetail();
    loadExecutionLogs();
  }, [id]);

  const loadTaskDetail = async () => {
    try {
      const response = await taskApi.getTasks();
      const foundTask = response.find((t: Task) => t.id === Number(id));
      if (foundTask) {
        setTask(foundTask);
      }
    } catch (error) {
      console.error('Failed to load task detail:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadExecutionLogs = async () => {
    try {
      // 模拟执行日志数据，实际应该从后端API获取
      const mockLogs: ExecutionLog[] = [
        {
          id: 1,
          taskId: Number(id),
          executionTime: new Date(Date.now() - 3600000).toISOString(),
          status: 'SUCCESS',
          duration: 1234,
        },
        {
          id: 2,
          taskId: Number(id),
          executionTime: new Date(Date.now() - 7200000).toISOString(),
          status: 'SUCCESS',
          duration: 1156,
        },
        {
          id: 3,
          taskId: Number(id),
          executionTime: new Date(Date.now() - 10800000).toISOString(),
          status: 'FAILURE',
          duration: 2341,
          errorMessage: '连接超时',
        },
        {
          id: 4,
          taskId: Number(id),
          executionTime: new Date(Date.now() - 14400000).toISOString(),
          status: 'SUCCESS',
          duration: 1089,
        },
        {
          id: 5,
          taskId: Number(id),
          executionTime: new Date(Date.now() - 18000000).toISOString(),
          status: 'SUCCESS',
          duration: 1298,
        },
      ];
      setLogs(mockLogs);
    } catch (error) {
      console.error('Failed to load execution logs:', error);
    }
  };

  const handlePause = async () => {
    if (!task) return;
    try {
      await taskApi.pauseTask(task.id);
      await loadTaskDetail();
    } catch (error) {
      console.error('Failed to pause task:', error);
    }
  };

  const handleResume = async () => {
    if (!task) return;
    try {
      await taskApi.resumeTask(task.id);
      await loadTaskDetail();
    } catch (error) {
      console.error('Failed to resume task:', error);
    }
  };

  const handleRetry = async () => {
    if (!task) return;
    try {
      await taskApi.retryTask(task.id);
      await loadTaskDetail();
      await loadExecutionLogs();
    } catch (error) {
      console.error('Failed to retry task:', error);
    }
  };

  const handleDelete = async () => {
    if (!task) return;
    if (window.confirm('确定要删除这个任务吗？')) {
      try {
        await taskApi.deleteTask(task.id);
        navigate('/tasks');
      } catch (error) {
        console.error('Failed to delete task:', error);
      }
    }
  };

  const getStatusBadge = (status: string) => {
    const statusMap: Record<string, { variant: 'default' | 'secondary' | 'destructive' | 'outline', label: string }> = {
      PENDING: { variant: 'default', label: '待执行' },
      RUNNING: { variant: 'default', label: '执行中' },
      PAUSED: { variant: 'secondary', label: '已暂停' },
      COMPLETED: { variant: 'outline', label: '已完成' },
      FAILED: { variant: 'destructive', label: '失败' },
      SUCCESS: { variant: 'outline', label: '成功' },
      FAILURE: { variant: 'destructive', label: '失败' },
    };
    const config = statusMap[status] || { variant: 'outline' as const, label: status };
    return <Badge variant={config.variant}>{config.label}</Badge>;
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('zh-CN');
  };

  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-500">加载中...</p>
        </div>
      </div>
    );
  }

  if (!task) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <AlertCircle className="h-12 w-12 text-gray-400 mx-auto mb-4" />
          <p className="text-gray-500">任务不存在</p>
          <Button variant="link" onClick={() => navigate('/tasks')} className="mt-4">
            返回任务列表
          </Button>
        </div>
      </div>
    );
  }

  const successRate = (task.executionCount || 0) > 0 
    ? (((task.successCount || 0) / (task.executionCount || 1)) * 100).toFixed(1)
    : '0.0';

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={() => navigate('/tasks')}>
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-3xl font-bold">{task.taskName}</h1>
            <p className="text-gray-500 mt-1">任务详情</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {task.status === 'PAUSED' ? (
            <Button onClick={handleResume} variant="default">
              <Play className="h-4 w-4 mr-2" />
              恢复
            </Button>
          ) : (
            <Button onClick={handlePause} variant="secondary">
              <Pause className="h-4 w-4 mr-2" />
              暂停
            </Button>
          )}
          <Button onClick={handleRetry} variant="outline">
            <RotateCcw className="h-4 w-4 mr-2" />
            重试
          </Button>
          <Button onClick={handleDelete} variant="destructive">
            <Trash2 className="h-4 w-4 mr-2" />
            删除
          </Button>
        </div>
      </div>

      {/* Basic Info */}
      <Card>
        <CardHeader>
          <CardTitle>基本信息</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-6">
            <div className="space-y-4">
              <div>
                <p className="text-sm text-gray-500 mb-1">任务类型</p>
                <Badge variant="outline" className="text-base">
                  {task.taskType}
                </Badge>
              </div>
              <div>
                <p className="text-sm text-gray-500 mb-1">任务状态</p>
                {getStatusBadge(task.status)}
              </div>
              {task.cronExpression && (
                <div>
                  <p className="text-sm text-gray-500 mb-1">Cron 表达式</p>
                  <code className="px-2 py-1 bg-gray-100 rounded text-sm">
                    {task.cronExpression}
                  </code>
                </div>
              )}
            </div>

            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <Calendar className="h-5 w-5 text-gray-400" />
                <div>
                  <p className="text-sm text-gray-500">创建时间</p>
                  <p className="text-sm font-medium">{formatDate(task.createdAt)}</p>
                </div>
              </div>
              {task.nextExecutionTime && (
                <div className="flex items-center gap-3">
                  <Clock className="h-5 w-5 text-gray-400" />
                  <div>
                    <p className="text-sm text-gray-500">下次执行</p>
                    <p className="text-sm font-medium">{formatDate(task.nextExecutionTime)}</p>
                  </div>
                </div>
              )}
              {task.lastExecutionTime && (
                <div className="flex items-center gap-3">
                  <Activity className="h-5 w-5 text-gray-400" />
                  <div>
                    <p className="text-sm text-gray-500">上次执行</p>
                    <p className="text-sm font-medium">{formatDate(task.lastExecutionTime)}</p>
                  </div>
                </div>
              )}
            </div>
          </div>

          {task.parameters && Object.keys(task.parameters).length > 0 && (
            <>
              <Separator className="my-6" />
              <div>
                <p className="text-sm text-gray-500 mb-2">任务参数</p>
                <pre className="p-4 bg-gray-50 rounded-lg text-sm overflow-x-auto">
                  {JSON.stringify(task.parameters, null, 2)}
                </pre>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      {/* Statistics */}
      <div className="grid grid-cols-4 gap-4">
        <Card>
          <CardHeader className="pb-3">
            <CardDescription>总执行次数</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{task.executionCount || 0}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <CardDescription>成功次数</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-green-600">{task.successCount || 0}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <CardDescription>失败次数</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-red-600">{task.failureCount || 0}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <CardDescription>成功率</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{successRate}%</div>
          </CardContent>
        </Card>
      </div>

      {/* Execution Logs */}
      <Card>
        <CardHeader>
          <CardTitle>执行历史</CardTitle>
          <CardDescription>最近的执行记录</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {logs.length === 0 ? (
              <p className="text-center text-gray-500 py-8">暂无执行记录</p>
            ) : (
              logs.map((log) => (
                <div
                  key={log.id}
                  className="flex items-center justify-between p-4 border rounded-lg hover:bg-gray-50 transition-colors"
                >
                  <div className="flex items-center gap-4 flex-1">
                    {log.status === 'SUCCESS' ? (
                      <CheckCircle2 className="h-5 w-5 text-green-600" />
                    ) : (
                      <XCircle className="h-5 w-5 text-red-600" />
                    )}
                    <div className="flex-1">
                      <div className="flex items-center gap-3">
                        <p className="font-medium">
                          {formatDate(log.executionTime)}
                        </p>
                        {getStatusBadge(log.status)}
                      </div>
                      {log.errorMessage && (
                        <p className="text-sm text-red-600 mt-1">
                          错误: {log.errorMessage}
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="text-sm text-gray-500">
                    耗时: {formatDuration(log.duration)}
                  </div>
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
