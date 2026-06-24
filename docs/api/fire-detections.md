# Fire Detection API

## Endpoint

- `POST /api/v1/fire-detections`

## Request

- `multipart/form-data`
- `image`: required
- `cctvName`: optional
- `cctvNum`: optional
- `source`: optional
- `capturedAt`: optional ISO-8601 datetime
- `imageId` is assigned from the database sequence

## Response Fields

- `imageId` (`Long`)
- `status`
- `blobPath`
- `fireDetected`
- `confidence`
- `riskLevel`
- `yoloResult`
- `vlmResult`
- `processedAt`

## AI Contracts

### YOLO

- `POST /api/v1/fire/analyze`
- `multipart/form-data`
- `imageId`: required
- `blobPath`: required
- `image`: required file

### VLM

- `POST /api/v1/fire/summary`
- `multipart/form-data`
- `imageId`: required
- `blobPath`: required
- `yoloResult`: required JSON string
- `image`: required file
