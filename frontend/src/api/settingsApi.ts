import { Settings } from '../mocks/mockData';
import { mockStore } from './mockStore';

export async function getSettings() {
  return mockStore.getSettings();
}

export async function saveSettings(settings: Settings) {
  return mockStore.saveSettings(settings);
}
