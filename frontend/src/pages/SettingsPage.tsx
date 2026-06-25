import { FormEvent, useEffect, useState } from 'react';
import { getSettings, saveSettings } from '../api/settingsApi';
import { Settings } from '../mocks/mockData';

export function SettingsPage() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [savedAt, setSavedAt] = useState<string>('');

  useEffect(() => {
    void getSettings().then(setSettings);
  }, []);

  const update = <K extends keyof Settings>(key: K, value: Settings[K]) => {
    setSettings((current) => (current ? { ...current, [key]: value } : current));
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!settings) return;
    await saveSettings(settings);
    setSavedAt(new Date().toLocaleTimeString());
  };

  if (!settings) return <div className="empty-panel">설정을 불러오는 중입니다.</div>;

  return (
    <div className="page-stack">
      <section className="page-title">
        <div>
          <h1>설정</h1>
        </div>
      </section>

      <form className="panel settings-form" onSubmit={handleSubmit}>
        <label>
          VLM beforeSeconds
          <input
            min={0}
            type="number"
            value={settings.beforeSeconds}
            onChange={(event) => update('beforeSeconds', Number(event.target.value))}
          />
        </label>
        <label>
          VLM afterSeconds
          <input
            min={0}
            type="number"
            value={settings.afterSeconds}
            onChange={(event) => update('afterSeconds', Number(event.target.value))}
          />
        </label>
        <label>
          Fire threshold
          <input
            max={1}
            min={0}
            step={0.01}
            type="number"
            value={settings.fireConfidenceThreshold}
            onChange={(event) => update('fireConfidenceThreshold', Number(event.target.value))}
          />
        </label>
        <label>
          Smoke threshold
          <input
            max={1}
            min={0}
            step={0.01}
            type="number"
            value={settings.smokeConfidenceThreshold}
            onChange={(event) => update('smokeConfidenceThreshold', Number(event.target.value))}
          />
        </label>
        <label className="full-span">
          신고 메시지 템플릿
          <textarea value={settings.reportTemplate} onChange={(event) => update('reportTemplate', event.target.value)} rows={5} />
        </label>
        <div className="form-footer">
          <button className="primary-button" type="submit">
            저장
          </button>
          {savedAt && <span>{savedAt} 저장됨</span>}
        </div>
      </form>
    </div>
  );
}
