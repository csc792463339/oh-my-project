import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../../api/client';

export default function AdminLayout() {
  const nav = useNavigate();
  const location = useLocation();
  const [checked, setChecked] = useState(false);
  const [navOpen, setNavOpen] = useState(false);

  useEffect(() => {
    api.adminMe().then(() => setChecked(true)).catch(() => nav('/admin/login'));
  }, [nav]);

  useEffect(() => {
    setNavOpen(false);
  }, [location.pathname]);

  async function logout() {
    await api.adminLogout();
    nav('/admin/login');
  }

  if (!checked) return null;

  return (
    <div className="admin-layout">
      <div className="admin-topbar">
        <button
          type="button"
          className="admin-nav-toggle"
          data-testid="nav-toggle"
          aria-label="打开导航"
          onClick={() => setNavOpen(true)}
        >
          <span aria-hidden>☰</span>
        </button>
        <div className="admin-topbar-title">后台</div>
      </div>

      <nav className={navOpen ? 'open' : ''}>
        <div className="brand">Oh My Project 后台</div>
        <NavLink to="/admin/projects" className={({ isActive }) => (isActive ? 'active' : '')}>
          项目管理
        </NavLink>
        <NavLink to="/admin/sessions" className={({ isActive }) => (isActive ? 'active' : '')}>
          会话列表
        </NavLink>
        <div style={{ marginTop: 'auto', paddingTop: 20 }}>
          <Link to="/">← 返回聊天</Link>
          <button className="btn secondary" onClick={logout} style={{ marginTop: 12, width: '100%' }}>
            退出登录
          </button>
        </div>
      </nav>

      {navOpen && (
        <div
          className="admin-backdrop"
          role="presentation"
          onClick={() => setNavOpen(false)}
        />
      )}

      <main>
        <Outlet />
      </main>
    </div>
  );
}
