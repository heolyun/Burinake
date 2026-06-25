import { NavLink } from 'react-router-dom';

const navItems = [
  { to: '/', label: '대시보드' },
  { to: '/snapshots/upload', label: '스냅샷 업로드' },
  { to: '/issues', label: '이슈 관리' },
  { to: '/reports', label: '신고 관리' },
  { to: '/settings', label: '설정' },
];

export function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="brand">
        <span>B</span>
        <div>
          <strong>Burinake</strong>
          <small>화재 관제</small>
        </div>
      </div>
      <nav>
        {navItems.map((item) => (
          <NavLink className={({ isActive }) => (isActive ? 'active' : undefined)} to={item.to} key={item.to}>
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
