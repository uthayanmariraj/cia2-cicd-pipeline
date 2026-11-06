#!/usr/bin/env bash
# Creates the Azure resources and sets the GitHub repository variables.
# Run from the repository root, signed in with `az login` and `gh auth login`:
#   bash infra/setup.sh <github-owner>/<repo-name>
# Optional overrides: LOCATION, NODE_SIZE, NODE_COUNT
set -euo pipefail
export MSYS_NO_PATHCONV=1   # stops Git Bash rewriting /subscriptions/... as a Windows path

GITHUB_REPO="${1:?usage: bash infra/setup.sh <github-owner>/<repo-name>}"
LOCATION="${LOCATION:-centralindia}"
NODE_SIZE="${NODE_SIZE:-Standard_B2s}"
NODE_COUNT="${NODE_COUNT:-1}"
RESOURCE_GROUP=cia-devops-rg
CLUSTER=cia-devops
IDENTITY=cia-devops-github
NAMESPACE=devops-app

# az on Windows ends tsv output with a carriage return.
value() { az "$@" -o tsv | tr -d '\r'; }

SUBSCRIPTION_ID=$(value account show --query id)
TENANT_ID=$(value account show --query tenantId)
# Registry names are global, so make this one unique to the subscription.
ACR_NAME="ciadevops${SUBSCRIPTION_ID:0:8}"
echo "Subscription: ${SUBSCRIPTION_ID}, location: ${LOCATION}, repository: ${GITHUB_REPO}"

echo "==> Registering resource providers"
for provider in Microsoft.ContainerService Microsoft.ContainerRegistry Microsoft.ManagedIdentity \
                Microsoft.Compute Microsoft.Network Microsoft.Storage; do
  az provider register --namespace "$provider" --wait
done

echo "==> Creating the resource group and container registry"
az group create --name "$RESOURCE_GROUP" --location "$LOCATION" --output none
az acr create --resource-group "$RESOURCE_GROUP" --name "$ACR_NAME" --sku Basic \
  --admin-enabled false --output none
ACR_ID=$(value acr show --name "$ACR_NAME" --query id)

echo "==> Creating the AKS cluster (about 5-10 minutes)"
if ! az aks show --resource-group "$RESOURCE_GROUP" --name "$CLUSTER" --output none 2>/dev/null; then
  az aks create \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CLUSTER" \
    --location "$LOCATION" \
    --tier free \
    --node-count "$NODE_COUNT" \
    --node-vm-size "$NODE_SIZE" \
    --enable-aad \
    --enable-azure-rbac \
    --no-ssh-key \
    --attach-acr "$ACR_NAME" \
    --output none
fi
AKS_ID=$(value aks show --resource-group "$RESOURCE_GROUP" --name "$CLUSTER" --query id)

az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$CLUSTER" --admin --overwrite-existing
kubectl apply -f k8s/namespace.yaml

echo "==> Creating the keyless pipeline identity"
az identity create --resource-group "$RESOURCE_GROUP" --name "$IDENTITY" --output none
CLIENT_ID=$(value identity show --resource-group "$RESOURCE_GROUP" --name "$IDENTITY" --query clientId)
PRINCIPAL_ID=$(value identity show --resource-group "$RESOURCE_GROUP" --name "$IDENTITY" --query principalId)

# Only the main branch of this repository can sign in as the identity.
az identity federated-credential create \
  --resource-group "$RESOURCE_GROUP" \
  --identity-name "$IDENTITY" \
  --name github-main \
  --issuer https://token.actions.githubusercontent.com \
  --subject "repo:${GITHUB_REPO}:ref:refs/heads/main" \
  --audiences api://AzureADTokenExchange \
  --output none

grant() {
  az role assignment create \
    --assignee-object-id "$PRINCIPAL_ID" \
    --assignee-principal-type ServicePrincipal \
    --role "$1" \
    --scope "$2" \
    --output none
}
grant "AcrPush" "$ACR_ID"
grant "Reader" "$ACR_ID"
grant "Azure Kubernetes Service Cluster User Role" "$AKS_ID"
# Deploy rights inside one namespace only, not across the cluster.
grant "Azure Kubernetes Service RBAC Writer" "${AKS_ID}/namespaces/${NAMESPACE}"

echo "==> Setting the GitHub repository variables"
gh variable set AZURE_CLIENT_ID --repo "$GITHUB_REPO" --body "$CLIENT_ID"
gh variable set AZURE_TENANT_ID --repo "$GITHUB_REPO" --body "$TENANT_ID"
gh variable set AZURE_SUBSCRIPTION_ID --repo "$GITHUB_REPO" --body "$SUBSCRIPTION_ID"
gh variable set ACR_NAME --repo "$GITHUB_REPO" --body "$ACR_NAME"

echo
echo "Done. Push to main to run the pipeline."
echo "Credit is used until you run: bash infra/teardown.sh"
