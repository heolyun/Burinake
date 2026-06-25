import { DetectionBox, SnapshotImage } from '../../mocks/mockData';

type Props = {
  image: SnapshotImage | null;
  boxes: DetectionBox[];
};

function toBoxStyle(box: DetectionBox, image: SnapshotImage) {
  const xs = [box.x1, box.x2, box.x3, box.x4];
  const ys = [box.y1, box.y2, box.y3, box.y4];
  const minX = Math.min(...xs);
  const minY = Math.min(...ys);
  const maxX = Math.max(...xs);
  const maxY = Math.max(...ys);

  if (box.coordinateType === 'NORMALIZED') {
    return {
      left: `${minX * 100}%`,
      top: `${minY * 100}%`,
      width: `${(maxX - minX) * 100}%`,
      height: `${(maxY - minY) * 100}%`,
    };
  }

  return {
    left: `${(minX / image.widthPx) * 100}%`,
    top: `${(minY / image.heightPx) * 100}%`,
    width: `${((maxX - minX) / image.widthPx) * 100}%`,
    height: `${((maxY - minY) / image.heightPx) * 100}%`,
  };
}

export function DetectionImageViewer({ image, boxes }: Props) {
  if (!image) {
    return <div className="empty-panel">이미지가 없습니다.</div>;
  }

  return (
    <div className="detection-viewer">
      <img src={image.imageUrl} alt="Trigger snapshot" />
      {boxes.map((box) => (
        <div
          className={`detection-box detection-${box.detectionType.toLowerCase()}`}
          key={box.boxId}
          style={toBoxStyle(box, image)}
        >
          <span>
            {box.detectionType} {(box.confidence * 100).toFixed(0)}%
          </span>
        </div>
      ))}
    </div>
  );
}
