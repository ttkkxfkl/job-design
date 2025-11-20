import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { taskApi, CreateTaskRequest } from '@/lib/api';

export default function CreateTask() {
  const [formData, setFormData] = useState<CreateTaskRequest>({
    taskName: '',
    taskType: 'EMAIL',
    executeTime: '',
    priority: 5,
  });
  const [loading, setLoading] = useState(false);
  const [useCron, setUseCron] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const requestData = useCron ? {
        ...formData,
        cronExpression: formData.cronExpression,
        executeTime: undefined,
      } : {
        ...formData,
        executeTime: formData.executeTime,
        cronExpression: undefined,
      };
      
      await taskApi.createTask(requestData);
      alert('任务创建成功！');
      // 重置表单
      setFormData({
        taskName: '',
        taskType: 'EMAIL',
        executeTime: '',
        priority: 5,
      });
      setUseCron(false);
    } catch (error) {
      alert('创建失败: ' + error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">创建任务</h2>
        <p className="text-muted-foreground">配置新的定时任务</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>基本信息</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="space-y-2">
              <Label htmlFor="taskName">任务名称 *</Label>
              <Input
                id="taskName"
                placeholder="输入任务名称"
                value={formData.taskName}
                onChange={(e) => setFormData({ ...formData, taskName: e.target.value })}
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="taskType">任务类型 *</Label>
              <select
                id="taskType"
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background"
                value={formData.taskType}
                onChange={(e) => setFormData({ ...formData, taskType: e.target.value })}
                required
              >
                <option value="EMAIL">邮件 - 发送邮件通知</option>
                <option value="SMS">短信 - 发送短信通知</option>
                <option value="WEBHOOK">Webhook - 发送HTTP请求</option>
                <option value="LOG">日志 - 记录系统日志</option>
                <option value="PLAN">计划 - 执行计划任务</option>
              </select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="scheduleMode">调度模式</Label>
              <div className="flex gap-4">
                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    checked={!useCron}
                    onChange={() => setUseCron(false)}
                    className="w-4 h-4"
                  />
                  <span className="text-sm">一次性任务</span>
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    checked={useCron}
                    onChange={() => setUseCron(true)}
                    className="w-4 h-4"
                  />
                  <span className="text-sm">周期性任务 (Cron)</span>
                </label>
              </div>
            </div>

            {!useCron ? (
              <div className="space-y-2">
                <Label htmlFor="executeTime">执行时间 *</Label>
                <Input
                  id="executeTime"
                  type="datetime-local"
                  value={formData.executeTime}
                  onChange={(e) => setFormData({ ...formData, executeTime: e.target.value })}
                  required={!useCron}
                />
              </div>
            ) : (
              <div className="space-y-2">
                <Label htmlFor="cronExpression">Cron 表达式 *</Label>
                <Input
                  id="cronExpression"
                  placeholder="例如: 0 0 12 * * ? (每天中午12点)"
                  value={formData.cronExpression || ''}
                  onChange={(e) => setFormData({ ...formData, cronExpression: e.target.value })}
                  required={useCron}
                />
                <p className="text-xs text-muted-foreground">
                  Cron 表达式格式: 秒 分 时 日 月 星期 [年]
                </p>
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="priority">优先级 (0-10)</Label>
              <Input
                id="priority"
                type="range"
                min="0"
                max="10"
                value={formData.priority}
                onChange={(e) =>
                  setFormData({ ...formData, priority: parseInt(e.target.value) })
                }
              />
              <div className="flex justify-end">
                <span className="text-sm font-medium">{formData.priority}</span>
              </div>
            </div>

            <div className="flex gap-4 pt-4">
              <Button type="submit" disabled={loading}>
                {loading ? '创建中...' : '创建任务'}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() =>
                  setFormData({
                    taskName: '',
                    taskType: 'EMAIL',
                    executeTime: '',
                    cronExpression: '',
                    priority: 5,
                  })
                }
              >
                重置
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
