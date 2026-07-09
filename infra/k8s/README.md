# Burinake AKS Manifests

This directory contains the first AKS-ready Kubernetes manifests for Burinake.

The existing Azure resources are reused:

- Resource group: `Burinake`
- ACR: `burinakeacr`
- Blob Storage account: `burinakestorage`
- Blob container: `fire-events`
- Azure OpenAI / AI Foundry resources remain external APIs

## Apply Order

Create the AKS cluster and connect kubectl first. Then create the namespace and real secrets:

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

Do not apply `secrets.example.yaml` with placeholder values to a real cluster.

Deploy:

```bash
kubectl apply -k infra/k8s
kubectl get pods -n burinake
kubectl get svc -n burinake
```

## Notes

- `frontend` is exposed as `LoadBalancer` for the first AKS test.
- `backend`, `yolo-server`, `vlm-server`, and `postgres` use internal ClusterIP services.
- `postgres.yaml` is a temporary staging StatefulSet with PVC. For stronger production architecture, replace it with Azure Database for PostgreSQL Flexible Server.
- `yolo-server` mounts `/app/models` from `emptyDir` as a placeholder. Real YOLO model provisioning is still required, preferably Azure Files PersistentVolume or Blob download through an initContainer.
- VLM uses Azure OpenAI / AI Foundry as an external API. The VLM model itself is not deployed into AKS.
- `ingress.yaml` is prepared but not included in `kustomization.yaml` until an ingress controller is installed.
