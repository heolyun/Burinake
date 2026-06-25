import { VlmResult } from '../../mocks/mockData';
import { IssueLevelBadge } from './IssueLevelBadge';

type Props = {
  result: VlmResult | null;
};

export function VlmResultPanel({ result }: Props) {
  if (!result) return <div className="empty-panel">VLM 분석 결과가 없습니다.</div>;

  return (
    <section className="panel">
      <div className="panel-title">
        <h3>VLM 분석</h3>
        <IssueLevelBadge level={result.level} />
      </div>
      <dl className="kv-grid">
        <div>
          <dt>실제 화재</dt>
          <dd>{result.isRealFire ? 'true' : 'false'}</dd>
        </div>
        <div>
          <dt>신뢰도</dt>
          <dd>{(result.confidence * 100).toFixed(1)}%</dd>
        </div>
        <div>
          <dt>발화 지점</dt>
          <dd>{result.fireStart}</dd>
        </div>
        <div>
          <dt>추정 원인</dt>
          <dd>{result.fireReason}</dd>
        </div>
      </dl>
      <p className="message-box">{result.message}</p>
    </section>
  );
}
