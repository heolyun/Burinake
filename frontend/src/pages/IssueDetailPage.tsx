import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  getIssueDetail,
  getIssueVlmResults,
  getSnapshotImageContentUrl,
  updateIssueStatus,
  type IssueDetail,
  type IssueStatus,
  type SnapshotImage,
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
import { IssueStatusBadge } from '../components/issue/IssueStatusBadge';
import { ReportStatusBadge } from '../components/report/ReportStatusBadge';

const INITIAL_TIMELINE_COUNT = 10;

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

function issueStatusText(status: IssueStatus) {
  const labels: Record<IssueStatus, string> = {
    CANDIDATE: '신고 검토 필요',
    VLM_ANALYZING: 'AI 분석 중',
    REAL_FIRE: '화재 의심',
    FALSE_ALARM: '오탐 처리',
    REPORTED: '신고 요청 생성됨',
    CLOSED: '종료',
  };
  return labels[status] ?? status;
}

function finalJudgement(issueStatus: IssueStatus, latestVlmResult?: VlmResultDetail | null) {
  if (issueStatus === 'FALSE_ALARM') return '오탐 가능';
  if (issueStatus === 'CLOSED') return '종료됨';
  if (issueStatus === 'VLM_ANALYZING') return '분석 중';
  if (latestVlmResult?.isRealFire === false) return '오탐 가능';
  if (latestVlmResult?.isRealFire === true || issueStatus === 'REAL_FIRE' || issueStatus === 'REPORTED') return '화재 의심';
  return '판단 대기';
}

function judgementTone(issueStatus: IssueStatus, latestVlmResult?: VlmResultDetail | null) {
  const judgement = finalJudgement(issueStatus, latestVlmResult);
  if (judgement === '화재 의심') return 'danger';
  if (judgement === '오탐 가능' || judgement === '종료됨') return 'calm';
  return 'warning';
}

function levelText(level?: number | null) {
  if (!level) return '미판단';
  return `Level ${level}`;
}

function reportStatusText(status: ReportStatus) {
  const labels: Record<ReportStatus, string> = {
    DRAFT: '승인 대기',
    APPROVED: '승인됨',
    SENT: '전송 완료',
    FAILED: '전송 실패',
    CANCELED: '취소',
  };
  return labels[status] ?? status;
}

function getBoxStyle(box: IssueDetail['detectionBoxes'][number], image: SnapshotImage | null) {
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
  const [selectedImageId, setSelectedImageId] = useState<number | null>(null);
  const [showAllTimeline, setShowAllTimeline] = useState(false);
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
      setSelectedImageId((current) => current ?? issueDetail.triggerImage?.imageId ?? issueDetail.timeline[0]?.imageId ?? null);
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
  const selectedSnapshot = timeline.find((snapshot) => snapshot.imageId === selectedImageId) ?? triggerImage ?? timeline[0] ?? null;
  const boxesForSelectedImage = selectedSnapshot?.imageId === yoloResult?.imageId ? detectionBoxes : [];
  const sortedVlmHistory = [...vlmHistory].sort((a, b) => b.analysisRound - a.analysisRound);
  const visibleTimeline = showAllTimeline ? timeline : timeline.slice(0, INITIAL_TIMELINE_COUNT);
  const pendingReport = reports.find((report) => report.reportStatus === 'DRAFT');
  const judgement = finalJudgement(issue.issueStatus, latestVlmResult);
  const tone = judgementTone(issue.issueStatus, latestVlmResult);
  const primaryActionLabel = pendingReport ? '119 신고 승인' : '신고 초안 생성';

  const handlePrimaryReportAction = () => {
    if (pendingReport) {
      void changeReportStatus(pendingReport.reportId, 'APPROVED');
      return;
    }
    void createDraft();
  };

  return (
    <div className="page-stack issue-detail-page">
      <section className={`issue-hero hero-${tone}`}>
        <div>
          <Link className="text-link" to="/issues">
            이슈 관리
          </Link>
          <div className="issue-hero-title">
            <h1>Issue #{issue.issueId}</h1>
            <span className={`hero-level level-tone-${issue.latestLevel ?? 'empty'}`}>{levelText(issue.latestLevel)}</span>
            <IssueStatusBadge status={issue.issueStatus} />
          </div>
          <p>
            {issue.cctvName ?? '-'} {issue.cctvNum ?? ''} / {issue.location ?? '위치 미등록'} / {typeLabel(issue.issueType)}
          </p>
        </div>
        <div className="hero-status-card">
          <span>현재 상태</span>
          <strong>{issueStatusText(issue.issueStatus)}</strong>
          <small>최근 업데이트 {formatDateTime(issue.updatedAt)}</small>
        </div>
      </section>

      {error ? <div className="message-box error-text">{error}</div> : null}

      <section className="control-grid">
        <article className="panel detection-card">
          <div className="panel-title">
            <div>
              <h2>감지 이미지</h2>
              <span>
                {selectedSnapshot ? `#${selectedSnapshot.imageId}` : '-'} / {formatDateTime(selectedSnapshot?.snapshotTime)}
              </span>
            </div>
            <div className="image-badge-row">
              <span className={yoloResult?.isFire ? 'signal-badge danger' : 'signal-badge calm'}>화재 {boolLabel(yoloResult?.isFire)}</span>
              <span className={yoloResult?.isSmoke ? 'signal-badge warning' : 'signal-badge calm'}>연기 {boolLabel(yoloResult?.isSmoke)}</span>
            </div>
          </div>
          {selectedSnapshot ? (
            <div className="snapshot-stage">
              <img src={getSnapshotImageContentUrl(selectedSnapshot.imageId)} alt={`Snapshot ${selectedSnapshot.imageId}`} />
              {boxesForSelectedImage.map((box) => (
                <div
                  className={`detection-box detection-${box.detectionType.toLowerCase()}`}
                  key={box.boxId}
                  style={getBoxStyle(box, selectedSnapshot)}
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
            <span>{selectedSnapshot?.storageKey ?? '-'}</span>
            <strong>{selectedSnapshot?.widthPx ?? '-'} x {selectedSnapshot?.heightPx ?? '-'}</strong>
          </div>
          {selectedSnapshot && boxesForSelectedImage.length === 0 ? (
            <p className="muted image-note">선택한 snapshot에는 현재 응답 구조상 연결된 bbox가 없습니다.</p>
          ) : null}
        </article>

        <aside className={`panel decision-card decision-${tone}`}>
          <div className="decision-header">
            <span>최종 판단</span>
            <strong>{judgement}</strong>
          </div>
          <dl className="decision-metrics">
            <div>
              <dt>위험 레벨</dt>
              <dd>{levelText(issue.latestLevel)}</dd>
            </div>
            <div>
              <dt>신뢰도</dt>
              <dd>{formatNumber(latestVlmResult?.confidence ?? yoloResult?.fireConfidence)}</dd>
            </div>
            <div>
              <dt>YOLO 결과</dt>
              <dd>
                화재 {boolLabel(yoloResult?.isFire)} / 연기 {boolLabel(yoloResult?.isSmoke)}
              </dd>
            </div>
            <div>
              <dt>분석 시간</dt>
              <dd>{formatDateTime(latestVlmResult?.analyzedAt ?? issue.lastVlmAnalyzedAt)}</dd>
            </div>
          </dl>
          <div className="decision-summary">
            <strong>VLM 판단 요약</strong>
            <p>{latestVlmResult?.message || latestVlmResult?.situationSummary || issue.latestMessage || '저장된 판단 내용이 없습니다.'}</p>
          </div>
          <div className="decision-actions">
            <button className="primary-button action-strong" type="button" disabled={isActionLoading || !latestVlmResult} onClick={handlePrimaryReportAction}>
              {primaryActionLabel}
            </button>
            <button className="secondary-button" type="button" disabled={isActionLoading} onClick={() => void changeIssueStatus('FALSE_ALARM')}>
              오탐 처리
            </button>
            <button className="ghost-button" type="button" disabled={isActionLoading} onClick={() => void changeIssueStatus('CLOSED')}>
              종료
            </button>
          </div>
        </aside>
      </section>

      <section className="operations-grid">
        <div className="operations-main">
          <section className="panel">
            <div className="panel-title">
              <h2>신고 이벤트</h2>
              <span>{reports.length}건</span>
            </div>
            <div className="report-card-list">
              {reports.map((report, index) => (
                <article className="report-event-card" key={report.reportId}>
                  <div className="report-event-head">
                    <div>
                      <strong>신고 이벤트 #{index + 1}</strong>
                      <span>Report #{report.reportId}</span>
                    </div>
                    <ReportStatusBadge status={report.reportStatus} />
                  </div>
                  <dl className="report-meta-grid">
                    <div>
                      <dt>상태</dt>
                      <dd>{reportStatusText(report.reportStatus)}</dd>
                    </div>
                    <div>
                      <dt>수신자</dt>
                      <dd>{report.receiver}</dd>
                    </div>
                    <div>
                      <dt>승인</dt>
                      <dd>{report.approvedBy ?? '-'}</dd>
                    </div>
                    <div>
                      <dt>전송</dt>
                      <dd>{formatDateTime(report.sentAt)}</dd>
                    </div>
                  </dl>
                  <details className="report-message" open={index === 0}>
                    <summary>메시지 내용</summary>
                    <p>{report.reportMessage}</p>
                  </details>
                  <div className="row-actions">
                    {report.reportStatus === 'DRAFT' ? (
                      <button className="primary-button" type="button" disabled={isActionLoading} onClick={() => void changeReportStatus(report.reportId, 'APPROVED')}>
                        승인
                      </button>
                    ) : null}
                    {report.reportStatus === 'DRAFT' || report.reportStatus === 'APPROVED' ? (
                      <button className="secondary-button" type="button" disabled={isActionLoading} onClick={() => void changeReportStatus(report.reportId, 'SENT')}>
                        전송 기록
                      </button>
                    ) : null}
                    {report.reportStatus !== 'SENT' && report.reportStatus !== 'CANCELED' ? (
                      <button className="ghost-button" type="button" disabled={isActionLoading} onClick={() => void changeReportStatus(report.reportId, 'CANCELED')}>
                        취소
                      </button>
                    ) : null}
                    {report.reportStatus === 'DRAFT' ? (
                      <button className="danger-button" type="button" disabled={isActionLoading} onClick={() => void removeReportDraft(report.reportId)}>
                        삭제
                      </button>
                    ) : null}
                  </div>
                </article>
              ))}
              {reports.length === 0 ? <div className="empty-panel">생성된 신고가 없습니다.</div> : null}
            </div>
          </section>

          <section className="panel">
            <div className="panel-title">
              <h2>VLM 이력</h2>
              <span>{sortedVlmHistory.length}건</span>
            </div>
            <div className="vlm-round-list">
              {sortedVlmHistory.map((vlm, index) => (
                <details className="vlm-round-card" key={vlm.vlmResultId} open={index === 0}>
                  <summary>
                    <span>round {vlm.analysisRound}</span>
                    <strong>{vlm.isRealFire ? '화재 가능' : '오탐 가능'}</strong>
                    <em>{levelText(vlm.level)} / confidence {formatNumber(vlm.confidence)}</em>
                  </summary>
                  <dl className="report-meta-grid">
                    <div>
                      <dt>감지 여부</dt>
                      <dd>{boolLabel(vlm.isRealFire)}</dd>
                    </div>
                    <div>
                      <dt>분석 시간</dt>
                      <dd>{formatDateTime(vlm.analyzedAt)}</dd>
                    </div>
                  </dl>
                  <div className="decision-summary compact-summary">
                    <strong>VLM 판단 요약</strong>
                    <p>{vlm.message ?? vlm.situationSummary ?? '-'}</p>
                  </div>
                  {vlm.rawResponse ? (
                    <details className="raw-response">
                      <summary>원문 보기</summary>
                      <pre>{vlm.rawResponse}</pre>
                    </details>
                  ) : null}
                </details>
              ))}
              {sortedVlmHistory.length === 0 ? <div className="empty-panel">VLM 이력이 없습니다.</div> : null}
            </div>
          </section>
        </div>

        <aside className="panel timeline-panel">
          <div className="panel-title">
            <h2>Snapshot Timeline</h2>
            <span>{timeline.length}장</span>
          </div>
          <div className="snapshot-select-list">
            {visibleTimeline.map((snapshot) => (
              <button
                className={snapshot.imageId === selectedSnapshot?.imageId ? 'snapshot-select-item active' : 'snapshot-select-item'}
                key={snapshot.imageId}
                type="button"
                onClick={() => setSelectedImageId(snapshot.imageId)}
              >
                <img src={getSnapshotImageContentUrl(snapshot.imageId)} alt={`Snapshot ${snapshot.imageId}`} />
                <span>#{snapshot.imageId}</span>
                <strong>{formatDateTime(snapshot.snapshotTime)}</strong>
              </button>
            ))}
          </div>
          {timeline.length > INITIAL_TIMELINE_COUNT ? (
            <button className="secondary-button timeline-more-button" type="button" onClick={() => setShowAllTimeline((current) => !current)}>
              {showAllTimeline ? '접기' : `더보기 ${timeline.length - INITIAL_TIMELINE_COUNT}장`}
            </button>
          ) : null}
        </aside>
      </section>
    </div>
  );
}
