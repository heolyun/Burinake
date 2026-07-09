# Burinake AKS Manifests

This directory contains the first AKS-ready Kubernetes manifests for Burinake.

The existing Azure resources are reused:

- Resource group: `Burinake`
- ACR: `burinakeacr`
- Blob Storage account: `burinakestorage`
- Blob container: `fire-events`
- Azure OpenAI / AI Foundry resources remain external APIs

## Apply Order

Create the AKS cluster and connect kubectl first. Runtime secrets are sourced from Azure Key Vault through the Secrets Store CSI Driver:

```bash
az aks enable-addons \
  --resource-group Burinake \
  --name burinake-aks \
  --addons azure-keyvault-secrets-provider

kubectl apply -k infra/k8s
```

`keyvault-secretproviderclass.yaml` currently points at `burinake-kv-368x19` and syncs Key Vault secrets into `burinake-runtime-secret`. Do not apply `secrets.example.yaml` with placeholder values to a real cluster.

Check:

```bash
kubectl apply -k infra/k8s
kubectl get pods -n burinake
kubectl get svc -n burinake
```

## Notes

- `frontend` is exposed through `ingress-nginx` with a temporary `nip.io` HTTPS host.
- `backend`, `yolo-server`, and `vlm-server` use internal ClusterIP services.
- PostgreSQL is Azure Database for PostgreSQL Flexible Server: `burinake-pg-368x19.postgres.database.azure.com`.
- `postgres.yaml` is kept in the repository only as the previous in-cluster fallback manifest and is not included in `kustomization.yaml`.
- `yolo-server` downloads the YOLO model from Azure Blob Storage in an initContainer.
- VLM uses Azure OpenAI / AI Foundry as an external API. The VLM model itself is not deployed into AKS.
- `ingress.yaml` requires `ingress-nginx` and `cert-manager` to be installed before applying the kustomization.
