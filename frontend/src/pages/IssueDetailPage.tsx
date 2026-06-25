import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getIssueDetail, updateIssueStatus } from '../api/issueApi';
import { approveReport, createReportDraft } from '../api/reportApi';
import { requestVlmAnalysis } from '../api/vlmApi';
import { DetectionImageViewer } from '../components/issue/DetectionImageViewer';
import { IssueLevelBadge } from '../components/issue/IssueLevelBadge';
import { IssueStatusBadge } from '../components/issue/IssueStatusBadge';
import { IssueStepper } from '../components/issue/IssueStepper';
import { SnapshotTimeline } from '../components/issue/SnapshotTimeline';
import { VlmResultPanel } from '../components/issue/VlmResultPanel';
import { YoloResultPanel } from '../components/issue/YoloResultPanel';

type IssueDetail = NonNullable<Awaited<ReturnType<typeof getIssueDetail>>>;

export function IssueDetailPage() {
  const { issueId } = useParams();
  const numericIssueId = Number(issueId);
  const [detail, setDetail] = useState<IssueDetail | null>(null);

  const refresh = useCallback(async () => {
    if (!Number.isFinite(numericIssueId)) return;
    setDetail(await getIssueDetail(numericIssueId));
  }, [numericIssueId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const runAction = async (action: () => Promise<unknown>) => {
    await action();
    await refresh();
  };

  if (!detail) {
    return (
      <div className="page-stack">
        <Link className="ghost-button" to="/issues">
          목록으로
        </Link>
        <div className="empty-panel">이슈를 찾을 수 없습니다.</div>
      </div>
    );
  }

  const { issue, cctv, triggerImage, yoloResult, detectionBoxes, timeline, vlmResult, report } = detail;

  return (
    <div className="page-stack">
      <section className="page-title split">
        <div>
          <Link className="text-link" to="/issues">
            이슈 관리
          </Link>
          <h1>Issue #{issue.issueId}</h1>
          <p>{cctv ? `${cctv.cctvName} ${cctv.cctvNum} · ${cctv.location}` : 'CCTV 정보 없음'}</p>
        </div>
        <div className="title-actions">
          <IssueLevelBadge level={issue.level} />
          <IssueStatusBadge status={issue.issueStatus} />
        </div>
      </section>

      <IssueStepper issueStatus={issue.issueStatus} hasVlmResult={Boolean(vlmResult)} reportStatus={report?.reportStatus} />

      <section className="detail-grid">
        <article className="panel image-panel">
          <div className="panel-title">
            <h2>트리거 이미지</h2>
            <span>{new Date(issue.detectedAt).toLocaleString()}</span>
          </div>
          <DetectionImageViewer image={triggerImage} boxes={detectionBoxes} />
        </article>

        <aside className="side-stack">
          <section className="panel">
            <div className="panel-title">
              <h2>Issue 정보</h2>
            </div>
            <dl className="kv-grid single">
              <div>
                <dt>유형</dt>
                <dd>{issue.issueType}</dd>
              </div>
              <div>
                <dt>VLM 입력 범위</dt>
                <dd>
                  {new Date(issue.vlmInputStartTime).toLocaleTimeString()} - {new Date(issue.vlmInputEndTime).toLocaleTimeString()}
                </dd>
              </div>
            </dl>
          </section>
          <YoloResultPanel result={yoloResult} boxes={detectionBoxes} />
        </aside>
      </section>

      <section className="detail-grid">
        <article className="panel">
          <div className="panel-title">
            <h2>VLM 입력 스냅샷</h2>
            <span>{timeline.length}건</span>
          </div>
          <SnapshotTimeline items={timeline} />
        </article>
        <VlmResultPanel result={vlmResult} />
      </section>

      <section className="action-bar">
        <button className="secondary-button" onClick={() => void runAction(() => updateIssueStatus(issue.issueId, 'VLM_ANALYZING'))}>
          VLM 분석 요청
        </button>
        <button className="primary-button" onClick={() => void runAction(() => requestVlmAnalysis(issue.issueId))}>
          분석 완료 mock 처리
        </button>
        <button className="secondary-button" onClick={() => void runAction(() => updateIssueStatus(issue.issueId, 'FALSE_ALARM'))}>
          오탐 처리
        </button>
        <button className="secondary-button" onClick={() => void runAction(() => createReportDraft(issue.issueId))}>
          신고 초안 생성
        </button>
        <button className="danger-button" onClick={() => void runAction(() => approveReport(issue.issueId))}>
          신고 승인
        </button>
      </section>
    </div>
  );
}
