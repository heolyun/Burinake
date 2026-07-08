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

function toLocalDateTimeValue(date: Date) {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16);
}

export function FireDetectionPage() {
  const [formState, setFormState] = useState<FormState>({
    ...initialFormState,
    capturedAt: toLocalDateTimeValue(new Date()),
  });
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
    <div className="page-stack">
      <section className="page-title">
        <div>
          <h1>화재 감지 업로드</h1>
          <p className="muted">이미지를 올리면 백엔드가 저장소, YOLO, VLM까지 연결해서 결과를 반환합니다.</p>
        </div>
      </section>

      <section className="form-grid">
        <form className="panel form-panel" onSubmit={handleSubmit}>
          <label>
            CCTV 이름
            <input value={formState.cctvName} onChange={handleChange('cctvName')} required />
          </label>
          <label>
            CCTV 번호
            <input value={formState.cctvNum} onChange={handleChange('cctvNum')} required />
          </label>
          <label>
            source
            <input value={formState.source} onChange={handleChange('source')} required />
          </label>
          <label>
            촬영 시각
            <input type="datetime-local" value={formState.capturedAt} onChange={handleChange('capturedAt')} required />
          </label>
          <label>
            이미지
            <input accept="image/*" type="file" onChange={handleImageChange} required />
          </label>
          <button className="primary-button" type="submit" disabled={isSubmitting}>
            {isSubmitting ? '전송 중...' : '화재 감지 요청'}
          </button>
          {error ? <div className="message-box">{error}</div> : null}
          {result ? (
            <div className="message-box">
              <strong>{result.status}</strong>
              <div>{result.vlmResult.emergency_report_korean_narrative}</div>
            </div>
          ) : null}
        </form>

        <aside className="panel upload-preview">
          <div className="panel-title">
            <h2>미리보기</h2>
            <span>real API 연결</span>
          </div>
          {previewUrl ? <img src={previewUrl} alt="Upload preview" /> : <div className="empty-panel">이미지를 선택해 주세요.</div>}

          <dl className="result-card">
            <div>
              <dt>status</dt>
              <dd>{result ? result.status : '-'}</dd>
            </div>
            <div>
              <dt>fireDetected</dt>
              <dd>{result ? (result.fireDetected ? 'true' : 'false') : '-'}</dd>
            </div>
            <div>
              <dt>riskLevel</dt>
              <dd>{result ? result.riskLevel : '-'}</dd>
            </div>
            <div>
              <dt>fire_confirmed</dt>
              <dd>{result ? (result.vlmResult.fire_confirmed ? 'true' : 'false') : '-'}</dd>
            </div>
            <div>
              <dt>confidence</dt>
              <dd>{result && result.vlmResult.confidence != null ? result.vlmResult.confidence.toFixed(2) : '-'}</dd>
            </div>
            <div>
              <dt>imageId</dt>
              <dd>{result ? result.imageId : '-'}</dd>
            </div>
            <div className="full-span">
              <dt>blobPath</dt>
              <dd>{result ? result.blobPath : '-'}</dd>
            </div>
          </dl>

          <div className="message-box">
            <strong>VLM 요약</strong>
            <div>{result ? result.vlmResult.emergency_report_korean_narrative : '아직 전송된 요청이 없습니다.'}</div>
          </div>

          <div className="message-box">
            <strong>응답 JSON</strong>
            <pre>{result ? JSON.stringify(result, null, 2) : '아직 전송된 요청이 없습니다.'}</pre>
          </div>
        </aside>
      </section>
    </div>
  );
}
