import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  getIssueDetail,
  getIssues,
  getSnapshotImageContentUrl,
  updateIssueStatus,
  type DetectionBoxDetail,
  type IssueDetail,
  type IssueStatus,
  type IssueSummary,
  type SnapshotImage,
} from '../api/issueApi';
import { createReportDraft, getReports, updateReportStatus, type EmergencyReport } from '../api/reportApi';
import { IssueLevelBadge } from '../components/issue/IssueLevelBadge';
import { IssueStatusBadge } from '../components/issue/IssueStatusBadge';
import { loadAutoModeSettings, saveAutoModeSettings, subscribeAutoModeSettings, type AutoModeSettings } from '../lib/autoMode';
import { downloadLocalReport } from '../lib/reportFile';

const ACTIVE_STATUSES: IssueStatus[] = ['CANDIDATE', 'VLM_ANALYZING', 'REAL_FIRE', 'FALSE_ALARM', 'REPORTED'];

type ParsedVlmRaw = {
  fire_location_detail?: unknown;
  visual_cause?: unknown;
  recommended_actions?: unknown;
};

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
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

function reportStatusLabel(status?: string | null) {
  const labels: Record<string, string> = {
    DRAFT: '초안',
    APPROVED: '승인',
    SENT: '접수 완료',
    FAILED: '실패',
    CANCELED: '취소',
  };
  return status ? labels[status] ?? status : '없음';
}

function boolDetectionLabel(label: string, value?: boolean | null) {
  if (value == null) return `${label} 미분석`;
  return value ? `${label} 탐지` : `${label} 미탐지`;
}

function judgementLabel(issue: IssueSummary) {
  if (issue.issueStatus === 'VLM_ANALYZING') return '분석 중';
  if (issue.issueStatus === 'FALSE_ALARM') return '오탐 처리';
  if (issue.issueStatus === 'REPORTED') return '신고 접수';
  if (issue.issueStatus === 'CLOSED') return '종료';
  if (issue.latestIsRealFire === true || issue.issueStatus === 'REAL_FIRE') return '화재 가능성 높음';
  if (issue.latestIsRealFire === false) return '화재 가능성 낮음';
  return '판단 대기';
}

function getBoxStyle(box: DetectionBoxDetail, image: SnapshotImage | null) {
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

function uniqueSnapshots(snapshots: Array<SnapshotImage | null | undefined>) {
  const seen = new Set<number>();
  return snapshots.filter((snapshot): snapshot is SnapshotImage => {
    if (!snapshot || seen.has(snapshot.imageId)) return false;
    seen.add(snapshot.imageId);
    return true;
  });
}

function alertTone(issue: IssueSummary) {
  if (issue.issueStatus === 'FALSE_ALARM') return 'normal';
  if (issue.latestLevel != null && issue.latestLevel <= 2) return 'danger';
  if (issue.issueStatus === 'VLM_ANALYZING' || issue.latestLevel === 3) return 'warning';
  return 'normal';
}

function issuePriority(issue: IssueSummary) {
  const statusWeight: Record<IssueStatus, number> = {
    VLM_ANALYZING: 0,
    REAL_FIRE: 1,
    REPORTED: 2,
    CANDIDATE: 3,
    FALSE_ALARM: 4,
    CLOSED: 5,
  };
  return (statusWeight[issue.issueStatus] ?? 9) * 10 + (issue.latestLevel ?? 9);
}

function reportForIssue(reports: EmergencyReport[], issueId: number) {
  return reports.find((report) => report.issueId === issueId && report.reportStatus !== 'CANCELED') ?? null;
}

function shouldAutoReport(issue: IssueSummary, settings: AutoModeSettings, reports: EmergencyReport[]) {
  if (!settings.enabled) return false;
  if (issue.latestIsRealFire !== true && issue.issueStatus !== 'REAL_FIRE') return false;
  if (issue.latestLevel == null || issue.latestLevel > settings.levelThreshold) return false;
  return !reportForIssue(reports, issue.issueId);
}

function oneLineSummary(issue: IssueSummary, report: EmergencyReport | null) {
  const cctv = `${issue.cctvName ?? '-'} ${issue.cctvNum ?? ''}`.trim();
  const level = issue.latestLevel ? `Level ${issue.latestLevel}` : 'Level -';
  return `${judgementLabel(issue)} · ${level} · ${cctv} · ${typeLabel(issue.issueType)} · 신고 ${reportStatusLabel(report?.reportStatus)}`;
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
  if (!isRecord(value)) return readableValue(value);

  const lines = fields
    .map(([key, label]) => {
      const text = readableValue(value[key]);
      return text ? `${label}: ${text}` : '';
    })
    .filter(Boolean);

  return lines.length > 0 ? lines.join('\n') : readableValue(value);
}

function parseVlmRaw(rawResponse?: string | null): ParsedVlmRaw | null {
  if (!rawResponse) return null;
  try {
    return JSON.parse(rawResponse) as ParsedVlmRaw;
  } catch {
    return null;
  }
}

function compactText(value: string, maxLength = 180) {
  const normalized = value.replace(/\s+/g, ' ').trim();
  if (normalized.length <= maxLength) return normalized;
  return `${normalized.slice(0, maxLength).trim()}...`;
}

function compactRecommendation(value: unknown, fallback: string) {
  const source = Array.isArray(value) ? value.map(readableValue).filter(Boolean) : readableValue(value).split('\n').filter(Boolean);
  const lines = source.map((line) => compactText(line)).slice(0, 2);
  return lines.length > 0 ? lines.join('\n') : fallback;
}

export function DashboardPage() {
  const [issues, setIssues] = useState<IssueSummary[]>([]);
  const [reports, setReports] = useState<EmergencyReport[]>([]);
  const [autoMode, setAutoMode] = useState(loadAutoModeSettings);
  const [modalIssueId, setModalIssueId] = useState<number | null>(null);
  const [modalDetail, setModalDetail] = useState<IssueDetail | null>(null);
  const [modalSelectedImageId, setModalSelectedImageId] = useState<number | null>(null);
  const [isModalLoading, setIsModalLoading] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [actionId, setActionId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const autoProcessedRef = useRef(new Set<number>());

  const loadDashboard = async () => {
    setError(null);
    try {
      const [nextIssues, nextReports] = await Promise.all([getIssues(100), getReports(100)]);
      setIssues(nextIssues);
      setReports(nextReports);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '대시보드 데이터를 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadDashboard();
    const timer = window.setInterval(() => void loadDashboard(), 10000);
    const unsubscribe = subscribeAutoModeSettings(setAutoMode);
    return () => {
      window.clearInterval(timer);
      unsubscribe();
    };
  }, []);

  const activeIssues = useMemo(() => {
    return [...issues]
      .filter((issue) => ACTIVE_STATUSES.includes(issue.issueStatus))
      .sort((a, b) => issuePriority(a) - issuePriority(b) || Date.parse(b.updatedAt) - Date.parse(a.updatedAt));
  }, [issues]);

  const criticalIssues = activeIssues.filter((issue) => issue.latestLevel != null && issue.latestLevel <= 2);
  const pendingReports = reports.filter((report) => report.reportStatus === 'DRAFT' || report.reportStatus === 'APPROVED');
  const sentReports = reports.filter((report) => report.reportStatus === 'SENT');
  const modalIssue = modalDetail?.issue ?? issues.find((issue) => issue.issueId === modalIssueId) ?? null;
  const modalReport = modalIssue ? reportForIssue(reports, modalIssue.issueId) : null;
  const modalSelectedImage =
    modalDetail?.timeline.find((snapshot) => snapshot.imageId === modalSelectedImageId) ?? modalDetail?.triggerImage ?? modalDetail?.timeline[0] ?? null;
  const modalYolo =
    modalSelectedImage?.yoloResult ?? (modalDetail && modalSelectedImage?.imageId === modalDetail.yoloResult?.imageId ? modalDetail.yoloResult : null);
  const modalBoxes =
    modalSelectedImage?.detectionBoxes ?? (modalDetail && modalSelectedImage?.imageId === modalDetail.yoloResult?.imageId ? modalDetail.detectionBoxes : []);
  const modalLatestVlm = modalDetail?.latestVlmResult ?? null;
  const modalVlmRaw = parseVlmRaw(modalLatestVlm?.rawResponse);
  const modalFireLocation =
    modalLatestVlm?.fireStart ||
    readableSection(modalVlmRaw?.fire_location_detail, [
      ['location', '위치'],
      ['cctv_location', 'CCTV 위치'],
      ['fire_source_zone', '발원 지점'],
      ['exact_location', '세부 위치'],
      ['source_zone', '발원 구역'],
    ]) ||
    (modalIssue?.location ? `${modalIssue.location} 주변` : 'VLM 판단 후 표시됩니다.');
  const modalFireCause =
    modalLatestVlm?.fireReason ||
    readableSection(modalVlmRaw?.visual_cause, [
      ['most_likely', '가장 가능성 높은 원인'],
      ['likely_ignition_mechanisms', '가능한 발화 원인'],
      ['confidence_explanation', '판단 근거'],
    ]) ||
    'VLM 판단 후 표시됩니다.';
  const modalJudgementSummary =
    modalLatestVlm?.situationSummary || modalLatestVlm?.message || modalIssue?.latestMessage || 'AI 판단 메시지가 아직 없습니다.';
  const modalBriefRecommendation = compactRecommendation(
    modalVlmRaw?.recommended_actions,
    modalIssue?.latestIsRealFire === true || modalIssue?.issueStatus === 'REAL_FIRE'
      ? '현장 확인 후 필요하면 신고 접수를 진행하세요.'
      : '화재 가능성이 낮으면 오탐 처리하고, 변화가 있으면 상세 기록을 확인하세요.'
  );
  const modalKeySnapshots = modalDetail
    ? uniqueSnapshots([
        modalDetail.triggerImage,
        ...modalDetail.timeline.filter((snapshot) => (snapshot.detectionBoxes?.length ?? 0) > 0),
      ]).slice(0, 8)
    : [];
  const modalKeySnapshotIds = new Set(modalKeySnapshots.map((snapshot) => snapshot.imageId));
  const modalRecentSnapshots = modalDetail
    ? uniqueSnapshots(modalDetail.timeline.slice(-6).reverse())
        .filter((snapshot) => !modalKeySnapshotIds.has(snapshot.imageId))
        .slice(0, 6)
    : [];

  const openIssueModal = async (issue: IssueSummary) => {
    setModalIssueId(issue.issueId);
    setModalDetail(null);
    setModalSelectedImageId(issue.triggerImageId ?? null);
    setModalError(null);
    setIsModalLoading(true);
    try {
      const issueDetail = await getIssueDetail(issue.issueId);
      setModalDetail(issueDetail);
      setModalSelectedImageId(issueDetail.triggerImage?.imageId ?? issueDetail.timeline[0]?.imageId ?? issue.triggerImageId ?? null);
    } catch (requestError) {
      setModalError(requestError instanceof Error ? requestError.message : '알림 상세를 불러오지 못했습니다.');
    } finally {
      setIsModalLoading(false);
    }
  };

  const reloadModalDetail = async (issueId: number) => {
    try {
      const issueDetail = await getIssueDetail(issueId);
      setModalDetail(issueDetail);
    } catch {
      setModalDetail(null);
    }
  };

  const completeLocalReport = async (issue: IssueSummary, existingReport?: EmergencyReport | null, mode = 'manual') => {
    setActionId(issue.issueId);
    setError(null);
    try {
      const draft = existingReport ?? (await createReportDraft({ issueId: issue.issueId }));
      const filename = downloadLocalReport(issue, draft);
      await updateReportStatus(draft.reportId, {
        reportStatus: 'SENT',
        approvedBy: mode === 'auto' ? 'auto-mode' : 'dashboard',
        responseCode: 'LOCAL_TXT',
        responseBody: filename,
      });
      await updateIssueStatus(issue.issueId, {
        issueStatus: 'REPORTED',
        latestIsRealFire: true,
        latestLevel: issue.latestLevel ?? undefined,
        latestMessage: issue.latestMessage ?? '신고 파일이 생성되었습니다.',
      });
      await loadDashboard();
      if (modalIssueId === issue.issueId) {
        await reloadModalDetail(issue.issueId);
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '신고 접수에 실패했습니다. VLM 결과와 신고 초안 생성 여부를 확인해주세요.');
    } finally {
      setActionId(null);
    }
  };

  useEffect(() => {
    for (const issue of issues) {
      if (autoProcessedRef.current.has(issue.issueId)) continue;
      if (!shouldAutoReport(issue, autoMode, reports)) continue;
      autoProcessedRef.current.add(issue.issueId);
      void completeLocalReport(issue, null, 'auto');
    }
  }, [autoMode, issues, reports]);

  const markFalseAlarm = async (issue: IssueSummary) => {
    setActionId(issue.issueId);
    setError(null);
    try {
      await updateIssueStatus(issue.issueId, {
        issueStatus: 'FALSE_ALARM',
        latestIsRealFire: false,
        latestLevel: issue.latestLevel ?? undefined,
        latestMessage: '관제 담당자가 오탐으로 처리했습니다.',
      });
      await loadDashboard();
      if (modalIssueId === issue.issueId) {
        await reloadModalDetail(issue.issueId);
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '오탐 처리에 실패했습니다.');
    } finally {
      setActionId(null);
    }
  };

  const closeIssue = async (issue: IssueSummary) => {
    setActionId(issue.issueId);
    setError(null);
    try {
      await updateIssueStatus(issue.issueId, {
        issueStatus: 'CLOSED',
        latestLevel: issue.latestLevel ?? undefined,
        latestMessage: issue.latestMessage ?? '관제 담당자가 이슈를 종료했습니다.',
      });
      await loadDashboard();
      if (modalIssueId === issue.issueId) {
        await reloadModalDetail(issue.issueId);
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '이슈 종료에 실패했습니다.');
    } finally {
      setActionId(null);
    }
  };

  const updateAutoMode = (patch: Partial<AutoModeSettings>) => {
    const nextSettings = { ...autoMode, ...patch };
    setAutoMode(nextSettings);
    saveAutoModeSettings(nextSettings);
  };

  return (
    <div className="page-stack dashboard-command compact-dashboard">
      <section className="command-topbar">
        <div>
          <span className="eyebrow">실시간 관제</span>
          <h1>대시보드</h1>
        </div>
        <div className="topbar-controls">
          <label className="switch-row compact-switch">
            <input checked={autoMode.enabled} type="checkbox" onChange={(event) => updateAutoMode({ enabled: event.target.checked })} />
            <span>자동 신고</span>
          </label>
          <select value={autoMode.levelThreshold} onChange={(event) => updateAutoMode({ levelThreshold: Number(event.target.value) })}>
            <option value={1}>Level 1 이상</option>
            <option value={2}>Level 2 이상</option>
            <option value={3}>Level 3 이상</option>
            <option value={4}>Level 4 이상</option>
          </select>
          <button className="secondary-button" type="button" onClick={() => void loadDashboard()}>
            새로고침
          </button>
        </div>
      </section>

      <section className="summary-grid command-summary">
        <article className="summary-card">
          <span>활성 이슈</span>
          <strong>{activeIssues.length}</strong>
        </article>
        <article className="summary-card danger-summary">
          <span>긴급</span>
          <strong>{criticalIssues.length}</strong>
        </article>
        <article className="summary-card">
          <span>신고 대기</span>
          <strong>{pendingReports.length}</strong>
        </article>
        <article className="summary-card">
          <span>접수 완료</span>
          <strong>{sentReports.length}</strong>
        </article>
      </section>

      {error ? <div className="message-box error-text">{error}</div> : null}

      <section className="dashboard-split dashboard-list-only">
        <article className="panel alert-table-panel">
          <div className="panel-title">
            <div>
              <h2>알림</h2>
              <span>{isLoading ? '불러오는 중' : `${activeIssues.length}건`}</span>
            </div>
          </div>

          <div className="alert-row-list">
            {activeIssues.map((issue) => {
              const report = reportForIssue(reports, issue.issueId);
              const selected = modalIssueId === issue.issueId;
              return (
                <button
                  className={`alert-row alert-row-${alertTone(issue)} ${selected ? 'active' : ''}`}
                  key={issue.issueId}
                  type="button"
                  onClick={() => void openIssueModal(issue)}
                >
                  <span className="alert-row-main">
                    <strong>{oneLineSummary(issue, report)}</strong>
                    <small>최근 감지 {formatDateTime(issue.lastDetectedAt ?? issue.detectedAt)}</small>
                  </span>
                  <IssueLevelBadge level={issue.latestLevel} />
                  <IssueStatusBadge status={issue.issueStatus} />
                </button>
              );
            })}
            {!isLoading && activeIssues.length === 0 ? <div className="empty-panel">처리할 활성 이슈가 없습니다.</div> : null}
          </div>
        </article>
      </section>

      {modalIssueId ? (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && setModalIssueId(null)}>
          <section className="alert-modal" role="dialog" aria-modal="true" aria-labelledby="dashboard-alert-modal-title">
            <div className="alert-modal-head">
              <div>
                <span className="eyebrow">알림 상세</span>
                <h2 id="dashboard-alert-modal-title">{modalIssue ? `이슈 #${modalIssue.issueId}` : '이슈'}</h2>
              </div>
              <button className="ghost-button modal-close-button" type="button" onClick={() => setModalIssueId(null)}>
                닫기
              </button>
            </div>

            {isModalLoading ? <div className="empty-panel">알림 상세를 불러오는 중입니다.</div> : null}
            {modalError ? <div className="message-box error-text">{modalError}</div> : null}

            {modalIssue ? (
              <div className="alert-modal-content alert-modal-flow">
                <section className="alert-modal-image-panel modal-primary-image-panel">
                  <div className="panel-title modal-image-title">
                    <div>
                      <h3>{judgementLabel(modalIssue)}</h3>
                      <span>
                        {modalIssue.cctvName ?? '-'} {modalIssue.cctvNum ?? ''} / {modalIssue.location ?? '위치 미등록'} ·{' '}
                        {modalSelectedImage ? `#${modalSelectedImage.imageId} / ${formatDateTime(modalSelectedImage.snapshotTime)}` : '-'}
                      </span>
                    </div>
                    <div className="image-badge-row">
                      <IssueLevelBadge level={modalIssue.latestLevel} />
                      <IssueStatusBadge status={modalIssue.issueStatus} />
                      <span className={modalYolo?.isFire ? 'signal-badge danger' : 'signal-badge calm'}>{boolDetectionLabel('화재', modalYolo?.isFire)}</span>
                      <span className={modalYolo?.isSmoke ? 'signal-badge warning' : 'signal-badge calm'}>{boolDetectionLabel('연기', modalYolo?.isSmoke)}</span>
                    </div>
                  </div>

                  {modalSelectedImage ? (
                    <div className="snapshot-stage modal-snapshot-stage">
                      <img src={getSnapshotImageContentUrl(modalSelectedImage.imageId)} alt={`감지 이미지 ${modalSelectedImage.imageId}`} />
                      {modalBoxes.map((box) => (
                        <div className={`detection-box detection-${box.detectionType.toLowerCase()}`} key={box.boxId} style={getBoxStyle(box, modalSelectedImage)}>
                          <span>{box.detectionType === 'FIRE' ? '화재' : '연기'}</span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="snapshot-placeholder">이미지 없음</div>
                  )}

                  <div className="modal-snapshot-sections">
                    <div className="modal-snapshot-group">
                      <div className="modal-snapshot-group-head">
                        <strong>주요 이미지</strong>
                        <span>감지 기준 이미지</span>
                      </div>
                      <div className="modal-snapshot-strip">
                        {modalKeySnapshots.map((snapshot) => (
                          <button
                            className={snapshot.imageId === modalSelectedImage?.imageId ? 'modal-snapshot-thumb active' : 'modal-snapshot-thumb'}
                            key={snapshot.imageId}
                            type="button"
                            onClick={() => setModalSelectedImageId(snapshot.imageId)}
                          >
                            <img src={getSnapshotImageContentUrl(snapshot.imageId)} alt={`주요 이미지 ${snapshot.imageId}`} />
                            <span>#{snapshot.imageId}</span>
                          </button>
                        ))}
                        {!isModalLoading && modalKeySnapshots.length === 0 ? <div className="empty-panel">주요 이미지가 없습니다.</div> : null}
                      </div>
                    </div>

                    <div className="modal-snapshot-group">
                      <div className="modal-snapshot-group-head">
                        <strong>최근 이미지</strong>
                        <span>최근 입력 이미지</span>
                      </div>
                      <div className="modal-snapshot-strip">
                        {modalRecentSnapshots.map((snapshot) => (
                          <button
                            className={snapshot.imageId === modalSelectedImage?.imageId ? 'modal-snapshot-thumb active' : 'modal-snapshot-thumb'}
                            key={snapshot.imageId}
                            type="button"
                            onClick={() => setModalSelectedImageId(snapshot.imageId)}
                          >
                            <img src={getSnapshotImageContentUrl(snapshot.imageId)} alt={`최근 이미지 ${snapshot.imageId}`} />
                            <span>#{snapshot.imageId}</span>
                          </button>
                        ))}
                        {!isModalLoading && modalRecentSnapshots.length === 0 ? <div className="muted modal-empty-note">최근 이미지가 주요 이미지와 중복됩니다.</div> : null}
                      </div>
                    </div>
                  </div>
                </section>

                <section className="selected-alert-insight modal-insight-row">
                  <div className="insight-item primary">
                    <span>화재 위치 / 원인 추정</span>
                    <strong>{modalFireLocation}</strong>
                    <p>{compactText(modalFireCause, 220)}</p>
                  </div>
                  <div className="insight-item">
                    <span>AI 판단 요약</span>
                    <p>{compactText(modalJudgementSummary, 220)}</p>
                  </div>
                  <div className="insight-item recommendation">
                    <span>권고</span>
                    <p>{modalBriefRecommendation}</p>
                  </div>
                </section>

                <div className="selected-alert-actions modal-action-bar">
                  <button
                    className="primary-button action-strong"
                    type="button"
                    disabled={
                      actionId === modalIssue.issueId ||
                      modalReport?.reportStatus === 'SENT' ||
                      (modalIssue.latestIsRealFire !== true && modalIssue.issueStatus !== 'REAL_FIRE')
                    }
                    onClick={() => void completeLocalReport(modalIssue, modalReport)}
                  >
                    {modalReport?.reportStatus === 'SENT' ? '접수 완료' : '신고 접수'}
                  </button>
                  <button className="secondary-button" type="button" disabled={actionId === modalIssue.issueId} onClick={() => void markFalseAlarm(modalIssue)}>
                    오탐 처리
                  </button>
                  <button className="ghost-button" type="button" disabled={actionId === modalIssue.issueId} onClick={() => void closeIssue(modalIssue)}>
                    이슈 종료
                  </button>
                  <Link className="ghost-button" to={`/issues/${modalIssue.issueId}`}>
                    상세 기록
                  </Link>
                </div>
              </div>
            ) : null}
          </section>
        </div>
      ) : null}

      <section className="panel compact-report-strip">
        <div className="panel-title">
          <h2>최근 신고 기록</h2>
          <Link className="text-link" to="/reports">
            전체 보기
          </Link>
        </div>
        <div className="report-chip-list">
          {reports.slice(0, 8).map((report) => (
            <span className="report-chip" key={report.reportId}>
              신고 #{report.reportId} · 이슈 #{report.issueId} · {reportStatusLabel(report.reportStatus)}
            </span>
          ))}
          {reports.length === 0 ? <span className="muted">신고 기록이 없습니다.</span> : null}
        </div>
      </section>
    </div>
  );
}
