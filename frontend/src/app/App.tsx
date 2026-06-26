import { type ChangeEvent, type FormEvent, useEffect, useState } from 'react';
import { detectFire, type FireDetectionResponse } from '../lib/api/fireDetection';

type FormState = {
  image: File | null;
  cctvName: string;
  cctvNum: string;
  source: string;
  capturedAt: string;
};

const initialFormState: FormState = {
  image: null,
  cctvName: 'DEV-CCTV',
  cctvNum: '1',
  source: 'frontend-demo',
  capturedAt: '',
};

export function App() {
  const [formState, setFormState] = useState<FormState>(initialFormState);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [result, setResult] = useState<FireDetectionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  const handleImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;

    setFormState((current) => ({ ...current, image: file }));
    setResult(null);
    setError(null);

    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }

    setPreviewUrl(file ? URL.createObjectURL(file) : null);
  };

  const handleChange = (field: keyof Omit<FormState, 'image'>) => (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    setFormState((current) => ({
      ...current,
      [field]: event.target.value,
    }));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!formState.image) {
      setError('이미지를 먼저 선택해 주세요.');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    setResult(null);

    try {
      const response = await detectFire({
        image: formState.image,
        cctvName: formState.cctvName || undefined,
        cctvNum: formState.cctvNum || undefined,
        source: formState.source || undefined,
        capturedAt: formState.capturedAt ? new Date(formState.capturedAt).toISOString() : undefined,
      });

      setResult(response);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '화재 감지 요청에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="app-shell">
      <section className="hero-card">
        <div className="hero-copy">
          <p className="eyebrow">Burinake Fire Detection</p>
          <h1>AI CCTV 화재 감지 데모</h1>
          <p className="lead">
            이미지를 올리면 백엔드가 저장소, YOLO, VLM까지 연결해서 화재 여부와 설명을
            반환합니다.
          </p>
        </div>

        <div className="content-grid">
          <form className="panel form-panel" onSubmit={handleSubmit}>
            <label className="field">
              <span>이미지</span>
              <input type="file" accept="image/*" onChange={handleImageChange} />
            </label>

            <div className="field-row">
              <label className="field">
                <span>CCTV 이름</span>
                <input value={formState.cctvName} onChange={handleChange('cctvName')} placeholder="DEV-CCTV" />
              </label>

              <label className="field">
                <span>CCTV 번호</span>
                <input value={formState.cctvNum} onChange={handleChange('cctvNum')} placeholder="1" />
              </label>
            </div>

            <label className="field">
              <span>source</span>
              <input value={formState.source} onChange={handleChange('source')} placeholder="frontend-demo" />
            </label>

            <label className="field">
              <span>capturedAt</span>
              <input
                type="datetime-local"
                value={formState.capturedAt}
                onChange={handleChange('capturedAt')}
              />
            </label>

            <button className="submit-button" type="submit" disabled={isSubmitting}>
              {isSubmitting ? '전송 중...' : '화재 감지 요청'}
            </button>

            {error ? <p className="message error">{error}</p> : null}
            {result ? (
              <div className="message success" aria-live="polite">
                <strong>{result.status}</strong>
                <span>{result.vlmResult.summary}</span>
              </div>
            ) : null}
          </form>

          <aside className="panel result-panel">
            <div className="preview-box">
              {previewUrl ? (
                <img src={previewUrl} alt="업로드 미리보기" />
              ) : (
                <span>이미지를 선택하면 미리보기가 표시됩니다.</span>
              )}
            </div>

            <div className="result-summary">
              <div>
                <span className="label">감지 상태</span>
                <strong>{result ? result.status : '-'}</strong>
              </div>
              <div>
                <span className="label">화재 여부</span>
                <strong>{result ? (result.fireDetected ? '감지됨' : '미감지') : '-'}</strong>
              </div>
              <div>
                <span className="label">위험도</span>
                <strong>{result ? result.riskLevel : '-'}</strong>
              </div>
              <div>
                <span className="label">imageId</span>
                <strong>{result ? result.imageId : '-'}</strong>
              </div>
            </div>

            <div className="result-json">
              <span className="label">응답 JSON</span>
              <pre>{result ? JSON.stringify(result, null, 2) : '아직 전송된 요청이 없습니다.'}</pre>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
