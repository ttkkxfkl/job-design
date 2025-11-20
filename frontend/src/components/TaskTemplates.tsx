import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { FileText, Copy, Clock, Mail, MessageSquare, Globe } from 'lucide-react';

interface TaskTemplate {
  id: string;
  name: string;
  type: string;
  description: string;
  cronExpression?: string;
  icon: any;
  color: string;
}

export default function TaskTemplates() {
  const templates: TaskTemplate[] = [
    {
      id: 'daily-report',
      name: '每日报表生成',
      type: 'EMAIL',
      description: '每天早上9点自动生成并发送日报',
      cronExpression: '0 0 9 * * ?',
      icon: FileText,
      color: 'blue',
    },
    {
      id: 'weekly-backup',
      name: '每周数据备份',
      type: 'PLAN',
      description: '每周一凌晨2点执行数据备份',
      cronExpression: '0 0 2 ? * MON',
      icon: Copy,
      color: 'green',
    },
    {
      id: 'hourly-check',
      name: '每小时健康检查',
      type: 'WEBHOOK',
      description: '每小时检查系统健康状态',
      cronExpression: '0 0 * * * ?',
      icon: Clock,
      color: 'purple',
    },
    {
      id: 'morning-notification',
      name: '早安通知',
      type: 'SMS',
      description: '每天早上8点发送问候短信',
      cronExpression: '0 0 8 * * ?',
      icon: MessageSquare,
      color: 'yellow',
    },
    {
      id: 'evening-summary',
      name: '晚间数据汇总',
      type: 'EMAIL',
      description: '每天晚上8点汇总当日数据',
      cronExpression: '0 0 20 * * ?',
      icon: Mail,
      color: 'red',
    },
    {
      id: 'api-sync',
      name: 'API 数据同步',
      type: 'WEBHOOK',
      description: '每30分钟同步一次外部API数据',
      cronExpression: '0 */30 * * * ?',
      icon: Globe,
      color: 'indigo',
    },
  ];

  const handleUseTemplate = (template: TaskTemplate) => {
    alert(`即将使用模板: ${template.name}\n类型: ${template.type}\nCron: ${template.cronExpression}`);
  };

  const getColorClasses = (color: string) => {
    const colors: Record<string, { bg: string; text: string; icon: string }> = {
      blue: { bg: 'bg-blue-50', text: 'text-blue-600', icon: 'bg-blue-100' },
      green: { bg: 'bg-green-50', text: 'text-green-600', icon: 'bg-green-100' },
      purple: { bg: 'bg-purple-50', text: 'text-purple-600', icon: 'bg-purple-100' },
      yellow: { bg: 'bg-yellow-50', text: 'text-yellow-600', icon: 'bg-yellow-100' },
      red: { bg: 'bg-red-50', text: 'text-red-600', icon: 'bg-red-100' },
      indigo: { bg: 'bg-indigo-50', text: 'text-indigo-600', icon: 'bg-indigo-100' },
    };
    return colors[color] || colors.blue;
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">任务模板</h2>
        <p className="text-muted-foreground">使用预设模板快速创建常用任务</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {templates.map((template) => {
          const Icon = template.icon;
          const colors = getColorClasses(template.color);
          return (
            <Card key={template.id} className="hover:shadow-lg transition-shadow">
              <CardHeader>
                <div className="flex items-start justify-between">
                  <div className={`p-3 rounded-lg ${colors.icon}`}>
                    <Icon className={`h-6 w-6 ${colors.text}`} />
                  </div>
                </div>
                <CardTitle className="mt-4">{template.name}</CardTitle>
                <CardDescription>{template.description}</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-3">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-600">任务类型</span>
                    <span className={`px-2 py-1 rounded ${colors.bg} ${colors.text} font-medium`}>
                      {template.type}
                    </span>
                  </div>
                  {template.cronExpression && (
                    <div className="text-xs text-gray-500 font-mono bg-gray-50 p-2 rounded">
                      {template.cronExpression}
                    </div>
                  )}
                  <Button
                    className="w-full mt-2"
                    variant="outline"
                    onClick={() => handleUseTemplate(template)}
                  >
                    使用模板
                  </Button>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>自定义模板</CardTitle>
          <CardDescription>将常用任务保存为模板，方便后续快速创建</CardDescription>
        </CardHeader>
        <CardContent>
          <Button>创建自定义模板</Button>
        </CardContent>
      </Card>
    </div>
  );
}
