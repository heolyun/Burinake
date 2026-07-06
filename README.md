# Burinake

AI 기반 CCTV 화재 감지 및 119 신고 지원 시스템을 위한 모노레포입니다.

## Tech Stack

- Frontend: React, Vite
- Backend: Spring Boot, Java
- AI Server: Python, FastAPI, YOLO, VLM
- Database: PostgreSQL 임시 로컬 DB
- Storage: Azure Blob Storage
- Container: Docker
- Deployment: Azure
- Version Control / CI: Git, GitHub, GitHub Actions

## Project Tree

```text
Burinake/
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── app/
│   │   ├── assets/
│   │   ├── components/
│   │   │   ├── common/
│   │   │   └── layout/
│   │   ├── config/
│   │   ├── features/
│   │   │   ├── auth/
│   │   │   ├── cameras/
│   │   │   ├── dashboard/
│   │   │   ├── fire-events/
│   │   │   ├── notifications/
│   │   │   └── reports/
│   │   ├── hooks/
│   │   ├── lib/api/
│   │   ├── pages/
│   │   ├── routes/
│   │   ├── stores/
│   │   ├── styles/
│   │   ├── types/
│   │   └── utils/
│   └── tests/
├── backend/
│   └── src/
│       ├── main/java/com/burinake/
│       │   ├── ai/
│       │   ├── cctv/
│       │   ├── common/
│       │   ├── controller/
│       │   ├── domain/
│       │   ├── dto/
│       │   ├── fire/
│       │   ├── repository/
│       │   ├── report/
│       │   ├── service/
│       │   └── storage/
│       └── main/resources/
├── ai/
│   ├── yolo-server/
│   │   ├── app/api/
│   │   ├── app/config/
│   │   ├── app/inference/
│   │   ├── app/model/
│   │   ├── app/schemas/
│   │   ├── app/services/
│   │   ├── app/utils/
│   └── vlm-server/
│       ├── app/api/
│       ├── app/config/
│       ├── app/inference/
│       ├── app/model/
│       ├── app/schemas/
│       ├── app/services/
│       └── app/utils/
├── infra/
│   ├── azure/
│   │   ├── bicep/
│   │   ├── container-apps/
│   │   ├── scripts/
│   │   └── storage/
│   ├── docker/
│   ├── monitoring/
│   └── nginx/
├── database/
│   ├── migrations/
│   ├── schema/
│   └── seed/
├── docs/
│   ├── api/
│   ├── architecture/
│   ├── deployment/
│   └── operations/
├── scripts/
├── .github/workflows/
├── docker-compose.dev.yml
├── docker-compose.prod.yml
```

## Folder Roles

- `frontend`: CCTV 관제 화면, 화재 이벤트 대시보드, 신고 지원 UI를 담당합니다.
- `backend`: 인증, 카메라/화재 이벤트 관리, AI 서버 연동, Azure Blob Storage 연동, 신고 지원 API를 담당합니다.
- `ai/yolo-server`: CCTV 프레임 또는 이미지에서 화재 후보를 탐지하는 YOLO 기반 서버입니다.
- `ai/vlm-server`: YOLO 탐지 결과를 VLM으로 검증하거나 설명을 생성하는 서버입니다.
- `/opt/burinake/models/*`: Azure VM에서 별도 유지하는 모델 저장 경로이며 Git과 Docker 빌드 대상에서 제외합니다.
- `infra/docker`: 공통 Docker 설정, 이미지 빌드 정책, 운영 문서를 둡니다.
- `infra/nginx`: 운영 환경 reverse proxy 설정을 둡니다.
- `infra/azure`: Azure Container Apps, Azure Blob Storage, Key Vault, 네트워크 등 클라우드 배포 리소스를 둡니다.
- `database`: DB가 확정되기 전까지 PostgreSQL 기반 임시 schema, seed, migration 파일을 관리합니다.
- `docs`: 아키텍처, API 계약, 배포, 운영 문서를 관리합니다.
- `.github/workflows`: CI/CD 파이프라인을 추가할 위치입니다.
- `scripts`: 로컬 개발, 배포 보조 스크립트를 둡니다.

## Dockerfile Locations

- `frontend/Dockerfile`
- `backend/Dockerfile`
- `ai/yolo-server/Dockerfile`
- `ai/vlm-server/Dockerfile`

## Environments

- Dev: `docker-compose.dev.yml`, `application-dev.yml`
- Prod: `docker-compose.prod.yml`, `application-prod.yml`, Azure Bicep parameters

## Local Start

```bash
docker compose -f docker-compose.dev.yml up --build
```

## Database Note

현재 DB는 최종 확정 전 임시 PostgreSQL 기준입니다. 추후 DB가 변경되면 `database/`, Backend datasource 설정, compose의 `postgres` 서비스를 교체하면 됩니다.
