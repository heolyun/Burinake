import { type ChangeEvent, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { deleteReportDraft, getReports, updateReportStatus, type EmergencyReport, type ReportStatus } from '../api/reportApi';
import { ReportStatusBadge } from '../components/report/ReportStatusBadge';

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

export function ReportListPage() {
  const [reports, setReports] = useState<EmergencyReport[]>([]);
  const [statusFilter, setStatusFilter] = useState<ReportStatus | 'ALL'>('ALL');
  const [isLoading, setIsLoading] = useState(true);
  const [actionId, setActionId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadReports = async () => {
    setIsLoading(true);
    setError(null);
    try {
      setReports(await getReports());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '신고 목록을 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadReports();
  }, []);

  const visibleReports = useMemo(() => {
    return reports.filter((report) => statusFilter === 'ALL' || report.reportStatus === statusFilter);
  }, [reports, statusFilter]);

  const changeStatus = async (reportId: number, reportStatus: ReportStatus) => {
    setActionId(reportId);
    setError(null);
    try {
      const approvedBy = reportStatus === 'APPROVED' || reportStatus === 'SENT' ? 'dashboard' : undefined;
      await updateReportStatus(reportId, { reportStatus, approvedBy });
      await loadReports();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '신고 상태를 변경하지 못했습니다.');
    } finally {
      setActionId(null);
    }
  };

  const removeDraft = async (reportId: number) => {
    setActionId(reportId);
    setError(null);
    try {
      await deleteReportDraft(reportId);
      await loadReports();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '신고 초안을 삭제하지 못했습니다.');
    } finally {
      setActionId(null);
    }
  };

  const onStatusFilter = (event: ChangeEvent<HTMLSelectElement>) => setStatusFilter(event.target.value as ReportStatus | 'ALL');

  return (
    <div className="page-stack">
      <section className="page-title split">
        <div>
          <h1>신고 관리</h1>
        </div>
        <div className="title-actions">
          <button className="secondary-button" type="button" onClick={() => void loadReports()}>
            새로고침
          </button>
        </div>
      </section>

      <section className="filter-bar">
        <select value={statusFilter} onChange={onStatusFilter}>
          <option value="ALL">전체 상태</option>
          <option value="DRAFT">초안</option>
          <option value="APPROVED">승인</option>
          <option value="SENT">전송</option>
          <option value="FAILED">실패</option>
          <option value="CANCELED">취소</option>
        </select>
      </section>

      {error ? <div className="message-box error-text">{error}</div> : null}
      {isLoading ? <div className="empty-panel">신고 목록을 불러오는 중입니다.</div> : null}

      {!isLoading && (
        <section className="panel table-panel">
          <table>
            <thead>
              <tr>
                <th>reportId</th>
                <th>issueId</th>
                <th>상태</th>
                <th>수신처</th>
                <th>승인</th>
                <th>전송</th>
                <th>메시지</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {visibleReports.map((report) => (
                <tr key={report.reportId}>
                  <td>#{report.reportId}</td>
                  <td>
                    <Link className="text-link" to={`/issues/${report.issueId}`}>
                      Issue #{report.issueId}
                    </Link>
                  </td>
                  <td>
                    <ReportStatusBadge status={report.reportStatus} />
                  </td>
                  <td>{report.receiver}</td>
                  <td>
                    <strong>{report.approvedBy ?? '-'}</strong>
                    <span>{formatDateTime(report.approvedAt)}</span>
                  </td>
                  <td>{formatDateTime(report.sentAt)}</td>
                  <td className="wide-cell">{report.reportMessage}</td>
                  <td>
                    <div className="row-actions">
                      {report.reportStatus === 'DRAFT' ? (
                        <button
                          className="secondary-button"
                          type="button"
                          disabled={actionId === report.reportId}
                          onClick={() => void changeStatus(report.reportId, 'APPROVED')}
                        >
                          승인
                        </button>
                      ) : null}
                      {report.reportStatus === 'DRAFT' || report.reportStatus === 'APPROVED' ? (
                        <button
                          className="primary-button"
                          type="button"
                          disabled={actionId === report.reportId}
                          onClick={() => void changeStatus(report.reportId, 'SENT')}
                        >
                          전송 처리
                        </button>
                      ) : null}
                      {report.reportStatus !== 'SENT' && report.reportStatus !== 'CANCELED' ? (
                        <button
                          className="ghost-button"
                          type="button"
                          disabled={actionId === report.reportId}
                          onClick={() => void changeStatus(report.reportId, 'CANCELED')}
                        >
                          취소
                        </button>
                      ) : null}
                      {report.reportStatus === 'DRAFT' ? (
                        <button
                          className="danger-button"
                          type="button"
                          disabled={actionId === report.reportId}
                          onClick={() => void removeDraft(report.reportId)}
                        >
                          삭제
                        </button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
              {visibleReports.length === 0 ? (
                <tr>
                  <td colSpan={8}>
                    <span className="muted">표시할 신고가 없습니다.</span>
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
