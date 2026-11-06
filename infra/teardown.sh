#!/usr/bin/env bash
# Deletes everything setup.sh and the pipeline created, so that charges stop.
# Everything is in one resource group, and AKS removes its own node resource group with it.
# Run from the repository root: bash infra/teardown.sh
set -euo pipefail

RESOURCE_GROUP=cia-devops-rg

echo "==> Deleting resource group ${RESOURCE_GROUP} (about 5-10 minutes)"
az group delete --name "$RESOURCE_GROUP" --yes

echo "Done. Remaining resource groups in this subscription:"
az group list --query '[].name' -o tsv
