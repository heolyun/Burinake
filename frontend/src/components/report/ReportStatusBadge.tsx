import { ReportStatus } from '../../mocks/mockData';

type Props = {
  status: ReportStatus;
};

const labels: Record<ReportStatus, string> = {
  DRAFT: '초안',
  APPROVED: '승인',
  SENT: '전송',
  FAILED: '실패',
  CANCELED: '취소',
};

export function ReportStatusBadge({ status }: Props) {
  return <span className={`badge report-status-${status.toLowerCase()}`}>{labels[status]}</span>;
}
