import { Link, useParams } from 'react-router-dom';

export function IssueDetailPage() {
  const { issueId } = useParams();

  return (
    <div className="page-stack">
      <section className="page-title split">
        <div>
          <Link className="text-link" to="/issues">
            이슈 관리
          </Link>
          <h1>Issue #{issueId}</h1>
        </div>
      </section>

      <section className="panel">
        <div className="panel-title">
          <h2>상세 API 준비 중</h2>
          <span>mock 제거됨</span>
        </div>
        <p className="muted">현재 백엔드는 감지 요청 응답으로 분석 결과를 반환합니다. 이슈 상세 조회 API가 추가되면 이 화면을 실제 데이터로 연결할 수 있습니다.</p>
      </section>
    </div>
  );
}
