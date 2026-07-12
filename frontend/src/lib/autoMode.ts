export type AutoModeSettings = {
  enabled: boolean;
  levelThreshold: number;
};

const STORAGE_KEY = 'burinake:auto-mode';

export const defaultAutoModeSettings: AutoModeSettings = {
  enabled: false,
  levelThreshold: 2,
};

export function loadAutoModeSettings(): AutoModeSettings {
  const rawValue = window.localStorage.getItem(STORAGE_KEY);
  if (!rawValue) return defaultAutoModeSettings;

  try {
    const parsed = JSON.parse(rawValue) as Partial<AutoModeSettings>;
    return {
      enabled: Boolean(parsed.enabled),
      levelThreshold: Number(parsed.levelThreshold ?? defaultAutoModeSettings.levelThreshold),
    };
  } catch {
    return defaultAutoModeSettings;
  }
}

export function saveAutoModeSettings(settings: AutoModeSettings) {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  window.dispatchEvent(new CustomEvent('burinake:auto-mode-change', { detail: settings }));
}

export function subscribeAutoModeSettings(listener: (settings: AutoModeSettings) => void) {
  const handleChange = () => listener(loadAutoModeSettings());
  window.addEventListener('storage', handleChange);
  window.addEventListener('burinake:auto-mode-change', handleChange);

  return () => {
    window.removeEventListener('storage', handleChange);
    window.removeEventListener('burinake:auto-mode-change', handleChange);
  };
}
