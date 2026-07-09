import axios from 'axios';
import { httpClient } from './httpClient';

export type ProcessingStatus = 'UPLOADED' | 'YOLO_PROCESSING' | 'YOLO_DONE' | 'VLM_PROCESSING' | 'COMPLETED' | 'FAILED';

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

export type DetectedBbox = {
  x: number;
  y: number;
  width: number;
  height: number;
  label: string;
  source: string;
};

export type TimelineSummaryItem = {
  frame_index: number;
  time_offset_s: number;
  observation: string;
};

export type VisualCause = {
  most_likely: string;
  likely_ignition_mechanisms: string[];
  confidence_explanation: string;
};

export type FireLocationDetail = {
  cctv_id: string;
  site_metadata_location: string;
  captured_at: string;
  precise_zone: string;
};

export type RiskAssessment = {
  level: string;
  rationale: string;
  current_fire_size_estimate: string;
  people_presence: string;
};

export type VlmResult = {
  fire_confirmed: boolean;
  confidence: number | null;
  detected_bbox?: DetectedBbox;
  timeline_summary?: TimelineSummaryItem[];
  visual_cause?: VisualCause;
  fire_location_detail?: FireLocationDetail;
  risk_assessment?: RiskAssessment;
  recommended_actions?: string[];
  notes: string;
  emergency_report_korean_narrative: string;
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

  try {
    const response = await httpClient.post<FireDetectionResponse>('/api/v1/fire-detections', formData);
    return response.data;
  } catch (error) {
    if (axios.isAxiosError<FireDetectionResponse>(error) && error.response?.data) {
      return error.response.data;
    }
    throw error;
  }
}
