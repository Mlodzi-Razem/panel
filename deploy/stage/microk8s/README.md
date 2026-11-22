# deploy/stage/microk8s/ — tor K: STAGE w microk8s (chart cloud/)

Wariant **K** (prod-parity z AD-9): ten sam chart co produkcja, instalowany
helmem do klastra microk8s na VPS. Procedura krok po kroku (właściciel):
[../RUNBOOK.md §11](../RUNBOOK.md). Decyzja K-vs-C: raport, sekcja „K vs C".

| plik | rola |
|---|---|
| `values.stage-microk8s.yaml` | overlay stage (bez sekretów): jaeger off, storageClass `microk8s-hostpath` (bez `cnpgPvcTemplate.volumeName` — klucz wyzerowany, patrz RUNBOOK §11.11 pkt 6), GatewayClass `istio`, domena `dev.panel.mlodzirazem.org`, TLS letsencrypt + `acmeEmail: bartosz.baginski@proton.me` (wariant origin-ca opisany w runbooku), `imagePullSecret` puste w bazie (per-transport, §11.4), `replicas: "1"`, mniejsze zasoby |
| `values.stage-secrets.example.yaml` | szablon pliku sekretów — właściciel kopiuje na VPS jako `values.stage-secrets.yaml` (nigdy w repo; chroni `../.gitignore`) |

**Wymagania platformy (zweryfikowane w pilotażu 2026-11-22, VPS AD-9):**

- **cert-manager ≥ 1.18** — solver issuing to `http01.gatewayHTTPRoute`
  (pole `gatewayHTTP01` ze starszych wersji NIE istnieje w CRD ≥1.18;
  admission webhook odrzuca stary zapis);
- **flaga controllera `--enable-gateway-api=true`** — w v1.19.1 domyślnie
  WYŁĄCZONY HTTP-01 solver przez Gateway API; bez flagi cert utyka w
  `Ready=False` z `challenge: "gateway api is not enabled"`. UWAGA: to
  NIE jest feature-gate — `--feature-gates=GatewayAPI=true` nie istnieje
  w v1.19.1 (CrashLoop: `unrecognized feature gate: GatewayAPI`).
  Krok ownera (komenda patchu): [../RUNBOOK.md §11.5](../RUNBOOK.md);
- GatewayClass `istio` (w klastrze AD-9: Installed=True) — solver i route
  web podpinają się sekcjami listenerów `panel-acme`/`panel-web`;
- CNPG operator (obraz klastra `postgresql:18.1`, spełnia wymóg PG18);
- storageClass z dynamicznym provisioningiem (`microk8s-hostpath`);
- ekspozycja publiczna 80/443: socat → VIP standalone dataplane istio
  (`panel-gateway-istio`, na pilotażu 10.64.100.2) — [../RUNBOOK.md
  §11.3](../RUNBOOK.md).

Deploy robi `ci/deploy-helm-on-vps.sh` (rsyncowany z workflow na VPS):
`microk8s helm upgrade --install panel-stage cloud/chart -n stage --create-namespace
-f overlay -f sekrety --set panel.{api,web}.image=… --wait --timeout 10m`,
potem rollout status + healthcheck + `last-stable.txt`.

Walidowane lokalnie (helm v3.16 w dockerze): `helm template` przechodzi dla
obu wariantów TLS (letsencrypt z overlay: ClusterIssuer + dodatkowy listener
HTTP:80 na gatewayu; origin-ca z domyślnymi values: bez zmian zachowanie
upstream). Szczegóły: raport, sekcja kliki „Sekcja K".
