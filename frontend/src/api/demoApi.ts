import { mockStore } from './mockStore';
import type {
  DetectionBoxDetail,
  IssueDetail,
  IssueStatusUpdatePayload,
  IssueSummary,
  SnapshotImage,
  VlmResultDetail,
  YoloResultDetail,
} from './issueApi';
import type { Cctv, CctvPayload } from './cctvApi';
import type { EmergencyReport, ReportCreatePayload, ReportStatusUpdatePayload } from './reportApi';

export const isDemoMode =
  import.meta.env.VITE_DEMO_MODE === 'true' || import.meta.env.MODE === 'demo';

const now = () => new Date().toISOString();

function cctvById(id: number | null) {
  return id == null ? null : mockStore.getCctvs().find((item) => item.cctvId === id) ?? null;
}

function toIssueSummary(issue: ReturnType<typeof mockStore.getIssues>[number]): IssueSummary {
  const cctv = cctvById(issue.cctvId);
  const vlm = mockStore.getVlmResults().find((item) => item.issueId === issue.issueId);
  return {
    issueId: issue.issueId,
    cctvId: issue.cctvId,
    cctvName: cctv?.cctvName ?? null,
    cctvNum: cctv?.cctvNum ?? null,
    location: cctv?.location ?? null,
    triggerImageId: issue.triggerImageId,
    issueType: issue.issueType,
    issueStatus: issue.issueStatus,
    detectedAt: issue.detectedAt,
    lastDetectedAt: issue.detectedAt,
    lastYoloAnalyzedAt: mockStore.getYoloResults().find((item) => item.yoloResultId === issue.yoloResultId)?.analyzedAt ?? null,
    lastVlmAnalyzedAt: vlm?.analyzedAt ?? null,
    latestIsRealFire: vlm?.isRealFire ?? null,
    latestLevel: issue.level,
    latestMessage: vlm?.message ?? null,
    maxBoxAreaRatio: null,
    lastBoxAreaRatio: null,
    snapshotCount: mockStore.getIssueSnapshots().filter((item) => item.issueId === issue.issueId).length,
    updatedAt: vlm?.analyzedAt ?? issue.detectedAt,
  };
}

function toYolo(id: number): YoloResultDetail | null {
  const item = mockStore.getYoloResults().find((result) => result.yoloResultId === id);
  if (!item) return null;
  return { ...item, modelName: 'YOLOv8', modelVersion: 'portfolio-demo', analysisRound: 1, rawResponse: null };
}

function toBoxes(yoloResultId: number): DetectionBoxDetail[] {
  return mockStore.getDetectionBoxes().filter((box) => box.yoloResultId === yoloResultId).map((box, index) => ({
    boxId: box.boxId,
    yoloResultId: box.yoloResultId,
    boxOrder: index + 1,
    detectionType: box.detectionType,
    confidence: box.confidence,
    x: box.x1,
    y: box.y1,
    width: box.x2 - box.x1,
    height: box.y3 - box.y2,
    coordinateType: box.coordinateType,
  }));
}

function toSnapshot(imageId: number): SnapshotImage | null {
  const item = mockStore.getSnapshots().find((snapshot) => snapshot.imageId === imageId);
  if (!item) return null;
  const yolo = mockStore.getYoloResults().find((result) => result.imageId === imageId);
  return {
    ...item,
    storageProvider: 'portfolio-demo',
    storageContainer: null,
    contentType: 'image/jpeg',
    fileSizeBytes: null,
    yoloResult: yolo ? toYolo(yolo.yoloResultId) : null,
    detectionBoxes: yolo ? toBoxes(yolo.yoloResultId) : [],
  };
}

function toVlm(item: ReturnType<typeof mockStore.getVlmResults>[number]): VlmResultDetail {
  return { ...item, modelName: 'Azure OpenAI', modelVersion: 'portfolio-demo', situationSummary: item.message, rawResponse: null };
}

function toReport(item: ReturnType<typeof mockStore.getReports>[number]): EmergencyReport {
  return {
    ...item,
    approvedBy: null,
    approvedAt: null,
    responseCode: item.reportStatus === 'SENT' ? 'DEMO' : null,
    responseBody: null,
    updatedAt: item.sentAt ?? item.createdAt,
  };
}

export const demoApi = {
  getIssues: () => mockStore.getIssues().map(toIssueSummary),
  getIssueDetail(issueId: number): IssueDetail | null {
    const issue = mockStore.getIssues().find((item) => item.issueId === issueId);
    if (!issue) return null;
    const triggerImage = toSnapshot(issue.triggerImageId);
    const timeline = mockStore.getIssueSnapshots().filter((item) => item.issueId === issueId).map((item) => toSnapshot(item.imageId)).filter((item): item is SnapshotImage => item != null);
    const vlm = mockStore.getVlmResults().find((item) => item.issueId === issueId);
    return {
      issue: toIssueSummary(issue),
      triggerImage,
      yoloResult: toYolo(issue.yoloResultId),
      detectionBoxes: toBoxes(issue.yoloResultId),
      latestVlmResult: vlm ? toVlm(vlm) : null,
      timeline,
    };
  },
  getIssueVlmResults: (issueId: number) => mockStore.getVlmResults().filter((item) => item.issueId === issueId).map(toVlm),
  updateIssueStatus(issueId: number, payload: IssueStatusUpdatePayload) {
    mockStore.updateIssue(issueId, { issueStatus: payload.issueStatus, level: payload.latestLevel });
    return this.getIssueDetail(issueId);
  },
  getSnapshotUrl(imageId: number) {
    return mockStore.getSnapshots().find((item) => item.imageId === imageId)?.imageUrl ?? '';
  },
  getReports: () => mockStore.getReports().map(toReport),
  getIssueReports: (issueId: number) => mockStore.getReports().filter((item) => item.issueId === issueId).map(toReport),
  createReport(payload: ReportCreatePayload) {
    const report = mockStore.createReportDraft(payload.issueId);
    return report ? toReport(report) : null;
  },
  updateReport(reportId: number, payload: ReportStatusUpdatePayload) {
    const report = mockStore.updateReport(reportId, {
      reportStatus: payload.reportStatus,
      reportMessage: payload.reportMessage ?? mockStore.getReports().find((item) => item.reportId === reportId)?.reportMessage ?? '',
      sentAt: payload.reportStatus === 'SENT' ? now() : null,
    });
    if (report?.reportStatus === 'SENT') mockStore.updateIssue(report.issueId, { issueStatus: 'REPORTED' });
    return report ? toReport(report) : null;
  },
  deleteReport: (reportId: number) => mockStore.deleteReport(reportId),
  getCctvs: (): Cctv[] => mockStore.getCctvs().map((item) => ({ ...item, isActive: true, createdAt: now(), updatedAt: now() })),
  createCctv(payload: CctvPayload): Cctv {
    const item = mockStore.createCctv({ cctvName: payload.cctvName, cctvNum: payload.cctvNum, location: payload.location ?? '' });
    return { ...item, isActive: true, createdAt: now(), updatedAt: now() };
  },
  updateCctv(cctvId: number, payload: CctvPayload): Cctv | null {
    const item = mockStore.updateCctv(cctvId, payload);
    return item ? { ...item, isActive: payload.isActive ?? true, createdAt: now(), updatedAt: now() } : null;
  },
  deleteCctv(cctvId: number): Cctv | null {
    const item = mockStore.deleteCctv(cctvId);
    return item ? { ...item, isActive: false, createdAt: now(), updatedAt: now() } : null;
  },
};
