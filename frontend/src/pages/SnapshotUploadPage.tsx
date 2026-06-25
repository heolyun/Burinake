import { ChangeEvent, FormEvent, useState } from 'react';
import { uploadSnapshot } from '../api/snapshotApi';
import { SnapshotImage } from '../mocks/mockData';

function toLocalDateTimeValue(date: Date) {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16);
}

export function SnapshotUploadPage() {
  const [cctvName, setCctvName] = useState('Cheonan Training Center');
  const [cctvNum, setCctvNum] = useState('CCTV-001');
  const [snapshotTime, setSnapshotTime] = useState(toLocalDateTimeValue(new Date()));
  const [previewUrl, setPreviewUrl] = useState<string>('');
  const [savedSnapshot, setSavedSnapshot] = useState<SnapshotImage | null>(null);

  const handleFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setPreviewUrl(URL.createObjectURL(file));
    setSavedSnapshot(null);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const imageUrl =
      previewUrl || 'https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?auto=format&fit=crop&w=1200&q=80';
    const snapshot = await uploadSnapshot({
      cctvName,
      cctvNum,
      imageUrl,
      snapshotTime: new Date(snapshotTime).toISOString(),
    });
    setSavedSnapshot(snapshot);
  };

  return (
    <div className="page-stack">
      <section className="page-title">
        <div>
          <h1>스냅샷 업로드</h1>
        </div>
      </section>

      <section className="form-grid">
        <form className="panel form-panel" onSubmit={handleSubmit}>
          <label>
            CCTV 이름
            <input value={cctvName} onChange={(event) => setCctvName(event.target.value)} required />
          </label>
          <label>
            CCTV 번호
            <input value={cctvNum} onChange={(event) => setCctvNum(event.target.value)} required />
          </label>
          <label>
            Snapshot 시간
            <input type="datetime-local" value={snapshotTime} onChange={(event) => setSnapshotTime(event.target.value)} required />
          </label>
          <label>
            이미지
            <input accept="image/*" type="file" onChange={handleFile} />
          </label>
          <button className="primary-button" type="submit">
            저장
          </button>
        </form>

        <aside className="panel upload-preview">
          <div className="panel-title">
            <h2>미리보기</h2>
            <span>mock URL 저장</span>
          </div>
          {previewUrl ? <img src={previewUrl} alt="Upload preview" /> : <div className="empty-panel">이미지를 선택하세요.</div>}
          {savedSnapshot && (
            <dl className="result-card">
              <div>
                <dt>imageId</dt>
                <dd>{savedSnapshot.imageId}</dd>
              </div>
              <div>
                <dt>imageUrl</dt>
                <dd>{savedSnapshot.imageUrl}</dd>
              </div>
              <div>
                <dt>snapshotTime</dt>
                <dd>{new Date(savedSnapshot.snapshotTime).toLocaleString()}</dd>
              </div>
            </dl>
          )}
        </aside>
      </section>
    </div>
  );
}
