{{ define "gateway.labels" -}}
"org.mlodzirazem.panel.component": "gateway"
"org.mlodzirazem.panel.layer": "gateway"
"app.kubernetes.io/version": {{ .Chart.AppVersion | quote }}
{{- end }}