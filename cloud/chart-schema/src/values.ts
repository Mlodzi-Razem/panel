/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import * as z from 'zod/v4';

const ServiceType = z.enum(['ClusterIP', 'LoadBalancer', 'NodePort']).meta({id: 'ServiceType'});
const K8SVersion = z.stringFormat("k8s-version", /v?\d+\.\d+\.\d+/).meta({id: 'K8SVersion'});
const Port = z.number().min(1).max(65535).meta({id: 'Port'});
const True = z.literal(true).meta({id: 'True'});
const False = z.literal(false).meta({id: 'False'});
const TargetResourcePercentage = z.number().min(0.001).max(100);

const PgVolume = z.object({
    cnpgPvcTemplate: z.object().or(z.string()),
    storageLimitGigabytes: z.number().int().min(1),
}).describe("Volume specification").meta({id: 'PgVolume'});

const ReplicaResources =
    (minMem: number = 2048, minCores: number = 2) => z.object({
                                                          maximumMemoryMb: z.number().int().min(minMem),
                                                          requestedCpuCores: z.number().min(minCores),
                                                      })
                                                      .describe(
                                                          "Resources requested for each replica. "
                                                          + "When there are multiple replicas, "
                                                          + "you need to multiply the "
                                                          + "requests by the number of replicas "
                                                          + "to calculate total resources usage.")
                                                      .meta({id: `ReplicaResources_${minMem}m_${minCores}c`});
const K8S = z.object({
    version: K8SVersion.optional()
                       .describe(
                           "Kubernetes version to assume when pulling 'alpine/kubectl'. "
                           + "Helm should be able to determine the proper version automatically, "
                           + "but your exact version tag may not be available in DockerHub",
                       ),
    prometheus: z.object({
        release: z.string().describe(
            "Helm release name for Prometheus Operator. "
            + "As kube-prometheus-stack is expected to be available in the cluster, "
            + "this setting is placed under the 'k8s' key.",
        ),
    }),
}).describe("Configuration specific to the running Kubernetes cluster.");
const Jaeger = z.discriminatedUnion('enabled', [
    z.looseObject({enabled: False.describe("OTEL collector will not be deployed and Jaeger will not be launched")}),
    z.object({
        enabled: True.describe("OTEL collector will be deployed and Jaeger will be launched"),
        image: z.string().describe("Jaeger image to use"),
        mode: z.enum(['deployment', 'daemonset', 'statefulset', 'sidecar']).describe("Jaeger Pod deployment mode"),
        gateway: z.discriminatedUnion('enabled', [
            z.looseObject({
                enabled: False.describe(
                    "No gateway will be deployed. To access Jaeger UI, you will have to manually forward service's ports"),
            }),
            z.object({
                enabled: True.describe("An API Gateway will be deployed to expose Jaeger UI on the specified port"),
                port: Port,
                gatewayClass: z.string().optional().describe("If empty, 'panel.gateway.gatewayClass' will be used"),
            }),
        ]),
    }),
]).describe("Jaeger and OpenTelemetry configuration");

const PgUser = z.object({
    username: z.string(),
    password: z.string(),
}).meta({id: 'PgUser'});

const Postgres = z.union([
    z.looseObject({
        jdbcUrl: z.url({protocol: /^jdbc:postgresql$/}),
    }).meta({id: 'ExternalPostgresConnection'}),
    z.looseObject({
        cnpg: z.object({
            install: z.boolean(),
            volumes: z.object({
                data: PgVolume,
                backup: PgVolume.optional(),
            }),
            resources: ReplicaResources(2048, 2),
            users: z.object({
                superuser: PgUser,
                mrpanel: PgUser,
            }),
        }),
    }).meta({id: 'CNPGClusterDescription'}),
]);

const HPA = z.union([
    z.looseObject({enabled: False}),
    z.object({
        enabled: True,
        minReplicas: z.number().int().min(1),
        maxReplicas: z.number().int().min(1),
        targetCpuPercentage: TargetResourcePercentage,
        targetMemoryPercentage: TargetResourcePercentage,
        behavior: z.object(),
    }),
]).describe("Horizontal Pod Autoscaler configuration").meta({id: 'HPA'});

const PanelApi = z.object({
    affinity: z.json(),
    replicaResources: ReplicaResources(1024, 1),
    image: z.string(),
    service: z.object({
        type: ServiceType,
    }),
    hpa: z.object({
        enabled: False,
    }),
    cache: z.object({
        externalized: False,
    }),
    config: z.union([z.json(), z.string()]),
})

const PanelWeb = z.object({
    url: z.url(),
    metricsToken: z.string().optional(),
    affinity: z.json(),
    replicaResources: ReplicaResources(512, 0.5),
    image: z.string(),
    service: z.object({
        type: ServiceType,
    }),
    hpa: HPA,
    rollingUpdates: z.union([
        z.looseObject({enabled: False}),
        z.object({
            maxUnavailable: z.number().int().min(1),
            maxSurge: z.number().int().min(1),
            enabled: True,
        }),
    ]),
});
const Gateway = z.discriminatedUnion('install', [
    z.looseObject({
        install: False,
    }),
    z.object({
        install: True,
        gatewayClass: z.string(),
        domains: z.string().array(),
    }),
]);

const Secrets = z.object({
    postgres: z.object({
        database: z.string(),
        admin: z.object({
            username: z.string(),
            password: z.string(),
        }),
        panel: z.object({
            username: z.string(),
            password: z.string(),
        }),
    }),
    google: z.object({
        oauth: z.object({
            clientId: z.string(),
            clientSecret: z.string(),
        }),
        workspace: z.object({
            accountToken: z.string(),
        }),
    }),
    smtp: z.object({
        host: z.string(),
        port: Port,
        username: z.string(),
        password: z.string(),
    }),
    cloudflare: z.object({
        apiKey: z.string(),
    }),
    panel: z.object({
        web: z.object({
            metricsToken: z.string(),
            nextauthToken: z.string().optional(),
        })
    })
});

const PanelValues = z.object({
    panel: z.object({
        k8s: K8S,
        jaeger: Jaeger,
        postgres: Postgres,
        api: PanelApi,
        web: PanelWeb,
        gateway: Gateway,
        secrets: Secrets,
    }),
}).meta({id: "https://panel.mlodzirazem.org/chart/values"});

export default PanelValues;