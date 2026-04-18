type Phase = 'idle' | 'thinking' | 'analyzing' | 'writing';

const PHASE_LABEL: Record<Exclude<Phase, 'idle'>, string> = {
  thinking: '正在思考…',
  analyzing: '正在分析代码…',
  writing: '正在整理答案…',
};

export default function StatusIndicator({ phase }: { phase: Phase }) {
  if (phase === 'idle') return null;
  const label = PHASE_LABEL[phase];
  return (
    <div className="status-card">
      <span className="spinner" />
      <span className="label">{label}</span>
      <span className="thinking-dots">
        <span />
        <span />
        <span />
      </span>
    </div>
  );
}
