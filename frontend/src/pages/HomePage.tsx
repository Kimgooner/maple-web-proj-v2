import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogoIcon } from '../components/icons';
import { NexonNotice } from '../components/NexonNotice';
import { SearchForm } from '../components/SearchForm';
import { loadRecent, pushRecent } from '../lib/recent';

export function HomePage() {
  const [recent] = useState(loadRecent);
  const navigate = useNavigate();

  const go = (name: string) => {
    pushRecent(name);
    navigate(`/c/${encodeURIComponent(name)}`);
  };

  return (
    <div className="home">
      <div className="topbar" style={{ borderBottom: 0 }}>
        <span className="logo"><LogoIcon /><span>전투력 변화 뷰어</span></span>
      </div>
      <div className="home-body">
        <div>
          <h1 className="home-title">내 전투력, 언제 무엇 때문에 변했나</h1>
          <p className="home-sub" style={{ marginTop: 14 }}>
            보스 프리셋 기준으로 다시 계산한 실전 전투력의 이력과, 두 시점 사이에 바뀐 장비·스탯을 보여줍니다.
          </p>
        </div>
        <div className="home-form">
          <SearchForm size="lg" autoFocus />
        </div>
        {recent.length > 0 && (
          <div className="recent">
            <span className="recent-label">최근 조회</span>
            {recent.map((name) => (
              <button type="button" className="chip-btn" key={name} onClick={() => go(name)}>{name}</button>
            ))}
          </div>
        )}
      </div>
      <NexonNotice />
    </div>
  );
}
