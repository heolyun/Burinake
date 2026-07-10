import { type ChangeEvent, type FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { detectFire, type FireDetectionResponse } from '../lib/api/fireDetection';

type FormState = {
  cctvName: string;
  cctvNum: string;
  source: string;
  capturedAt: string;
};

type HistoryItem = {
  fileName: string;
  frameIndex: number;
  response: FireDetectionResponse;
};

const initialFormState: FormState = {
  cctvName: '정문 주차장 CCTV',
  cctvNum: 'CAM-01',
  source: 'frontend-demo',
  capturedAt: '',
};

const FRAME_INTERVAL_SECONDS = 3;

function toLocalDateTimeValue(date: Date) {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16);
}

function formatDateTime(value?: string) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

function statusLabel(status?: string) {
  if (!status) return '-';
  const labels: Record<string, string> = {
    UPLOADED: '저장됨',
    YOLO_PROCESSING: 'YOLO 분석 중',
    YOLO_DONE: 'YOLO 완료',
    VLM_PROCESSING: 'VLM 분석 중',
    COMPLETED: '완료',
    FAILED: '실패',
  };
  return labels[status] ?? status;
}

function riskLabel(level?: string) {
  if (!level) return '-';
  const labels: Record<string, string> = {
    HIGH: '높음',
    MEDIUM: '중간',
    LOW: '낮음',
    UNKNOWN: '알 수 없음',
  };
  return labels[level] ?? level;
}

function primaryMessage(result: FireDetectionResponse | null) {
  if (!result) return '아직 요청 결과가 없습니다.';
  return (
    result.vlmResult.emergency_report_korean_narrative ||
    result.vlmResult.notes ||
    result.vlmResult.risk_assessment?.rationale ||
    '분석 메시지가 없습니다.'
  );
}

function addSeconds(value: string, seconds: number) {
  const base = value ? new Date(value) : new Date();
  return new Date(base.getTime() + seconds * 1000);
}

export function FireDetectionPage() {
  const [formState, setFormState] = useState<FormState>({
    ...initialFormState,
    capturedAt: toLocalDateTimeValue(new Date()),
  });
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [currentFileIndex, setCurrentFileIndex] = useState(0);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [result, setResult] = useState<FireDetectionResponse | null>(null);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSequenceRunning, setIsSequenceRunning] = useState(false);

  const intervalRef = useRef<number | null>(null);
  const formStateRef = useRef(formState);
  const selectedFilesRef = useRef(selectedFiles);
  const sequenceIndexRef = useRef(0);
  const sequenceBaseCapturedAtRef = useRef(formState.capturedAt);
  const isSendingRef = useRef(false);

  const currentFile = selectedFiles[currentFileIndex] ?? null;
  const canSubmit = Boolean(currentFile) && !isSubmitting;
  const detectedBoxes = result?.yoloResult.boxes ?? [];

  const pipelineState = useMemo(() => {
    if (!result) return ['이미지 선택', '요청 대기'];
    if (result.status === 'UPLOADED') return ['Storage 저장', '기존 이슈 연결', 'YOLO 쿨다운으로 분석 스킵'];
    if (!result.fireDetected) return ['Storage 저장', 'YOLO 분석', '화재/연기 미탐지'];
    return ['Storage 저장', 'YOLO 탐지', 'Issue 생성 또는 연결', 'VLM 판단'];
  }, [result]);

  useEffect(() => {
    formStateRef.current = formState;
  }, [formState]);

  useEffect(() => {
    selectedFilesRef.current = selectedFiles;
  }, [selectedFiles]);

  useEffect(() => {
    if (!currentFile) {
      setPreviewUrl(null);
      return;
    }

    const nextPreviewUrl = URL.createObjectURL(currentFile);
    setPreviewUrl(nextPreviewUrl);
    return () => URL.revokeObjectURL(nextPreviewUrl);
  }, [currentFile]);

  useEffect(() => {
    return () => {
      if (intervalRef.current) window.clearInterval(intervalRef.current);
    };
  }, []);

  const updateField = (field: keyof FormState) => (event: ChangeEvent<HTMLInputElement>) => {
    setFormState((current) => ({ ...current, [field]: event.target.value }));
  };

  const handleImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    setSelectedFiles(files);
    setCurrentFileIndex(0);
    setResult(null);
    setError(null);
    setHistory([]);
  };

  const stopSequence = () => {
    if (intervalRef.current) window.clearInterval(intervalRef.current);
    intervalRef.current = null;
    setIsSequenceRunning(false);
    isSendingRef.current = false;
  };

  const submitDetection = async (file: File, frameIndex: number, capturedAtValue: string) => {
    const currentForm = formStateRef.current;
    setIsSubmitting(true);
    setError(null);

    try {
      const response = await detectFire({
        image: file,
        cctvName: currentForm.cctvName || undefined,
        cctvNum: currentForm.cctvNum || undefined,
        source: currentForm.source || undefined,
        capturedAt: new Date(capturedAtValue).toISOString(),
      });

      setResult(response);
      setHistory((current) => [{ fileName: file.name, frameIndex, response }, ...current].slice(0, 12));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '화재 감지 요청에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const sendSequenceFrame = async (index: number) => {
    const files = selectedFilesRef.current;
    const file = files[index];
    if (!file) {
      stopSequence();
      return;
    }

    if (isSendingRef.current) return;

    isSendingRef.current = true;
    setCurrentFileIndex(index);
    const capturedAt = toLocalDateTimeValue(addSeconds(sequenceBaseCapturedAtRef.current, index * FRAME_INTERVAL_SECONDS));
    setFormState((current) => ({ ...current, capturedAt }));
    await submitDetection(file, index + 1, capturedAt);
    isSendingRef.current = false;

    if (index >= files.length - 1) {
      stopSequence();
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!currentFile) {
      setError('이미지를 먼저 선택해 주세요.');
      return;
    }
    await submitDetection(currentFile, currentFileIndex + 1, formState.capturedAt);
  };

  const toggleSequence = () => {
    if (isSequenceRunning) {
      stopSequence();
      return;
    }

    if (selectedFiles.length === 0) {
      setError('여러 snapshot 이미지를 먼저 선택해 주세요.');
      return;
    }

    setError(null);
    setHistory([]);
    setResult(null);
    sequenceIndexRef.current = 0;
    sequenceBaseCapturedAtRef.current = formState.capturedAt || toLocalDateTimeValue(new Date());
    setIsSequenceRunning(true);

    void sendSequenceFrame(0);
    intervalRef.current = window.setInterval(() => {
      sequenceIndexRef.current += 1;
      void sendSequenceFrame(sequenceIndexRef.current);
    }, FRAME_INTERVAL_SECONDS * 1000);
  };

  return (
    <div className="page-stack">
      <section className="page-title split">
        <div>
          <h1>화재 감지 테스트</h1>
        </div>
        <div className="title-actions">
          <button className="secondary-button" type="button" onClick={toggleSequence} disabled={selectedFiles.length === 0}>
            {isSequenceRunning ? '순차 전송 중지' : '3초 간격 순차 전송'}
          </button>
        </div>
      </section>

      <section className="form-grid">
        <form className="panel form-panel" onSubmit={handleSubmit}>
          <label>
            CCTV 이름
            <input value={formState.cctvName} onChange={updateField('cctvName')} required />
          </label>
          <label>
            CCTV 번호
            <input value={formState.cctvNum} onChange={updateField('cctvNum')} required />
          </label>
          <label>
            Source
            <input value={formState.source} onChange={updateField('source')} required />
          </label>
          <label>
            시작 촬영 시각
            <input type="datetime-local" value={formState.capturedAt} onChange={updateField('capturedAt')} required />
          </label>
          <label className="full-span">
            Snapshot 이미지들
            <input accept="image/*" type="file" multiple onChange={handleImageChange} required />
          </label>

          {selectedFiles.length > 0 ? (
            <div className="file-list full-span">
              {selectedFiles.map((file, index) => (
                <button
                  className={index === currentFileIndex ? 'file-chip active' : 'file-chip'}
                  type="button"
                  key={`${file.name}-${file.lastModified}-${index}`}
                  onClick={() => setCurrentFileIndex(index)}
                >
                  {index + 1}. {file.name}
                </button>
              ))}
            </div>
          ) : null}

          <div className="form-footer">
            <button className="primary-button" type="submit" disabled={!canSubmit}>
              {isSubmitting ? '요청 중' : '선택 이미지 탐지'}
            </button>
            {error ? <span className="error-text">{error}</span> : null}
          </div>
        </form>

        <aside className="panel upload-preview">
          <div className="panel-title">
            <h2>Snapshot</h2>
            <span>{currentFile ? `${currentFileIndex + 1}/${selectedFiles.length} ${currentFile.name}` : '이미지 미선택'}</span>
          </div>
          {previewUrl ? <img src={previewUrl} alt="선택한 snapshot 미리보기" /> : <div className="empty-panel">이미지를 선택해 주세요.</div>}
        </aside>
      </section>

      <section className="dashboard-grid">
        <article className="panel wide">
          <div className="panel-title">
            <h2>분석 결과</h2>
            <span>{result ? formatDateTime(result.processedAt) : '-'}</span>
          </div>

          <dl className="result-card">
            <div>
              <dt>상태</dt>
              <dd>{statusLabel(result?.status)}</dd>
            </div>
            <div>
              <dt>위험도</dt>
              <dd>{riskLabel(result?.riskLevel)}</dd>
            </div>
            <div>
              <dt>화재/연기 탐지</dt>
              <dd>{result ? (result.fireDetected ? '탐지됨' : '미탐지') : '-'}</dd>
            </div>
            <div>
              <dt>YOLO confidence</dt>
              <dd>{result?.confidence == null ? '-' : result.confidence.toFixed(3)}</dd>
            </div>
            <div>
              <dt>imageId</dt>
              <dd>{result?.imageId ?? '-'}</dd>
            </div>
            <div>
              <dt>bbox 개수</dt>
              <dd>{detectedBoxes.length}</dd>
            </div>
            <div className="full-span">
              <dt>blobPath</dt>
              <dd>{result?.blobPath ?? '-'}</dd>
            </div>
          </dl>

          <div className="message-box">
            <strong>VLM 판단</strong>
            <p>{primaryMessage(result)}</p>
          </div>

          {detectedBoxes.length > 0 ? (
            <div className="compact-list">
              {detectedBoxes.map((box, index) => (
                <div key={`${box.label}-${index}`}>
                  <strong>{box.label}</strong>
                  <span>{box.score == null ? '-' : box.score.toFixed(3)}</span>
                  <span>
                    {Math.round(box.x)}, {Math.round(box.y)} / {Math.round(box.width)}x{Math.round(box.height)}
                  </span>
                </div>
              ))}
            </div>
          ) : null}
        </article>

        <aside className="panel">
          <div className="panel-title">
            <h2>처리 흐름</h2>
            <span>{pipelineState.length}단계</span>
          </div>
          <ol className="stepper vertical">
            {pipelineState.map((step, index) => (
              <li className="step done" key={step}>
                <span>{index + 1}</span>
                <p>{step}</p>
              </li>
            ))}
          </ol>
        </aside>
      </section>

      <section className="panel table-panel">
        <div className="panel-title">
          <h2>최근 요청</h2>
          <span>{history.length}건</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>frame</th>
              <th>파일</th>
              <th>imageId</th>
              <th>상태</th>
              <th>탐지</th>
              <th>위험도</th>
              <th>처리 시각</th>
            </tr>
          </thead>
          <tbody>
            {history.map((item) => (
              <tr key={`${item.response.imageId}-${item.response.processedAt}`}>
                <td>{item.frameIndex}</td>
                <td>{item.fileName}</td>
                <td>#{item.response.imageId}</td>
                <td>{statusLabel(item.response.status)}</td>
                <td>{item.response.fireDetected ? '탐지' : '미탐지'}</td>
                <td>{riskLabel(item.response.riskLevel)}</td>
                <td>{formatDateTime(item.response.processedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
