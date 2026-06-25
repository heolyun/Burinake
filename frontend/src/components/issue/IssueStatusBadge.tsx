import { IssueStatus } from '../../mocks/mockData';

type Props = {
  status: IssueStatus;
};

const statusLabels: Record<IssueStatus, string> = {
  CANDIDATE: '후보',
  VLM_ANALYZING: 'VLM 분석중',
  REAL_FIRE: '실제 화재',
  FALSE_ALARM: '오탐',
  REPORTED: '신고 완료',
  CLOSED: '종료',
};

export function IssueStatusBadge({ status }: Props) {
  return <span className={`badge issue-status-${status.toLowerCase()}`}>{statusLabels[status]}</span>;
}
