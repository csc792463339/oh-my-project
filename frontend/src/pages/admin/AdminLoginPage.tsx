import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';

export default function AdminLoginPage() {
  const [password, setPassword] = useState('');
  const [err, setErr] = useState('');
  const nav = useNavigate();

  async function submit() {
    try {
      await api.adminLogin(password);
      nav('/admin');
    } catch (e) {
      setErr((e as Error).message);
    }
  }

  return (
    <div
      className="admin-login-card"
      style={{
        maxWidth: 360,
        margin: '120px auto',
        padding: 32,
        background: 'var(--surface)',
        borderRadius: 'var(--radius-lg)',
        border: '1px solid var(--border)',
        boxShadow: 'var(--shadow)',
      }}
    >
      <h3
        style={{
          fontFamily: 'var(--font-serif)',
          fontWeight: 500,
          fontSize: 22,
          letterSpacing: '-0.015em',
          marginTop: 0,
          marginBottom: 20,
          color: 'var(--text)',
        }}
      >
        管理员登录
      </h3>
      <input
        type="password"
        value={password}
        placeholder="口令"
        onChange={(e) => setPassword(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && submit()}
        style={{
          width: '100%',
          padding: '10px 13px',
          margin: '0 0 14px',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-sm)',
          fontSize: 14,
          background: 'var(--bg-soft)',
          outline: 'none',
        }}
      />
      {err && (
        <div
          style={{
            color: 'var(--error-text)',
            marginBottom: 12,
            fontSize: 13,
            padding: '8px 12px',
            background: 'var(--error)',
            borderRadius: 'var(--radius-sm)',
          }}
        >
          {err}
        </div>
      )}
      <button className="btn" onClick={submit} style={{ width: '100%' }}>
        登录
      </button>
    </div>
  );
}
