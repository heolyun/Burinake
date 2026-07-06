# ACR-Based CI/CD

## Deployment Flow

1. A push to `develop` triggers GitHub Actions.
2. GitHub Actions builds `frontend`, `backend`, `yolo-server`, and `vlm-server`.
3. The built images are pushed to Azure Container Registry.
4. GitHub Actions connects to the Azure VM over SSH.
5. The VM logs in to ACR, pulls the latest images, and runs `docker compose -f docker-compose.prod.yml up -d`.
6. The VM does not run `docker compose build` and does not build images locally.

## Why This Helps

- Heavy AI images are built once in GitHub Actions instead of on the VM.
- The VM only pulls ready-made images, which reduces local disk pressure during deployment.
- The previous `no space left on device` failure path is reduced because the VM is no longer unpacking large build stages for every deploy.

## Required GitHub Secrets

- `ACR_LOGIN_SERVER`
- `ACR_USERNAME`
- `ACR_PASSWORD`
- `AZURE_VM_HOST`
- `AZURE_VM_USER`
- `AZURE_VM_SSH_KEY`

## VM Runtime Requirements

- Docker and Docker Compose installed
- Repository checked out at `~/Burinake`
- Model directories kept outside the repo:
  - `/opt/burinake/models/yolo`
  - `/opt/burinake/models/vlm`
- A `.env` file or exported environment variables for runtime settings such as datasource and Azure storage credentials
