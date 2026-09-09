/**
 * 랜딩 아래에 붙는 소개와 자주 나올 질문.
 *
 * <p>여기 적는 숫자와 규칙은 전부 근거가 있어야 한다. 15분·오전 2시는 OPEN API
 * 공식 반영 주기이고, 정확도는 레벨 260 이상 표본 대조에서 나온 값이다.
 * 근거 없이 "거의 정확합니다" 같은 말을 늘리지 않는다.
 */
const FAQ: { q: string; lines: string[] }[] = [
  {
    q: '게임 안에서 보는 전투력과 왜 다른가요?',
    lines: [
      '보스용 프리셋 기준으로 전투력을 계산합니다. 혹시 재획용 프리셋을 착용 중이신가요?',
    ],
  },
  {
    q: '계산이 얼마나 맞나요?',
    lines: [
      '260레벨 이상 캐릭터 랜덤 표본 10000명(직업당 200명 이상)으로 대조한 결과, 99% 이상이 인 게임 전투력(보스용 프리셋 장착 기준)과 오차 없이 동일합니다.',
      '다만 데몬 어벤져는 아직 조금 확인 중에 있고, 레벨 260 미만의 캐릭터는 변수가 많아 지원하지 않습니다.',
      '화살이나 표창, 불릿을 사용하는 직업은 각각 티타늄 화살, 플레임 표창, 자이언트 불릿 기준입니다.',
    ],
  },
  {
    q: '방금 장비를 바꿨는데 반영이 안 돼요',
    lines: [
      '넥슨 API는 평균적으로 15분 뒤에 반영됩니다. 캐시샵에 들어갔다 나오는 걸로 바로 반영이 가능합니다.',
      '하루 전의 기록은 다음 날 02시에 반영됩니다.',
    ],
  },
  {
    q: '조각을 썼는데 반영이 안 돼요',
    lines: [
      '솔 야누스는 전투에 들어가지 않아 조각 사용량에서 빼고 셉니다. 그 코어에 쓴 조각은 그래프에도 변경 내역에도 나오지 않습니다.',
      '이벤트로 받은 코어 레벨도 조각이 들지 않아 세지 않습니다.',
    ],
  },
  {
    q: '어떤 날만 값이 뚝 떨어져요',
    lines: [
      '가끔 보스용 프리셋과 다른 프리셋의 점수가 같아 특정 날짜에 평소와는 다른 프리셋이 적용될 수도 있습니다. 해당 일자는 수동으로 프리셋 수정을 부탁 드릴게요.',
    ],
  },
];

export function AboutPanel() {
  return (
    <section className="about panel" aria-labelledby="about-title">
      <div className="about-inner">
        <h2 id="about-title">MapleDelta는 어떤 서비스인가요?</h2>
        <p className="about-lead">
          캐릭터의 전투력이 <b>언제, 무엇 때문에</b> 변했는지 보여 줍니다.
          넥슨 OPEN API의 원본을 받아 보스 프리셋 기준으로 전투력을 다시 계산하고,
          두 시점 사이에 바뀐 장비·스킬·심볼·세트·유니온을 이름 단위로 풀어 놓습니다.
        </p>
        <div className="faq">
          {FAQ.map(({ q, lines }) => (
            <details className="faq-item" key={q}>
              <summary>{q}<span className="chevron" aria-hidden="true">›</span></summary>
              <div className="faq-answer">
                {lines.map((line) => <p key={line}>{line}</p>)}
              </div>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
