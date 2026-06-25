import { IssueStatus } from '../mocks/mockData';
import { mockStore } from './mockStore';

export async function getIssues() {
  return mockStore.getIssues();
}

export async function getIssueDetail(issueId: number) {
  const issues = mockStore.getIssues();
  const issue = issues.find((item) => item.issueId === issueId);
  if (!issue) return null;

  const cctv = mockStore.getCctvs().find((item) => item.cctvId === issue.cctvId) ?? null;
  const triggerImage = mockStore.getSnapshots().find((item) => item.imageId === issue.triggerImageId) ?? null;
  const yoloResult = mockStore.getYoloResults().find((item) => item.yoloResultId === issue.yoloResultId) ?? null;
  const detectionBoxes = mockStore.getDetectionBoxes().filter((item) => item.yoloResultId === issue.yoloResultId);
  const issueSnapshots = mockStore
    .getIssueSnapshots()
    .filter((item) => item.issueId === issueId)
    .sort((a, b) => a.sequenceNo - b.sequenceNo);
  const snapshots = mockStore.getSnapshots();
  const timeline = issueSnapshots.map((item) => ({
    ...item,
    snapshot: snapshots.find((snapshot) => snapshot.imageId === item.imageId) ?? null,
  }));
  const vlmResult = mockStore.getVlmResults().find((item) => item.issueId === issueId) ?? null;
  const report = mockStore.getReports().find((item) => item.issueId === issueId) ?? null;

  return { issue, cctv, triggerImage, yoloResult, detectionBoxes, timeline, vlmResult, report };
}

export async function updateIssueStatus(issueId: number, issueStatus: IssueStatus) {
  return mockStore.updateIssue(issueId, { issueStatus });
}
