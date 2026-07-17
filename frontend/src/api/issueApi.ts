import { httpClient } from '../lib/api/httpClient';
import { demoApi, isDemoMode } from './demoApi';

export type IssueStatus = 'CANDIDATE' | 'VLM_ANALYZING' | 'REAL_FIRE' | 'FALSE_ALARM' | 'REPORTED' | 'CLOSED';
export type IssueType = 'FIRE' | 'SMOKE' | 'FIRE_SMOKE' | 'NONE';

export type IssueSummary = {
  issueId: number;
  cctvId: number | null;
  cctvName: string | null;
  cctvNum: string | null;
  location: string | null;
  triggerImageId: number | null;
  issueType: IssueType;
  issueStatus: IssueStatus;
  detectedAt: string;
  lastDetectedAt: string | null;
  lastYoloAnalyzedAt: string | null;
  lastVlmAnalyzedAt: string | null;
  latestIsRealFire: boolean | null;
  latestLevel: number | null;
  latestMessage: string | null;
  maxBoxAreaRatio: number | null;
  lastBoxAreaRatio: number | null;
  snapshotCount: number | null;
  updatedAt: string;
};

export type SnapshotImage = {
  imageId: number;
  cctvId: number | null;
  storageProvider: string | null;
  storageContainer: string | null;
  storageKey: string | null;
  imageUrl: string | null;
  contentType: string | null;
  fileSizeBytes: number | null;
  widthPx: number | null;
  heightPx: number | null;
  snapshotTime: string;
  createdAt: string;
  yoloResult?: YoloResultDetail | null;
  detectionBoxes?: DetectionBoxDetail[];
};

export type YoloResultDetail = {
  yoloResultId: number;
  imageId: number;
  modelName: string | null;
  modelVersion: string | null;
  analysisRound: number | null;
  isFire: boolean | null;
  isSmoke: boolean | null;
  fireConfidence: number | null;
  smokeConfidence: number | null;
  rawResponse: string | null;
  analyzedAt: string;
};

export type DetectionBoxDetail = {
  boxId: number;
  yoloResultId: number;
  boxOrder: number;
  detectionType: string;
  confidence: number | null;
  x: number | null;
  y: number | null;
  width: number | null;
  height: number | null;
  coordinateType: string | null;
};

export type VlmResultDetail = {
  vlmResultId: number;
  issueId: number;
  analysisRound: number;
  modelName: string | null;
  modelVersion: string | null;
  isRealFire: boolean | null;
  fireStart: string | null;
  fireReason: string | null;
  situationSummary: string | null;
  level: number | null;
  message: string | null;
  confidence: number | null;
  rawResponse: string | null;
  analyzedAt: string;
};

export type IssueDetail = {
  issue: IssueSummary;
  triggerImage: SnapshotImage | null;
  yoloResult: YoloResultDetail | null;
  detectionBoxes: DetectionBoxDetail[];
  latestVlmResult: VlmResultDetail | null;
  timeline: SnapshotImage[];
};

export type IssueStatusUpdatePayload = {
  issueStatus: IssueStatus;
  latestIsRealFire?: boolean;
  latestLevel?: number;
  latestMessage?: string;
};

export async function getIssues(limit = 100) {
  if (isDemoMode) return demoApi.getIssues().slice(0, limit);
  const response = await httpClient.get<IssueSummary[]>('/api/v1/issues', { params: { limit } });
  return response.data;
}

export async function getIssueDetail(issueId: number) {
  if (isDemoMode) return demoApi.getIssueDetail(issueId)!;
  const response = await httpClient.get<IssueDetail>(`/api/v1/issues/${issueId}`);
  return response.data;
}

export async function getIssueVlmResults(issueId: number) {
  if (isDemoMode) return demoApi.getIssueVlmResults(issueId);
  const response = await httpClient.get<VlmResultDetail[]>(`/api/v1/issues/${issueId}/vlm-results`);
  return response.data;
}

export async function updateIssueStatus(issueId: number, payload: IssueStatusUpdatePayload) {
  if (isDemoMode) return demoApi.updateIssueStatus(issueId, payload)!;
  const response = await httpClient.patch<IssueDetail>(`/api/v1/issues/${issueId}/status`, payload);
  return response.data;
}

export function getSnapshotImageContentUrl(imageId: number) {
  if (isDemoMode) return demoApi.getSnapshotUrl(imageId);
  const path = `/api/v1/snapshot-images/${imageId}/content`;
  const baseUrl = import.meta.env.VITE_API_BASE_URL;
  return baseUrl ? new URL(path, baseUrl).toString() : path;
}
