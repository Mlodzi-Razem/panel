{{- define "panel-web.labels" -}}
"org.mlodzirazem.panel.component": "panel-web"
"org.mlodzirazem.panel.layer": "frontend"
"app.kubernetes.io/version": {{ .Chart.AppVersion | quote }}
{{- end -}}