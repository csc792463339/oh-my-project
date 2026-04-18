import { getUserId } from './user';

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('X-User-Id', getUserId());
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const resp = await fetch(path, { ...init, headers, credentials: 'include' });
  if (!resp.ok) {
    let msg = `HTTP ${resp.status}`;
    try {
      const j = await resp.json();
      msg = j.message || msg;
    } catch {}
    throw new Error(msg);
  }
  if (resp.status === 204) return undefined as T;
  return resp.json();
}

export const api = {
  // 用户端
  listProjects: () => request<{ id: string; name: string }[]>('/api/projects'),
  listSessions: () =>
    request<{ sessionId: string; title: string; projectId: string; lastActiveAt: number }[]>(
      '/api/sessions',
    ),
  createSession: (projectId: string, title?: string) =>
    request<Session>('/api/sessions', {
      method: 'POST',
      body: JSON.stringify({ projectId, title }),
    }),
  getSession: (id: string) => request<Session>(`/api/sessions/${id}`),
  deleteSession: (id: string) => request<void>(`/api/sessions/${id}`, { method: 'DELETE' }),

  // 管理员
  adminLogin: (password: string) =>
    request<{ ok: boolean }>('/api/admin/login', {
      method: 'POST',
      body: JSON.stringify({ password }),
    }),
  adminLogout: () => request<{ ok: boolean }>('/api/admin/logout', { method: 'POST' }),
  adminMe: () => request<{ admin: boolean }>('/api/admin/me'),

  adminListProjects: () => request<Project[]>('/api/admin/projects'),
  adminCreateProject: (p: ProjectInput) =>
    request<Project>('/api/admin/projects', { method: 'POST', body: JSON.stringify(p) }),
  adminUpdateProject: (id: string, p: ProjectInput) =>
    request<Project>(`/api/admin/projects/${id}`, { method: 'PUT', body: JSON.stringify(p) }),
  adminDeleteProject: (id: string) =>
    request<void>(`/api/admin/projects/${id}`, { method: 'DELETE' }),

  adminListSessions: () =>
    request<
      {
        sessionId: string;
        title: string;
        userId: string;
        projectId: string;
        lastActiveAt: number;
      }[]
    >('/api/admin/sessions'),
  adminGetSession: (id: string) => request<Session>(`/api/admin/sessions/${id}`),

  getFileContent: (sessionId: string, path: string) =>
    request<FileContent>(
      `/api/files?sessionId=${encodeURIComponent(sessionId)}&path=${encodeURIComponent(path)}`,
    ),

  uploadAttachment: (sessionId: string, file: File, signal?: AbortSignal) => {
    const fd = new FormData();
    fd.append('sessionId', sessionId);
    fd.append('file', file);
    return fetch('/api/uploads', {
      method: 'POST',
      body: fd,
      headers: { 'X-User-Id': getUserId() },
      credentials: 'include',
      signal,
    }).then(async (r) => {
      if (!r.ok) {
        let msg = `HTTP ${r.status}`;
        try {
          const j = await r.json();
          msg = j.message || msg;
        } catch {}
        throw new Error(msg);
      }
      return (await r.json()) as AttachmentMeta;
    });
  },

  attachmentRawUrl: (id: string) => `/api/uploads/${encodeURIComponent(id)}/raw`,
};

export interface AttachmentMeta {
  id: string;
  sessionId: string;
  originalName: string;
  storedFilename: string;
  mimeType: string;
  kind: 'IMAGE' | 'TEXT';
  size: number;
  createdAt: number;
  boundToMessage?: boolean | null;
}

export interface FileContent {
  path: string;
  content: string;
  size: number;
  truncated: boolean;
}

export interface Project {
  id: string;
  name: string;
  path: string;
  description: string;
  createdAt: number;
  updatedAt: number;
}

export interface ProjectInput {
  name: string;
  path: string;
  description: string;
}

export interface Session {
  sessionId: string;
  userId: string;
  projectId: string;
  projectPath: string;
  codexThreadId: string | null;
  title: string;
  messages: {
    role: 'user' | 'assistant';
    content: string;
    ts: number;
    attachments?: AttachmentMeta[] | null;
  }[];
  createdAt: number;
  lastActiveAt: number;
}
