import { useEffect, useState } from 'react';
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
import { createReportDraft, deleteReportDraft, getIssueReports, updateReportStatus, type EmergencyReport, type ReportStatus } from '../api/reportApi';
import { IssueLevelBadge } from '../components/issue/IssueLevelBadge';
import { IssueStatusBadge } from '../components/issue/IssueStatusBadge';
import { ReportStatusBadge } from '../components/report/ReportStatusBadge';
import { downloadLocalReport } from '../lib/reportFile';

const INITIAL_TIMELINE_COUNT = 10;
const SNAPSHOT_MATCH_TOLERANCE_MS = 1500;

type ParsedVlmRaw = {
  fire_location_detail?: {
    captured_at?: string;
  };
  visual_cause?: unknown;
  risk_assessment?: unknown;
  recommended_actions?: unknown;
  emergency_report_korean_narrative?: unknown;
};

type SnapshotDetectionKind = 'skipped' | 'none' | 'smoke' | 'fire' | 'fire-smoke';

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

function formatNumber(value?: number | null, digits = 3) {
  if (value == null) return '-';
  return Number(value).toFixed(digits);
}

function boolLabel(value?: boolean | null) {
  if (value == null) return '-';
  return value ? '해당' : '아님';
}

function fireProbabilityLabel(value?: boolean | null) {
  if (value == null) return '판단 대기';
  return value ? '화재 가능성 높음' : '화재 가능성 낮음';
}

function detectionStateLabel(label: string, value?: boolean | null) {
  if (value == null) return `${label} 미분석`;
  return value ? `${label} 탐지` : `${label} 미탐지`;
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

function detectionTypeLabel(type?: string | null) {
  const labels: Record<string, string> = {
    FIRE: '화재',
    SMOKE: '연기',
  };
  return type ? labels[type] ?? type : '-';
}

function reportStatusText(status: ReportStatus) {
  const labels: Record<ReportStatus, string> = {
    DRAFT: '초안',
    APPROVED: '승인',
    SENT: '접수 완료',
    FAILED: '실패',
    CANCELED: '취소',
  };
  return labels[status] ?? status;
}

function issueStatusMessage(status: IssueStatus) {
  const labels: Record<IssueStatus, string> = {
    CANDIDATE: '화재 후보',
    VLM_ANALYZING: 'AI 분석 중',
    REAL_FIRE: '실제 화재',
    FALSE_ALARM: '오탐 처리',
    REPORTED: '신고 접수',
    CLOSED: '종료',
  };
  return labels[status] ?? status;
}

function finalJudgement(issueStatus: IssueStatus, latestVlmResult?: VlmResultDetail | null) {
  if (issueStatus === 'FALSE_ALARM') return '화재 가능성 낮음';
  if (issueStatus === 'CLOSED') return '종료됨';
  if (issueStatus === 'VLM_ANALYZING') return '분석 중';
  if (latestVlmResult?.isRealFire === false) return '화재 가능성 낮음';
  if (latestVlmResult?.isRealFire === true || issueStatus === 'REAL_FIRE' || issueStatus === 'REPORTED') return '화재 가능성 높음';
  return '판단 대기';
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

function parseVlmRaw(rawResponse?: string | null): ParsedVlmRaw | null {
  if (!rawResponse) return null;
  try {
    return JSON.parse(rawResponse) as ParsedVlmRaw;
  } catch {
    return null;
  }
}

function toTimestamp(value?: string | null) {
  if (!value) return null;
  const timestamp = Date.parse(value);
  return Number.isFinite(timestamp) ? timestamp : null;
}

function findSnapshotForVlm(vlm: VlmResultDetail, timeline: SnapshotImage[]) {
  if (timeline.length === 0) {
    return { snapshot: null, matchedBy: 'none' as const, capturedAt: null, deltaMs: null };
  }

  const rawCapturedAt = parseVlmRaw(vlm.rawResponse)?.fire_location_detail?.captured_at ?? null;
  const targetTimestamp = toTimestamp(rawCapturedAt);

  if (targetTimestamp != null) {
    let closest = timeline[0];
    let closestDelta = Number.POSITIVE_INFINITY;

    for (const snapshot of timeline) {
      const snapshotTimestamp = toTimestamp(snapshot.snapshotTime);
      if (snapshotTimestamp == null) continue;

      const delta = Math.abs(snapshotTimestamp - targetTimestamp);
      if (delta < closestDelta) {
        closest = snapshot;
        closestDelta = delta;
      }
    }

    return {
      snapshot: closest,
      matchedBy: closestDelta <= SNAPSHOT_MATCH_TOLERANCE_MS ? ('capturedAt' as const) : ('nearestCapturedAt' as const),
      capturedAt: rawCapturedAt,
      deltaMs: closestDelta,
    };
  }

  return {
    snapshot: timeline[0],
    matchedBy: 'fallback' as const,
    capturedAt: null,
    deltaMs: null,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value);
}

function readableValue(value: unknown): string {
  if (value == null || value === '') return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (Array.isArray(value)) {
    return value.map(readableValue).filter(Boolean).join('\n');
  }
  if (isRecord(value)) {
    return Object.values(value).map(readableValue).filter(Boolean).join('\n');
  }
  return '';
}

function readableSection(value: unknown, fields: Array<[string, string]>) {
  if (!isRecord(value)) return readableValue(value) || '-';

  const lines = fields
    .map(([key, label]) => {
      const text = readableValue(value[key]);
      return text ? `${label}: ${text}` : '';
    })
    .filter(Boolean);

  return lines.length > 0 ? lines.join('\n') : '-';
}

function readableList(value: unknown) {
  if (Array.isArray(value)) {
    const lines = value.map(readableValue).filter(Boolean);
    return lines.length > 0 ? lines.map((line) => `- ${line}`).join('\n') : '-';
  }

  return readableValue(value) || '-';
}

function getSnapshotDetectionKind(snapshot: SnapshotImage): SnapshotDetectionKind {
  const yolo = snapshot.yoloResult;
  if (!yolo) return 'skipped';

  const boxes = snapshot.detectionBoxes ?? [];
  const hasFire = Boolean(yolo.isFire) || boxes.some((box) => box.detectionType === 'FIRE');
  const hasSmoke = Boolean(yolo.isSmoke) || boxes.some((box) => box.detectionType === 'SMOKE');

  if (hasFire && hasSmoke) return 'fire-smoke';
  if (hasFire) return 'fire';
  if (hasSmoke) return 'smoke';
  return 'none';
}

function getSnapshotDetectionLabel(kind: SnapshotDetectionKind) {
  const labels: Record<SnapshotDetectionKind, string> = {
    skipped: 'YOLO 생략',
    none: '미탐지',
    smoke: '연기',
    fire: '화재',
    'fire-smoke': '화재/연기',
  };
  return labels[kind];
}

function getVlmLevelClass(level?: number | null) {
  return level != null && level >= 1 && level <= 4 ? `vlm-level-${level}` : 'vlm-level-unknown';
}

export function IssueDetailPage() {
  const { issueId } = useParams();
  const numericIssueId = Number(issueId);
  const [detail, setDetail] = useState<IssueDetail | null>(null);
  const [vlmHistory, setVlmHistory] = useState<VlmResultDetail[]>([]);
  const [reports, setReports] = useState<EmergencyReport[]>([]);
  const [selectedImageId, setSelectedImageId] = useState<number | null>(null);
  const [selectedVlmResultId, setSelectedVlmResultId] = useState<number | null>(null);
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
      const sortedHistory = [...vlmResults].sort((a, b) => b.analysisRound - a.analysisRound);
      const latestMatchedSnapshot = sortedHistory[0] ? findSnapshotForVlm(sortedHistory[0], issueDetail.timeline).snapshot : null;

      setDetail(issueDetail);
      setVlmHistory(vlmResults);
      setReports(issueReports);
      setSelectedVlmResultId((current) => current ?? sortedHistory[0]?.vlmResultId ?? null);
      setSelectedImageId((current) => current ?? latestMatchedSnapshot?.imageId ?? issueDetail.triggerImage?.imageId ?? issueDetail.timeline[0]?.imageId ?? null);
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
      await updateIssueStatus(numericIssueId, {
        issueStatus,
        latestIsRealFire: issueStatus === 'REAL_FIRE' || issueStatus === 'REPORTED' ? true : issueStatus === 'FALSE_ALARM' ? false : undefined,
        latestLevel: detail?.issue.latestLevel ?? undefined,
        latestMessage:
          issueStatus === 'FALSE_ALARM'
            ? '관제 담당자가 오탐으로 처리했습니다.'
            : issueStatus === 'CLOSED'
              ? '관제 담당자가 이슈를 종료했습니다.'
              : detail?.issue.latestMessage ?? undefined,
      });
      await loadDetail();
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

  const completeLocalReport = async (report: EmergencyReport) => {
    if (!detail) return;

    setIsActionLoading(true);
    setError(null);
    try {
      const filename = downloadLocalReport(detail.issue, report);
      await updateReportStatus(report.reportId, {
        reportStatus: 'SENT',
        approvedBy: 'issue-detail',
        responseCode: 'LOCAL_TXT',
        responseBody: filename,
      });
      await updateIssueStatus(numericIssueId, {
        issueStatus: 'REPORTED',
        latestIsRealFire: true,
        latestLevel: detail.issue.latestLevel ?? undefined,
        latestMessage: detail.issue.latestMessage ?? '신고 파일이 생성되었습니다.',
      });
      await loadDetail();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '신고 접수에 실패했습니다.');
    } finally {
      setIsActionLoading(false);
    }
  };

  const changeReportStatus = async (reportId: number, reportStatus: ReportStatus) => {
    setIsActionLoading(true);
    setError(null);
    try {
      await updateReportStatus(reportId, { reportStatus, approvedBy: reportStatus === 'APPROVED' ? 'issue-detail' : undefined });
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

  if (isLoading) return <div className="empty-panel">이슈 상세를 불러오는 중입니다.</div>;

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

  if (!detail) return <div className="empty-panel">이슈를 찾을 수 없습니다.</div>;

  const { issue, triggerImage, yoloResult, detectionBoxes, latestVlmResult, timeline } = detail;
  const sortedVlmHistory = [...vlmHistory].sort((a, b) => b.analysisRound - a.analysisRound);
  const selectedSnapshot = timeline.find((snapshot) => snapshot.imageId === selectedImageId) ?? triggerImage ?? timeline[0] ?? null;
  const yoloForSelectedImage =
    selectedSnapshot?.yoloResult ?? (selectedSnapshot?.imageId === yoloResult?.imageId ? yoloResult : null);
  const boxesForSelectedImage =
    selectedSnapshot?.detectionBoxes ?? (selectedSnapshot?.imageId === yoloResult?.imageId ? detectionBoxes : []);
  const selectedVlm = sortedVlmHistory.find((vlm) => vlm.vlmResultId === selectedVlmResultId) ?? sortedVlmHistory[0] ?? null;
  const selectedVlmMatch = selectedVlm ? findSnapshotForVlm(selectedVlm, timeline) : null;
  const selectedVlmRaw = parseVlmRaw(selectedVlm?.rawResponse);
  const selectedVisualCauseText = selectedVlm?.fireReason || readableSection(selectedVlmRaw?.visual_cause, [
    ['most_likely', '가장 가능성 높은 원인'],
    ['likely_ignition_mechanisms', '가능한 발화 메커니즘'],
    ['confidence_explanation', '판단 근거'],
  ]);
  const selectedRiskText = readableSection(selectedVlmRaw?.risk_assessment, [
    ['level', '위험 수준'],
    ['rationale', '판단 근거'],
    ['current_fire_size_estimate', '현재 화재 규모'],
    ['people_presence', '인명 징후'],
  ]);
  const selectedRecommendedActionsText = readableList(selectedVlmRaw?.recommended_actions);
  const selectedReportText = readableValue(selectedVlmRaw?.emergency_report_korean_narrative) || selectedVlm?.message || '-';
  const visibleTimeline = showAllTimeline ? timeline : timeline.slice(0, INITIAL_TIMELINE_COUNT);
  const nonCanceledReport = reports.find((report) => report.reportStatus !== 'CANCELED');
  const actionableReport = reports.find((report) => ['DRAFT', 'APPROVED', 'FAILED'].includes(report.reportStatus));
  const vlmBySnapshotId = new Map<number, VlmResultDetail>();

  for (const vlm of sortedVlmHistory) {
    const match = findSnapshotForVlm(vlm, timeline);
    if (match.snapshot && !vlmBySnapshotId.has(match.snapshot.imageId)) {
      vlmBySnapshotId.set(match.snapshot.imageId, vlm);
    }
  }

  const selectVlmRound = (vlm: VlmResultDetail) => {
    const match = findSnapshotForVlm(vlm, timeline);
    setSelectedVlmResultId(vlm.vlmResultId);
    if (match.snapshot) {
      setSelectedImageId(match.snapshot.imageId);
      if (timeline.findIndex((item) => item.imageId === match.snapshot?.imageId) >= INITIAL_TIMELINE_COUNT) {
        setShowAllTimeline(true);
      }
    }
  };

  return (
    <div className="page-stack issue-detail-page">
      <section className="issue-hero hero-danger">
        <div>
          <Link className="text-link" to="/issues">
            이슈 관리
          </Link>
          <div className="issue-hero-title">
            <h1>이슈 #{issue.issueId}</h1>
            <IssueLevelBadge level={issue.latestLevel} />
            <IssueStatusBadge status={issue.issueStatus} />
          </div>
          <p>
            {issue.cctvName ?? '-'} {issue.cctvNum ?? ''} / {issue.location ?? '위치 미등록'} / {typeLabel(issue.issueType)}
          </p>
        </div>
        <div className="hero-status-card">
          <span>최종 판단</span>
          <strong>{finalJudgement(issue.issueStatus, latestVlmResult)}</strong>
          <small>
            {issueStatusMessage(issue.issueStatus)} / 최근 업데이트 {formatDateTime(issue.updatedAt)}
          </small>
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
              <span className={yoloForSelectedImage?.isFire ? 'signal-badge danger' : 'signal-badge calm'}>{detectionStateLabel('화재', yoloForSelectedImage?.isFire)}</span>
              <span className={yoloForSelectedImage?.isSmoke ? 'signal-badge warning' : 'signal-badge calm'}>{detectionStateLabel('연기', yoloForSelectedImage?.isSmoke)}</span>
            </div>
          </div>
          {selectedSnapshot ? (
            <div className="snapshot-stage">
              <img src={getSnapshotImageContentUrl(selectedSnapshot.imageId)} alt={`감지 이미지 ${selectedSnapshot.imageId}`} />
              {boxesForSelectedImage.map((box) => (
                <div className={`detection-box detection-${box.detectionType.toLowerCase()}`} key={box.boxId} style={getBoxStyle(box, selectedSnapshot)}>
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
            <strong>
              {selectedSnapshot?.widthPx ?? '-'} x {selectedSnapshot?.heightPx ?? '-'}
            </strong>
          </div>
        </article>

        <aside className="panel decision-card decision-danger">
          <div className="decision-header">
            <span>핵심 판단</span>
            <strong>{finalJudgement(issue.issueStatus, latestVlmResult)}</strong>
          </div>
          <dl className="decision-metrics">
            <div>
              <dt>위험 레벨</dt>
              <dd>Level {issue.latestLevel ?? '-'}</dd>
            </div>
            <div>
              <dt>신뢰도</dt>
              <dd>{formatNumber(latestVlmResult?.confidence ?? yoloForSelectedImage?.fireConfidence)}</dd>
            </div>
            <div>
              <dt>YOLO</dt>
              <dd>
                {detectionStateLabel('화재', yoloForSelectedImage?.isFire)} / {detectionStateLabel('연기', yoloForSelectedImage?.isSmoke)}
              </dd>
            </div>
            <div>
              <dt>최근 VLM</dt>
              <dd>{formatDateTime(latestVlmResult?.analyzedAt ?? issue.lastVlmAnalyzedAt)}</dd>
            </div>
          </dl>
          <div className="decision-summary">
            <strong>VLM 판단 요약</strong>
            <p>{latestVlmResult?.message || latestVlmResult?.situationSummary || issue.latestMessage || '저장된 판단 내용이 없습니다.'}</p>
          </div>
          <div className="decision-actions">
            {nonCanceledReport?.reportStatus === 'SENT' ? (
              <button className="primary-button action-strong" type="button" disabled>
                접수 완료
              </button>
            ) : actionableReport ? (
              <button className="primary-button action-strong" type="button" disabled={isActionLoading} onClick={() => void completeLocalReport(actionableReport)}>
                신고 접수
              </button>
            ) : (
              <button className="primary-button action-strong" type="button" disabled={isActionLoading || !latestVlmResult} onClick={() => void createDraft()}>
                신고 초안 생성
              </button>
            )}
            <button className="secondary-button" type="button" disabled={isActionLoading} onClick={() => void changeIssueStatus('FALSE_ALARM')}>
              오탐 처리
            </button>
            <button className="ghost-button" type="button" disabled={isActionLoading} onClick={() => void changeIssueStatus('CLOSED')}>
              이슈 종료
            </button>
          </div>
        </aside>
      </section>

      <section className="operations-grid">
        <div className="operations-main">
          <section className="panel vlm-insight-panel">
            <div className="panel-title">
              <h2>VLM 이력</h2>
              <span>{sortedVlmHistory.length}건</span>
            </div>
            <div className="vlm-round-list">
              {sortedVlmHistory.map((vlm) => {
                const match = findSnapshotForVlm(vlm, timeline);
                const isSelected = vlm.vlmResultId === selectedVlmResultId;

                return (
                  <article className={isSelected ? 'vlm-round-card active' : 'vlm-round-card'} key={vlm.vlmResultId}>
                    <button className="round-summary-button" type="button" onClick={() => selectVlmRound(vlm)}>
                      <span>round {vlm.analysisRound}</span>
                      <strong>{fireProbabilityLabel(vlm.isRealFire)}</strong>
                      <em>Level {vlm.level ?? '-'} / 신뢰도 {formatNumber(vlm.confidence)}</em>
                    </button>
                    <div className="vlm-linked-snapshot">
                      <span>기준 이미지 {match.snapshot ? `#${match.snapshot.imageId}` : '-'}</span>
                      <span>{match.capturedAt ? formatDateTime(match.capturedAt) : '기준 시각 없음'}</span>
                    </div>
                    <p>{vlm.message ?? vlm.situationSummary ?? '-'}</p>
                  </article>
                );
              })}
              {sortedVlmHistory.length === 0 ? <div className="empty-panel">VLM 이력이 없습니다.</div> : null}
            </div>
          </section>

          <section className="panel vlm-insight-panel">
            <div className="panel-title">
              <h2>선택 round 상세</h2>
              <span>{selectedVlm ? `round ${selectedVlm.analysisRound}` : '-'}</span>
            </div>
            {selectedVlm ? (
              <div className="vlm-detail-grid">
                <dl className="report-meta-grid">
                  <div>
                    <dt>기준 이미지</dt>
                    <dd>{selectedVlmMatch?.snapshot ? `#${selectedVlmMatch.snapshot.imageId}` : '-'}</dd>
                  </div>
                  <div>
                    <dt>VLM 입력 시각</dt>
                    <dd>{selectedVlmMatch?.capturedAt ? formatDateTime(selectedVlmMatch.capturedAt) : '-'}</dd>
                  </div>
                  <div>
                    <dt>실제 화재</dt>
                    <dd>{boolLabel(selectedVlm.isRealFire)}</dd>
                  </div>
                  <div>
                    <dt>위험 레벨</dt>
                    <dd>{selectedVlm.level ?? '-'}</dd>
                  </div>
                  <div>
                    <dt>신뢰도</dt>
                    <dd>{formatNumber(selectedVlm.confidence)}</dd>
                  </div>
                  <div>
                    <dt>분석 저장 시각</dt>
                    <dd>{formatDateTime(selectedVlm.analyzedAt)}</dd>
                  </div>
                </dl>
                <div className="decision-summary">
                  <strong>상황 요약</strong>
                  <p>{selectedVlm.situationSummary || selectedVlm.message || '-'}</p>
                </div>
                <div className="decision-summary">
                  <strong>화재 원인/근거</strong>
                  <p>{selectedVisualCauseText}</p>
                </div>
                <div className="decision-summary">
                  <strong>위험도 근거</strong>
                  <p>{selectedRiskText}</p>
                </div>
                <div className="decision-summary">
                  <strong>권장 조치</strong>
                  <p>{selectedRecommendedActionsText}</p>
                </div>
                <div className="decision-summary">
                  <strong>신고 문안</strong>
                  <p>{selectedReportText}</p>
                </div>
                {selectedVlm.rawResponse ? (
                  <details className="raw-response">
                    <summary>원문 JSON 보기</summary>
                    <pre>{selectedVlm.rawResponse}</pre>
                  </details>
                ) : null}
              </div>
            ) : (
              <div className="empty-panel">선택된 VLM 결과가 없습니다.</div>
            )}
          </section>

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
                      <span>신고 #{report.reportId}</span>
                    </div>
                    <ReportStatusBadge status={report.reportStatus} />
                  </div>
                  <dl className="report-meta-grid">
                    <div>
                      <dt>상태</dt>
                      <dd>{reportStatusText(report.reportStatus)}</dd>
                    </div>
                    <div>
                      <dt>수신처</dt>
                      <dd>{report.receiver}</dd>
                    </div>
                    <div>
                      <dt>승인</dt>
                      <dd>{report.approvedBy ?? '-'}</dd>
                    </div>
                    <div>
                      <dt>접수 시각</dt>
                      <dd>{formatDateTime(report.sentAt)}</dd>
                    </div>
                  </dl>
                  <details className="report-message" open={index === 0}>
                    <summary>메시지 내용</summary>
                    <p>{report.reportMessage}</p>
                  </details>
                  <div className="row-actions">
                    {['DRAFT', 'APPROVED', 'FAILED'].includes(report.reportStatus) ? (
                      <button className="primary-button" type="button" disabled={isActionLoading} onClick={() => void completeLocalReport(report)}>
                        {report.reportStatus === 'FAILED' ? '신고 재접수' : '신고 접수'}
                      </button>
                    ) : null}
                    {report.reportStatus === 'DRAFT' ? (
                      <button className="secondary-button" type="button" disabled={isActionLoading} onClick={() => void changeReportStatus(report.reportId, 'APPROVED')}>
                        승인 기록
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
        </div>

        <aside className="panel timeline-panel">
          <div className="panel-title">
            <h2>이미지 타임라인</h2>
            <span>{timeline.length}장</span>
          </div>
          <div className="snapshot-select-list">
            {visibleTimeline.map((snapshot) => {
              const detectionKind = getSnapshotDetectionKind(snapshot);
              const snapshotVlm = vlmBySnapshotId.get(snapshot.imageId);
              const className = [
                'snapshot-select-item',
                `yolo-${detectionKind}`,
                snapshotVlm ? 'has-vlm' : '',
                snapshotVlm ? getVlmLevelClass(snapshotVlm.level) : '',
                snapshot.imageId === selectedSnapshot?.imageId ? 'active' : '',
              ]
                .filter(Boolean)
                .join(' ');

              return (
                <button className={className} key={snapshot.imageId} type="button" onClick={() => setSelectedImageId(snapshot.imageId)}>
                  <img src={getSnapshotImageContentUrl(snapshot.imageId)} alt={`타임라인 이미지 ${snapshot.imageId}`} />
                  <span className="timeline-image-id">#{snapshot.imageId}</span>
                  <strong>{formatDateTime(snapshot.snapshotTime)}</strong>
                  <div className="timeline-badge-row">
                    <span className={`timeline-status yolo-${detectionKind}`}>{getSnapshotDetectionLabel(detectionKind)}</span>
                    {snapshotVlm ? <span className={`timeline-status ${getVlmLevelClass(snapshotVlm.level)}`}>VLM L{snapshotVlm.level ?? '-'}</span> : null}
                  </div>
                </button>
              );
            })}
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
