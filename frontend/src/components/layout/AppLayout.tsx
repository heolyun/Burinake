import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { isDemoMode } from '../../api/demoApi';

export function AppLayout() {
  return (
    <div className="app-layout">
      <Sidebar />
      <main className="page-shell">
        {isDemoMode ? (
          <aside className="demo-notice">
            <strong>Portfolio Demo</strong>
            <span>실제 119 신고와 Azure AI 호출 없이 검증된 시나리오 데이터를 재현합니다.</span>
          </aside>
        ) : null}
        <Outlet />
      </main>
    </div>
  );
}
