import { Link } from 'react-router-dom';
import { LogoIcon } from './icons';
import { SearchForm } from './SearchForm';

export function TopBar({ withSearch }: { withSearch?: boolean }) {
  return (
    <div className="topbar">
      <Link to="/" className="logo"><LogoIcon /><span>전투력 변화 뷰어</span></Link>
      {withSearch && <div className="topbar-search"><SearchForm size="sm" placeholder="다른 캐릭터 조회" /></div>}
    </div>
  );
}
