import { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { taskApi, Task } from '@/lib/api';
import { Calendar, Clock } from 'lucide-react';

export default function CalendarView() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentMonth, setCurrentMonth] = useState(new Date());

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

  const getDaysInMonth = (date: Date) => {
    const year = date.getFullYear();
    const month = date.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const daysInMonth = lastDay.getDate();
    const startingDayOfWeek = firstDay.getDay();

    return { daysInMonth, startingDayOfWeek, year, month };
  };

  const getTasksForDate = (date: string) => {
    return tasks.filter((task) => {
      if (task.executeTime) {
        const taskDate = new Date(task.executeTime).toDateString();
        return taskDate === date;
      }
      return false;
    });
  };

  const { daysInMonth, startingDayOfWeek, year, month } = getDaysInMonth(currentMonth);
  const monthNames = [
    '一月', '二月', '三月', '四月', '五月', '六月',
    '七月', '八月', '九月', '十月', '十一月', '十二月',
  ];
  const weekDays = ['日', '一', '二', '三', '四', '五', '六'];

  const previousMonth = () => {
    setCurrentMonth(new Date(year, month - 1, 1));
  };

  const nextMonth = () => {
    setCurrentMonth(new Date(year, month + 1, 1));
  };

  const today = new Date().toDateString();

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-muted-foreground">加载日历...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">日历视图</h2>
          <p className="text-muted-foreground">任务时间线可视化</p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <div className="flex justify-between items-center">
            <CardTitle className="flex items-center gap-2">
              <Calendar className="h-5 w-5" />
              {year}年 {monthNames[month]}
            </CardTitle>
            <div className="flex gap-2">
              <button
                onClick={previousMonth}
                className="px-3 py-1 rounded border hover:bg-gray-100"
              >
                上月
              </button>
              <button
                onClick={nextMonth}
                className="px-3 py-1 rounded border hover:bg-gray-100"
              >
                下月
              </button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-7 gap-2">
            {weekDays.map((day) => (
              <div
                key={day}
                className="text-center font-semibold text-sm p-2 text-gray-600"
              >
                星期{day}
              </div>
            ))}

            {Array.from({ length: startingDayOfWeek }).map((_, index) => (
              <div key={`empty-${index}`} className="p-2" />
            ))}

            {Array.from({ length: daysInMonth }).map((_, index) => {
              const day = index + 1;
              const currentDate = new Date(year, month, day);
              const dateString = currentDate.toDateString();
              const dayTasks = getTasksForDate(dateString);
              const isToday = dateString === today;

              return (
                <div
                  key={day}
                  className={`min-h-[100px] p-2 border rounded-lg ${
                    isToday ? 'bg-blue-50 border-blue-300' : 'bg-white'
                  } hover:shadow-md transition-shadow`}
                >
                  <div
                    className={`text-sm font-medium mb-1 ${
                      isToday ? 'text-blue-600' : 'text-gray-700'
                    }`}
                  >
                    {day}
                  </div>
                  <div className="space-y-1">
                    {dayTasks.slice(0, 3).map((task) => (
                      <div
                        key={task.id}
                        className={`text-xs p-1 rounded truncate ${
                          task.status === 'SUCCESS'
                            ? 'bg-green-100 text-green-800'
                            : task.status === 'PENDING'
                            ? 'bg-blue-100 text-blue-800'
                            : task.status === 'FAILED'
                            ? 'bg-red-100 text-red-800'
                            : 'bg-gray-100 text-gray-800'
                        }`}
                        title={task.taskName}
                      >
                        <Clock className="h-3 w-3 inline mr-1" />
                        {task.taskName}
                      </div>
                    ))}
                    {dayTasks.length > 3 && (
                      <div className="text-xs text-gray-500">
                        +{dayTasks.length - 3} 更多
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>本月任务统计</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-4 gap-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-blue-600">
                {tasks.filter((t) => t.status === 'PENDING').length}
              </div>
              <div className="text-sm text-gray-600">待执行</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-yellow-600">
                {tasks.filter((t) => t.status === 'RUNNING').length}
              </div>
              <div className="text-sm text-gray-600">执行中</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-green-600">
                {tasks.filter((t) => t.status === 'SUCCESS').length}
              </div>
              <div className="text-sm text-gray-600">已完成</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-red-600">
                {tasks.filter((t) => t.status === 'FAILED').length}
              </div>
              <div className="text-sm text-gray-600">失败</div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
