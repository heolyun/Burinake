import { ChangeEvent, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getCctvs } from '../api/cctvApi';
import { getIssues } from '../api/issueApi';
import { getReports } from '../api/reportApi';
import { IssueLevelBadge } from '../components/issue/IssueLevelBadge';
import { IssueStatusBadge } from '../components/issue/IssueStatusBadge';
import { ReportStatusBadge } from '../components/report/ReportStatusBadge';
import { Cctv, EmergencyReport, Issue, IssueStatus, IssueType } from '../mocks/mockData';

type SortMode = 'latest' | 'risk';

export function IssueListPage() {
  const [issues, setIssues] = useState<Issue[]>([]);
  const [cctvs, setCctvs] = useState<Cctv[]>([]);
  const [reports, setReports] = useState<EmergencyReport[]>([]);
  const [statusFilter, setStatusFilter] = useState<IssueStatus | 'ALL'>('ALL');
  const [typeFilter, setTypeFilter] = useState<IssueType | 'ALL'>('ALL');
  const [levelFilter, setLevelFilter] = useState<string>('ALL');
  const [sortMode, setSortMode] = useState<SortMode>('latest');

  useEffect(() => {
    void Promise.all([getIssues(), getCctvs(), getReports()]).then(([nextIssues, nextCctvs, nextReports]) => {
      setIssues(nextIssues);
      setCctvs(nextCctvs);
      setReports(nextReports);
    });
  }, []);

  const visibleIssues = useMemo(() => {
    return [...issues]
      .filter((issue) => statusFilter === 'ALL' || issue.issueStatus === statusFilter)
      .filter((issue) => typeFilter === 'ALL' || issue.issueType === typeFilter)
      .filter((issue) => levelFilter === 'ALL' || String(issue.level ?? '') === levelFilter)
      .sort((a, b) => {
        if (sortMode === 'risk') return (a.level ?? 9) - (b.level ?? 9);
        return Date.parse(b.detectedAt) - Date.parse(a.detectedAt);
      });
  }, [issues, levelFilter, sortMode, statusFilter, typeFilter]);

  const onStatusFilter = (event: ChangeEvent<HTMLSelectElement>) => setStatusFilter(event.target.value as IssueStatus | 'ALL');
  const onTypeFilter = (event: ChangeEvent<HTMLSelectElement>) => setTypeFilter(event.target.value as IssueType | 'ALL');

  return (
    <div className="page-stack">
      <section className="page-title">
        <div>
          <h1>이슈 관리</h1>
        </div>
      </section>

      <section className="filter-bar">
        <select value={statusFilter} onChange={onStatusFilter}>
          <option value="ALL">전체 상태</option>
          <option value="CANDIDATE">CANDIDATE</option>
          <option value="VLM_ANALYZING">VLM_ANALYZING</option>
          <option value="REAL_FIRE">REAL_FIRE</option>
          <option value="FALSE_ALARM">FALSE_ALARM</option>
          <option value="REPORTED">REPORTED</option>
          <option value="CLOSED">CLOSED</option>
        </select>
        <select value={typeFilter} onChange={onTypeFilter}>
          <option value="ALL">전체 유형</option>
          <option value="FIRE">FIRE</option>
          <option value="SMOKE">SMOKE</option>
          <option value="FIRE_SMOKE">FIRE_SMOKE</option>
        </select>
        <select value={levelFilter} onChange={(event) => setLevelFilter(event.target.value)}>
          <option value="ALL">전체 Level</option>
          <option value="1">Level 1</option>
          <option value="2">Level 2</option>
          <option value="3">Level 3</option>
          <option value="4">Level 4</option>
        </select>
        <select value={sortMode} onChange={(event) => setSortMode(event.target.value as SortMode)}>
          <option value="latest">최신순</option>
          <option value="risk">위험등급 높은 순</option>
        </select>
      </section>

      <section className="panel table-panel">
        <table>
          <thead>
            <tr>
              <th>issueId</th>
              <th>CCTV</th>
              <th>발생 시간</th>
              <th>유형</th>
              <th>상태</th>
              <th>Level</th>
              <th>Report</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {visibleIssues.map((issue) => {
              const cctv = cctvs.find((item) => item.cctvId === issue.cctvId);
              const report = reports.find((item) => item.issueId === issue.issueId);
              return (
                <tr key={issue.issueId}>
                  <td>#{issue.issueId}</td>
                  <td>
                    <strong>{cctv?.cctvName ?? '-'}</strong>
                    <span>{cctv?.cctvNum ?? '-'}</span>
                  </td>
                  <td>{new Date(issue.detectedAt).toLocaleString()}</td>
                  <td>{issue.issueType}</td>
                  <td>
                    <IssueStatusBadge status={issue.issueStatus} />
                  </td>
                  <td>
                    <IssueLevelBadge level={issue.level} />
                  </td>
                  <td>{report ? <ReportStatusBadge status={report.reportStatus} /> : <span className="muted">없음</span>}</td>
                  <td>
                    <Link className="ghost-button" to={`/issues/${issue.issueId}`}>
                      상세보기
                    </Link>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </section>
    </div>
  );
}
