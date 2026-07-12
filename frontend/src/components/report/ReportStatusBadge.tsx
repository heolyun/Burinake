import type { ReportStatus } from '../../api/reportApi';

type Props = {
  status: ReportStatus;
};

const labels: Record<ReportStatus, string> = {
  DRAFT: '초안',
  APPROVED: '승인',
  SENT: '접수 완료',
  FAILED: '실패',
  CANCELED: '취소',
};

export function ReportStatusBadge({ status }: Props) {
  return <span className={`badge report-status-${status.toLowerCase()}`}>{labels[status] ?? status}</span>;
}
