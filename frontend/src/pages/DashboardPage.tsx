import { useEffect, useState } from 'react';
import { getCctvs } from '../api/cctvApi';
import { getIssues } from '../api/issueApi';
import { getReports } from '../api/reportApi';
import { getSnapshots } from '../api/snapshotApi';
import { RecentIssueList } from '../components/dashboard/RecentIssueList';
import { RecentSnapshotGrid } from '../components/dashboard/RecentSnapshotGrid';
import { SummaryCard } from '../components/dashboard/SummaryCard';
import { Cctv, EmergencyReport, Issue, SnapshotImage } from '../mocks/mockData';

export function DashboardPage() {
  const [cctvs, setCctvs] = useState<Cctv[]>([]);
  const [snapshots, setSnapshots] = useState<SnapshotImage[]>([]);
  const [issues, setIssues] = useState<Issue[]>([]);
  const [reports, setReports] = useState<EmergencyReport[]>([]);

  useEffect(() => {
    void Promise.all([getCctvs(), getSnapshots(), getIssues(), getReports()]).then(
      ([nextCctvs, nextSnapshots, nextIssues, nextReports]) => {
        setCctvs(nextCctvs);
        setSnapshots(nextSnapshots);
        setIssues(nextIssues);
        setReports(nextReports);
      },
    );
  }, []);

  const today = new Date().toISOString().slice(0, 10);
  const todaySnapshots = snapshots.filter((snapshot) => snapshot.snapshotTime.slice(0, 10) === today);
  const candidateIssues = issues.filter((issue) => ['CANDIDATE', 'VLM_ANALYZING'].includes(issue.issueStatus));
  const realFireIssues = issues.filter((issue) => issue.issueStatus === 'REAL_FIRE' || issue.issueStatus === 'REPORTED');
  const waitingReports = reports.filter((report) => report.reportStatus === 'DRAFT');
  const recentIssues = [...issues].sort((a, b) => Date.parse(b.detectedAt) - Date.parse(a.detectedAt)).slice(0, 5);
  const recentSnapshots = [...snapshots].sort((a, b) => Date.parse(b.snapshotTime) - Date.parse(a.snapshotTime)).slice(0, 6);

  return (
    <div className="page-stack">
      <section className="page-title">
        <div>
          <h1>대시보드</h1>
        </div>
      </section>

      <section className="summary-grid">
        <SummaryCard label="전체 CCTV" value={cctvs.length} />
        <SummaryCard label="오늘 스냅샷" value={todaySnapshots.length} />
        <SummaryCard label="화재/연기 후보" value={candidateIssues.length} tone="warning" />
        <SummaryCard label="실제 화재" value={realFireIssues.length} tone="danger" />
        <SummaryCard label="신고 대기" value={waitingReports.length} tone="warning" />
      </section>

      <section className="dashboard-grid">
        <article className="panel wide">
          <div className="panel-title">
            <h2>최근 발생 이슈</h2>
            <span>Level 1 우선 강조</span>
          </div>
          <RecentIssueList issues={recentIssues} cctvs={cctvs} />
        </article>
        <article className="panel">
          <div className="panel-title">
            <h2>최근 Snapshot</h2>
            <span>{recentSnapshots.length}건</span>
          </div>
          <RecentSnapshotGrid snapshots={recentSnapshots} cctvs={cctvs} />
        </article>
      </section>
    </div>
  );
}
