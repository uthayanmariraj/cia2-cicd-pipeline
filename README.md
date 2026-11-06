# End-to-End CI/CD Pipeline on Azure

A Java web application that goes from `git push` to a public endpoint on Azure Kubernetes Service with no manual steps.

```
git push -> GitHub Actions -> Maven build and test -> Docker image -> Azure Container Registry
                                                                              |
             browser <- Azure load balancer <- AKS pods <- Ansible deploy <---+
```

| Concern | Tool |
| --- | --- |
| Source control | Git, GitHub |
| Build and test | Maven, JUnit 5 |
| Container | Docker |
| Registry | Azure Container Registry (ACR) |
| CI/CD | GitHub Actions |
| Deployment automation | Ansible |
| Orchestration | Kubernetes on Azure Kubernetes Service (AKS) |

## Repository layout

| Path | Purpose |
| --- | --- |
| `src/` | Spring Boot application and tests |
| `pom.xml` | Maven build |
| `Dockerfile`, `.dockerignore` | Container image |
| `.github/workflows/ci-cd.yml` | The pipeline |
| `k8s/` | Kubernetes manifests |
| `ansible/` | Deploy and verify playbooks, inventory |
| `infra/` | Azure setup and teardown |

## Pipeline stages

| Stage | Where |
| --- | --- |
| Checkout source code | every job |
| Compile and package | `Build and test` job |
| Run automated tests | `Build and test` job |
| Build and tag the image | `Build and push Docker image` job |
| Push to the registry | `Build and push Docker image` job |
| Deploy or update Kubernetes | `Deploy to AKS and verify` job, `ansible/deploy.yml` |
| Verify status and availability | `Deploy to AKS and verify` job, `ansible/verify.yml` |

Pull requests run only the build and test job. Images are tagged with the Git commit, so each deployment traces back to one commit. If a new version does not become ready, Ansible rolls back and the pipeline fails.

## Run locally

```bash
mvn clean package
docker build -t devops-app:local .
docker run --rm -p 8080:8080 devops-app:local
```

Then open http://localhost:8080.

## Deploy to Azure

Needs the Azure CLI (`az login`), the GitHub CLI (`gh auth login`), `kubectl` and a GitHub repository.

```bash
bash infra/setup.sh <github-owner>/<repo-name>
```

The script creates the resources and sets the repository variables. Push to `main` to run the pipeline. The application URL appears in the output of the verify step, or from:

```bash
kubectl get service devops-app -n devops-app
```

## Demonstrate a change

Edit `app.message` in `src/main/resources/application.properties`, commit and push to `main`. When the pipeline finishes, refresh the page: the new message appears, and `Version` shows the new commit.

## Delete everything

The cluster uses credit for as long as it exists.

```bash
bash infra/teardown.sh
```
