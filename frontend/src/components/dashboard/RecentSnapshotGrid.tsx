import { Cctv, SnapshotImage } from '../../mocks/mockData';

type Props = {
  snapshots: SnapshotImage[];
  cctvs: Cctv[];
};

export function RecentSnapshotGrid({ snapshots, cctvs }: Props) {
  return (
    <div className="snapshot-grid">
      {snapshots.map((snapshot) => {
        const cctv = cctvs.find((item) => item.cctvId === snapshot.cctvId);
        return (
          <article className="snapshot-tile" key={snapshot.imageId}>
            <img src={snapshot.imageUrl} alt={`최근 이미지 ${snapshot.imageId}`} />
            <div>
              <strong>{cctv?.cctvNum ?? `#${snapshot.imageId}`}</strong>
              <span>{new Date(snapshot.snapshotTime).toLocaleTimeString()}</span>
            </div>
          </article>
        );
      })}
    </div>
  );
}
