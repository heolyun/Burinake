#!/usr/bin/env bash
set -euo pipefail

kubectl apply -k infra/k8s

kubectl rollout status deployment/frontend -n burinake
kubectl rollout status deployment/backend -n burinake
kubectl rollout status deployment/yolo-server -n burinake
kubectl rollout status deployment/vlm-server -n burinake

kubectl get pods -n burinake
kubectl get svc -n burinake
