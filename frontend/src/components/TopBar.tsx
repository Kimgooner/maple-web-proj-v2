import { Link } from 'react-router-dom';
import { BrandMark } from './BrandMark';
import { SearchForm } from './SearchForm';

/** 모든 화면 위에 있는 줄. 브랜드와 검색창. */
export function TopBar() {
  return (
    <header className="topbar">
      <Link to="/" className="brand" aria-label="MapleDelta 홈">
        <span className="brand-mark" aria-hidden="true"><BrandMark /></span>MapleDelta
      </Link>
      <span className="brand-caption">캐릭터의 변화를 읽다</span>
      <SearchForm />
    </header>
  );
}
