#!/usr/bin/env bash
# Deletes everything setup.sh and the pipeline created, so that billing stops.
# Run from the repository root: bash infra/teardown.sh
set -euo pipefail

REGION=ap-south-1
CLUSTER=cia-devops
STACK=cia-devops-bootstrap

if aws eks describe-cluster --name "$CLUSTER" --region "$REGION" >/dev/null 2>&1; then
  aws eks update-kubeconfig --name "$CLUSTER" --region "$REGION"
  # The load balancer belongs to the Service. Remove it first or the VPC cannot be deleted.
  echo "==> Deleting the Service and its load balancer"
  kubectl delete service devops-app -n devops-app --ignore-not-found --wait=true

  echo "==> Deleting the EKS cluster (about 10-15 minutes)"
  eksctl delete cluster -f infra/cluster.yaml --wait
fi

echo "==> Deleting the ECR repository and pipeline role"
aws cloudformation delete-stack --stack-name "$STACK" --region "$REGION"
aws cloudformation wait stack-delete-complete --stack-name "$STACK" --region "$REGION"

echo "Done. Check the AWS console to confirm nothing is left."
