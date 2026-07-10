import { type FormEvent, useEffect, useState } from 'react';
import { createCctv, deactivateCctv, getCctvs, updateCctv, type Cctv } from '../api/cctvApi';

type FormState = {
  cctvName: string;
  cctvNum: string;
  location: string;
  isActive: boolean;
};

const emptyForm: FormState = {
  cctvName: '',
  cctvNum: '',
  location: '',
  isActive: true,
};

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

export function SettingsPage() {
  const [cctvs, setCctvs] = useState<Cctv[]>([]);
  const [formState, setFormState] = useState<FormState>(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadCctvs = async () => {
    setIsLoading(true);
    setError(null);
    try {
      setCctvs(await getCctvs());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'CCTV 목록을 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadCctvs();
  }, []);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSaving(true);
    setError(null);
    try {
      const payload = {
        cctvName: formState.cctvName,
        cctvNum: formState.cctvNum,
        location: formState.location,
        isActive: formState.isActive,
      };
      if (editingId) {
        await updateCctv(editingId, payload);
      } else {
        await createCctv(payload);
      }
      setFormState(emptyForm);
      setEditingId(null);
      await loadCctvs();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'CCTV 정보를 저장하지 못했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  const edit = (cctv: Cctv) => {
    setEditingId(cctv.cctvId);
    setFormState({
      cctvName: cctv.cctvName,
      cctvNum: cctv.cctvNum,
      location: cctv.location ?? '',
      isActive: cctv.isActive,
    });
  };

  const deactivate = async (cctvId: number) => {
    setIsSaving(true);
    setError(null);
    try {
      await deactivateCctv(cctvId);
      await loadCctvs();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'CCTV를 비활성화하지 못했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="page-stack">
      <section className="page-title">
        <div>
          <h1>설정</h1>
        </div>
      </section>

      <section className="panel">
        <div className="panel-title">
          <h2>런타임 설정</h2>
          <span>환경변수 기준</span>
        </div>
        <dl className="result-card">
          <div>
            <dt>Frontend API base URL</dt>
            <dd>{import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}</dd>
          </div>
          <div>
            <dt>Fire detection endpoint</dt>
            <dd>/api/v1/fire-detections</dd>
          </div>
        </dl>
      </section>

      {error ? <div className="message-box error-text">{error}</div> : null}

      <section className="detail-grid">
        <form className="panel form-panel" onSubmit={submit}>
          <div className="panel-title">
            <h2>{editingId ? `CCTV #${editingId} 수정` : 'CCTV 등록'}</h2>
            <span>{isSaving ? '저장 중' : '대기'}</span>
          </div>
          <label>
            CCTV 이름
            <input
              value={formState.cctvName}
              onChange={(event) => setFormState((prev) => ({ ...prev, cctvName: event.target.value }))}
              required
            />
          </label>
          <label>
            CCTV 번호
            <input
              value={formState.cctvNum}
              onChange={(event) => setFormState((prev) => ({ ...prev, cctvNum: event.target.value }))}
              required
            />
          </label>
          <label>
            위치
            <input
              value={formState.location}
              onChange={(event) => setFormState((prev) => ({ ...prev, location: event.target.value }))}
            />
          </label>
          <label className="checkbox-row">
            <input
              checked={formState.isActive}
              type="checkbox"
              onChange={(event) => setFormState((prev) => ({ ...prev, isActive: event.target.checked }))}
            />
            활성화
          </label>
          <div className="form-footer">
            <button className="primary-button" type="submit" disabled={isSaving}>
              {editingId ? '수정 저장' : '등록'}
            </button>
            {editingId ? (
              <button
                className="ghost-button"
                type="button"
                disabled={isSaving}
                onClick={() => {
                  setEditingId(null);
                  setFormState(emptyForm);
                }}
              >
                취소
              </button>
            ) : null}
          </div>
        </form>

        <section className="panel table-panel">
          <div className="panel-title padded-title">
            <h2>CCTV 목록</h2>
            <span>{cctvs.length}대</span>
          </div>
          {isLoading ? (
            <div className="empty-panel">CCTV 목록을 불러오는 중입니다.</div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>이름</th>
                  <th>번호</th>
                  <th>상태</th>
                  <th>수정일</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {cctvs.map((cctv) => (
                  <tr key={cctv.cctvId}>
                    <td>#{cctv.cctvId}</td>
                    <td>
                      <strong>{cctv.cctvName}</strong>
                      <span>{cctv.location ?? '-'}</span>
                    </td>
                    <td>{cctv.cctvNum}</td>
                    <td>
                      <span className={`badge ${cctv.isActive ? 'status-active' : 'status-inactive'}`}>
                        {cctv.isActive ? '활성' : '비활성'}
                      </span>
                    </td>
                    <td>{formatDateTime(cctv.updatedAt)}</td>
                    <td>
                      <div className="row-actions">
                        <button className="secondary-button" type="button" disabled={isSaving} onClick={() => edit(cctv)}>
                          수정
                        </button>
                        {cctv.isActive ? (
                          <button className="ghost-button" type="button" disabled={isSaving} onClick={() => void deactivate(cctv.cctvId)}>
                            비활성화
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </section>
    </div>
  );
}
