import { httpClient } from './httpClient';

export type ProcessingStatus = 'COMPLETED' | 'FAILED' | 'PENDING';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN';

export type BoundingBox = {
  x: number;
  y: number;
  width: number;
  height: number;
  label: string;
  score: number | null;
};

export type YoloResult = {
  detected: boolean;
  confidence: number | null;
  boxes: BoundingBox[];
};

export type VlmResult = {
  summary: string;
  riskLevel: RiskLevel;
  recommendedAction: string;
};

export type FireDetectionResponse = {
  imageId: number;
  status: ProcessingStatus;
  blobPath: string;
  fireDetected: boolean;
  confidence: number | null;
  riskLevel: RiskLevel;
  yoloResult: YoloResult;
  vlmResult: VlmResult;
  processedAt: string;
};

export type FireDetectionRequest = {
  image: File;
  cctvName?: string;
  cctvNum?: string;
  source?: string;
  capturedAt?: string;
};

export async function detectFire(request: FireDetectionRequest): Promise<FireDetectionResponse> {
  const formData = new FormData();
  formData.append('image', request.image);

  if (request.cctvName) {
    formData.append('cctvName', request.cctvName);
  }

  if (request.cctvNum) {
    formData.append('cctvNum', request.cctvNum);
  }

  if (request.source) {
    formData.append('source', request.source);
  }

  if (request.capturedAt) {
    formData.append('capturedAt', request.capturedAt);
  }

  const response = await httpClient.post<FireDetectionResponse>('/api/v1/fire-detections', formData);

  return response.data;
}
