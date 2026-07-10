import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  getIssueDetail,
  getIssueVlmResults,
  getSnapshotImageContentUrl,
  updateIssueStatus,
  type IssueDetail,
  type IssueStatus,
  type VlmResultDetail,
} from '../api/issueApi';
import {
  createReportDraft,
  deleteReportDraft,
  getIssueReports,
  updateReportStatus,
  type EmergencyReport,
  type ReportStatus,
} from '../api/reportApi';
import { IssueLevelBadge } from '../components/issue/IssueLevelBadge';
import { IssueStatusBadge } from '../components/issue/IssueStatusBadge';
import { ReportStatusBadge } from '../components/report/ReportStatusBadge';

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

function formatNumber(value?: number | null, digits = 3) {
  if (value == null) return '-';
  return Number(value).toFixed(digits);
}

function typeLabel(type?: string | null) {
  const labels: Record<string, string> = {
    FIRE: '화재',
    SMOKE: '연기',
    FIRE_SMOKE: '화재/연기',
    NONE: '미분류',
  };
  return type ? labels[type] ?? type : '-';
}

function boolLabel(value?: boolean | null) {
  if (value == null) return '-';
  return value ? '예' : '아니오';
}

function detectionTypeLabel(type?: string | null) {
  const labels: Record<string, string> = {
    FIRE: '화재',
    SMOKE: '연기',
  };
  return type ? labels[type] ?? type : '-';
}

function getBoxStyle(box: IssueDetail['detectionBoxes'][number], image: IssueDetail['triggerImage']) {
  const imageWidth = image?.widthPx ?? 1;
  const imageHeight = image?.heightPx ?? 1;
  const x = box.x ?? 0;
  const y = box.y ?? 0;
  const width = box.width ?? 0;
  const height = box.height ?? 0;
  const isNormalized = box.coordinateType === 'NORMALIZED' || (x <= 1 && y <= 1 && width <= 1 && height <= 1);

  return {
    left: `${isNormalized ? x * 100 : (x / imageWidth) * 100}%`,
    top: `${isNormalized ? y * 100 : (y / imageHeight) * 100}%`,
    width: `${isNormalized ? width * 100 : (width / imageWidth) * 100}%`,
    height: `${isNormalized ? height * 100 : (height / imageHeight) * 100}%`,
  };
}

export function IssueDetailPage() {
  const { issueId } = useParams();
  const numericIssueId = Number(issueId);
  const [detail, setDetail] = useState<IssueDetail | null>(null);
  const [vlmHistory, setVlmHistory] = useState<VlmResultDetail[]>([]);
  const [reports, setReports] = useState<EmergencyReport[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isActionLoading, setIsActionLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadDetail = async () => {
    if (!Number.isFinite(numericIssueId)) {
      setError('올바른 이슈 ID가 아닙니다.');
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const [issueDetail, vlmResults, issueReports] = await Promise.all([
        getIssueDetail(numericIssueId),
        getIssueVlmResults(numericIssueId),
        getIssueReports(numericIssueId),
      ]);
      setDetail(issueDetail);
      setVlmHistory(vlmResults);
      setReports(issueReports);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '이슈 상세를 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadDetail();
  }, [numericIssueId]);

  const changeIssueStatus = async (issueStatus: IssueStatus) => {
    setIsActionLoading(true);
    setError(null);
    try {
      const payload = {
        issueStatus,
        latestIsRealFire: issueStatus === 'REAL_FIRE' || issueStatus === 'REPORTED' ? true : issueStatus === 'FALSE_ALARM' ? false : undefined,
        latestMessage:
          issueStatus === 'FALSE_ALARM'
            ? '운영자가 오탐으로 처리했습니다.'
            : issueStatus === 'CLOSED'
              ? '운영자가 이슈를 종료했습니다.'
              : undefined,
      };
      const nextDetail = await updateIssueStatus(numericIssueId, payload);
      setDetail(nextDetail);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '이슈 상태를 변경하지 못했습니다.');
    } finally {
      setIsActionLoading(false);
    }
  };

  const createDraft = async () => {
    setIsActionLoading(true);
    setError(null);
    try {
      await createReportDraft({ issueId: numericIssueId });
      await loadDetail();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '신고 초안을 생성하지 못했습니다. VLM 결과가 있는지 확인해주세요.');
    } finally {
      setIsActionLoading(false);
    }
  };

  const changeReportStatus = async (reportId: number, reportStatus: ReportStatus) => {
    setIsActionLoading(true);
    setError(null);
    try {
      const approvedBy = reportStatus === 'APPROVED' || reportStatus === 'SENT' ? 'dashboard' : undefined;
      await updateReportStatus(reportId, { reportStatus, approvedBy });
      await loadDetail();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '신고 상태를 변경하지 못했습니다.');
    } finally {
      setIsActionLoading(false);
    }
  };

  const removeReportDraft = async (reportId: number) => {
    setIsActionLoading(true);
    setError(null);
    try {
      await deleteReportDraft(reportId);
      await loadDetail();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '신고 초안을 삭제하지 못했습니다.');
    } finally {
      setIsActionLoading(false);
    }
  };

  if (isLoading) {
    return <div className="empty-panel">이슈 상세를 불러오는 중입니다.</div>;
  }

  if (error && !detail) {
    return (
      <div className="page-stack">
        <Link className="ghost-button" to="/issues">
          목록으로
        </Link>
        <div className="empty-panel">{error}</div>
      </div>
    );
  }

  if (!detail) {
    return <div className="empty-panel">이슈를 찾을 수 없습니다.</div>;
  }

  const { issue, triggerImage, yoloResult, detectionBoxes, latestVlmResult, timeline } = detail;

  return (
    <div className="page-stack">
      <section className="page-title split">
        <div>
          <Link className="text-link" to="/issues">
            이슈 관리
          </Link>
          <h1>Issue #{issue.issueId}</h1>
          <p className="muted">
            {issue.cctvName ?? '-'} {issue.cctvNum ?? ''} / {typeLabel(issue.issueType)}
          </p>
        </div>
        <div className="title-actions">
          <IssueLevelBadge level={issue.latestLevel} />
          <IssueStatusBadge status={issue.issueStatus} />
        </div>
      </section>

      {error ? <div className="message-box error-text">{error}</div> : null}

      <section className="issue-overview-grid">
        <article className="panel image-panel">
          <div className="panel-title">
            <h2>감지 이미지</h2>
            <span>{triggerImage ? `#${triggerImage.imageId}` : '-'}</span>
          </div>
          {triggerImage ? (
            <div className="snapshot-stage">
              <img src={getSnapshotImageContentUrl(triggerImage.imageId)} alt={`Snapshot ${triggerImage.imageId}`} />
              {detectionBoxes.map((box) => (
                <div
                  className={`detection-box detection-${box.detectionType.toLowerCase()}`}
                  key={box.boxId}
                  style={getBoxStyle(box, triggerImage)}
                >
                  <span>
                    {detectionTypeLabel(box.detectionType)} {box.confidence == null ? '' : `${Math.round(box.confidence * 100)}%`}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <div className="snapshot-placeholder">이미지 없음</div>
          )}
          <div className="image-caption">
            <span>{triggerImage?.storageKey ?? '-'}</span>
            <strong>{triggerImage?.widthPx ?? '-'} x {triggerImage?.heightPx ?? '-'}</strong>
          </div>
        </article>

        <aside className="panel issue-summary-panel">
          <div className="panel-title">
            <h2>핵심 판단</h2>
            <span>{isActionLoading ? '처리 중' : '대기'}</span>
          </div>
          <dl className="kv-grid compact">
            <div>
              <dt>최초/최근 감지</dt>
              <dd>{formatDateTime(issue.detectedAt)}</dd>
            </div>
            <div>
              <dt>YOLO</dt>
              <dd>
                화재 {boolLabel(yoloResult?.isFire)} / 연기 {boolLabel(yoloResult?.isSmoke)}
              </dd>
            </div>
            <div>
              <dt>bbox</dt>
              <dd>{detectionBoxes.length}개</dd>
            </div>
            <div>
              <dt>VLM 실제 화재</dt>
              <dd>{boolLabel(latestVlmResult?.isRealFire)}</dd>
            </div>
          </dl>
          <div className="judgement-box">
            <strong>판단 내용 원문</strong>
            <p>{latestVlmResult?.message || issue.latestMessage || '저장된 판단 내용이 없습니다.'}</p>
          </div>
          <div className="row-actions">
            <button className="secondary-button" type="button" disabled={isActionLoading} onClick={() => void changeIssueStatus('REAL_FIRE')}>
              실제 화재
            </button>
            <button className="ghost-button" type="button" disabled={isActionLoading} onClick={() => void changeIssueStatus('FALSE_ALARM')}>
              오탐 처리
            </button>
            <button className="ghost-button" type="button" disabled={isActionLoading} onClick={() => void changeIssueStatus('CLOSED')}>
              종료
            </button>
            <button className="primary-button" type="button" disabled={isActionLoading || !latestVlmResult} onClick={() => void createDraft()}>
              신고 초안 생성
            </button>
          </div>
        </aside>
      </section>

      <section className="panel table-panel">
        <div className="panel-title padded-title">
          <h2>신고 이력</h2>
          <span>{reports.length}건</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>reportId</th>
              <th>상태</th>
              <th>수신처</th>
              <th>승인</th>
              <th>전송</th>
              <th>메시지</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {reports.map((report) => (
              <tr key={report.reportId}>
                <td>#{report.reportId}</td>
                <td>
                  <ReportStatusBadge status={report.reportStatus} />
                </td>
                <td>{report.receiver}</td>
                <td>{report.approvedBy ?? '-'}</td>
                <td>{formatDateTime(report.sentAt)}</td>
                <td className="wide-cell">{report.reportMessage}</td>
                <td>
                  <div className="row-actions">
                    {report.reportStatus === 'DRAFT' ? (
                      <button
                        className="secondary-button"
                        type="button"
                        disabled={isActionLoading}
                        onClick={() => void changeReportStatus(report.reportId, 'APPROVED')}
                      >
                        승인
                      </button>
                    ) : null}
                    {report.reportStatus === 'DRAFT' || report.reportStatus === 'APPROVED' ? (
                      <button
                        className="primary-button"
                        type="button"
                        disabled={isActionLoading}
                        onClick={() => void changeReportStatus(report.reportId, 'SENT')}
                      >
                        전송 처리
                      </button>
                    ) : null}
                    {report.reportStatus !== 'SENT' && report.reportStatus !== 'CANCELED' ? (
                      <button
                        className="ghost-button"
                        type="button"
                        disabled={isActionLoading}
                        onClick={() => void changeReportStatus(report.reportId, 'CANCELED')}
                      >
                        취소
                      </button>
                    ) : null}
                    {report.reportStatus === 'DRAFT' ? (
                      <button
                        className="danger-button"
                        type="button"
                        disabled={isActionLoading}
                        onClick={() => void removeReportDraft(report.reportId)}
                      >
                        삭제
                      </button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ))}
            {reports.length === 0 ? (
              <tr>
                <td colSpan={7}>
                  <span className="muted">생성된 신고가 없습니다.</span>
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </section>

      <section className="detail-grid">
        <article className="panel table-panel">
          <div className="panel-title padded-title">
            <h2>VLM 이력</h2>
            <span>{vlmHistory.length}건</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>round</th>
                <th>실제 화재</th>
                <th>Level</th>
                <th>confidence</th>
                <th>분석 시각</th>
              </tr>
            </thead>
            <tbody>
              {vlmHistory.map((vlm) => (
                <tr key={vlm.vlmResultId}>
                  <td>{vlm.analysisRound}</td>
                  <td>{vlm.isRealFire == null ? '-' : vlm.isRealFire ? 'true' : 'false'}</td>
                  <td>{vlm.level ?? '-'}</td>
                  <td>{formatNumber(vlm.confidence)}</td>
                  <td>{formatDateTime(vlm.analyzedAt)}</td>
                </tr>
              ))}
              {vlmHistory.length === 0 ? (
                <tr>
                  <td colSpan={5}>
                    <span className="muted">VLM 이력이 없습니다.</span>
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
          {vlmHistory.length > 0 ? (
            <div className="history-message-list">
              {vlmHistory.map((vlm) => (
                <details key={`message-${vlm.vlmResultId}`} open={vlm.analysisRound === latestVlmResult?.analysisRound}>
                  <summary>round {vlm.analysisRound} 판단 내용 원문</summary>
                  <p>{vlm.message ?? vlm.situationSummary ?? '-'}</p>
                </details>
              ))}
            </div>
          ) : null}
        </article>

        <aside className="panel table-panel">
          <div className="panel-title padded-title">
            <h2>Snapshot Timeline</h2>
            <span>{timeline.length}장</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>imageId</th>
                <th>image</th>
                <th>snapshotTime</th>
                <th>storageKey</th>
              </tr>
            </thead>
            <tbody>
              {timeline.map((snapshot) => (
                <tr key={snapshot.imageId}>
                  <td>#{snapshot.imageId}</td>
                  <td>
                    <img
                      className="timeline-thumb"
                      src={getSnapshotImageContentUrl(snapshot.imageId)}
                      alt={`Snapshot ${snapshot.imageId}`}
                    />
                  </td>
                  <td>{formatDateTime(snapshot.snapshotTime)}</td>
                  <td className="wide-cell">{snapshot.storageKey ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </aside>
      </section>
    </div>
  );
}
