# Burinake Kubernetes Environments

The current live AKS deployment is treated as PRD:

- Namespace: `burinake`
- Entry point: `infra/k8s`
- Public host: `https://burinake.20.249.106.231.nip.io`
- Database: Azure Database for PostgreSQL Flexible Server
- Runtime secrets: Azure Key Vault through Secrets Store CSI

DEV is prepared as a separate namespace with guardrails:

- Namespace: `burinake-dev`
- Entry point: `infra/k8s/environments/dev`
- Public host: `https://dev-burinake.20.249.106.231.nip.io`
- Workloads: lightweight `frontend` and `backend`
- Database: `burinake_dev` on the shared Azure PostgreSQL Flexible Server
- YOLO/VLM: shared from PRD through cluster DNS (`*.burinake.svc.cluster.local`)
- `ResourceQuota` blocks LoadBalancer/NodePort services in DEV so accidental public IP creation does not happen.

This keeps DEV useful for frontend/backend changes without doubling the AI server footprint on the current single-node AKS cluster.
