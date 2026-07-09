# AKS Deployment

This document describes the first AKS migration path for Burinake.

The current Azure VM and Docker Compose deployment should remain available until AKS is verified. This AKS setup reuses existing Azure resources instead of redesigning the platform from scratch.

## Reused Azure Resources

- Resource Group: `Burinake`
- Azure Container Registry: `burinakeacr`
- Storage Account: `burinakestorage`
- Blob Container: `fire-events`
- Azure OpenAI / AI Foundry: existing Burinake resources
- VM: keep as fallback until AKS is verified

## Runtime Target

```text
AKS
  -> frontend Deployment + Service
  -> backend Deployment + Service
  -> yolo-server Deployment + Service
  -> vlm-server Deployment + Service
  -> PostgreSQL StatefulSet for staging
  -> Azure Blob Storage
  -> Azure OpenAI / AI Foundry API
  -> ACR images
```

## Create AKS

```bash
az login

az aks create \
  --resource-group Burinake \
  --name burinake-aks \
  --node-count 1 \
  --node-vm-size Standard_B2s \
  --generate-ssh-keys \
  --attach-acr burinakeacr

az aks get-credentials \
  --resource-group Burinake \
  --name burinake-aks \
  --overwrite-existing

kubectl get nodes
```

`--attach-acr burinakeacr` lets AKS pull images from the existing ACR.

## Create Runtime Secrets

Do not commit real secrets. Do not apply `infra/k8s/secrets.example.yaml` to production with placeholder values.

Create real secrets with:

```bash
kubectl apply -f infra/k8s/namespace.yaml

kubectl create secret generic burinake-secret \
  --from-literal=POSTGRES_PASSWORD="..." \
  --from-literal=AZURE_STORAGE_CONNECTION_STRING="..." \
  --from-literal=AZURE_OPENAI_ENDPOINT="..." \
  --from-literal=AZURE_OPENAI_API_KEY="..." \
  --from-literal=AZURE_OPENAI_DEPLOYMENT_NAME="Burinake-vlm" \
  --from-literal=AZURE_OPENAI_API_VERSION="2024-02-15-preview" \
  -n burinake
```

## Deploy Manifests

```bash
kubectl apply -k infra/k8s
kubectl get pods -n burinake
kubectl get svc -n burinake
```

Or use the helper script:

```bash
./scripts/deploy-aks.sh
```

## GitHub Actions

AKS deployment is separated from the existing VM deployment.

Workflow:

- `.github/workflows/deploy-aks.yml`

Trigger:

- Manual `workflow_dispatch`

Expected secrets:

- `AZURE_CREDENTIALS`
- `AZURE_RESOURCE_GROUP`
- `AKS_CLUSTER_NAME`
- `ACR_LOGIN_SERVER`
- `ACR_USERNAME`
- `ACR_PASSWORD`

The workflow can build and push frontend/backend images, then apply `infra/k8s`. It does not build YOLO/VLM images.

Because current manifests use `latest` image tags, the workflow restarts frontend/backend deployments after apply. A future improvement is to use immutable Git SHA image tags.

## Services

- `frontend`: LoadBalancer on port 80 for first AKS testing
- `backend`: ClusterIP on port 8080
- `yolo-server`: ClusterIP on port 8001
- `vlm-server`: ClusterIP on port 8002
- `postgres`: ClusterIP on port 5432

Internal backend URLs:

```text
AI_YOLO_BASE_URL=http://yolo-server:8001
AI_VLM_BASE_URL=http://vlm-server:8002
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/burinake
```

## PostgreSQL

`infra/k8s/postgres.yaml` uses PostgreSQL as a StatefulSet with a PVC for the first staging test.

For a stronger production architecture, replace in-cluster PostgreSQL with Azure Database for PostgreSQL Flexible Server.

The current Kubernetes manifests do not automate schema migrations. Apply database schema/migration files separately until migration tooling is added.

## AI Model Storage

YOLO model files are still excluded from Git and Docker images.

The first AKS manifest mounts `/app/models` as `emptyDir` only to preserve the container path. This is not enough for real YOLO inference.

Recommended future options:

1. Azure Files PersistentVolume mounted to `/app/models`.
2. Blob Storage initContainer that downloads the required model into an `emptyDir`.
3. Model-included images are not recommended because they create large images.

VLM uses Azure OpenAI / AI Foundry as an external API. Do not deploy the VLM model itself into AKS.

## Optional Ingress

`infra/k8s/ingress.yaml` is prepared for NGINX Ingress Controller, but it is not included in `kustomization.yaml` yet.

For the first test, use the frontend LoadBalancer service. Add Ingress after an ingress controller and domain are ready.

## Migration Summary

VM 기반 Docker Compose 운영에서 AKS 기반 Kubernetes 운영으로 확장하기 위한 초안입니다.

기존 ACR, Blob Storage, Azure OpenAI 리소스는 유지하고, 애플리케이션 실행 계층만 VM에서 AKS로 이동할 수 있게 준비했습니다. 이를 통해 컨테이너 오케스트레이션, 서비스 디스커버리, 롤링 업데이트, 확장성의 기반을 마련합니다.

## Known Limitations

- YOLO model provisioning is not solved yet.
- PostgreSQL is temporary in-cluster storage for staging.
- Ingress is optional and disabled by default.
- Images still use `latest`; immutable tags should be added later.
- The existing Azure VM deployment remains the safer production path until AKS is verified.
