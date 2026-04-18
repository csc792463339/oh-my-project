import { ClipboardEvent, DragEvent, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, AttachmentMeta, Session } from '../api/client';
import { streamChat } from '../api/sse';
import MarkdownView from '../components/MarkdownView';
import StatusIndicator from '../components/StatusIndicator';
import AttachmentChip, { PendingUpload } from '../components/AttachmentChip';
import { randomUUID } from '../utils/uuid';

interface SessionSummary {
  sessionId: string;
  title: string;
  projectId: string;
  lastActiveAt: number;
}

interface ProjectBrief {
  id: string;
  name: string;
}

interface Bubble {
  role: 'user' | 'assistant' | 'error';
  content: string;
  attachments?: AttachmentMeta[];
}

type Phase = 'idle' | 'thinking' | 'analyzing' | 'writing';

const IMAGE_EXTS = ['.png', '.jpg', '.jpeg', '.webp', '.gif'];
const TEXT_EXTS = ['.txt', '.log', '.json', '.md', '.csv', '.xml', '.yaml', '.yml', '.ini', '.conf'];
const ALLOWED_EXTS = [...IMAGE_EXTS, ...TEXT_EXTS];
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_FILES_PER_MESSAGE = 6;

function extOf(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot >= 0 ? name.substring(dot).toLowerCase() : '';
}

export default function ChatPage() {
  const [projects, setProjects] = useState<ProjectBrief[]>([]);
  const [projectId, setProjectId] = useState<string>('');
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [currentSession, setCurrentSession] = useState<Session | null>(null);
  const [draft, setDraft] = useState('');
  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [phase, setPhase] = useState<Phase>('idle');
  const [pending, setPending] = useState<PendingUpload[]>([]);
  const [dragOver, setDragOver] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const sessionRef = useRef<Session | null>(null);

  const streaming = phase !== 'idle';
  const uploadsInFlight = pending.some((p) => p.status === 'uploading');

  useEffect(() => { sessionRef.current = currentSession; }, [currentSession]);

  useEffect(() => {
    api
      .listProjects()
      .then((ps) => {
        setProjects(ps);
        if (ps.length > 0) setProjectId((pid) => pid || ps[0].id);
      })
      .catch(() => setProjects([]));
    refreshSessions();
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [bubbles, phase]);

  useEffect(() => {
    return () => {
      pending.forEach((p) => {
        if (p.previewUrl) URL.revokeObjectURL(p.previewUrl);
      });
    };
  }, []);

  function refreshSessions() {
    api
      .listSessions()
      .then(setSessions)
      .catch(() => setSessions([]));
  }

  function clearPending() {
    pending.forEach((p) => {
      if (p.status === 'uploading') p.abortCtrl.abort();
      if (p.previewUrl) URL.revokeObjectURL(p.previewUrl);
    });
    setPending([]);
  }

  async function ensureSession(): Promise<Session | null> {
    if (sessionRef.current) return sessionRef.current;
    if (!projectId) {
      alert('请先选择项目');
      return null;
    }
    const s = await api.createSession(projectId);
    setCurrentSession(s);
    sessionRef.current = s;
    refreshSessions();
    return s;
  }

  async function openSession(id: string) {
    if (streaming) return;
    clearPending();
    const s = await api.getSession(id);
    setCurrentSession(s);
    setProjectId(s.projectId);
    setBubbles(
      s.messages.map((m) => ({
        role: m.role,
        content: m.content,
        attachments: m.attachments ?? undefined,
      })),
    );
  }

  async function newSession() {
    if (!projectId) {
      alert('请先选择项目');
      return;
    }
    clearPending();
    const s = await api.createSession(projectId);
    setCurrentSession(s);
    setBubbles([]);
    refreshSessions();
  }

  async function deleteSession(id: string) {
    if (!confirm('删除该会话？')) return;
    await api.deleteSession(id);
    if (currentSession?.sessionId === id) {
      setCurrentSession(null);
      setBubbles([]);
      clearPending();
    }
    refreshSessions();
  }

  async function addFiles(files: File[]) {
    if (files.length === 0) return;
    const existing = pending.length;
    const slots = MAX_FILES_PER_MESSAGE - existing;
    if (slots <= 0) {
      alert(`最多只能附加 ${MAX_FILES_PER_MESSAGE} 个文件`);
      return;
    }
    const accepted: File[] = [];
    for (const file of files.slice(0, slots)) {
      const ext = extOf(file.name);
      if (!ALLOWED_EXTS.includes(ext)) {
        alert(`不支持的文件类型：${file.name}`);
        continue;
      }
      if (file.size > MAX_FILE_BYTES) {
        alert(`文件过大（>10MB）：${file.name}`);
        continue;
      }
      accepted.push(file);
    }
    if (accepted.length === 0) return;

    const session = await ensureSession();
    if (!session) return;

    const newItems: PendingUpload[] = accepted.map((file) => {
      const isImg = IMAGE_EXTS.includes(extOf(file.name));
      return {
        tempId: randomUUID(),
        file,
        previewUrl: isImg ? URL.createObjectURL(file) : undefined,
        status: 'uploading',
        abortCtrl: new AbortController(),
      };
    });
    setPending((prev) => [...prev, ...newItems]);

    newItems.forEach((item) => uploadOne(session.sessionId, item));
  }

  async function uploadOne(sessionId: string, item: PendingUpload) {
    try {
      const meta = await api.uploadAttachment(sessionId, item.file, item.abortCtrl.signal);
      setPending((prev) =>
        prev.map((p) => (p.tempId === item.tempId ? { ...p, status: 'done', meta } : p)),
      );
    } catch (e) {
      if (item.abortCtrl.signal.aborted) return;
      const msg = (e as Error).message ?? '上传失败';
      setPending((prev) =>
        prev.map((p) => (p.tempId === item.tempId ? { ...p, status: 'failed', error: msg } : p)),
      );
    }
  }

  function retryUpload(tempId: string) {
    const session = sessionRef.current;
    if (!session) return;
    const item = pending.find((p) => p.tempId === tempId);
    if (!item) return;
    const newCtrl = new AbortController();
    const refreshed: PendingUpload = {
      ...item,
      status: 'uploading',
      error: undefined,
      abortCtrl: newCtrl,
    };
    setPending((prev) => prev.map((p) => (p.tempId === tempId ? refreshed : p)));
    uploadOne(session.sessionId, refreshed);
  }

  function removePending(tempId: string) {
    setPending((prev) => {
      const target = prev.find((p) => p.tempId === tempId);
      if (target) {
        if (target.status === 'uploading') target.abortCtrl.abort();
        if (target.previewUrl) URL.revokeObjectURL(target.previewUrl);
      }
      return prev.filter((p) => p.tempId !== tempId);
    });
  }

  function handleFilesSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const list = e.target.files;
    if (!list) return;
    addFiles(Array.from(list));
    e.target.value = '';
  }

  function handlePaste(e: ClipboardEvent<HTMLTextAreaElement>) {
    const files: File[] = [];
    for (const item of Array.from(e.clipboardData.items)) {
      if (item.kind === 'file') {
        const f = item.getAsFile();
        if (f) files.push(f);
      }
    }
    if (files.length > 0) {
      e.preventDefault();
      addFiles(files);
    }
  }

  function handleDrop(e: DragEvent<HTMLElement>) {
    e.preventDefault();
    setDragOver(false);
    const files = Array.from(e.dataTransfer.files ?? []);
    if (files.length > 0) addFiles(files);
  }

  async function send() {
    const text = draft.trim();
    if (!text || streaming) return;
    if (uploadsInFlight) return;

    const session = await ensureSession();
    if (!session) return;

    const readyAttachments = pending
      .filter((p) => p.status === 'done' && p.meta)
      .map((p) => p.meta!);
    const attachmentIds = readyAttachments.map((m) => m.id);

    pending.forEach((p) => {
      if (p.previewUrl) URL.revokeObjectURL(p.previewUrl);
    });

    setDraft('');
    setPending([]);
    setBubbles((prev) => [
      ...prev,
      { role: 'user', content: text, attachments: readyAttachments.length > 0 ? readyAttachments : undefined },
    ]);
    setPhase('thinking');

    const ctrl = new AbortController();
    abortRef.current = ctrl;

    try {
      await streamChat(
        session.sessionId,
        text,
        attachmentIds,
        {
          onMessage(delta) {
            setPhase('writing');
            setBubbles((prev) => {
              const next = [...prev];
              const last = next[next.length - 1];
              if (last?.role === 'assistant') {
                next[next.length - 1] = {
                  role: 'assistant',
                  content: last.content ? last.content + '\n\n' + delta : delta,
                };
              } else {
                next.push({ role: 'assistant', content: delta });
              }
              return next;
            });
          },
          onStatus(data) {
            if (data.phase === 'analyzing') setPhase('analyzing');
            else if (data.phase === 'started') setPhase('thinking');
          },
          onDone() {
            setPhase('idle');
            refreshSessions();
          },
          onError(msg) {
            setBubbles((prev) => [...prev, { role: 'error', content: msg }]);
            setPhase('idle');
            refreshSessions();
          },
        },
        ctrl.signal,
      );
    } catch (e) {
      if (!ctrl.signal.aborted) {
        setBubbles((prev) => [
          ...prev,
          { role: 'error', content: (e as Error).message ?? '请求失败' },
        ]);
      }
      setPhase('idle');
    }
  }

  function cancel() {
    abortRef.current?.abort();
    setPhase('idle');
  }

  const headerTitle = useMemo(() => {
    if (!currentSession) return '新会话';
    return currentSession.title || '新会话';
  }, [currentSession]);

  const headerProject = useMemo(() => {
    if (!currentSession) return null;
    return projects.find((p) => p.id === currentSession.projectId)?.name;
  }, [currentSession, projects]);

  return (
    <div className="layout">
      <aside className={'sidebar' + (sidebarOpen ? ' open' : '')}>
        <header>
          <select value={projectId} onChange={(e) => setProjectId(e.target.value)}>
            <option value="">选择项目…</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          <button className="new-btn" onClick={newSession} disabled={streaming} title="新建会话">
            +
          </button>
        </header>
        {sessions.length > 0 && <div className="section-label">近期会话</div>}
        <div className="session-list">
          {sessions.map((s) => (
            <div
              key={s.sessionId}
              className={'session-item' + (currentSession?.sessionId === s.sessionId ? ' active' : '')}
              onClick={() => { openSession(s.sessionId); setSidebarOpen(false); }}
            >
              <span className="title">{s.title || '无标题'}</span>
              <button
                className="del"
                onClick={(e) => {
                  e.stopPropagation();
                  deleteSession(s.sessionId);
                }}
                title="删除会话"
              >
                ×
              </button>
            </div>
          ))}
          {sessions.length === 0 && (
            <div className="empty" style={{ padding: '32px 16px', fontSize: 13 }}>
              暂无会话
              <br />
              点右上角 + 新建
            </div>
          )}
        </div>
        <footer>
          <span>Oh My Project</span>
          <Link to="/admin/login">管理后台</Link>
        </footer>
      </aside>

      {sidebarOpen && (
        <div
          className="sidebar-backdrop"
          role="presentation"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <section
        className={'main' + (dragOver ? ' drag-over' : '')}
        onDragOver={(e) => {
          if (Array.from(e.dataTransfer.types).includes('Files')) {
            e.preventDefault();
            setDragOver(true);
          }
        }}
        onDragLeave={(e) => {
          if (e.currentTarget === e.target) setDragOver(false);
        }}
        onDrop={handleDrop}
      >
        <header>
          <button
            type="button"
            className="sidebar-toggle"
            aria-label="打开侧边栏"
            onClick={() => setSidebarOpen(true)}
          >
            ☰
          </button>
          <span>{headerTitle}</span>
          {headerProject && <span className="header-badge">{headerProject}</span>}
        </header>
        <div className="messages">
          <div className="messages-inner">
            {bubbles.map((b, i) => (
              <div key={i} className={`bubble ${b.role}`}>
                {b.attachments && b.attachments.length > 0 && (
                  <div className="attachments">
                    {b.attachments.map((a) => (
                      <AttachmentChip key={a.id} meta={a} />
                    ))}
                  </div>
                )}
                {b.role === 'assistant' ? (
                  <MarkdownView text={b.content} sessionId={currentSession?.sessionId} />
                ) : (
                  b.content
                )}
              </div>
            ))}
            {streaming && <StatusIndicator phase={phase} />}
            {bubbles.length === 0 && !streaming && (
              <div className="empty">
                <div className="empty-title">开始与代码对话</div>
                <div className="empty-hint">
                  选择一个项目，问任何你想了解的问题。
                  <br />
                  比如：“用户注册流程是怎么实现的？”
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
        </div>
        <div className="composer">
          <div className="composer-inner">
            {pending.length > 0 && (
              <div className="composer-attachments">
                {pending.map((p) => (
                  <AttachmentChip
                    key={p.tempId}
                    pending={p}
                    onRemove={() => removePending(p.tempId)}
                    onRetry={p.status === 'failed' ? () => retryUpload(p.tempId) : undefined}
                  />
                ))}
              </div>
            )}
            <div className="composer-row">
              <button
                className="attach-btn"
                type="button"
                title="上传图片或文本文件"
                onClick={() => fileInputRef.current?.click()}
                disabled={streaming}
                aria-label="上传文件"
              >
                📎
              </button>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept={ALLOWED_EXTS.join(',')}
                style={{ display: 'none' }}
                onChange={handleFilesSelected}
              />
              <textarea
                rows={1}
                value={draft}
                placeholder="向 Oh My Project 提问…"
                onChange={(e) => setDraft(e.target.value)}
                onPaste={handlePaste}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    send();
                  }
                }}
                disabled={streaming}
              />
              {streaming ? (
                <button className="stop" onClick={cancel}>
                  停止
                </button>
              ) : (
                <button onClick={send} disabled={!draft.trim() || uploadsInFlight}>
                  发送
                </button>
              )}
            </div>
          </div>
          <div className="composer-hint">
            Enter 发送 · Shift + Enter 换行 · 支持拖拽 / 粘贴文件（图片、.log、.json 等）
          </div>
        </div>
      </section>
    </div>
  );
}
