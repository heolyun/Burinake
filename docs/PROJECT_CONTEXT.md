# Burinake Project Context

## Project Summary

Burinake is an AI-based CCTV fire detection and 119 emergency reporting support system.

The system receives CCTV snapshots or uploaded fire images, asks AI services to detect and analyze fire risk, stores issue and snapshot metadata, and provides an operation dashboard for monitoring fire events and report status.

## Current Main Goal

Build a monorepo service with these parts:

- React frontend for control dashboard, fire issue pages, snapshot upload, and report views
- Spring Boot backend for API orchestration, persistence, AI server integration, and storage integration
- Python AI servers split into YOLO and VLM services
- PostgreSQL for temporary relational data storage
- Azure VM + Docker Compose for runtime
- Azure Container Registry for Docker image storage
- GitHub Actions for automatic deployment from `develop`

## High-Level Architecture

```text
User / Operator
  -> React Frontend
  -> Spring Boot Backend
  -> YOLO Server for fire detection
  -> VLM Server for fire analysis or summary
  -> PostgreSQL for event, issue, snapshot, and result metadata
  -> Azure Blob Storage planned for image, snapshot, and report file storage
```

Current runtime is Azure VM with Docker Compose. Docker images are pulled from ACR in production deployment.

## Repository Structure

```text
Burinake/
|-- frontend/
|-- backend/
|-- ai/
|   |-- yolo-server/
|   `-- vlm-server/
|-- database/
|   |-- migrations/
|   |-- schema/
|   `-- seed/
|-- docs/
|   |-- api/
|   |-- architecture/
|   |-- deployment/
|   `-- operations/
|-- infra/
|   |-- azure/
|   |-- docker/
|   |-- monitoring/
|   `-- nginx/
|-- scripts/
|-- .github/
|   `-- workflows/
|-- docker-compose.dev.yml
`-- docker-compose.prod.yml
```

## Frontend

Location: `frontend/`

Stack:

- React
- Vite
- TypeScript
- React Router
- Axios
- Zustand

Important areas:

- `src/pages/`: dashboard, issue detail/list, report list, settings, snapshot upload, fire detection page
- `src/components/`: layout, dashboard widgets, issue panels, report badges
- `src/api/`: frontend API modules and mock store
- `src/lib/api/`: shared HTTP client and fire detection API helper
- `src/routes/`: router setup
- `src/styles/`: global styling

Recent build note:

- `src/vite-env.d.ts` exists so TypeScript recognizes `import.meta.env`.
- `package-lock.json` is committed for reproducible GitHub Actions builds.

## Backend

Location: `backend/`

Stack:

- Spring Boot
- Java 21
- Gradle
- PostgreSQL

Important package areas:

- `controller`: REST controllers such as health and fire detection
- `service`: business interfaces for fire detection, persistence, storage, YOLO, and VLM clients
- `service/impl`: default and placeholder implementations
- `domain`: persistence row objects and persistence command/result objects
- `dto`: API DTOs and AI response DTOs
- `mapper`: row mapping helpers
- `client`: multipart helper
- `config`: AI and Azure storage properties

Current backend focus:

- Fire detection API flow
- Issue grouping and tracking
- Snapshot persistence flow
- YOLO detection response integration
- VLM result handling

## AI Servers

Location:

- `ai/yolo-server/`
- `ai/vlm-server/`

Stack:

- Python
- FastAPI
- Uvicorn
- YOLO service dependencies for yolo-server
- Lightweight VLM API scaffold for vlm-server

Important policy:

- AI server source code stays in Git.
- AI model files do not stay in Git.
- AI Docker images are not built by the regular GitHub Actions deployment workflow.
- AI images are expected to be built and pushed to ACR separately when needed.

## Model File Policy

Model files and AI artifacts must not be committed or included in Docker build contexts.

Ignored examples:

- `models/`
- `*.pt`
- `*.pth`
- `*.onnx`
- `*.safetensors`
- `dataset/`
- `datasets/`
- `checkpoints/`
- `logs/`
- training outputs
- Hugging Face caches

Runtime model paths on Azure VM:

```text
/opt/burinake/models/yolo/
/opt/burinake/models/vlm/
```

These paths are mounted into containers as `/app/models`.

## Database

Location: `database/`

Current DB:

- PostgreSQL
- Temporary choice for current development stage

Important files:

- `database/migrations/001_create_cctv_fire_tables.sql`
- `database/migrations/002_add_issue_tracking_columns.sql`
- `database/schema/001_init.sql`
- `database/seed/001_dev_seed.sql`

PostgreSQL data is currently persisted through Docker volume on VM.

## Storage

Current state:

- Docker images: Azure Container Registry
- AI models: Azure VM local path under `/opt/burinake/models/...`
- PostgreSQL data: VM Docker volume
- CCTV snapshots, fire event images, reports: Azure Blob Storage is wired through `ImageStorageService`

Recommended next storage work:

- Use the `fire-events` Blob container with logical prefixes for snapshots, fire event evidence, and reports
- Keep local storage available only as a development fallback when `AZURE_STORAGE_CONNECTION_STRING` is not set

## Docker Compose

Development compose:

- File: `docker-compose.dev.yml`
- Uses local `build:` sections
- Intended for local or development VM use
- Backend currently points YOLO base URL to `AI_YOLO_BASE_URL` with a default of `http://host.docker.internal:8000`
- VLM defaults to Compose service URL `http://vlm-server:8002`

Production compose:

- File: `docker-compose.prod.yml`
- Uses ACR images only
- Does not contain `build:` sections
- Uses images:
  - `${ACR_LOGIN_SERVER}/burinake-frontend:latest`
  - `${ACR_LOGIN_SERVER}/burinake-backend:latest`
  - `${ACR_LOGIN_SERVER}/burinake-yolo-server:latest`
  - `${ACR_LOGIN_SERVER}/burinake-vlm-server:latest`
- Keeps model volume mounts:
  - `${YOLO_MODEL_DIR:-/opt/burinake/models/yolo}:/app/models`
  - `${VLM_MODEL_DIR:-/opt/burinake/models/vlm}:/app/models`

## CI/CD

Workflow:

- File: `.github/workflows/deploy.yml`
- Trigger: push to `develop`
- Builds and pushes only:
  - frontend
  - backend
- Does not build:
  - yolo-server
  - vlm-server

Required GitHub Secrets:

- `ACR_LOGIN_SERVER`
- `ACR_USERNAME`
- `ACR_PASSWORD`
- `AZURE_STORAGE_CONNECTION_STRING`
- `AZURE_BLOB_CONTAINER`
- `AZURE_VM_HOST`
- `AZURE_VM_USER`
- `AZURE_VM_SSH_KEY`

Deployment script:

- File: `scripts/deploy.sh`
- Runs on Azure VM through SSH
- Logs in to ACR
- Runs `docker compose -f docker-compose.prod.yml pull`
- Runs `docker compose -f docker-compose.prod.yml up -d`
- Prints `docker ps`
- Must not run `docker compose build` on the VM

## Important Deployment Rule

Do not reintroduce VM-side builds for production deployment.

Production deployment must pull prebuilt images from ACR. This was changed because VM-side builds of AI images caused very large Docker layers and previously triggered `no space left on device`.

## Recent Context And Decisions

- ACR was introduced to prevent Azure VM from building Docker images.
- Regular GitHub Actions deployment was changed to build only frontend and backend.
- AI images are managed separately and manually pushed to ACR.
- AI code remains in the repository for source control and future maintenance.
- AI model files remain outside Git and outside Docker images.
- `.env.example` was removed because project-specific secrets and runtime values are managed through GitHub Secrets, VM environment, or local `.env` files.

## Things To Be Careful About

- Do not commit model files, datasets, training output, or checkpoints.
- Do not add `build:` sections back to `docker-compose.prod.yml`.
- Do not add YOLO/VLM build steps back to the regular `develop` deployment workflow unless explicitly requested.
- Do not store production secrets in the repository.
- Be careful with existing local generated files like `frontend/node_modules/` and `frontend/dist/`; they are not meaningful project structure changes.
- README currently has some encoding artifacts, so prefer this context document and focused docs under `docs/` for accurate current project information.

## Good Next Steps

- Add health checks to verify Azure Blob Storage connectivity during deployment.
- Document exact ACR image names and manual AI image publishing command.
- Add health checks to `docker-compose.prod.yml`.
- Add CI validation jobs for frontend build and backend test before deployment.
- Decide whether PostgreSQL should stay on VM or move to Azure Database for PostgreSQL.
