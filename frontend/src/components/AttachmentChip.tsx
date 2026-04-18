import { api, AttachmentMeta } from '../api/client';

export interface PendingUpload {
  tempId: string;
  file: File;
  previewUrl?: string;
  status: 'uploading' | 'done' | 'failed';
  meta?: AttachmentMeta;
  error?: string;
  abortCtrl: AbortController;
}

interface Props {
  pending?: PendingUpload;
  meta?: AttachmentMeta;
  onRemove?: () => void;
  onRetry?: () => void;
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

function isImage(meta?: AttachmentMeta, file?: File): boolean {
  if (meta) return meta.kind === 'IMAGE';
  if (file) return file.type.startsWith('image/');
  return false;
}

export default function AttachmentChip({ pending, meta, onRemove, onRetry }: Props) {
  const effectiveMeta = pending?.meta ?? meta;
  const image = isImage(effectiveMeta, pending?.file);
  const name = effectiveMeta?.originalName ?? pending?.file.name ?? '';
  const size = effectiveMeta?.size ?? pending?.file.size ?? 0;
  const previewSrc = pending?.previewUrl
    ?? (effectiveMeta ? api.attachmentRawUrl(effectiveMeta.id) : undefined);

  const statusClass = pending
    ? pending.status === 'uploading'
      ? ' uploading'
      : pending.status === 'failed'
        ? ' failed'
        : ''
    : '';

  const kindClass = image ? ' image' : ' text';

  const clickable = !pending && effectiveMeta;

  return (
    <div className={`attachment-chip${kindClass}${statusClass}`}>
      {image && previewSrc ? (
        <a
          className="thumb-link"
          href={clickable ? api.attachmentRawUrl(effectiveMeta!.id) : undefined}
          target="_blank"
          rel="noreferrer"
          onClick={(e) => { if (!clickable) e.preventDefault(); }}
          title={name}
        >
          <img src={previewSrc} alt={name} />
        </a>
      ) : (
        <div className="file-card">
          <div className="file-icon" aria-hidden="true">
            <svg width="14" height="16" viewBox="0 0 14 16" xmlns="http://www.w3.org/2000/svg">
              <path
                d="M2 1.5A1.5 1.5 0 0 1 3.5 0H8.5L13 4.5V14.5A1.5 1.5 0 0 1 11.5 16H3.5A1.5 1.5 0 0 1 2 14.5V1.5Z"
                fill="currentColor"
                fillOpacity="0.25"
              />
              <path
                d="M8.5 0V4.5H13"
                stroke="currentColor"
                strokeOpacity="0.55"
                strokeWidth="1"
                fill="none"
              />
            </svg>
          </div>
          <div className="file-meta">
            <div className="name" title={name}>{name}</div>
            <div className="size">{formatSize(size)}</div>
          </div>
        </div>
      )}

      {pending?.status === 'uploading' && <div className="chip-spinner" />}
      {pending?.status === 'failed' && (
        <div className="chip-error">
          <span title={pending.error}>上传失败</span>
          {onRetry && <button className="chip-btn" onClick={onRetry}>重试</button>}
        </div>
      )}
      {onRemove && (
        <button className="chip-remove" onClick={onRemove} title="移除" aria-label="移除">
          ×
        </button>
      )}
    </div>
  );
}
