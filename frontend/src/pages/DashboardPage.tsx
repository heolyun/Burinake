import { Link } from 'react-router-dom';

const availableItems = [
  { label: '화재 감지 요청', value: 'POST /api/v1/fire-detections' },
  { label: '이미지 저장', value: 'blobPath 응답 확인' },
  { label: 'YOLO 결과', value: 'detected / boxes' },
  { label: 'VLM 판단', value: 'riskLevel / message' },
];

const pendingItems = ['이슈 목록 조회 API', '이슈 상세 조회 API', '신고 목록 및 발송 API', '실시간 관제 WebSocket/SSE'];

export function DashboardPage() {
  return (
    <div className="page-stack">
      <section className="page-title split">
        <div>
          <h1>대시보드</h1>
        </div>
        <Link className="primary-button" to="/fire-detection">
          감지 테스트 열기
        </Link>
      </section>

      <section className="summary-grid">
        {availableItems.map((item) => (
          <article className="summary-card" key={item.label}>
            <span>{item.label}</span>
            <strong className="summary-text">{item.value}</strong>
          </article>
        ))}
      </section>

      <section className="dashboard-grid">
        <article className="panel wide">
          <div className="panel-title">
            <h2>현재 연결된 백엔드 기능</h2>
            <span>실제 API 기준</span>
          </div>
          <div className="compact-list single">
            <div>
              <strong>화재 감지 테스트</strong>
              <span>여러 snapshot 이미지를 선택하고 3초 간격으로 백엔드에 순차 전송합니다.</span>
            </div>
            <div>
              <strong>분석 응답 확인</strong>
              <span>status, fireDetected, riskLevel, yoloResult, vlmResult를 한 화면에서 확인합니다.</span>
            </div>
            <div>
              <strong>이슈 그룹핑 검증</strong>
              <span>같은 CCTV로 연속 요청을 보내 backend의 issue grouping 흐름을 확인할 수 있습니다.</span>
            </div>
          </div>
        </article>

        <aside className="panel">
          <div className="panel-title">
            <h2>아직 API가 필요한 화면</h2>
            <span>{pendingItems.length}개</span>
          </div>
          <div className="compact-list single">
            {pendingItems.map((item) => (
              <div key={item}>
                <strong>{item}</strong>
                <span>mock 데이터는 제거했고, 백엔드 조회 API가 생기면 연결합니다.</span>
              </div>
            ))}
          </div>
        </aside>
      </section>
    </div>
  );
}
