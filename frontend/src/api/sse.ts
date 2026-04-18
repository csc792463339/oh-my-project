import { fetchEventSource } from '@microsoft/fetch-event-source';
import { getUserId } from './user';

export interface SseHandlers {
  onMessage: (delta: string) => void;
  onStatus?: (data: Record<string, unknown>) => void;
  onDone: () => void;
  onError: (msg: string) => void;
}

export function streamChat(
  sessionId: string,
  message: string,
  attachmentIds: string[],
  handlers: SseHandlers,
  signal: AbortSignal,
): Promise<void> {
  return fetchEventSource('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': getUserId(),
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ sessionId, message, attachmentIds }),
    signal,
    openWhenHidden: true,
    onmessage(ev) {
      const data = safeParse(ev.data);
      switch (ev.event) {
        case 'message':
          handlers.onMessage(String(data.delta ?? ''));
          break;
        case 'status':
          handlers.onStatus?.(data);
          break;
        case 'done':
          handlers.onDone();
          break;
        case 'error':
          handlers.onError(String(data.message ?? 'unknown error'));
          break;
      }
    },
    onerror(err) {
      handlers.onError(err?.message ?? 'connection error');
      throw err; // 告诉 fetchEventSource 不再重试
    },
  });
}

function safeParse(s: string): Record<string, unknown> {
  try {
    return JSON.parse(s);
  } catch {
    return {};
  }
}
