import { Link } from 'react-router-dom';

const availableItems = [
  { label: 'Snapshot 업로드', value: 'POST /api/v1/fire-detections' },
  { label: 'Storage 저장', value: '응답 blobPath 확인' },
  { label: 'YOLO 결과', value: 'yoloResult.detected / boxes' },
  { label: 'VLM 판단', value: 'vlmResult / riskLevel' },
];

const pendingItems = [
  '이슈 목록 조회 API',
  '이슈 상세 조회 API',
  '신고 목록 및 발송 API',
  '실시간 관제 WebSocket/SSE',
];

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
              <strong>화재 감지 요청</strong>
              <span>이미지와 CCTV 메타데이터를 multipart/form-data로 전송합니다.</span>
            </div>
            <div>
              <strong>응답 확인</strong>
              <span>status, fireDetected, riskLevel, yoloResult, vlmResult를 화면에 표시합니다.</span>
            </div>
            <div>
              <strong>3초 반복 전송</strong>
              <span>데모용 snapshot 연속 요청을 브라우저에서 실행할 수 있습니다.</span>
            </div>
          </div>
        </article>

        <aside className="panel">
          <div className="panel-title">
            <h2>아직 백엔드 API가 필요한 화면</h2>
            <span>{pendingItems.length}개</span>
          </div>
          <div className="compact-list single">
            {pendingItems.map((item) => (
              <div key={item}>
                <strong>{item}</strong>
                <span>목업 데이터는 제거하고 준비 중 상태로 둡니다.</span>
              </div>
            ))}
          </div>
        </aside>
      </section>
    </div>
  );
}
