import { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { taskApi } from '@/lib/api';
import {
  BarChart3,
  TrendingUp,
  PieChart,
  Activity,
  Calendar,
  Clock,
} from 'lucide-react';

export default function Analytics() {
  const [loading, setLoading] = useState(true);
  const [statusDistribution, setStatusDistribution] = useState<Record<string, number>>({});
  const [typeDistribution, setTypeDistribution] = useState<any[]>([]);

  useEffect(() => {
    loadAnalytics();
  }, []);

  const loadAnalytics = async () => {
    try {
      const [statusData, typeData] = await Promise.all([
        taskApi.getStatusDistribution(),
        taskApi.getTypeDistribution(),
      ]);
      setStatusDistribution(statusData);
      setTypeDistribution(typeData);
    } catch (error) {
      console.error('Failed to load analytics:', error);
    } finally {
      setLoading(false);
    }
  };

  const totalTasks = Object.values(statusDistribution).reduce((a, b) => a + b, 0);

  const statusCards = [
    { label: '待执行', key: 'PENDING', color: 'blue', icon: Clock },
    { label: '执行中', key: 'RUNNING', color: 'yellow', icon: Activity },
    { label: '已完成', key: 'SUCCESS', color: 'green', icon: TrendingUp },
    { label: '失败', key: 'FAILED', color: 'red', icon: BarChart3 },
  ];

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-muted-foreground">加载数据...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">数据分析</h2>
        <p className="text-muted-foreground">任务执行统计与性能分析</p>
      </div>

      {/* 状态分布 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {statusCards.map((card) => {
          const Icon = card.icon;
          const count = statusDistribution[card.key] || 0;
          const percentage = totalTasks > 0 ? ((count / totalTasks) * 100).toFixed(1) : '0';
          return (
            <Card key={card.key}>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm font-medium">{card.label}</CardTitle>
                  <Icon className={`h-4 w-4 text-${card.color}-600`} />
                </div>
              </CardHeader>
              <CardContent>
                <div className={`text-2xl font-bold text-${card.color}-600`}>{count}</div>
                <p className="text-xs text-muted-foreground mt-1">占比 {percentage}%</p>
                <div className="w-full bg-gray-200 rounded-full h-1 mt-2">
                  <div
                    className={`bg-${card.color}-600 h-1 rounded-full`}
                    style={{ width: `${percentage}%` }}
                  ></div>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 任务类型分布 */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <PieChart className="h-5 w-5" />
              任务类型分布
            </CardTitle>
            <CardDescription>各类型任务数量统计</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {typeDistribution.length > 0 ? (
                typeDistribution.map((item) => {
                  const percentage =
                    totalTasks > 0 ? ((item.taskCount / totalTasks) * 100).toFixed(1) : '0';
                  return (
                    <div key={item.taskType} className="space-y-2">
                      <div className="flex justify-between items-center">
                        <span className="text-sm font-medium">{item.taskType}</span>
                        <span className="text-sm text-gray-600">
                          {item.taskCount} ({percentage}%)
                        </span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className="bg-gradient-to-r from-blue-500 to-blue-600 h-2 rounded-full"
                          style={{ width: `${percentage}%` }}
                        ></div>
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="text-center py-8 text-gray-500">暂无数据</div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* 执行趋势 */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Calendar className="h-5 w-5" />
              近7日执行趋势
            </CardTitle>
            <CardDescription>任务执行统计趋势图</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-64 flex items-end justify-between gap-2">
              {[45, 52, 38, 65, 48, 58, 62].map((height, index) => (
                <div key={index} className="flex-1 flex flex-col items-center gap-2">
                  <div
                    className="w-full bg-gradient-to-t from-blue-500 to-blue-300 rounded-t"
                    style={{ height: `${height}%` }}
                  ></div>
                  <span className="text-xs text-gray-500">
                    {new Date(Date.now() - (6 - index) * 86400000).getDate()}日
                  </span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 性能指标 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">平均执行时间</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">2.4s</div>
            <p className="text-xs text-muted-foreground mt-1">比上周快 12%</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">成功率</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-green-600">97.8%</div>
            <p className="text-xs text-muted-foreground mt-1">较上周提升 2.3%</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">今日执行</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-blue-600">128</div>
            <p className="text-xs text-muted-foreground mt-1">比昨天多 12 个</p>
          </CardContent>
        </Card>
      </div>

      {/* 任务执行详情表 */}
      <Card>
        <CardHeader>
          <CardTitle>任务类型详细统计</CardTitle>
          <CardDescription>各类型任务的执行情况</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b">
                  <th className="text-left p-3 text-sm font-medium text-gray-600">任务类型</th>
                  <th className="text-right p-3 text-sm font-medium text-gray-600">总数</th>
                  <th className="text-right p-3 text-sm font-medium text-gray-600">成功</th>
                  <th className="text-right p-3 text-sm font-medium text-gray-600">失败</th>
                  <th className="text-right p-3 text-sm font-medium text-gray-600">成功率</th>
                </tr>
              </thead>
              <tbody>
                {typeDistribution.map((item) => {
                  const successRate = item.successCount
                    ? ((item.successCount / item.taskCount) * 100).toFixed(1)
                    : '0';
                  return (
                    <tr key={item.taskType} className="border-b hover:bg-gray-50">
                      <td className="p-3">
                        <span className="font-medium">{item.taskType}</span>
                      </td>
                      <td className="p-3 text-right">{item.taskCount}</td>
                      <td className="p-3 text-right text-green-600">
                        {item.successCount || 0}
                      </td>
                      <td className="p-3 text-right text-red-600">{item.failedCount || 0}</td>
                      <td className="p-3 text-right">
                        <span
                          className={`px-2 py-1 rounded text-xs font-medium ${
                            parseFloat(successRate) >= 90
                              ? 'bg-green-100 text-green-800'
                              : parseFloat(successRate) >= 70
                              ? 'bg-yellow-100 text-yellow-800'
                              : 'bg-red-100 text-red-800'
                          }`}
                        >
                          {successRate}%
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
