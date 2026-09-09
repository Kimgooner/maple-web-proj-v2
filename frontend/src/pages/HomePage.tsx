import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AboutPanel } from '../components/AboutPanel';
import { SearchForm } from '../components/SearchForm';
import { TopBar } from '../components/TopBar';
import { loadRecent, pushRecent } from '../lib/recent';

export function HomePage() {
  const [recent] = useState(loadRecent);
  const navigate = useNavigate();

  const go = (name: string) => {
    pushRecent(name);
    navigate(`/c/${encodeURIComponent(name)}`);
  };

  return (
    <>
      <TopBar />
      <main className="page">
        <div className="breadcrumb"><span aria-hidden="true">⌂</span><span>/</span>캐릭터 분석</div>
        <section className="welcome panel">
          <span className="eyebrow">MY CHARACTER, MY HISTORY</span>
          <h1>어제보다 강해진 나,<br />무엇이 달라졌을까요?</h1>
          <p>
            보스 프리셋 기준의 실전 전투력부터 장비와 스킬의 변화까지.<br />
            캐릭터 닉네임을 검색하고 성장의 기록을 살펴보세요.
          </p>
          {/* 위 줄에도 검색창이 있지만 그쪽은 navbar 다. 여기서 바로 칠 수 있어야 한다. */}
          <div className="welcome-search">
            <SearchForm id="home-character-input" />
          </div>
          {recent.length > 0 && (
            <div className="recent">
              <span className="recent-label">최근 조회</span>
              {recent.map((name) => (
                <button type="button" className="chip-btn" key={name} onClick={() => go(name)}>{name}</button>
              ))}
            </div>
          )}
          <div className="welcome-steps">
            <span><b>01</b> 전투력 추이</span>
            <span><b>02</b> 두 시점 비교</span>
            <span><b>03</b> 변경 내역 확인</span>
          </div>
        </section>
        <AboutPanel />
        <footer>
          <span>Data based on NEXON Open API</span>
          <span>MapleDelta <span className="footer-dot">·</span> 성장의 순간을 기록하다</span>
        </footer>
      </main>
    </>
  );
}
