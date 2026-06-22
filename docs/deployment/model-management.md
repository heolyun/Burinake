# Model Management Strategy

## Goal

GitHub에는 서비스 코드와 인프라 설정만 저장하고, 대용량 AI 모델 파일과 학습 산출물은 Azure VM의 별도 경로에서 독립적으로 관리합니다.

## What Stays in GitHub

- `frontend/`
- `backend/`
- `ai/yolo-server/` code only
- `ai/vlm-server/` code only
- `docker-compose.dev.yml`
- `docker-compose.prod.yml`
- `infra/`
- `scripts/`
- `.github/workflows/`

## What Must Not Be Committed

- YOLO and VLM model files: `*.pt`, `*.pth`, `*.onnx`, `*.safetensors`
- Hugging Face cache directories
- datasets and dataset variants
- training outputs
- logs
- checkpoints
- experiment artifacts

## Azure VM Model Paths

- YOLO models: `/opt/burinake/models/yolo/`
- VLM models: `/opt/burinake/models/vlm/`

These directories are mounted into containers with Docker volumes and are not part of Git pull targets.

## Deployment Behavior

The GitHub Actions deploy workflow only runs:

1. `git pull --ff-only origin develop`
2. `docker compose -f docker-compose.dev.yml up -d --build`

Because model directories live outside the repository and are mounted from `/opt/burinake/models/...`, deployment updates code and images without deleting or overwriting model assets.

## Operational Rules

- Do not store production model files under the repository path.
- Place downloaded or manually provisioned model files under `/opt/burinake/models/yolo/` or `/opt/burinake/models/vlm/`.
- Keep model version metadata in a separate ops document or filename convention.
- Replace models in-place only during approved maintenance windows.
- Back up model directories separately from application code.

## Recommended Environment Variables

- `YOLO_MODEL_DIR=/opt/burinake/models/yolo`
- `VLM_MODEL_DIR=/opt/burinake/models/vlm`

Set these in the Azure VM `.env` file used by Docker Compose.
