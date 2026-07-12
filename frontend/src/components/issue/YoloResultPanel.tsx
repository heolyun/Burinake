import { DetectionBox, YoloResult } from '../../mocks/mockData';

type Props = {
  result: YoloResult | null;
  boxes: DetectionBox[];
};

function detectionTypeLabel(type: string) {
  const labels: Record<string, string> = {
    FIRE: '화재',
    SMOKE: '연기',
  };
  return labels[type] ?? type;
}

export function YoloResultPanel({ result, boxes }: Props) {
  if (!result) return <div className="empty-panel">YOLO 결과가 없습니다.</div>;

  return (
    <section className="panel">
      <div className="panel-title">
        <h3>YOLO 분석</h3>
        <span>{new Date(result.analyzedAt).toLocaleString()}</span>
      </div>
      <dl className="kv-grid">
        <div>
          <dt>화재 탐지</dt>
          <dd>{result.isFire ? '탐지' : '미탐지'}</dd>
        </div>
        <div>
          <dt>연기 탐지</dt>
          <dd>{result.isSmoke ? '탐지' : '미탐지'}</dd>
        </div>
        <div>
          <dt>화재 신뢰도</dt>
          <dd>{(result.fireConfidence * 100).toFixed(1)}%</dd>
        </div>
        <div>
          <dt>연기 신뢰도</dt>
          <dd>{(result.smokeConfidence * 100).toFixed(1)}%</dd>
        </div>
      </dl>
      <div className="compact-list">
        {boxes.map((box) => (
          <div key={box.boxId}>
            <strong>{detectionTypeLabel(box.detectionType)}</strong>
            <span>{(box.confidence * 100).toFixed(1)}%</span>
            <small>{box.coordinateType}</small>
          </div>
        ))}
      </div>
    </section>
  );
}
