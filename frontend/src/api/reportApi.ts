import { httpClient } from '../lib/api/httpClient';
import { demoApi, isDemoMode } from './demoApi';

export type ReportStatus = 'DRAFT' | 'APPROVED' | 'SENT' | 'FAILED' | 'CANCELED';

export type EmergencyReport = {
  reportId: number;
  issueId: number;
  vlmResultId: number;
  reportStatus: ReportStatus;
  reportMessage: string;
  receiver: string;
  approvedBy: string | null;
  approvedAt: string | null;
  sentAt: string | null;
  responseCode: string | null;
  responseBody: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ReportCreatePayload = {
  issueId: number;
  reportMessage?: string;
  receiver?: string;
};

export type ReportStatusUpdatePayload = {
  reportStatus: ReportStatus;
  reportMessage?: string;
  approvedBy?: string;
  responseCode?: string;
  responseBody?: string;
};

export async function getReports(limit = 100) {
  if (isDemoMode) return demoApi.getReports().slice(0, limit);
  const response = await httpClient.get<EmergencyReport[]>('/api/v1/reports', { params: { limit } });
  return response.data;
}

export async function getIssueReports(issueId: number) {
  if (isDemoMode) return demoApi.getIssueReports(issueId);
  const response = await httpClient.get<EmergencyReport[]>(`/api/v1/issues/${issueId}/reports`);
  return response.data;
}

export async function createReportDraft(payload: ReportCreatePayload) {
  if (isDemoMode) return demoApi.createReport(payload)!;
  const response = await httpClient.post<EmergencyReport>('/api/v1/reports', payload);
  return response.data;
}

export async function updateReportStatus(reportId: number, payload: ReportStatusUpdatePayload) {
  if (isDemoMode) return demoApi.updateReport(reportId, payload)!;
  const response = await httpClient.patch<EmergencyReport>(`/api/v1/reports/${reportId}/status`, payload);
  return response.data;
}

export async function deleteReportDraft(reportId: number) {
  if (isDemoMode) {
    demoApi.deleteReport(reportId);
    return;
  }
  await httpClient.delete(`/api/v1/reports/${reportId}`);
}
