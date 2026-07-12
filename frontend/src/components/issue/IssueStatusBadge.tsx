import type { IssueStatus } from '../../api/issueApi';

type Props = {
  status: IssueStatus;
};

const statusLabels: Record<IssueStatus, string> = {
  CANDIDATE: '후보',
  VLM_ANALYZING: 'AI 분석 중',
  REAL_FIRE: '실제 화재',
  FALSE_ALARM: '오탐',
  REPORTED: '신고 접수',
  CLOSED: '종료',
};

export function IssueStatusBadge({ status }: Props) {
  return <span className={`badge issue-status-${status.toLowerCase()}`}>{statusLabels[status] ?? status}</span>;
}
