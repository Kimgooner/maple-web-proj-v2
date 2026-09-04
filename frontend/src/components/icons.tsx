const stroke = { fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round' } as const;

export function LogoIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" {...stroke} style={{ color: 'var(--accent)' }}>
      <path d="M3 17l5-6 4 4 6-8 3 3" /><path d="M3 21h18" />
    </svg>
  );
}

export function SearchIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" {...stroke} style={{ color: 'var(--muted)' }}>
      <circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" />
    </svg>
  );
}

export function ArrowRight({ color = 'var(--muted)' }: { color?: string }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" {...stroke} style={{ color, flexShrink: 0 }}>
      <path d="M5 12h14" /><path d="M13 6l6 6-6 6" />
    </svg>
  );
}

export function PersonIcon() {
  return (
    <svg width="40" height="40" viewBox="0 0 24 24" {...stroke} strokeWidth={1.6} style={{ color: 'var(--muted)' }}>
      <circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7" />
    </svg>
  );
}

export function ItemPlaceholderIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" {...stroke} strokeWidth={1.8} style={{ color: 'var(--muted)' }}>
      <path d="M4 20l7-7" /><path d="M9 9l6 6" /><path d="M14 4l6 6-4 4-6-6z" />
    </svg>
  );
}
