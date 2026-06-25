import { DetectionBox, YoloResult } from '../../mocks/mockData';

type Props = {
  result: YoloResult | null;
  boxes: DetectionBox[];
};

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
          <dt>Fire</dt>
          <dd>{result.isFire ? 'true' : 'false'}</dd>
        </div>
        <div>
          <dt>Smoke</dt>
          <dd>{result.isSmoke ? 'true' : 'false'}</dd>
        </div>
        <div>
          <dt>Fire confidence</dt>
          <dd>{(result.fireConfidence * 100).toFixed(1)}%</dd>
        </div>
        <div>
          <dt>Smoke confidence</dt>
          <dd>{(result.smokeConfidence * 100).toFixed(1)}%</dd>
        </div>
      </dl>
      <div className="compact-list">
        {boxes.map((box) => (
          <div key={box.boxId}>
            <strong>{box.detectionType}</strong>
            <span>{(box.confidence * 100).toFixed(1)}%</span>
            <small>{box.coordinateType}</small>
          </div>
        ))}
      </div>
    </section>
  );
}
