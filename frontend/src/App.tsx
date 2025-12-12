import { BrowserRouter as Router, Routes, Route, Link, useLocation } from 'react-router-dom';
import Dashboard from './components/Dashboard';
import TaskList from './components/TaskList';
import CreateTask from './components/CreateTask';
import TaskDetail from './components/TaskDetail';
import CalendarView from './components/CalendarView';
import TaskTemplates from './components/TaskTemplates';
import NotificationCenter from './components/NotificationCenter';
import Analytics from './components/Analytics';
import AlertRuleForm from './components/AlertRuleForm';
import { Button } from './components/ui/button';
import { 
  LayoutDashboard, 
  ListTodo, 
  CalendarDays, 
  PlusCircle, 
  FileText, 
  Bell, 
  BarChart3,
  Settings
} from 'lucide-react';

function Sidebar() {
  const location = useLocation();
  
  const menuItems = [
    { path: '/', icon: LayoutDashboard, label: '仪表板' },
    { path: '/tasks', icon: ListTodo, label: '任务列表' },
    { path: '/calendar', icon: CalendarDays, label: '日历视图' },
    { path: '/create', icon: PlusCircle, label: '创建任务' },
  ];

  const managementItems = [
    { path: '/templates', icon: FileText, label: '任务模板' },
    { path: '/notifications', icon: Bell, label: '通知中心' },
    { path: '/analytics', icon: BarChart3, label: '数据分析' },
    { path: '/alert-rules/new', icon: PlusCircle, label: '新建报警规则' },
  ];

  const isActive = (path: string) => location.pathname === path;

  return (
    <div className="w-64 bg-white border-r h-screen flex flex-col">
      <div className="p-6 border-b">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-blue-600 rounded-lg">
            <Settings className="h-6 w-6 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold">定时任务系统</h1>
            <p className="text-xs text-gray-500">Task Scheduler Management</p>
          </div>
        </div>
      </div>

      <nav className="flex-1 p-4 space-y-1">
        {menuItems.map((item) => {
          const Icon = item.icon;
          return (
            <Link key={item.path} to={item.path}>
              <div
                className={`flex items-center gap-3 px-3 py-2 rounded-lg transition-colors ${
                  isActive(item.path)
                    ? 'bg-blue-50 text-blue-600'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <Icon className="h-5 w-5" />
                <span className="text-sm font-medium">{item.label}</span>
              </div>
            </Link>
          );
        })}

        <div className="pt-4 pb-2">
          <p className="px-3 text-xs font-semibold text-gray-500 uppercase">管理工具</p>
        </div>

        {managementItems.map((item) => {
          const Icon = item.icon;
          return (
            <Link key={item.path} to={item.path}>
              <div
                className={`flex items-center gap-3 px-3 py-2 rounded-lg transition-colors ${
                  isActive(item.path)
                    ? 'bg-blue-50 text-blue-600'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <Icon className="h-5 w-5" />
                <span className="text-sm font-medium">{item.label}</span>
                {item.path === '/notifications' && (
                  <span className="ml-auto bg-red-500 text-white text-xs px-2 py-0.5 rounded-full">
                    3
                  </span>
                )}
              </div>
            </Link>
          );
        })}
      </nav>
    </div>
  );
}

function Header() {
  return (
    <header className="h-16 border-b bg-white flex items-center justify-between px-8">
      <div className="flex-1" />
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon">
          <Bell className="h-5 w-5" />
          <span className="absolute top-2 right-2 w-2 h-2 bg-red-500 rounded-full" />
        </Button>
        <Button>
          <PlusCircle className="h-4 w-4 mr-2" />
          创建任务
        </Button>
      </div>
    </header>
  );
}

function AppContent() {
  return (
    <div className="flex h-screen bg-gray-50">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto p-8">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/tasks" element={<TaskList />} />
            <Route path="/tasks/:id" element={<TaskDetail />} />
            <Route path="/create" element={<CreateTask />} />
            <Route path="/calendar" element={<CalendarView />} />
            <Route path="/templates" element={<TaskTemplates />} />
            <Route path="/notifications" element={<NotificationCenter />} />
            <Route path="/analytics" element={<Analytics />} />
            <Route path="/alert-rules/new" element={<AlertRuleForm />} />
          </Routes>
        </main>
      </div>
    </div>
  );
}

function App() {
  return (
    <Router>
      <AppContent />
    </Router>
  );
}

export default App;
