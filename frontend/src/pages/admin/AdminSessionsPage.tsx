import { useEffect, useState } from 'react';
import { api, Session } from '../../api/client';

interface AdminSummary {
  sessionId: string;
  title: string;
  userId: string;
  projectId: string;
  lastActiveAt: number;
}

export default function AdminSessionsPage() {
  const [items, setItems] = useState<AdminSummary[]>([]);
  const [detail, setDetail] = useState<Session | null>(null);

  useEffect(() => {
    api.adminListSessions().then(setItems);
  }, []);

  async function open(id: string) {
    setDetail(await api.adminGetSession(id));
  }

  return (
    <div>
      <h2>全部会话</h2>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <table>
          <thead>
            <tr>
              <th>标题</th>
              <th>用户</th>
              <th>最近活跃</th>
            </tr>
          </thead>
          <tbody>
            {items.map((s) => (
              <tr key={s.sessionId} style={{ cursor: 'pointer' }} onClick={() => open(s.sessionId)}>
                <td data-label="标题">{s.title}</td>
                <td data-label="用户" style={{ fontFamily: 'monospace', fontSize: 12 }}>{s.userId.slice(0, 8)}</td>
                <td data-label="最近活跃">{new Date(s.lastActiveAt).toLocaleString()}</td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr>
                <td colSpan={3} className="empty" data-label="">
                  暂无会话
                </td>
              </tr>
            )}
          </tbody>
        </table>

        <div style={{ background: 'white', padding: 16, borderRadius: 8, maxHeight: '70vh', overflow: 'auto' }}>
          {detail ? (
            <>
              <h3 style={{ marginTop: 0 }}>{detail.title}</h3>
              <div style={{ color: '#6b7280', fontSize: 12, marginBottom: 12 }}>
                项目: {detail.projectId} · 用户: {detail.userId}
              </div>
              {detail.messages.map((m, i) => (
                <div key={i} style={{ marginBottom: 12 }}>
                  <div style={{ fontWeight: 600, color: m.role === 'user' ? '#2563eb' : '#111827' }}>
                    {m.role === 'user' ? '用户' : '助手'}
                  </div>
                  <div style={{ whiteSpace: 'pre-wrap' }}>{m.content}</div>
                </div>
              ))}
            </>
          ) : (
            <div className="empty">点击左侧会话查看详情</div>
          )}
        </div>
      </div>
    </div>
  );
}
