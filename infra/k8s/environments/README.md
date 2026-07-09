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
- Workloads are not deployed by default to avoid doubling node cost and CPU pressure on the current single-node AKS cluster.
- `ResourceQuota` blocks LoadBalancer/NodePort services in DEV so accidental public IP creation does not happen.

Recommended next step for a full DEV stack is to add a dedicated dev overlay with separate database/schema, Key Vault secret names, and an internal-only ingress or port-forward workflow.
