export type IssueType = 'FIRE' | 'SMOKE' | 'FIRE_SMOKE';
export type IssueStatus =
  | 'CANDIDATE'
  | 'VLM_ANALYZING'
  | 'REAL_FIRE'
  | 'FALSE_ALARM'
  | 'REPORTED'
  | 'CLOSED';
export type ReportStatus = 'DRAFT' | 'APPROVED' | 'SENT' | 'FAILED' | 'CANCELED';
export type DetectionType = 'FIRE' | 'SMOKE';
export type CoordinateType = 'PIXEL' | 'NORMALIZED';

export type Cctv = {
  cctvId: number;
  cctvName: string;
  cctvNum: string;
  location: string;
};

export type SnapshotImage = {
  imageId: number;
  cctvId: number;
  imageUrl: string;
  storageKey: string;
  snapshotTime: string;
  createdAt: string;
  widthPx: number;
  heightPx: number;
};

export type YoloResult = {
  yoloResultId: number;
  imageId: number;
  isFire: boolean;
  isSmoke: boolean;
  fireConfidence: number;
  smokeConfidence: number;
  analyzedAt: string;
};

export type DetectionBox = {
  boxId: number;
  yoloResultId: number;
  detectionType: DetectionType;
  confidence: number;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  x3: number;
  y3: number;
  x4: number;
  y4: number;
  coordinateType: CoordinateType;
};

export type Issue = {
  issueId: number;
  cctvId: number;
  triggerImageId: number;
  yoloResultId: number;
  issueType: IssueType;
  issueStatus: IssueStatus;
  detectedAt: string;
  vlmInputStartTime: string;
  vlmInputEndTime: string;
  level: number | null;
};

export type IssueSnapshot = {
  issueSnapshotId: number;
  issueId: number;
  imageId: number;
  sequenceNo: number;
  relativeSeconds: number;
};

export type VlmResult = {
  vlmResultId: number;
  issueId: number;
  analysisRound: number;
  isRealFire: boolean;
  fireStart: string;
  fireReason: string;
  level: number;
  message: string;
  confidence: number;
  analyzedAt: string;
};

export type EmergencyReport = {
  reportId: number;
  issueId: number;
  vlmResultId: number;
  reportStatus: ReportStatus;
  reportMessage: string;
  receiver: string;
  sentAt: string | null;
  createdAt: string;
};

export type Settings = {
  beforeSeconds: number;
  afterSeconds: number;
  fireConfidenceThreshold: number;
  smokeConfidenceThreshold: number;
  reportTemplate: string;
};

export type UploadSnapshotInput = {
  cctvName: string;
  cctvNum: string;
  snapshotTime: string;
  imageUrl: string;
};

export const mockCctvs: Cctv[] = [
  { cctvId: 1, cctvName: 'Cheonan Training Center', cctvNum: 'CCTV-001', location: 'B1 parking entrance' },
  { cctvId: 2, cctvName: 'Logistics Yard', cctvNum: 'CCTV-014', location: 'North loading dock' },
  { cctvId: 3, cctvName: 'Factory Wing A', cctvNum: 'CCTV-027', location: 'Compressor room corridor' },
  { cctvId: 4, cctvName: 'Dormitory', cctvNum: 'CCTV-006', location: 'Main lobby' },
];

export const mockSnapshots: SnapshotImage[] = [
  {
    imageId: 101,
    cctvId: 1,
    imageUrl: 'https://images.unsplash.com/photo-1516937941344-00b4e0337589?auto=format&fit=crop&w=1200&q=80',
    storageKey: 'snapshots/101.jpg',
    snapshotTime: '2026-06-24T09:18:20+09:00',
    createdAt: '2026-06-24T09:18:23+09:00',
    widthPx: 1200,
    heightPx: 800,
  },
  {
    imageId: 102,
    cctvId: 1,
    imageUrl: 'https://images.unsplash.com/photo-1517048676732-d65bc937f952?auto=format&fit=crop&w=1200&q=80',
    storageKey: 'snapshots/102.jpg',
    snapshotTime: '2026-06-24T09:18:25+09:00',
    createdAt: '2026-06-24T09:18:27+09:00',
    widthPx: 1200,
    heightPx: 800,
  },
  {
    imageId: 103,
    cctvId: 2,
    imageUrl: 'https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?auto=format&fit=crop&w=1200&q=80',
    storageKey: 'snapshots/103.jpg',
    snapshotTime: '2026-06-24T10:41:15+09:00',
    createdAt: '2026-06-24T10:41:17+09:00',
    widthPx: 1200,
    heightPx: 800,
  },
  {
    imageId: 104,
    cctvId: 3,
    imageUrl: 'https://images.unsplash.com/photo-1497366811353-6870744d04b2?auto=format&fit=crop&w=1200&q=80',
    storageKey: 'snapshots/104.jpg',
    snapshotTime: '2026-06-24T11:02:40+09:00',
    createdAt: '2026-06-24T11:02:42+09:00',
    widthPx: 1200,
    heightPx: 800,
  },
  {
    imageId: 105,
    cctvId: 3,
    imageUrl: 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=80',
    storageKey: 'snapshots/105.jpg',
    snapshotTime: '2026-06-24T11:02:45+09:00',
    createdAt: '2026-06-24T11:02:48+09:00',
    widthPx: 1200,
    heightPx: 800,
  },
];

export const mockYoloResults: YoloResult[] = [
  { yoloResultId: 201, imageId: 101, isFire: true, isSmoke: true, fireConfidence: 0.94, smokeConfidence: 0.73, analyzedAt: '2026-06-24T09:18:29+09:00' },
  { yoloResultId: 202, imageId: 103, isFire: false, isSmoke: true, fireConfidence: 0.18, smokeConfidence: 0.86, analyzedAt: '2026-06-24T10:41:22+09:00' },
  { yoloResultId: 203, imageId: 104, isFire: true, isSmoke: false, fireConfidence: 0.81, smokeConfidence: 0.22, analyzedAt: '2026-06-24T11:02:48+09:00' },
];

export const mockDetectionBoxes: DetectionBox[] = [
  { boxId: 301, yoloResultId: 201, detectionType: 'FIRE', confidence: 0.94, x1: 525, y1: 330, x2: 760, y2: 330, x3: 760, y3: 590, x4: 525, y4: 590, coordinateType: 'PIXEL' },
  { boxId: 302, yoloResultId: 201, detectionType: 'SMOKE', confidence: 0.73, x1: 0.38, y1: 0.08, x2: 0.78, y2: 0.08, x3: 0.78, y3: 0.42, x4: 0.38, y4: 0.42, coordinateType: 'NORMALIZED' },
  { boxId: 303, yoloResultId: 202, detectionType: 'SMOKE', confidence: 0.86, x1: 430, y1: 145, x2: 780, y2: 145, x3: 780, y3: 435, x4: 430, y4: 435, coordinateType: 'PIXEL' },
  { boxId: 304, yoloResultId: 203, detectionType: 'FIRE', confidence: 0.81, x1: 0.22, y1: 0.35, x2: 0.44, y2: 0.35, x3: 0.44, y3: 0.62, x4: 0.22, y4: 0.62, coordinateType: 'NORMALIZED' },
];

export const mockIssues: Issue[] = [
  {
    issueId: 401,
    cctvId: 1,
    triggerImageId: 101,
    yoloResultId: 201,
    issueType: 'FIRE_SMOKE',
    issueStatus: 'REAL_FIRE',
    detectedAt: '2026-06-24T09:18:20+09:00',
    vlmInputStartTime: '2026-06-24T09:17:50+09:00',
    vlmInputEndTime: '2026-06-24T09:18:25+09:00',
    level: 1,
  },
  {
    issueId: 402,
    cctvId: 2,
    triggerImageId: 103,
    yoloResultId: 202,
    issueType: 'SMOKE',
    issueStatus: 'CANDIDATE',
    detectedAt: '2026-06-24T10:41:15+09:00',
    vlmInputStartTime: '2026-06-24T10:40:45+09:00',
    vlmInputEndTime: '2026-06-24T10:41:20+09:00',
    level: 3,
  },
  {
    issueId: 403,
    cctvId: 3,
    triggerImageId: 104,
    yoloResultId: 203,
    issueType: 'FIRE',
    issueStatus: 'VLM_ANALYZING',
    detectedAt: '2026-06-24T11:02:40+09:00',
    vlmInputStartTime: '2026-06-24T11:02:10+09:00',
    vlmInputEndTime: '2026-06-24T11:02:45+09:00',
    level: 2,
  },
];

export const mockIssueSnapshots: IssueSnapshot[] = [
  { issueSnapshotId: 501, issueId: 401, imageId: 101, sequenceNo: 1, relativeSeconds: 0 },
  { issueSnapshotId: 502, issueId: 401, imageId: 102, sequenceNo: 2, relativeSeconds: 5 },
  { issueSnapshotId: 503, issueId: 402, imageId: 103, sequenceNo: 1, relativeSeconds: 0 },
  { issueSnapshotId: 504, issueId: 403, imageId: 104, sequenceNo: 1, relativeSeconds: 0 },
  { issueSnapshotId: 505, issueId: 403, imageId: 105, sequenceNo: 2, relativeSeconds: 5 },
];

export const mockVlmResults: VlmResult[] = [
  {
    vlmResultId: 601,
    issueId: 401,
    analysisRound: 1,
    isRealFire: true,
    fireStart: 'B1 parking entrance, near the utility cabinet',
    fireReason: 'Visible flame and smoke around stored materials',
    level: 1,
    message: 'B1 parking entrance camera detected visible flame and smoke. Immediate dispatch is recommended.',
    confidence: 0.91,
    analyzedAt: '2026-06-24T09:18:45+09:00',
  },
];

export const mockReports: EmergencyReport[] = [
  {
    reportId: 701,
    issueId: 401,
    vlmResultId: 601,
    reportStatus: 'DRAFT',
    reportMessage: 'Fire suspected at B1 parking entrance. Visible flame and smoke detected by CCTV-001.',
    receiver: '119',
    sentAt: null,
    createdAt: '2026-06-24T09:19:00+09:00',
  },
];

export const mockSettings: Settings = {
  beforeSeconds: 30,
  afterSeconds: 5,
  fireConfidenceThreshold: 0.7,
  smokeConfidenceThreshold: 0.65,
  reportTemplate: 'Fire suspected at {{location}}. Level {{level}}. CCTV {{cctvNum}} detected {{issueType}}.',
};
