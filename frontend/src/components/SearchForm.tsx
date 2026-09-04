import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { SearchIcon } from './icons';
import { pushRecent } from '../lib/recent';

interface Props {
  size: 'lg' | 'sm';
  placeholder?: string;
  autoFocus?: boolean;
}

export function SearchForm({ size, placeholder = '캐릭터 닉네임', autoFocus }: Props) {
  const [value, setValue] = useState('');
  const navigate = useNavigate();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const name = value.trim();
    if (!name) return;
    pushRecent(name);
    navigate(`/c/${encodeURIComponent(name)}`);
    setValue('');
  };

  return (
    <form className={`search search-${size}`} onSubmit={submit}>
      <SearchIcon />
      <input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={placeholder}
        autoFocus={autoFocus}
        autoComplete="off"
        aria-label="캐릭터 닉네임"
      />
      <button type="submit" className="btn-primary" disabled={!value.trim()}>조회</button>
    </form>
  );
}
