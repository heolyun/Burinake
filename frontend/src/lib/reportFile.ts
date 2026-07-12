import type { IssueSummary } from '../api/issueApi';
import type { EmergencyReport } from '../api/reportApi';

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

function sanitizeFilePart(value: string) {
  return value.replace(/[\\/:*?"<>|]/g, '-').replace(/\s+/g, '_');
}

export function buildLocalReportText(issue: IssueSummary, report: EmergencyReport) {
  return [
    '[Burinake Local Fire Report]',
    `reportId: ${report.reportId}`,
    `issueId: ${issue.issueId}`,
    `receiver: ${report.receiver}`,
    `status: ${report.reportStatus}`,
    `cctv: ${issue.cctvName ?? '-'} ${issue.cctvNum ?? ''}`,
    `location: ${issue.location ?? '-'}`,
    `issueType: ${issue.issueType}`,
    `level: ${issue.latestLevel ?? '-'}`,
    `detectedAt: ${formatDateTime(issue.detectedAt)}`,
    `lastDetectedAt: ${formatDateTime(issue.lastDetectedAt)}`,
    '',
    '[message]',
    report.reportMessage || issue.latestMessage || '',
    '',
    '[generatedAt]',
    new Date().toLocaleString(),
    '',
  ].join('\n');
}

export function downloadLocalReport(issue: IssueSummary, report: EmergencyReport) {
  const blob = new Blob([buildLocalReportText(issue, report)], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const filename = `burinake_report_issue-${issue.issueId}_report-${report.reportId}_${sanitizeFilePart(new Date().toISOString())}.txt`;
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
  return filename;
}
