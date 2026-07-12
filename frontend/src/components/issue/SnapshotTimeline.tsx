import { IssueSnapshot, SnapshotImage } from '../../mocks/mockData';

type TimelineItem = IssueSnapshot & {
  snapshot: SnapshotImage | null;
};

type Props = {
  items: TimelineItem[];
};

export function SnapshotTimeline({ items }: Props) {
  return (
    <div className="timeline">
      {items.map((item) => (
        <article className="timeline-item" key={item.issueSnapshotId}>
          {item.snapshot ? <img src={item.snapshot.imageUrl} alt={`타임라인 이미지 ${item.sequenceNo}`} /> : <div />}
          <strong>{item.relativeSeconds > 0 ? `+${item.relativeSeconds}s` : `${item.relativeSeconds}s`}</strong>
          <span>#{item.sequenceNo}</span>
        </article>
      ))}
    </div>
  );
}
