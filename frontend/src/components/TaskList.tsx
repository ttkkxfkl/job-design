import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { taskApi, Task } from '@/lib/api';
import { Play, Pause, Trash2, MoreVertical } from 'lucide-react';

export default function TaskList() {
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadTasks();
  }, []);

  const loadTasks = async () => {
    try {
      const data = await taskApi.getTasks();
      setTasks(data);
    } catch (error) {
      console.error('Failed to load tasks:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleRetry = async (id: number) => {
    try {
      await taskApi.retryTask(id);
      loadTasks();
    } catch (error) {
      console.error('Failed to retry task:', error);
    }
  };

  const handlePause = async (id: number) => {
    try {
      await taskApi.pauseTask(id);
      loadTasks();
    } catch (error) {
      console.error('Failed to pause task:', error);
    }
  };

  const handleResume = async (id: number) => {
    try {
      await taskApi.resumeTask(id);
      loadTasks();
    } catch (error) {
      console.error('Failed to resume task:', error);
    }
  };

  const handleDelete = async (id: number) => {
    if (confirm('确定要删除这个任务吗？')) {
      try {
        await taskApi.deleteTask(id);
        loadTasks();
      } catch (error) {
        console.error('Failed to delete task:', error);
      }
    }
  };

  const getStatusBadge = (status: string) => {
    const statusMap = {
      PENDING: { label: '待执行', className: 'bg-blue-100 text-blue-800' },
      RUNNING: { label: '执行中', className: 'bg-yellow-100 text-yellow-800' },
      PAUSED: { label: '已暂停', className: 'bg-gray-100 text-gray-800' },
      COMPLETED: { label: '已完成', className: 'bg-green-100 text-green-800' },
      FAILED: { label: '失败', className: 'bg-red-100 text-red-800' },
    };
    const config = statusMap[status as keyof typeof statusMap] || statusMap.PENDING;
    return (
      <span className={`px-2 py-1 rounded-full text-xs font-medium ${config.className}`}>
        {config.label}
      </span>
    );
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-muted-foreground">加载任务列表...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">任务列表</h2>
          <p className="text-muted-foreground">管理和监控所有定时任务</p>
        </div>
      </div>

      <div className="space-y-4">
        {tasks.length === 0 ? (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-12">
              <div className="text-center">
                <h3 className="text-lg font-semibold mb-2">暂无任务</h3>
                <p className="text-muted-foreground mb-4">点击右上角「创建任务」按钮开始创建第一个定时任务</p>
              </div>
            </CardContent>
          </Card>
        ) : (
          tasks.map((task) => (
          <Card 
            key={task.id} 
            className="cursor-pointer hover:shadow-md transition-shadow"
            onClick={() => navigate(`/tasks/${task.id}`)}
          >
            <CardHeader className="pb-3">
              <div className="flex justify-between items-start">
                <div className="space-y-1">
                  <CardTitle className="text-lg">{task.taskName}</CardTitle>
                  <div className="flex items-center gap-3 text-sm text-muted-foreground">
                    <span>{task.taskType}</span>
                    <span>•</span>
                    <span className="font-mono">{task.cronExpression}</span>
                    <span>•</span>
                    <span>优先级: {task.priority}</span>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {getStatusBadge(task.status)}
                </div>
              </div>
            </CardHeader>
            <CardContent>
              <div className="flex justify-between items-center">
                <div className="text-sm text-muted-foreground">
                  更新时间: {new Date(task.updatedAt).toLocaleString('zh-CN')}
                </div>
                <div className="flex gap-2">
                  {task.status === 'RUNNING' || task.status === 'PENDING' ? (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={(e) => {
                        e.stopPropagation();
                        handlePause(task.id);
                      }}
                    >
                      <Pause className="h-4 w-4 mr-1" />
                      暂停
                    </Button>
                  ) : task.status === 'PAUSED' ? (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleResume(task.id);
                      }}
                    >
                      <Play className="h-4 w-4 mr-1" />
                      恢复
                    </Button>
                  ) : task.status === 'FAILED' ? (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRetry(task.id);
                      }}
                    >
                      <Play className="h-4 w-4 mr-1" />
                      重试
                    </Button>
                  ) : null}
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDelete(task.id);
                    }}
                  >
                    <Trash2 className="h-4 w-4 mr-1" />
                    删除
                  </Button>
                  <Button 
                    size="sm" 
                    variant="ghost"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <MoreVertical className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        ))
        )}
      </div>
    </div>
  );
}
