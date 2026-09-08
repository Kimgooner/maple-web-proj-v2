import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { pushRecent } from '../lib/recent';

export function SearchForm({ autoFocus }: { autoFocus?: boolean }) {
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
      <label className="sr-only" htmlFor="character-input">캐릭터 닉네임</label>
      <span aria-hidden="true" className="search-icon">⌕</span>
      <input
        id="character-input"
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
