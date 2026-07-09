# AKS CI/CD

## Deployment Flow

1. A push to `develop` deploys the DEV environment.
2. A push to `main` deploys the PRD environment.
3. GitHub Actions logs in to Azure with OIDC.
4. GitHub Actions logs in to Azure Container Registry.
5. Frontend and backend images are built and pushed to ACR.
6. Kubernetes manifests are applied to AKS.
7. The related deployments are restarted so they pull the latest image tags.
8. The public health endpoint is checked after rollout.

## Environments

| Branch or trigger | Namespace | URL |
| --- | --- | --- |
| `develop` push | `burinake-dev` | `https://dev-burinake.20.249.106.231.nip.io` |
| `main` push | `burinake` | `https://burinake.20.249.106.231.nip.io` |
| Manual dispatch | selected in workflow | selected in workflow |

## GitHub Secrets

The workflow uses Azure OIDC, so it does not need an Azure client secret or ACR password.

Required repository secrets:

- `AZURE_CLIENT_ID`
- `AZURE_TENANT_ID`
- `AZURE_SUBSCRIPTION_ID`

Current Azure values:

- `AZURE_CLIENT_ID`: `92ea9c79-a5f4-4a1b-94b5-01d41e1e0703`
- `AZURE_TENANT_ID`: `3da85eb2-33fa-45ff-b012-e413d0c7297d`
- `AZURE_SUBSCRIPTION_ID`: `f2f8b7f1-d665-45ce-a219-e9f5d043f061`

`AZURE_CLIENT_ID` is the app ID of the Azure app registration used by GitHub Actions.

## Azure Permissions

The GitHub Actions identity needs:

- `AcrPush` on `burinakeacr`
- `Azure Kubernetes Service Cluster Admin Role` on `burinake-aks`

The workflow uses `az aks get-credentials --admin` because it applies Kubernetes manifests and restarts deployments.

## AI Image Policy

Regular branch deployments build only:

- `burinake-frontend`
- `burinake-backend`

YOLO and VLM images are built only from manual workflow dispatch with `build_ai_images=true`.
This keeps ordinary deployments lighter and avoids rebuilding heavy AI images when only the web or API code changed.

## Runtime Services

The deployed AKS system depends on these Azure resources:

- AKS: `burinake-aks`
- ACR: `burinakeacr.azurecr.io`
- Key Vault: `burinake-kv-368x19`
- PostgreSQL Flexible Server: `burinake-pg-368x19.postgres.database.azure.com`
- Storage Account: `burinakestorage`
- Azure OpenAI / AI Foundry: `burinakeai-resource`
