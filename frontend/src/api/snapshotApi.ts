import { UploadSnapshotInput } from '../mocks/mockData';
import { mockStore } from './mockStore';

export async function getSnapshots() {
  return mockStore.getSnapshots();
}

export async function uploadSnapshot(input: UploadSnapshotInput) {
  return mockStore.createSnapshot(input);
}
