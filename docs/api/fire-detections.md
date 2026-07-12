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

## Issue Grouping

- If a CCTV already has an open issue in the last 5 minutes, the new snapshot is attached to that issue instead of creating another issue.
- Once an issue is open, subsequent snapshots from the same CCTV are kept on the issue timeline as temporal context even when YOLO analysis is skipped or returns no detection.
- YOLO is skipped for an open issue until at least 10 seconds have passed since the last YOLO analysis.
- VLM is called again only when the issue worsens: smoke becomes fire, fire and smoke are both detected, the largest bbox area grows sharply, or 30 seconds have passed since the last VLM analysis.
- New issues send only the trigger snapshot to VLM. Existing issue re-analysis sends up to 6 snapshots: the current snapshot plus up to 5 prior snapshots ordered by `snapshot_time`.
- YOLO confidence is stored, but it is not used as the escalation trigger.

## VLM Runtime

- The main issue pipeline calls Azure OpenAI directly through `AzureOpenAiSequenceVlmClient`.
- Configure Azure OpenAI with `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_DEPLOYMENT_NAME`, and `AZURE_OPENAI_API_VERSION`.
- `vlm-server` exposes `/api/v1/fire/summary` for legacy/local fallback flows, but it is not the main sequence VLM path.

## AI Contracts

### YOLO

- `POST /api/detect`
- `multipart/form-data`
- `imageId`: required
- `capturedAt`: required ISO-8601 datetime
- `image`: required file

Expected response:

```json
{
  "result": {
    "detected": true,
    "boxes": [
      {
        "x": 120.5,
        "y": 80.2,
        "width": 240.1,
        "height": 180.5,
        "label": "fire",
        "score": 0.92
      }
    ]
  },
  "metadata": {
    "imageId": "123",
    "capturedAt": "2026-07-08T14:47:00+09:00"
  }
}
```

### VLM

- `POST /api/v1/fire/summary`
- `multipart/form-data`
- `imageId`: required
- `blobPath`: required
- `yoloResult`: required JSON string
- `image`: required file
