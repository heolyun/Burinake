import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';

export function AppLayout() {
  return (
    <div className="app-layout">
      <Sidebar />
      <div className="content-shell">
        <main className="page-shell">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
