import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { pushRecent } from '../lib/recent';

/**
 * @param id 라벨이 가리킬 입력 칸의 id. 한 화면에 검색창이 둘이면 서로 달라야 한다 -
 *           같은 id 가 둘이면 라벨이 엉뚱한 칸을 가리키고 접근성 도구가 헷갈린다.
 */
export function SearchForm({ autoFocus, id = 'character-input' }: { autoFocus?: boolean; id?: string }) {
  const [value, setValue] = useState('');
  const navigate = useNavigate();

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const name = value.trim();
    if (!name) return;
    pushRecent(name);
    navigate(`/c/${encodeURIComponent(name)}`);
    setValue('');
  };

  return (
    <form className="search" role="search" onSubmit={submit}>
      <label className="sr-only" htmlFor={id}>캐릭터 닉네임</label>
      <span aria-hidden="true" className="search-icon">⌕</span>
      <input
        id={id}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder="캐릭터 닉네임 검색"
        autoComplete="off"
        maxLength={30}
        autoFocus={autoFocus}
      />
      <button type="submit" disabled={!value.trim()}>조회 <span aria-hidden="true">→</span></button>
    </form>
  );
}
