import { type ChangeEvent, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getIssues, type IssueStatus, type IssueSummary, type IssueType } from '../api/issueApi';
import { IssueLevelBadge } from '../components/issue/IssueLevelBadge';
import { IssueStatusBadge } from '../components/issue/IssueStatusBadge';

type SortMode = 'latest' | 'risk';

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

function typeLabel(type?: string | null) {
  const labels: Record<string, string> = {
    FIRE: '화재',
    SMOKE: '연기',
    FIRE_SMOKE: '화재/연기',
    NONE: '미분류',
  };
  return type ? labels[type] ?? type : '-';
}

export function IssueListPage() {
  const [issues, setIssues] = useState<IssueSummary[]>([]);
  const [statusFilter, setStatusFilter] = useState<IssueStatus | 'ALL'>('ALL');
  const [typeFilter, setTypeFilter] = useState<IssueType | 'ALL'>('ALL');
  const [sortMode, setSortMode] = useState<SortMode>('latest');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadIssues = async () => {
    setIsLoading(true);
    setError(null);
    try {
      setIssues(await getIssues());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '이슈 목록을 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadIssues();
  }, []);

  const visibleIssues = useMemo(() => {
    return [...issues]
      .filter((issue) => statusFilter === 'ALL' || issue.issueStatus === statusFilter)
      .filter((issue) => typeFilter === 'ALL' || issue.issueType === typeFilter)
      .sort((a, b) => {
        if (sortMode === 'risk') return (a.latestLevel ?? 9) - (b.latestLevel ?? 9);
        return Date.parse(b.lastDetectedAt ?? b.detectedAt) - Date.parse(a.lastDetectedAt ?? a.detectedAt);
      });
  }, [issues, sortMode, statusFilter, typeFilter]);

  const onStatusFilter = (event: ChangeEvent<HTMLSelectElement>) => setStatusFilter(event.target.value as IssueStatus | 'ALL');
  const onTypeFilter = (event: ChangeEvent<HTMLSelectElement>) => setTypeFilter(event.target.value as IssueType | 'ALL');

  return (
    <div className="page-stack">
      <section className="page-title split">
        <div>
          <h1>이슈 관리</h1>
        </div>
        <div className="title-actions">
          <button className="secondary-button" type="button" onClick={() => void loadIssues()}>
            새로고침
          </button>
        </div>
      </section>

      <section className="filter-bar">
        <select value={statusFilter} onChange={onStatusFilter}>
          <option value="ALL">전체 상태</option>
          <option value="CANDIDATE">후보</option>
          <option value="VLM_ANALYZING">VLM 분석 중</option>
          <option value="REAL_FIRE">실제 화재</option>
          <option value="FALSE_ALARM">오탐</option>
          <option value="REPORTED">신고 완료</option>
          <option value="CLOSED">종료</option>
        </select>
        <select value={typeFilter} onChange={onTypeFilter}>
          <option value="ALL">전체 유형</option>
          <option value="FIRE">화재</option>
          <option value="SMOKE">연기</option>
          <option value="FIRE_SMOKE">화재/연기</option>
          <option value="NONE">미분류</option>
        </select>
        <select value={sortMode} onChange={(event) => setSortMode(event.target.value as SortMode)}>
          <option value="latest">최근 감지순</option>
          <option value="risk">위험도 높은순</option>
        </select>
      </section>

      {error ? <div className="message-box error-text">{error}</div> : null}
      {isLoading ? <div className="empty-panel">이슈 목록을 불러오는 중입니다.</div> : null}

      {!isLoading && (
        <section className="panel table-panel">
          <table>
            <thead>
              <tr>
                <th>issueId</th>
                <th>CCTV</th>
                <th>유형</th>
                <th>상태</th>
                <th>Level</th>
                <th>Snapshot</th>
                <th>최근 감지</th>
                <th>VLM</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {visibleIssues.map((issue) => (
                <tr key={issue.issueId}>
                  <td>#{issue.issueId}</td>
                  <td>
                    <strong>{issue.cctvName ?? '-'}</strong>
                    <span>{issue.cctvNum ?? '-'}</span>
                  </td>
                  <td>{typeLabel(issue.issueType)}</td>
                  <td>
                    <IssueStatusBadge status={issue.issueStatus} />
                  </td>
                  <td>
                    <IssueLevelBadge level={issue.latestLevel} />
                  </td>
                  <td>{issue.snapshotCount ?? 0}장</td>
                  <td>{formatDateTime(issue.lastDetectedAt ?? issue.detectedAt)}</td>
                  <td>{formatDateTime(issue.lastVlmAnalyzedAt)}</td>
                  <td>
                    <Link className="ghost-button" to={`/issues/${issue.issueId}`}>
                      상세
                    </Link>
                  </td>
                </tr>
              ))}
              {visibleIssues.length === 0 ? (
                <tr>
                  <td colSpan={9}>
                    <span className="muted">표시할 이슈가 없습니다.</span>
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}
