import { useEffect, useState } from 'react';
import { api, Project, ProjectInput } from '../../api/client';

const EMPTY: ProjectInput = { name: '', path: '', description: '' };

export default function AdminProjectsPage() {
  const [items, setItems] = useState<Project[]>([]);
  const [editing, setEditing] = useState<ProjectInput | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);

  useEffect(() => {
    load();
  }, []);

  async function load() {
    setItems(await api.adminListProjects());
  }

  function startNew() {
    setEditing({ ...EMPTY });
    setEditingId(null);
  }

  function startEdit(p: Project) {
    setEditing({
      name: p.name,
      path: p.path,
      description: p.description,
    });
    setEditingId(p.id);
  }

  async function save() {
    if (!editing) return;
    if (editingId) {
      await api.adminUpdateProject(editingId, editing);
    } else {
      await api.adminCreateProject(editing);
    }
    setEditing(null);
    setEditingId(null);
    load();
  }

  async function remove(id: string) {
    if (!confirm('确认删除项目？该操作不会影响代码文件。')) return;
    await api.adminDeleteProject(id);
    load();
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>项目管理</h2>
        <button className="btn" onClick={startNew}>
          新增项目
        </button>
      </div>

      {editing && (
        <div style={{ background: 'white', padding: 20, borderRadius: 8, marginBottom: 20, boxShadow: '0 1px 3px rgba(0,0,0,.06)' }}>
          <h3 style={{ marginTop: 0 }}>{editingId ? '编辑项目' : '新增项目'}</h3>
          <div className="form-grid">
            <label>
              名称
              <input
                value={editing.name}
                onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                placeholder="Order System"
              />
            </label>
            <label>
              本地路径
              <input
                value={editing.path}
                onChange={(e) => setEditing({ ...editing, path: e.target.value })}
                placeholder="/absolute/path/to/project"
              />
            </label>
            <label>
              项目介绍
              <textarea
                value={editing.description}
                onChange={(e) => setEditing({ ...editing, description: e.target.value })}
                placeholder="简要说明这个项目的业务定位、技术栈、主要模块，帮助助手快速定位上下文"
                style={{ minHeight: 120 }}
              />
            </label>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn" onClick={save}>
                保存
              </button>
              <button className="btn secondary" onClick={() => setEditing(null)}>
                取消
              </button>
            </div>
          </div>
        </div>
      )}

      <table>
        <thead>
          <tr>
            <th style={{ width: 180 }}>名称</th>
            <th style={{ width: 280 }}>路径</th>
            <th>介绍</th>
            <th style={{ width: 160 }}>操作</th>
          </tr>
        </thead>
        <tbody>
          {items.map((p) => (
            <tr key={p.id}>
              <td>{p.name}</td>
              <td style={{ fontFamily: 'ui-monospace, SFMono-Regular, monospace', fontSize: 12 }}>{p.path}</td>
              <td style={{ color: '#4b5563', whiteSpace: 'pre-wrap' }}>
                {p.description || <span style={{ color: '#9ca3af' }}>—</span>}
              </td>
              <td>
                <button className="btn secondary" onClick={() => startEdit(p)} style={{ marginRight: 8 }}>
                  编辑
                </button>
                <button className="btn danger" onClick={() => remove(p.id)}>
                  删除
                </button>
              </td>
            </tr>
          ))}
          {items.length === 0 && (
            <tr>
              <td colSpan={4} className="empty">
                暂无项目，点击“新增项目”开始
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
