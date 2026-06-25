import { mockStore } from './mockStore';

export async function getYoloResult(yoloResultId: number) {
  return mockStore.getYoloResults().find((item) => item.yoloResultId === yoloResultId) ?? null;
}
