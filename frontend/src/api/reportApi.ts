import { mockStore } from './mockStore';

export async function getReports() {
  return mockStore.getReports();
}

export async function createReportDraft(issueId: number) {
  return mockStore.createReportDraft(issueId);
}

export async function approveReport(issueId: number) {
  return mockStore.approveReport(issueId);
}
