import { mockStore } from './mockStore';

export async function requestVlmAnalysis(issueId: number) {
  mockStore.updateIssue(issueId, { issueStatus: 'VLM_ANALYZING' });
  return mockStore.completeVlmAnalysis(issueId);
}
