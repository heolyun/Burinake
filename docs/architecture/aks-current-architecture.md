# Current AKS Architecture

## High-Level Architecture

```mermaid
flowchart TB
  User[User / CCTV Client]
  DNS[nip.io temporary domain]
  HTTPS[HTTPS Ingress<br/>ingress-nginx + cert-manager]

  subgraph AKS[Azure Kubernetes Service: burinake-aks]
    subgraph DEV[DEV namespace: burinake-dev]
      DevFE[Frontend]
      DevBE[Backend]
    end

    subgraph PRD[PRD namespace: burinake]
      PrdFE[Frontend]
      PrdBE[Backend]
      YOLO[YOLO Server]
      VLM[VLM Server]
    end

    CSI[Key Vault CSI Driver]
    HPA[Horizontal Pod Autoscaler]
  end

  ACR[Azure Container Registry<br/>burinakeacr.azurecr.io]
  KV[Azure Key Vault<br/>burinake-kv-368x19]
  PG[Azure Database for PostgreSQL<br/>burinake / burinake_dev]
  Blob[Azure Blob Storage<br/>fire-events]
  AOAI[Azure OpenAI / AI Foundry<br/>Burinake-vlm]
  Monitor[Azure Monitor<br/>Container Insights]

  User --> DNS --> HTTPS
  HTTPS --> DevFE
  HTTPS --> PrdFE

  DevFE --> DevBE
  PrdFE --> PrdBE

  DevBE --> YOLO
  DevBE --> VLM
  PrdBE --> YOLO
  PrdBE --> VLM

  PrdBE --> PG
  DevBE --> PG
  PrdBE --> Blob
  DevBE --> Blob
  VLM --> AOAI

  CSI --> KV
  DevBE -.runtime secrets.-> CSI
  PrdBE -.runtime secrets.-> CSI
  VLM -.runtime secrets.-> CSI
  YOLO -.runtime secrets.-> CSI

  ACR --> DevFE
  ACR --> DevBE
  ACR --> PrdFE
  ACR --> PrdBE
  ACR --> YOLO
  ACR --> VLM

  AKS --> Monitor
  HPA -.scales.-> PrdFE
  HPA -.scales.-> PrdBE
  HPA -.scales.-> YOLO
  HPA -.scales.-> VLM
```

## CI/CD Architecture

```mermaid
flowchart LR
  DevPush[Push to develop]
  MainPush[Push to main]
  Actions[GitHub Actions<br/>Deploy to AKS]
  OIDC[Azure OIDC Login]
  Build[Build frontend/backend images]
  Push[Push images to ACR]
  Apply[Kubectl apply manifests]
  DevDeploy[Deploy to DEV<br/>burinake-dev]
  PrdDeploy[Deploy to PRD<br/>burinake]

  DevPush --> Actions
  MainPush --> Actions
  Actions --> OIDC --> Build --> Push --> Apply
  Apply --> DevDeploy
  Apply --> PrdDeploy
```

## Runtime Request Flow

```mermaid
sequenceDiagram
  participant Client as User / CCTV Client
  participant FE as Frontend
  participant BE as Backend
  participant Blob as Azure Blob Storage
  participant YOLO as YOLO Server
  participant VLM as VLM Server
  participant AOAI as Azure OpenAI
  participant PG as Azure PostgreSQL

  Client->>FE: Open service or upload fire image
  FE->>BE: API request
  BE->>Blob: Store original/error image
  BE->>YOLO: Request fire detection
  YOLO-->>BE: Detection result
  BE->>VLM: Request validation/explanation
  VLM->>AOAI: Vision-language inference
  AOAI-->>VLM: Analysis result
  VLM-->>BE: Validation/explanation
  BE->>PG: Save event and status
  BE-->>FE: Return fire event result
  FE-->>Client: Show detection result
```

## Current Deployment Summary

| Area | Current state |
| --- | --- |
| Runtime platform | Azure Kubernetes Service |
| PRD URL | `https://burinake.20.249.106.231.nip.io` |
| DEV URL | `https://dev-burinake.20.249.106.231.nip.io` |
| Container registry | Azure Container Registry |
| Secret management | Azure Key Vault + Secrets Store CSI Driver |
| Database | Azure Database for PostgreSQL Flexible Server |
| Image storage | Azure Blob Storage |
| AI model runtime | YOLO server uses `burinake-ai-server:v2`, VLM server runs in AKS and calls Azure OpenAI |
| Monitoring | Azure Monitor Container Insights |
| Autoscaling | HPA configured for PRD workloads |
| CI/CD | GitHub Actions with Azure OIDC |

## Notes for Presentation

- The old Azure VM runtime has been removed.
- AKS is now the main runtime for the service.
- DEV and PRD are separated by Kubernetes namespaces.
- DEV is lightweight and reuses the PRD YOLO/VLM services to reduce cost.
- PRD contains the full application stack: frontend, backend, YOLO server, and VLM server.
- The YOLO runtime currently uses the legacy `burinake-ai-server:v2` image because it preserves the tested model behavior.
- GPU node pool is intentionally deferred because the current subscription quota does not allow it.
- The current domain uses `nip.io` as a temporary HTTPS domain until a real domain is connected.
