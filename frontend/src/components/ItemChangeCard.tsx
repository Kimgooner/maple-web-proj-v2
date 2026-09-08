import type { SlotChange } from '../api/types';
import { CHANGE_TYPE_LABELS } from '../lib/labels';
import { ArrowRight, ItemPlaceholderIcon } from './icons';
import { StatPop } from './StatPop';
import { useHoverPop } from '../lib/useHoverPop';

/** 백엔드 슬롯 키는 "장비 - 무기" 처럼 접두사가 붙는다. 부위명만 남긴다 */
function bareSlot(slot: string | null): string | null {
  if (!slot) return null;
  const at = slot.lastIndexOf(' - ');
  return at >= 0 ? slot.slice(at + 3) : slot;
}

function slotLabel(change: SlotChange): string {
  const prev = bareSlot(change.previousSlot);
  const cur = bareSlot(change.currentSlot);
  if (prev && cur && prev !== cur) return `${prev} → ${cur}`;
  return cur ?? prev ?? bareSlot(change.slot) ?? '?';
}

function Side({ icon, name, kind }: { icon: string | null; name: string | null; kind: 'prev' | 'cur' }) {
  return (
    <div className={`item-side ${kind}`}>
      <div className="icon-box">{icon ? <img src={icon} alt="" /> : <ItemPlaceholderIcon />}</div>
      <span className="item-name" title={name ?? undefined}>{name ?? '없음'}</span>
    </div>
  );
}

export function ItemChangeCard({ change }: { change: SlotChange }) {
  const { anchor, popStyle, onMouseEnter } = useHoverPop();
  return (
    <div className="card item-card" ref={anchor} onMouseEnter={onMouseEnter}>
      <div className="item-head">
        <span className="item-slot">{slotLabel(change)}</span>
        <span className={`badge badge-${change.changeType}`}>{CHANGE_TYPE_LABELS[change.changeType] ?? change.changeType}</span>
      </div>
      <div className="item-body">
        <Side icon={change.previousItemIcon} name={change.previousItemName} kind="prev" />
        <ArrowRight />
        <Side icon={change.currentItemIcon} name={change.currentItemName} kind="cur" />
      </div>
      <StatPop groups={change.deltaGroups ?? []} style={popStyle} />
    </div>
  );
}
