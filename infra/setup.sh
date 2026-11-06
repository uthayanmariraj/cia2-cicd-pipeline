#!/usr/bin/env bash
# Creates the AWS resources. Run from the repository root:
#   bash infra/setup.sh <github-owner>/<repo-name>
set -euo pipefail

GITHUB_REPO="${1:?usage: bash infra/setup.sh <github-owner>/<repo-name>}"
REGION=ap-south-1
CLUSTER=cia-devops
STACK=cia-devops-bootstrap
NAMESPACE=devops-app

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/cia-devops-github-actions"
echo "AWS account: ${ACCOUNT_ID}, region: ${REGION}, repository: ${GITHUB_REPO}"

# An account can only have one GitHub OIDC provider, so reuse it if it is already there.
if aws iam get-open-id-connect-provider \
  --open-id-connect-provider-arn "arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com" \
  >/dev/null 2>&1; then
  CREATE_OIDC=false
else
  CREATE_OIDC=true
fi

echo "==> Creating the ECR repository and pipeline role"
aws cloudformation deploy \
  --region "$REGION" \
  --stack-name "$STACK" \
  --template-file infra/bootstrap.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides "GitHubRepo=${GITHUB_REPO}" "ClusterName=${CLUSTER}" "CreateOidcProvider=${CREATE_OIDC}"

echo "==> Creating the EKS cluster (about 15-20 minutes)"
if aws eks describe-cluster --name "$CLUSTER" --region "$REGION" >/dev/null 2>&1; then
  echo "Cluster already exists"
else
  eksctl create cluster -f infra/cluster.yaml
fi

aws eks update-kubeconfig --name "$CLUSTER" --region "$REGION"
kubectl apply -f k8s/namespace.yaml

echo "==> Letting the pipeline role deploy to the ${NAMESPACE} namespace only"
if ! aws eks describe-access-entry --cluster-name "$CLUSTER" --principal-arn "$ROLE_ARN" --region "$REGION" >/dev/null 2>&1; then
  aws eks create-access-entry --cluster-name "$CLUSTER" --principal-arn "$ROLE_ARN" --region "$REGION" >/dev/null
fi
aws eks associate-access-policy \
  --cluster-name "$CLUSTER" \
  --region "$REGION" \
  --principal-arn "$ROLE_ARN" \
  --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSEditPolicy \
  --access-scope "type=namespace,namespaces=${NAMESPACE}" >/dev/null

echo
echo "Done. Set this GitHub repository variable, then push to main:"
echo "  AWS_ACCOUNT_ID=${ACCOUNT_ID}"
echo
echo "Costs accrue until you run: bash infra/teardown.sh"
