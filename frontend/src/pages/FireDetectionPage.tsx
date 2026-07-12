import { type ChangeEvent, type FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { detectFire, type FireDetectionResponse } from '../lib/api/fireDetection';

type FormState = {
  cctvName: string;
  cctvNum: string;
  source: string;
  capturedAt: string;
};

type HistoryItem = {
  id: string;
  fileName: string;
  frameIndex: number;
  capturedAt: string;
  startedAt: string;
  completedAt?: string;
  response?: FireDetectionResponse;
  errorMessage?: string;
};

const FRAME_INTERVAL_SECONDS = 3;

const initialFormState: FormState = {
  cctvName: '데모 주차장 CCTV',
  cctvNum: 'CAM-DEMO-01',
  source: 'frontend-demo-sequence',
  capturedAt: '',
};

function toLocalDateTimeValue(date: Date) {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 19);
}

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

function statusLabel(status?: string) {
  if (!status) return '대기';
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
    UNKNOWN: '미판단',
  };
  return labels[level] ?? level;
}

function primaryMessage(result?: FireDetectionResponse | null) {
  if (!result) return '아직 분석 결과가 없습니다.';
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

function makeHistoryId(frameIndex: number, file: File) {
  return `${frameIndex}-${file.name}-${file.lastModified}-${Date.now()}`;
}

function pipelineSteps(item?: HistoryItem | null) {
  if (!item) return ['이미지 선택', '전송 대기'];
  if (!item.response && !item.errorMessage) return ['요청 전송', '백엔드 처리 중'];
  if (item.errorMessage || item.response?.status === 'FAILED') return ['이미지 저장 시도', '분석 실패', '오류 확인'];
  if (!item.response?.fireDetected) return ['이미지 저장', 'YOLO 분석', '화재/연기 미탐지'];
  return ['이미지 저장', 'YOLO 탐지', '이슈 생성/연결', 'VLM 판단'];
}

export function FireDetectionPage() {
  const [formState, setFormState] = useState<FormState>({
    ...initialFormState,
    capturedAt: toLocalDateTimeValue(new Date()),
  });
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [currentFileIndex, setCurrentFileIndex] = useState(0);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [selectedHistoryId, setSelectedHistoryId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSequenceRunning, setIsSequenceRunning] = useState(false);

  const intervalRef = useRef<number | null>(null);
  const formStateRef = useRef(formState);
  const selectedFilesRef = useRef(selectedFiles);
  const sequenceIndexRef = useRef(0);
  const sequenceBaseCapturedAtRef = useRef(formState.capturedAt);
  const inFlightRequestCountRef = useRef(0);

  const currentFile = selectedFiles[currentFileIndex] ?? null;
  const completedFrames = history.filter((item) => item.response || item.errorMessage).length;
  const sentFrames = history.length;
  const progressRatio = selectedFiles.length > 0 ? Math.min(100, Math.round((sentFrames / selectedFiles.length) * 100)) : 0;
  const selectedHistory = history.find((item) => item.id === selectedHistoryId) ?? history[0] ?? null;
  const selectedResult = selectedHistory?.response ?? null;
  const detectedBoxes = selectedResult?.yoloResult.boxes ?? [];
  const canSubmit = Boolean(currentFile) && !isSubmitting;

  const latestSummary = useMemo(() => {
    if (isSequenceRunning) return `${sentFrames}/${selectedFiles.length}장 전송 중, 응답 대기 ${inFlightRequestCountRef.current}건`;
    if (history.length > 0) return `${completedFrames}/${history.length}건 처리 완료`;
    return '전송을 시작하면 프레임별 처리 결과가 이곳에 표시됩니다.';
  }, [completedFrames, history.length, isSequenceRunning, selectedFiles.length, sentFrames]);

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
    setHistory([]);
    setSelectedHistoryId(null);
    setError(null);
  };

  const stopSequence = () => {
    if (intervalRef.current) window.clearInterval(intervalRef.current);
    intervalRef.current = null;
    setIsSequenceRunning(false);
  };

  const upsertHistory = (item: HistoryItem) => {
    setHistory((current) => {
      const exists = current.some((historyItem) => historyItem.id === item.id);
      if (exists) {
        return current.map((historyItem) => (historyItem.id === item.id ? item : historyItem));
      }
      return [...current, item].sort((a, b) => a.frameIndex - b.frameIndex);
    });
    setSelectedHistoryId((current) => current ?? item.id);
  };

  const submitDetection = async (file: File, frameIndex: number, capturedAtValue: string) => {
    const currentForm = formStateRef.current;
    const startedAt = new Date().toISOString();
    const historyId = makeHistoryId(frameIndex, file);
    const pendingItem: HistoryItem = {
      id: historyId,
      fileName: file.name,
      frameIndex,
      capturedAt: capturedAtValue,
      startedAt,
    };

    upsertHistory(pendingItem);
    inFlightRequestCountRef.current += 1;
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

      const completedItem = {
        ...pendingItem,
        completedAt: new Date().toISOString(),
        response,
        errorMessage: response.status === 'FAILED' ? response.errorMessage || '분석 처리 중 오류가 발생했습니다.' : undefined,
      };
      upsertHistory(completedItem);
      setSelectedHistoryId(completedItem.id);
      setError(completedItem.errorMessage ?? null);
    } catch (requestError) {
      const errorMessage = requestError instanceof Error ? requestError.message : '화재 감지 요청에 실패했습니다.';
      upsertHistory({
        ...pendingItem,
        completedAt: new Date().toISOString(),
        errorMessage,
      });
      setError(errorMessage);
    } finally {
      inFlightRequestCountRef.current = Math.max(0, inFlightRequestCountRef.current - 1);
      setIsSubmitting(inFlightRequestCountRef.current > 0);
    }
  };

  const sendSequenceFrame = (index: number) => {
    const files = selectedFilesRef.current;
    const file = files[index];
    if (!file) {
      stopSequence();
      return;
    }

    setCurrentFileIndex(index);
    const capturedAt = toLocalDateTimeValue(addSeconds(sequenceBaseCapturedAtRef.current, index * FRAME_INTERVAL_SECONDS));
    setFormState((current) => ({ ...current, capturedAt }));
    void submitDetection(file, index + 1, capturedAt);

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
      setError('여러 이미지를 먼저 선택해 주세요.');
      return;
    }

    setError(null);
    setHistory([]);
    setSelectedHistoryId(null);
    sequenceIndexRef.current = 0;
    sequenceBaseCapturedAtRef.current = formState.capturedAt || toLocalDateTimeValue(new Date());
    setIsSequenceRunning(true);

    sendSequenceFrame(0);
    intervalRef.current = window.setInterval(() => {
      sequenceIndexRef.current += 1;
      sendSequenceFrame(sequenceIndexRef.current);
    }, FRAME_INTERVAL_SECONDS * 1000);
  };

  return (
    <div className="page-stack test-console-page">
      <section className="test-hero">
        <div>
          <span className="eyebrow">테스트 콘솔</span>
          <h1>화재 감지 테스트</h1>
          <p>여러 이미지를 3초 간격으로 전송하고 저장, 탐지, 이슈 연결 결과를 프레임별로 확인합니다.</p>
        </div>
        <div className="test-hero-actions">
          <button className="primary-button" type="button" onClick={toggleSequence} disabled={selectedFiles.length === 0}>
            {isSequenceRunning ? '순차 전송 중지' : '3초 간격 순차 전송'}
          </button>
          <Link className="secondary-button" to="/">
            대시보드 보기
          </Link>
        </div>
      </section>

      <section className="test-status-strip">
        <article>
          <span>선택 이미지</span>
          <strong>{selectedFiles.length}장</strong>
        </article>
        <article>
          <span>전송</span>
          <strong>{sentFrames}건</strong>
        </article>
        <article>
          <span>완료</span>
          <strong>{completedFrames}건</strong>
        </article>
        <article>
          <span>상태</span>
          <strong>{isSequenceRunning ? '전송 중' : '대기'}</strong>
        </article>
      </section>

      <section className="test-progress-panel panel">
        <div className="progress-head">
          <strong>{latestSummary}</strong>
          <span>{progressRatio}%</span>
        </div>
        <div className="progress-track">
          <span style={{ width: `${progressRatio}%` }} />
        </div>
      </section>

      {error ? <div className="message-box error-text">{error}</div> : null}

      <section className="test-layout">
        <form className="panel test-input-panel" onSubmit={handleSubmit}>
          <div className="panel-title">
            <h2>입력 세트</h2>
            <span>입력 기준</span>
          </div>
          <div className="test-form-grid">
            <label>
              CCTV 이름
              <input value={formState.cctvName} onChange={updateField('cctvName')} required />
            </label>
            <label>
              CCTV 번호
              <input value={formState.cctvNum} onChange={updateField('cctvNum')} required />
            </label>
            <label>
              입력 출처
              <input value={formState.source} onChange={updateField('source')} required />
            </label>
            <label>
              시작 촬영 시각
              <input type="datetime-local" step="1" value={formState.capturedAt} onChange={updateField('capturedAt')} required />
            </label>
          </div>
          <label className="file-drop">
            <span>이미지 선택</span>
            <input accept="image/*" type="file" multiple onChange={handleImageChange} required />
          </label>
          {selectedFiles.length > 0 ? (
            <div className="file-strip">
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
            <button className="secondary-button" type="submit" disabled={!canSubmit}>
              선택 이미지 1장 전송
            </button>
          </div>
        </form>

        <aside className="panel test-preview-panel">
          <div className="panel-title">
            <h2>현재 프레임</h2>
            <span>{currentFile ? `${currentFileIndex + 1}/${selectedFiles.length}` : '미선택'}</span>
          </div>
          {previewUrl ? <img src={previewUrl} alt="선택 이미지 미리보기" /> : <div className="empty-panel">이미지를 선택해 주세요.</div>}
          <p>{currentFile?.name ?? '선택된 파일이 없습니다.'}</p>
        </aside>
      </section>

      <section className="test-result-layout">
        <article className="panel frame-log-panel">
          <div className="panel-title">
            <h2>프레임 처리 로그</h2>
            <span>{history.length}건</span>
          </div>
          <div className="frame-log-list">
            {history.map((item) => {
              const active = selectedHistory?.id === item.id;
              const failed = Boolean(item.errorMessage || item.response?.status === 'FAILED');
              return (
                <button className={`frame-log-row ${active ? 'active' : ''} ${failed ? 'failed' : ''}`} key={item.id} type="button" onClick={() => setSelectedHistoryId(item.id)}>
                  <span>#{item.frameIndex}</span>
                  <strong>{item.fileName}</strong>
                  <em>{item.response ? statusLabel(item.response.status) : item.errorMessage ? '실패' : '처리 중'}</em>
                  <small>{item.response ? (item.response.fireDetected ? '탐지' : '미탐지') : '-'}</small>
                  <small>{item.response ? riskLabel(item.response.riskLevel) : '-'}</small>
                  <small>{formatDateTime(item.completedAt ?? item.startedAt)}</small>
                </button>
              );
            })}
            {history.length === 0 ? <div className="empty-panel">아직 전송 기록이 없습니다.</div> : null}
          </div>
        </article>

        <aside className="panel frame-detail-panel">
          <div className="panel-title">
            <h2>선택 프레임 상세</h2>
            <span>{selectedHistory ? `frame #${selectedHistory.frameIndex}` : '-'}</span>
          </div>
          {selectedHistory ? (
            <>
              <dl className="result-card compact-result-card">
                <div>
                  <dt>파일</dt>
                  <dd>{selectedHistory.fileName}</dd>
                </div>
                <div>
                  <dt>촬영 시각</dt>
                  <dd>{formatDateTime(selectedHistory.capturedAt)}</dd>
                </div>
                <div>
                  <dt>이미지 ID</dt>
                  <dd>{selectedResult?.imageId ?? '-'}</dd>
                </div>
                <div>
                  <dt>상태</dt>
                  <dd>{statusLabel(selectedResult?.status)}</dd>
                </div>
                <div>
                  <dt>탐지</dt>
                  <dd>{selectedResult ? (selectedResult.fireDetected ? '탐지' : '미탐지') : '-'}</dd>
                </div>
                <div>
                  <dt>위험도</dt>
                  <dd>{riskLabel(selectedResult?.riskLevel)}</dd>
                </div>
                <div>
                  <dt>YOLO 신뢰도</dt>
                  <dd>{selectedResult?.confidence == null ? '-' : selectedResult.confidence.toFixed(3)}</dd>
                </div>
                <div>
                  <dt>bbox</dt>
                  <dd>{detectedBoxes.length}개</dd>
                </div>
                <div className="full-span">
                  <dt>저장 경로</dt>
                  <dd>{selectedResult?.blobPath ?? '-'}</dd>
                </div>
              </dl>

              <div className="pipeline-mini">
                {pipelineSteps(selectedHistory).map((step, index) => (
                  <span key={`${step}-${index}`}>{step}</span>
                ))}
              </div>

              <div className="message-box">
                <strong>VLM 판단 / 오류</strong>
                {selectedHistory.errorMessage ? <p className="error-text">{selectedHistory.errorMessage}</p> : null}
                {selectedResult?.errorMessage ? <p className="error-text">{selectedResult.errorMessage}</p> : null}
                <p>{primaryMessage(selectedResult)}</p>
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
            </>
          ) : (
            <div className="empty-panel">프레임 로그를 선택하면 상세가 표시됩니다.</div>
          )}
        </aside>
      </section>
    </div>
  );
}
