export function SettingsPage() {
  return (
    <div className="page-stack">
      <section className="page-title">
        <div>
          <h1>설정</h1>
        </div>
      </section>
      <section className="panel">
        <div className="panel-title">
          <h2>런타임 설정</h2>
          <span>환경변수 기준</span>
        </div>
        <dl className="result-card">
          <div>
            <dt>Frontend API base URL</dt>
            <dd>{import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}</dd>
          </div>
          <div>
            <dt>Fire detection endpoint</dt>
            <dd>/api/v1/fire-detections</dd>
          </div>
        </dl>
      </section>
    </div>
  );
}
