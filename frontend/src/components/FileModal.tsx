import { useEffect, useState } from 'react';
import { api, FileContent } from '../api/client';

interface Props {
  sessionId: string;
  path: string;
  onClose: () => void;
}

export default function FileModal({ sessionId, path, onClose }: Props) {
  const [data, setData] = useState<FileContent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    setData(null);
    api
      .getFileContent(sessionId, path)
      .then((d) => {
        if (alive) setData(d);
      })
      .catch((e: Error) => {
        if (alive) setError(e.message || '读取失败');
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [sessionId, path]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="file-modal-overlay" onClick={onClose}>
      <div className="file-modal" onClick={(e) => e.stopPropagation()}>
        <header className="file-modal-header">
          <span className="file-modal-path" title={path}>
            {path}
          </span>
          <div className="file-modal-actions">
            {data && (
              <span className="file-modal-meta">
                {formatSize(data.size)}
                {data.truncated && ' · 已截断'}
              </span>
            )}
            <button className="file-modal-close" onClick={onClose} title="关闭 (Esc)">
              ×
            </button>
          </div>
        </header>
        <div className="file-modal-body">
          {loading && <div className="file-modal-status">加载中…</div>}
          {error && <div className="file-modal-status error">{error}</div>}
          {data && (
            <pre className="file-modal-content">
              <code>{data.content}</code>
            </pre>
          )}
        </div>
      </div>
    </div>
  );
}

function formatSize(n: number): string {
  if (n < 1024) return n + ' B';
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
  return (n / (1024 * 1024)).toFixed(2) + ' MB';
}
