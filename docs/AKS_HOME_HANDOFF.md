# Burinake AKS Home Handoff

Last updated: 2026-07-09

This file is for continuing the AKS migration work from another computer.

## Current Situation

The AKS migration work has been started on a separate Git branch.

Branch:

```text
k8s
```

Remote branch:

```text
origin/k8s
```

Latest AKS commit:

```text
598de5e AKS deployment draft added
```

The existing `develop` branch and Azure VM deployment are intentionally left untouched.

## Important Note About Local Worktree

On the school computer, `docs/PROJECT_CONTEXT.md` appeared as deleted in the local worktree.

That deletion was not included in the AKS commit.

When continuing from home, the safest approach is to freshly clone or fetch from GitHub and checkout `k8s`.

## Project Goal

Burinake is an AI-based CCTV fire detection and 119 emergency reporting support system.

Current production-like deployment:

```text
Azure VM
  -> Docker Compose
  -> ACR images
  -> frontend
  -> backend
  -> postgres
  -> yolo-server
  -> vlm-server
  -> Azure Blob Storage
  -> Azure OpenAI / AI Foundry
```

Target AKS direction:

```text
AKS
  -> frontend Deployment + Service
  -> backend Deployment + Service
  -> yolo-server Deployment + Service
  -> vlm-server Deployment + Service
  -> temporary PostgreSQL StatefulSet
  -> Azure Blob Storage reused
  -> Azure OpenAI / AI Foundry reused
  -> ACR reused
```

## Existing Azure Resources To Reuse

Do not redesign from scratch. Reuse existing Azure resources where possible.

- Resource Group: `Burinake`
- ACR: `burinakeacr`
- Storage Account: `burinakestorage`
- Blob Container: `fire-events`
- Azure OpenAI / AI Foundry: existing Burinake resources
- Existing VM: keep as fallback until AKS is verified

## Files Added For AKS

Kubernetes manifests:

```text
infra/k8s/
|-- README.md
|-- namespace.yaml
|-- configmap.yaml
|-- secrets.example.yaml
|-- frontend.yaml
|-- backend.yaml
|-- yolo-server.yaml
|-- vlm-server.yaml
|-- postgres.yaml
|-- ingress.yaml
`-- kustomization.yaml
```

Documentation:

```text
docs/deployment/aks-deployment.md
```

Manual deploy script:

```text
scripts/deploy-aks.sh
```

Manual GitHub Actions workflow:

```text
.github/workflows/deploy-aks.yml
```

## What Was Implemented

### Kubernetes Manifests

Created namespace:

```text
burinake
```

Created ConfigMap:

```text
burinake-config
```

Important config values:

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/burinake
SPRING_DATASOURCE_USERNAME=burinake
AI_YOLO_BASE_URL=http://yolo-server:8001
AI_VLM_BASE_URL=http://vlm-server:8002
AZURE_BLOB_CONTAINER=fire-events
AZURE_OPENAI_DEPLOYMENT_NAME=Burinake-vlm
AZURE_OPENAI_API_VERSION=2024-02-15-preview
```

Created `secrets.example.yaml` only. It contains placeholders and must not be applied to real environments with placeholder values.

### Services

Frontend:

- Image: `burinakeacr.azurecr.io/burinake-frontend:latest`
- Deployment + Service
- Service type: `LoadBalancer`
- Port: `80`

Backend:

- Image: `burinakeacr.azurecr.io/burinake-backend:latest`
- Deployment + Service
- Service type: `ClusterIP`
- Port: `8080`
- Health probe: `/actuator/health`

YOLO:

- Image: `burinakeacr.azurecr.io/burinake-yolo-server:latest`
- Deployment + Service
- Service type: `ClusterIP`
- Port: `8001`
- Health probe: `/health`
- Mounts `/app/models` as `emptyDir` placeholder

VLM:

- Image: `burinakeacr.azurecr.io/burinake-vlm-server:latest`
- Deployment + Service
- Service type: `ClusterIP`
- Port: `8002`
- Health probe: `/health`
- Uses Azure OpenAI / AI Foundry API through env vars

PostgreSQL:

- Temporary StatefulSet for staging
- Service name: `postgres`
- PVC size: `10Gi`
- Not final production DB architecture

Ingress:

- `infra/k8s/ingress.yaml` exists
- It is not included in `kustomization.yaml`
- Use it later after installing an ingress controller

### GitHub Actions

Added separate workflow:

```text
.github/workflows/deploy-aks.yml
```

It is manual only:

```text
workflow_dispatch
```

It does not replace the existing VM deployment workflow.

It can optionally build and push frontend/backend images, then apply AKS manifests.

It does not build YOLO/VLM images.

## Current Validation Done

The following local validation passed:

```bash
kubectl kustomize infra/k8s
```

`kubectl apply -k infra/k8s --dry-run=client` timed out because the school computer had no AKS cluster context/login configured yet.

No real Azure login or AKS cluster creation has been done yet.

## Rules To Preserve

Do not break these rules:

- Do not commit model files.
- Do not commit datasets, checkpoints, logs, or training output.
- Do not include model files in Docker images.
- Do not hardcode Azure secrets or connection strings.
- Do not reintroduce VM-side production builds.
- Do not make YOLO/VLM build on every regular deployment.
- Keep ACR as image registry.
- Keep Azure Blob Storage integration.
- Keep Azure OpenAI / AI Foundry external.
- Keep the current VM deployment until AKS is verified.

## Home Computer Setup

From home, start with:

```bash
git clone https://github.com/heolyun/Burinake.git
cd Burinake
git checkout k8s
```

If already cloned:

```bash
cd Burinake
git fetch origin
git checkout k8s
git pull origin k8s
```

Install tools on Windows if missing:

```powershell
winget install Microsoft.AzureCLI
```

Restart terminal, then:

```bash
az --version
az login
az aks install-cli
kubectl version --client
```

## Next Manual Azure Steps

Create AKS:

```bash
az aks create \
  --resource-group Burinake \
  --name burinake-aks \
  --node-count 1 \
  --node-vm-size Standard_B2s \
  --generate-ssh-keys \
  --attach-acr burinakeacr
```

Connect kubectl:

```bash
az aks get-credentials \
  --resource-group Burinake \
  --name burinake-aks \
  --overwrite-existing
```

Verify:

```bash
kubectl get nodes
```

Create namespace:

```bash
kubectl apply -f infra/k8s/namespace.yaml
```

Create real runtime secret:

```bash
kubectl create secret generic burinake-secret \
  --from-literal=POSTGRES_PASSWORD="..." \
  --from-literal=AZURE_STORAGE_CONNECTION_STRING="..." \
  --from-literal=AZURE_OPENAI_ENDPOINT="..." \
  --from-literal=AZURE_OPENAI_API_KEY="..." \
  --from-literal=AZURE_OPENAI_DEPLOYMENT_NAME="Burinake-vlm" \
  --from-literal=AZURE_OPENAI_API_VERSION="2024-02-15-preview" \
  -n burinake
```

Deploy:

```bash
kubectl apply -k infra/k8s
```

Or:

```bash
./scripts/deploy-aks.sh
```

Check status:

```bash
kubectl get pods -n burinake
kubectl get svc -n burinake
kubectl logs deployment/backend -n burinake
```

Get frontend external IP:

```bash
kubectl get svc frontend -n burinake
```

## ACR Images Required

These images must exist in ACR:

```text
burinakeacr.azurecr.io/burinake-frontend:latest
burinakeacr.azurecr.io/burinake-backend:latest
burinakeacr.azurecr.io/burinake-yolo-server:latest
burinakeacr.azurecr.io/burinake-vlm-server:latest
```

Frontend/backend can be built by the manual AKS workflow or existing process.

YOLO/VLM are still separately managed heavy images.

## Known Limitations

YOLO model storage is not solved yet.

Current manifest uses:

```text
emptyDir -> /app/models
```

This only preserves the container path. It does not provide the real model.

Recommended future options:

1. Azure Files PersistentVolume mounted at `/app/models`
2. Blob Storage initContainer downloading model files into `emptyDir`
3. Do not use model-included images unless absolutely necessary

PostgreSQL is temporary in-cluster storage for staging. For production, consider Azure Database for PostgreSQL Flexible Server.

Ingress is prepared but disabled by default.

Images use `latest`. Later, prefer immutable Git SHA tags.

## Suggested Next Work For Codex At Home

1. Verify `k8s` branch is checked out.
2. Confirm no accidental local deletion is present.
3. Run `kubectl kustomize infra/k8s`.
4. Install Azure CLI and kubectl if needed.
5. Run `az login`.
6. Create AKS or connect to existing AKS.
7. Create real Kubernetes Secret.
8. Apply manifests.
9. Inspect pod startup failures.
10. Decide YOLO model provisioning method.

## Current Caution

The school computer had not logged in to Azure and had not created AKS yet.

The AKS work so far is repository preparation, not a live cluster deployment.
