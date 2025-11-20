import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Bell, CheckCircle, AlertCircle, Info, XCircle, Trash2 } from 'lucide-react';

interface Notification {
  id: number;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message: string;
  time: string;
  read: boolean;
}

export default function NotificationCenter() {
  const notifications: Notification[] = [
    {
      id: 1,
      type: 'error',
      title: '任务执行失败',
      message: '任务 "每日数据备份" 执行失败，请检查日志',
      time: '5分钟前',
      read: false,
    },
    {
      id: 2,
      type: 'success',
      title: '任务完成',
      message: '任务 "周报生成" 已成功完成',
      time: '1小时前',
      read: false,
    },
    {
      id: 3,
      type: 'warning',
      title: '任务即将执行',
      message: '任务 "系统维护" 将在30分钟后执行',
      time: '2小时前',
      read: false,
    },
    {
      id: 4,
      type: 'info',
      title: '系统更新',
      message: '调度系统已升级到最新版本',
      time: '昨天',
      read: true,
    },
    {
      id: 5,
      type: 'success',
      title: '任务创建成功',
      message: '新任务 "API数据同步" 已创建并启动',
      time: '昨天',
      read: true,
    },
    {
      id: 6,
      type: 'error',
      title: '连接超时',
      message: '任务 "外部API调用" 连接超时',
      time: '2天前',
      read: true,
    },
  ];

  const getNotificationIcon = (type: string) => {
    switch (type) {
      case 'success':
        return <CheckCircle className="h-5 w-5 text-green-600" />;
      case 'error':
        return <XCircle className="h-5 w-5 text-red-600" />;
      case 'warning':
        return <AlertCircle className="h-5 w-5 text-yellow-600" />;
      case 'info':
        return <Info className="h-5 w-5 text-blue-600" />;
      default:
        return <Bell className="h-5 w-5 text-gray-600" />;
    }
  };

  const getNotificationBg = (type: string, read: boolean) => {
    if (read) return 'bg-gray-50';
    switch (type) {
      case 'success':
        return 'bg-green-50 border-green-200';
      case 'error':
        return 'bg-red-50 border-red-200';
      case 'warning':
        return 'bg-yellow-50 border-yellow-200';
      case 'info':
        return 'bg-blue-50 border-blue-200';
      default:
        return 'bg-white';
    }
  };

  const unreadCount = notifications.filter((n) => !n.read).length;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">通知中心</h2>
          <p className="text-muted-foreground">
            {unreadCount > 0 ? `${unreadCount} 条未读通知` : '所有通知已读'}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline">全部标为已读</Button>
          <Button variant="outline">
            <Trash2 className="h-4 w-4 mr-2" />
            清空已读
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium text-gray-600">总通知</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{notifications.length}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium text-gray-600">未读</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">{unreadCount}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium text-gray-600">今日新增</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">3</div>
          </CardContent>
        </Card>
      </div>

      <div className="space-y-3">
        {notifications.map((notification) => (
          <Card
            key={notification.id}
            className={`${getNotificationBg(
              notification.type,
              notification.read
            )} border transition-all hover:shadow-md`}
          >
            <CardContent className="p-4">
              <div className="flex items-start gap-4">
                <div className="mt-1">{getNotificationIcon(notification.type)}</div>
                <div className="flex-1">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="font-semibold text-gray-900">
                        {notification.title}
                        {!notification.read && (
                          <span className="ml-2 inline-block w-2 h-2 bg-blue-600 rounded-full"></span>
                        )}
                      </h3>
                      <p className="text-sm text-gray-600 mt-1">{notification.message}</p>
                      <p className="text-xs text-gray-400 mt-2">{notification.time}</p>
                    </div>
                    <div className="flex gap-2">
                      {!notification.read && (
                        <Button size="sm" variant="ghost">
                          标为已读
                        </Button>
                      )}
                      <Button size="sm" variant="ghost">
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {notifications.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Bell className="h-12 w-12 text-gray-300 mb-4" />
            <h3 className="text-lg font-semibold mb-2">暂无通知</h3>
            <p className="text-muted-foreground">当前没有任何系统通知</p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
