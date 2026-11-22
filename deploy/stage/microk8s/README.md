# deploy/stage/microk8s/ — tor K: STAGE w microk8s (chart cloud/)

Wariant **K** (prod-parity z AD-9): ten sam chart co produkcja, instalowany
helmem do klastra microk8s na VPS. Procedura krok po kroku (właściciel):
[../RUNBOOK.md §11](../RUNBOOK.md). Decyzja K-vs-C: raport, sekcja „K vs C".

| plik | rola |
|---|---|
| `values.stage-microk8s.yaml` | overlay stage (bez sekretów): jaeger off, storageClass `microk8s-hostpath`, GatewayClass `istio`, domena `dev.panel.mlodzirazem.org`, TLS letsencrypt (wariant origin-ca opisany w runbooku), `replicas: "1"`, mniejsze zasoby |
| `values.stage-secrets.example.yaml` | szablon pliku sekretów — właściciel kopiuje na VPS jako `values.stage-secrets.yaml` (nigdy w repo; chroni `../.gitignore`) |

Deploy robi `ci/deploy-helm-on-vps.sh` (rsyncowany z workflow na VPS):
`microk8s helm upgrade --install panel-stage cloud/chart -n stage --create-namespace
-f overlay -f sekrety --set panel.{api,web}.image=… --wait --timeout 10m`,
potem rollout status + healthcheck + `last-stable.txt`.

Walidowane lokalnie (helm v3.16 w dockerze): `helm template` przechodzi dla
obu wariantów TLS (letsencrypt z overlay: ClusterIssuer + dodatkowy listener
HTTP:80 na gatewayu; origin-ca z domyślnymi values: bez zmian zachowanie
upstream). Szczegóły: raport, sekcja kliki „Sekcja K".
