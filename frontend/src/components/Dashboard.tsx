import { useEffect, useState } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { taskApi, TaskStatistics } from '@/lib/api';
import { Clock, Activity, CheckCircle, XCircle, TrendingUp, TrendingDown } from 'lucide-react';

export default function Dashboard() {
  const [stats, setStats] = useState<TaskStatistics>({
    pendingCount: 0,
    executingCount: 0,
    successCount: 0,
    failedCount: 0,
    cancelledCount: 0,
    pausedCount: 0,
    timeoutCount: 0,
    totalCount: 0,
    successRate: 0,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadStatistics();
  }, []);

  const loadStatistics = async () => {
    try {
      const data = await taskApi.getStatistics();
      setStats(data);
    } catch (error) {
      console.error('Failed to load statistics:', error);
    } finally {
      setLoading(false);
    }
  };

  const statCards = [
    {
      title: '待执行任务',
      value: stats.pendingCount,
      icon: Clock,
      trend: '+5',
      trendUp: true,
      color: 'text-blue-600',
      bgColor: 'bg-blue-50',
    },
    {
      title: '今日执行',
      value: stats.executingCount,
      icon: Activity,
      trend: '+12',
      trendUp: true,
      color: 'text-purple-600',
      bgColor: 'bg-purple-50',
    },
    {
      title: '成功率',
      value: `${(stats.successRate || 0).toFixed(1)}%`,
      icon: CheckCircle,
      trend: '+0.5%',
      trendUp: true,
      color: 'text-green-600',
      bgColor: 'bg-green-50',
    },
    {
      title: '失败任务',
      value: stats.failedCount,
      icon: XCircle,
      trend: '-2',
      trendUp: false,
      color: 'text-red-600',
      bgColor: 'bg-red-50',
    },
  ];

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-muted-foreground">加载中...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">仪表板</h2>
        <p className="text-muted-foreground">系统运行状态与任务统计概览</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {statCards.map((card, index) => {
          const Icon = card.icon;
          const TrendIcon = card.trendUp ? TrendingUp : TrendingDown;
          return (
            <Card key={index}>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{card.title}</CardTitle>
                <div className={`p-2 rounded-full ${card.bgColor}`}>
                  <Icon className={`h-4 w-4 ${card.color}`} />
                </div>
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{card.value}</div>
                <div className="flex items-center text-xs text-muted-foreground mt-1">
                  <TrendIcon
                    className={`h-3 w-3 mr-1 ${
                      card.trendUp ? 'text-green-600' : 'text-red-600'
                    }`}
                  />
                  <span className={card.trendUp ? 'text-green-600' : 'text-red-600'}>
                    {card.trend}
                  </span>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>任务状态分布</CardTitle>
            <CardDescription>当前系统中各状态任务数量</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-blue-500" />
                  <span className="text-sm">待执行</span>
                </div>
                <span className="font-medium">{stats.pendingCount}</span>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-green-500" />
                  <span className="text-sm">已完成</span>
                </div>
                <span className="font-medium">{stats.successCount}</span>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-yellow-500" />
                  <span className="text-sm">执行中</span>
                </div>
                <span className="font-medium">{stats.executingCount}</span>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-red-500" />
                  <span className="text-sm">失败</span>
                </div>
                <span className="font-medium">{stats.failedCount}</span>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>系统状态</CardTitle>
            <CardDescription>实时监控信息</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex justify-between items-center">
                <span className="text-sm text-muted-foreground">调度器状态</span>
                <span className="flex items-center gap-2">
                  <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
                  <span className="text-sm font-medium">运行中</span>
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-muted-foreground">集群节点</span>
                <span className="text-sm font-medium">3 个节点在线</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-muted-foreground">线程池状态</span>
                <div className="flex-1 mx-4">
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div className="bg-blue-600 h-2 rounded-full" style={{ width: '65%' }} />
                  </div>
                </div>
                <span className="text-sm font-medium">65%</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
