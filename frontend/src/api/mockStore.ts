import {
  Cctv,
  EmergencyReport,
  Issue,
  IssueSnapshot,
  Settings,
  SnapshotImage,
  UploadSnapshotInput,
  VlmResult,
  YoloResult,
  mockCctvs,
  mockDetectionBoxes,
  mockIssueSnapshots,
  mockIssues,
  mockReports,
  mockSettings,
  mockSnapshots,
  mockVlmResults,
  mockYoloResults,
} from '../mocks/mockData';

let cctvs = [...mockCctvs];
let snapshots = [...mockSnapshots];
let yoloResults = [...mockYoloResults];
let issues = [...mockIssues];
let issueSnapshots = [...mockIssueSnapshots];
let vlmResults = [...mockVlmResults];
let reports = [...mockReports];
let settings = { ...mockSettings };

const clone = <T>(data: T): T => structuredClone(data);

function updateIssueRecord(issueId: number, patch: Partial<Issue>): Issue | null {
  const current = issues.find((issue) => issue.issueId === issueId);
  if (!current) return null;
  issues = issues.map((issue) => (issue.issueId === issueId ? { ...issue, ...patch } : issue));
  return clone(issues.find((issue) => issue.issueId === issueId)!);
}

export const mockStore = {
  getCctvs: (): Cctv[] => clone(cctvs),
  getSnapshots: (): SnapshotImage[] => clone(snapshots),
  getYoloResults: (): YoloResult[] => clone(yoloResults),
  getDetectionBoxes: () => clone(mockDetectionBoxes),
  getIssues: (): Issue[] => clone(issues),
  getIssueSnapshots: (): IssueSnapshot[] => clone(issueSnapshots),
  getVlmResults: (): VlmResult[] => clone(vlmResults),
  getReports: (): EmergencyReport[] => clone(reports),
  getSettings: (): Settings => clone(settings),

  createCctv(input: Omit<Cctv, 'cctvId'>): Cctv {
    const cctv = { ...input, cctvId: Math.max(...cctvs.map((item) => item.cctvId), 0) + 1 };
    cctvs = [cctv, ...cctvs];
    return clone(cctv);
  },

  updateCctv(cctvId: number, patch: Partial<Cctv>): Cctv | null {
    const current = cctvs.find((item) => item.cctvId === cctvId);
    if (!current) return null;
    cctvs = cctvs.map((item) => (item.cctvId === cctvId ? { ...item, ...patch } : item));
    return clone(cctvs.find((item) => item.cctvId === cctvId)!);
  },

  deleteCctv(cctvId: number): Cctv | null {
    const current = cctvs.find((item) => item.cctvId === cctvId);
    if (!current) return null;
    cctvs = cctvs.filter((item) => item.cctvId !== cctvId);
    return clone(current);
  },

  createSnapshot(input: UploadSnapshotInput): SnapshotImage {
    let cctv = cctvs.find((item) => item.cctvName === input.cctvName && item.cctvNum === input.cctvNum);
    if (!cctv) {
      cctv = {
        cctvId: Math.max(...cctvs.map((item) => item.cctvId), 0) + 1,
        cctvName: input.cctvName,
        cctvNum: input.cctvNum,
        location: 'Manual upload point',
      };
      cctvs = [cctv, ...cctvs];
    }

    const snapshot: SnapshotImage = {
      imageId: Math.max(...snapshots.map((item) => item.imageId), 100) + 1,
      cctvId: cctv.cctvId,
      imageUrl: input.imageUrl,
      storageKey: `mock/${Date.now()}.jpg`,
      snapshotTime: input.snapshotTime,
      createdAt: new Date().toISOString(),
      widthPx: 1200,
      heightPx: 800,
    };
    snapshots = [snapshot, ...snapshots];
    return clone(snapshot);
  },

  updateIssue(issueId: number, patch: Partial<Issue>): Issue | null {
    return updateIssueRecord(issueId, patch);
  },

  completeVlmAnalysis(issueId: number): VlmResult | null {
    const issue = issues.find((item) => item.issueId === issueId);
    if (!issue) return null;
    const existing = vlmResults.find((item) => item.issueId === issueId);
    if (existing) {
      updateIssueRecord(issueId, { issueStatus: existing.isRealFire ? 'REAL_FIRE' : 'FALSE_ALARM', level: existing.level });
      return clone(existing);
    }

    const result: VlmResult = {
      vlmResultId: Math.max(...vlmResults.map((item) => item.vlmResultId), 600) + 1,
      issueId,
      analysisRound: 1,
      isRealFire: true,
      fireStart: 'Detected area around the trigger frame',
      fireReason: 'Flame-like region persisted across the input snapshot window',
      level: issue.level ?? 2,
      message: `Issue ${issueId} requires immediate field verification and emergency report review.`,
      confidence: 0.88,
      analyzedAt: new Date().toISOString(),
    };
    vlmResults = [result, ...vlmResults];
    updateIssueRecord(issueId, { issueStatus: 'REAL_FIRE', level: result.level });
    return clone(result);
  },

  createReportDraft(issueId: number): EmergencyReport | null {
    const issue = issues.find((item) => item.issueId === issueId);
    const vlmResult = vlmResults.find((item) => item.issueId === issueId);
    if (!issue || !vlmResult) return null;
    const existing = reports.find((item) => item.issueId === issueId && item.reportStatus === 'DRAFT');
    if (existing) return clone(existing);

    const report: EmergencyReport = {
      reportId: Math.max(...reports.map((item) => item.reportId), 700) + 1,
      issueId,
      vlmResultId: vlmResult.vlmResultId,
      reportStatus: 'DRAFT',
      reportMessage: vlmResult.message,
      receiver: '119',
      sentAt: null,
      createdAt: new Date().toISOString(),
    };
    reports = [report, ...reports];
    return clone(report);
  },

  approveReport(issueId: number): EmergencyReport | null {
    const report = reports.find((item) => item.issueId === issueId && item.reportStatus === 'DRAFT');
    if (!report) return null;
    reports = reports.map((item) =>
      item.reportId === report.reportId ? { ...item, reportStatus: 'SENT', sentAt: new Date().toISOString() } : item,
    );
    updateIssueRecord(issueId, { issueStatus: 'REPORTED' });
    return clone(reports.find((item) => item.reportId === report.reportId)!);
  },

  updateReport(reportId: number, patch: Partial<EmergencyReport>): EmergencyReport | null {
    const current = reports.find((item) => item.reportId === reportId);
    if (!current) return null;
    reports = reports.map((item) => (item.reportId === reportId ? { ...item, ...patch } : item));
    return clone(reports.find((item) => item.reportId === reportId)!);
  },

  deleteReport(reportId: number): boolean {
    const before = reports.length;
    reports = reports.filter((item) => item.reportId !== reportId);
    return reports.length < before;
  },

  saveSettings(nextSettings: Settings): Settings {
    settings = { ...nextSettings };
    return clone(settings);
  },
};
