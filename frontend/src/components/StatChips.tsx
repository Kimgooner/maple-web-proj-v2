import type { StatDelta } from '../api/types';
import { formatStatDelta } from '../lib/format';
import { statLabel } from '../lib/labels';

export function StatChips({ deltas }: { deltas: StatDelta[] }) {
  if (deltas.length === 0) return null;
  return (
    <div className="chips">
      {deltas.map((d) => (
        <span className="chip" key={d.statName}>
          <span className="label">{statLabel(d.statName)}</span>
          <span className={`mono ${d.delta > 0 ? 'up' : d.delta < 0 ? 'down' : 'muted'}`}>{formatStatDelta(d.statName, d.delta)}</span>
        </span>
      ))}
    </div>
  );
}
