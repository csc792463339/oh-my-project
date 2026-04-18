import { useState, MouseEvent } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import FileModal from './FileModal';

interface Props {
  text: string;
  sessionId?: string;
}

export default function MarkdownView({ text, sessionId }: Props) {
  const [openPath, setOpenPath] = useState<string | null>(null);

  function handleAnchorClick(e: MouseEvent<HTMLAnchorElement>, href: string | undefined) {
    if (!href || !sessionId) return;
    if (!isLikelyFilePath(href)) return;
    e.preventDefault();
    setOpenPath(stripLineAnchor(href));
  }

  return (
    <div className="markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a({ href, children, ...rest }) {
            const fileLike = sessionId && href && isLikelyFilePath(href);
            return (
              <a
                {...rest}
                href={href}
                className={fileLike ? 'file-ref' : undefined}
                onClick={(e) => handleAnchorClick(e, href)}
                target={fileLike ? undefined : '_blank'}
                rel={fileLike ? undefined : 'noreferrer'}
              >
                {children}
              </a>
            );
          },
        }}
      >
        {text}
      </ReactMarkdown>
      {openPath && sessionId && (
        <FileModal sessionId={sessionId} path={openPath} onClose={() => setOpenPath(null)} />
      )}
    </div>
  );
}

function isLikelyFilePath(href: string): boolean {
  if (!href) return false;
  const h = href.trim();
  if (!h) return false;
  if (/^(https?:|mailto:|tel:|ftp:|data:|javascript:|vscode:|file:)/i.test(h)) return false;
  if (h.startsWith('#')) return false;
  if (h.startsWith('//')) return false;
  return true;
}

function stripLineAnchor(href: string): string {
  let h = href.trim();
  while (h.startsWith('/')) h = h.substring(1);
  const hash = h.indexOf('#');
  if (hash >= 0) h = h.substring(0, hash);
  const q = h.indexOf('?');
  if (q >= 0) h = h.substring(0, q);
  h = h.replace(/:\d+(-\d+)?$/, '');
  return h;
}
