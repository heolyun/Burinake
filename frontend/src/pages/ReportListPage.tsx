import { useEffect, useState } from 'react';
import { getCctvs } from '../api/cctvApi';
import { getIssues } from '../api/issueApi';
import { getReports } from '../api/reportApi';
import { IssueLevelBadge } from '../components/issue/IssueLevelBadge';
import { ReportStatusBadge } from '../components/report/ReportStatusBadge';
import { Cctv, EmergencyReport, Issue } from '../mocks/mockData';

export function ReportListPage() {
  const [reports, setReports] = useState<EmergencyReport[]>([]);
  const [issues, setIssues] = useState<Issue[]>([]);
  const [cctvs, setCctvs] = useState<Cctv[]>([]);

  useEffect(() => {
    void Promise.all([getReports(), getIssues(), getCctvs()]).then(([nextReports, nextIssues, nextCctvs]) => {
      setReports(nextReports);
      setIssues(nextIssues);
      setCctvs(nextCctvs);
    });
  }, []);

  return (
    <div className="page-stack">
      <section className="page-title">
        <div>
          <h1>신고 관리</h1>
        </div>
      </section>

      <section className="report-list">
        {reports.map((report) => {
          const issue = issues.find((item) => item.issueId === report.issueId);
          const cctv = issue ? cctvs.find((item) => item.cctvId === issue.cctvId) : null;
          return (
            <article className="panel report-card" key={report.reportId}>
              <div className="panel-title">
                <h2>Report #{report.reportId}</h2>
                <ReportStatusBadge status={report.reportStatus} />
              </div>
              <div className="report-meta">
                <span>Issue #{report.issueId}</span>
                <span>{cctv ? `${cctv.cctvName} ${cctv.cctvNum}` : '-'}</span>
                <IssueLevelBadge level={issue?.level ?? null} />
                <span>{new Date(report.createdAt).toLocaleString()}</span>
                <span>{report.sentAt ? new Date(report.sentAt).toLocaleString() : '전송 전'}</span>
              </div>
              <details>
                <summary>{report.reportMessage}</summary>
                <p>{report.reportMessage}</p>
              </details>
            </article>
          );
        })}
      </section>
    </div>
  );
}
