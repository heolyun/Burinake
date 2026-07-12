import { Link } from 'react-router-dom';
import { Cctv, Issue } from '../../mocks/mockData';
import { IssueLevelBadge } from '../issue/IssueLevelBadge';
import { IssueStatusBadge } from '../issue/IssueStatusBadge';

type Props = {
  issues: Issue[];
  cctvs: Cctv[];
};

export function RecentIssueList({ issues, cctvs }: Props) {
  return (
    <div className="list-stack">
      {issues.map((issue) => {
        const cctv = cctvs.find((item) => item.cctvId === issue.cctvId);
        return (
          <Link className={`recent-issue risk-${issue.level ?? 'empty'}`} to={`/issues/${issue.issueId}`} key={issue.issueId}>
            <div>
              <strong>{cctv ? `${cctv.cctvName} ${cctv.cctvNum}` : `이슈 ${issue.issueId}`}</strong>
              <span>{new Date(issue.detectedAt).toLocaleString()}</span>
            </div>
            <IssueLevelBadge level={issue.level} />
            <IssueStatusBadge status={issue.issueStatus} />
          </Link>
        );
      })}
    </div>
  );
}
