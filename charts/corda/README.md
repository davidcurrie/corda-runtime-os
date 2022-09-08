# corda

![Version: 0.1.0](https://img.shields.io/badge/Version-0.1.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: unstable](https://img.shields.io/badge/AppVersion-unstable-informational?style=flat-square)

A Helm chart for Corda

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| bootstrap.db.enabled | bool | `true` | Indicates whether DB bootstrap is enabled as part of installation |
| bootstrap.image.registry | string | `""` | CLI image registry; defaults to image.registry |
| bootstrap.image.repository | string | `"corda-os-plugins"` | CLI image repository |
| bootstrap.image.tag | string | `""` | CLI default tag; defaults to image.tag |
| bootstrap.initialAdminUser.password | string | `""` | Password for the initial admin user; generated if not specified |
| bootstrap.initialAdminUser.secretRef.name | string | `""` | If specified, the name of an existing secret that contains the initial admin user credentials to be used in preference to any provided via username and password |
| bootstrap.initialAdminUser.secretRef.passwordKey | string | `"password"` | The key name for the secret entry containing the initial admin user's password |
| bootstrap.initialAdminUser.secretRef.usernameKey | string | `"username"` | The key name for the secret entry containing the initial admin user's username |
| bootstrap.initialAdminUser.username | string | `"admin"` | Username for the initial admin user |
| bootstrap.kafka.cleanup | bool | `false` | Specifies whether existing topics with the given prefix should be deleted before trying to create new ones (deletes all existing topics if no prefix is given) |
| bootstrap.kafka.enabled | bool | `true` | Indicates whether Kafka bootstrap is enabled as part of installation |
| bootstrap.kafka.partitions | int | `10` | Kafka topic partitions |
| bootstrap.kafka.replicas | int | `3` | Kafka topic replicas |
| bootstrap.nodeSelector | object | `{}` |  |
| bootstrap.resources.limits | object | `{}` | the CPU/memory resource limits for the bootstrap containers |
| bootstrap.resources.requests | object | `{}` | the CPU/memory resource requests for the bootstrap containers |
| db.clientImage.registry | string | `""` | registry for image containing a db client, used to set up the db |
| db.clientImage.repository | string | `"postgres"` | repository for image containing a db client, used to set up the db |
| db.clientImage.tag | float | `14.4` | tag for image containing a db client, used to set up the db |
| db.cluster.database | string | `"cordacluster"` | the name of the cluster database |
| db.cluster.existingSecret | string | `""` | the name of an existing secret containing the cluster database password with a key of 'password' |
| db.cluster.host | string | `""` | the cluster database host (required) |
| db.cluster.password | string | `""` | the cluster database password (ignored if existingSecret is set, otherwise required) |
| db.cluster.port | int | `5432` | the cluster database port |
| db.cluster.type | string | `"postgresql"` | the cluster database type |
| db.cluster.user | string | `"user"` | the cluster database user |
| dumpHostPath | string | `""` | Path on Kubernetes hosts to mount on Corda workers for collecting dumps |
| fullnameOverride | string | `""` | override chart fullname |
| heapDumpOnOutOfMemoryError | bool | `false` | Enables capturing JVM heap dumps from Corda workers on an OutOfMemory error |
| image.registry | string | `"corda-os-docker.software.r3.com"` | worker image registry |
| image.tag | string | `""` | worker image tag, defaults to Chart appVersion |
| imagePullPolicy | string | `"Always"` | the image policy |
| imagePullSecrets | list | `[]` | image pull secrets |
| kafka.bootstrapServers | string | `""` | comma-separated list of Kafka bootstrap servers (required) |
| kafka.sasl.enabled | bool | `false` | enable/disable SASL for client connection to Kafka |
| kafka.sasl.mechanism | string | `"SCRAM-SHA-256"` | SASL mechanism for client connection to Kafka |
| kafka.sasl.password | string | `""` | SASL password for client connection to Kafka |
| kafka.sasl.username | string | `"user"` | SASL username for client connection to Kafka |
| kafka.tls.enabled | bool | `false` | indicates whether TLS should be used for client connections to Kafka |
| kafka.tls.truststore.password | string | `""` | if TLS is enabled, the password for the truststore for client connections to Kafka, if any |
| kafka.tls.truststore.secretRef.key | string | `"ca.crt"` | if TLS is enabled, the name of an existing Kubernetes secret containing the truststore for client connections to Kafka |
| kafka.tls.truststore.secretRef.name | string | `""` | if TLS is enabled, the name of an existing Kubernetes secret containing the truststore for client connections to Kafka; blank if no truststore is required |
| kafka.tls.truststore.type | string | `"PEM"` | if TLS is enabled, the type of the truststore for client connections to Kafka; one of PEM or JKS |
| kafka.topicPrefix | string | `""` | prefix to use for Kafka topic names (to support the use of a single Kafka cluster by multiple Corda clusters) |
| logging.format | string | `"json"` | log format; "json" or "text" |
| logging.level | string | `"warn"` | log level; one of "all", "trace", "debug", "info", "warn" (the default), "error", "fatal", or "off" |
| nameOverride | string | `""` | override chart name |
| nodeSelector | object | `{}` | node labels for pod assignment, see https://kubernetes.io/docs/user-guide/node-selection/ |
| openTelemetry.enabled | bool | `false` | enables the Open Telemetry Java agent for the Corda workers |
| openTelemetry.endpoint | string | `""` | the Open Telemetry endpoint to use e.g https://otel.example.com:4317; telemetry will be logged locally if this is unset |
| openTelemetry.protocol | string | `"grpc"` | the Open Telemetry protocol the endpoint is using; one of `grpc` or `http/protobuf` |
| resources.limits | object | `{}` | the default CPU/memory resource limits for the Corda containers |
| resources.requests | object | `{}` | the default CPU/memory resource request for the Corda containers |
| workers.crypto.debug.enabled | bool | `false` | run crypto worker with debug enabled |
| workers.crypto.debug.suspend | bool | `false` | if debug is enabled, suspend the crypto worker until the debugger is attached |
| workers.crypto.image.registry | string | `""` | crypto worker image registry, defaults to image.registry |
| workers.crypto.image.repository | string | `"corda-os-crypto-worker"` | crypto worker image repository |
| workers.crypto.image.tag | string | `""` | crypto worker image tag, defaults to image.tag |
| workers.crypto.logging.level | string | `""` | log level: one of "all", "trace", "debug", "info", "warn", "error", "fatal", or "off"; defaults to logging.level if not specified |
| workers.crypto.profiling.enabled | bool | `false` | run crypto worker with profiling enabled |
| workers.crypto.replicaCount | int | `1` | crypto worker replica count |
| workers.crypto.resources.limits | object | `{}` | the CPU/memory resource limits for the crypto worker containers |
| workers.crypto.resources.requests | object | `{}` | the CPU/memory resource requests for the crypto worker containers |
| workers.db.debug.enabled | bool | `false` | run DB worker with debug enabled |
| workers.db.debug.suspend | bool | `false` | if debug is enabled, suspend the DB worker until the debugger is attached |
| workers.db.image.registry | string | `""` | DB worker image registry, defaults to image.registry |
| workers.db.image.repository | string | `"corda-os-db-worker"` | DB worker image repository |
| workers.db.image.tag | string | `""` | DB worker image tag, defaults to image.tag |
| workers.db.logging.level | string | `""` | log level: one of "all", "trace", "debug", "info", "warn", "error", "fatal", or "off"; defaults to logging.level if not specified |
| workers.db.passphrase | string | `""` | DB worker passphrase, defaults to a value randomly-generated on install |
| workers.db.profiling.enabled | bool | `false` | run DB worker with profiling enabled |
| workers.db.replicaCount | int | `1` | DB worker replica count |
| workers.db.resources.limits | object | `{}` | the CPU/memory resource limits for the DB worker containers |
| workers.db.resources.requests | object | `{}` | the CPU/memory resource requests for the DB worker containers |
| workers.db.salt | string | `""` | DB worker salt, defaults to a value randomly-generated on install |
| workers.flow.debug.enabled | bool | `false` | run flow worker with debug enabled |
| workers.flow.debug.suspend | bool | `false` | if debug is enabled, suspend the flow worker until the debugger is attached |
| workers.flow.image.registry | string | `""` | flow worker image registry, defaults to image.registry |
| workers.flow.image.repository | string | `"corda-os-flow-worker"` | flow worker image repository |
| workers.flow.image.tag | string | `""` | flow worker image tag, defaults to image.tag |
| workers.flow.logging.level | string | `""` | log level: one of "all", "trace", "debug", "info", "warn", "error", "fatal", or "off"; defaults to logging.level if not specified |
| workers.flow.profiling.enabled | bool | `false` | run flow worker with profiling enabled |
| workers.flow.replicaCount | int | `1` | flow worker replica count |
| workers.flow.resources.limits | object | `{}` | the CPU/memory resource limits for the flow worker containers |
| workers.flow.resources.requests | object | `{}` | the CPU/memory resource requests for the flow worker containers |
| workers.flow.verifyInstrumentation | bool | `false` | run flow worker with Quasar's verifyInstrumentation enabled |
| workers.membership.debug.enabled | bool | `false` | run membership worker with debug enabled |
| workers.membership.debug.suspend | bool | `false` | if debug is enabled, suspend the membership worker until the debugger is attached |
| workers.membership.image.registry | string | `""` | membership worker image registry, defaults to image.registry |
| workers.membership.image.repository | string | `"corda-os-member-worker"` | membership worker image repository |
| workers.membership.image.tag | string | `""` | membership worker image tag, defaults to image.tag |
| workers.membership.logging.level | string | `""` | log level: one of "all", "trace", "debug", "info", "warn", "error", "fatal", or "off"; defaults to logging.level if not specified |
| workers.membership.profiling.enabled | bool | `false` | run membership worker with profiling enabled |
| workers.membership.replicaCount | int | `1` | membership worker replica count |
| workers.membership.resources.limits | object | `{}` | the CPU/memory resource limits for the membership worker containers |
| workers.membership.resources.requests | object | `{}` | the CPU/memory resource requests for the membership worker containers |
| workers.p2pGateway.debug.enabled | bool | `false` | run p2p-gateway worker with debug enabled |
| workers.p2pGateway.debug.suspend | bool | `false` | if debug is enabled, suspend the p2p-gateway worker until the debugger is attached |
| workers.p2pGateway.image.registry | string | `""` | p2p-gateway worker image registry, defaults to image.registry |
| workers.p2pGateway.image.repository | string | `"corda-os-p2p-gateway-worker"` | p2p-gateway worker image repository |
| workers.p2pGateway.image.tag | string | `""` | p2p-gateway worker image tag, defaults to image.tag |
| workers.p2pGateway.logging.level | string | `""` | log level: one of "all", "trace", "debug", "info", "warn", "error", "fatal", or "off"; defaults to logging.level if not specified |
| workers.p2pGateway.profiling.enabled | bool | `false` | run p2p-gateway worker with profiling enabled |
| workers.p2pGateway.replicaCount | int | `1` | p2p-gateway worker replica count |
| workers.p2pGateway.resources.limits | object | `{}` | the CPU/memory resource limits for the p2p-gateway worker containers |
| workers.p2pGateway.resources.requests | object | `{}` | the CPU/memory resource requests for the p2p-gateway worker containers |
| workers.p2pGateway.service.port | int | `8080` | The Gateway HTTP port |
| workers.p2pGateway.useStubs | bool | `false` | Use stub crypto processor |
| workers.p2pLinkManager.debug.enabled | bool | `false` | run p2p-link-manager worker with debug enabled |
| workers.p2pLinkManager.debug.suspend | bool | `false` | if debug is enabled, suspend the p2p-link-manager worker until the debugger is attached |
| workers.p2pLinkManager.image.registry | string | `""` | p2p-link-manager worker image registry, defaults to image.registry |
| workers.p2pLinkManager.image.repository | string | `"corda-os-p2p-link-manager-worker"` | p2p-link-manager worker image repository |
| workers.p2pLinkManager.image.tag | string | `""` | p2p-link-manager worker image tag, defaults to image.tag |
| workers.p2pLinkManager.logging.level | string | `""` | log level: one of "all", "trace", "debug", "info", "warn", "error", "fatal", or "off"; defaults to logging.level if not specified |
| workers.p2pLinkManager.profiling.enabled | bool | `false` | run p2p-link-manager worker with profiling enabled |
| workers.p2pLinkManager.replicaCount | int | `1` | p2p-link-manager worker replica count |
| workers.p2pLinkManager.resources.limits | object | `{}` | the CPU/memory resource limits for the p2p-link-manager worker containers |
| workers.p2pLinkManager.resources.requests | object | `{}` | the CPU/memory resource requests for the p2p-link-manager worker containers |
| workers.p2pLinkManager.useStubs | bool | `false` | Use stubbed crypto processor, membership group reader and group policy provider |
| workers.rpc.debug.enabled | bool | `false` | run RPC worker with debug enabled |
| workers.rpc.debug.suspend | bool | `false` | if debug is enabled, suspend the RPC worker until the debugger is attached |
| workers.rpc.image.registry | string | `""` | RPC worker image registry, defaults to image.registry |
| workers.rpc.image.repository | string | `"corda-os-rpc-worker"` | RPC worker image repository |
| workers.rpc.image.tag | string | `""` | RPC worker image tag, defaults to image.tag |
| workers.rpc.logging.level | string | `""` | log level: one of "all", "trace", "debug", "info", "warn", "error", "fatal", or "off"; defaults to logging.level if not specified |
| workers.rpc.profiling.enabled | bool | `false` | run RPC worker with profiling enabled |
| workers.rpc.replicaCount | int | `1` | RPC worker replica count |
| workers.rpc.resources | object | `{"limits":{},"requests":{}}` | resource limits and requests configuration for the RPC worker containers. |
| workers.rpc.resources.limits | object | `{}` | the CPU/memory resource limits for the RPC worker containers |
| workers.rpc.resources.requests | object | `{}` | the CPU/memory resource requests for the RPC worker containers |
| workers.rpc.service.annotations | object | `{}` | the annotations for RPC worker service |
| workers.rpc.service.externalTrafficPolicy | string | `""` | the traffic policy for the RPC worker service |
| workers.rpc.service.loadBalancerSourceRanges | list | `[]` | the LoadBalancer source ranges to limit access to the RPC worker service |
| workers.rpc.service.type | string | `"ClusterIP"` | the type for the RPC worker service |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
