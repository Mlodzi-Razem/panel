{{- define "panel-api.labels" -}}
"org.mlodzirazem.panel.component": "panel-api"
"org.mlodzirazem.panel.layer": "backend"
"app.kubernetes.io/version": {{ .Chart.AppVersion | quote }}
{{- end -}}