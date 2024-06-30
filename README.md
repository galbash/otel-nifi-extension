# Nifi OpenTelemetry Extension

## Introduction

This extension aims to add OpenTelemetry traces to a NiFi deployment


## Build and add extensions

To build this extension project, run `./gradlew build`. You can find the resulting jar file in `build/libs/`.

To add the extension to the instrumentation agent:

1. Copy the jar file to a host that is running an application to which you've attached the OpenTelemetry Java instrumentation.
2. Modify the startup command to add the full path to the extension file. For example:

   ```bash
   java -javaagent:path/to/opentelemetry-javaagent.jar \
        -Dotel.javaagent.extensions=build/libs/otel-nifi-extension-1.0-0-all.jar
        -jar myapp.jar
   ```

Note: to load multiple extensions, you can specify a comma-separated list of extension jars or directories (that
contain extension jars) for the `otel.javaagent.extensions` value.

### Adding the extension through NiFi conf file

You can load the example using NiFi's `bootstrap.conf` file together with the OpenTelemetry agent in the following way:

```yaml
java.arg.20=-javaagent:/opt/nifi/nifi-current/conf/opentelemetry-javaagent.jar # path to agent jar
java.arg.21=-Dotel.service.name=nifi
java.arg.22=-Dotel.traces.exporter=otlp
java.arg.23=-Dotel.exporter.otlp.endpoint=http://grafana:4318 # collector endpoint
java.arg.24=-Dotel.exporter.otlp.protocol=http/protobuf # collector protocol
java.arg.25=-Dotel.javaagent.extensions=/opt/nifi/nifi-current/conf/otel-nifi-extension.jar

# Following are optional debug flags
java.arg.26=-Dotel.javaagent.logging=application
java.arg.27=-Dotel.javaagent.debug=true
```

## Configuration options
| System property                                               | Type   | Default  | Description                                                                                                                                                                      |
|---------------------------------------------------------------|--------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.nifi.external-propagation-processors`   | List   | `GetWMQ` | A list of processors for which the external active context is used when a flow file is created / read without context                                                            |
| `otel.instrumentation.nifi.use-links-processors`              | List   | []       | A list of processors for which when a FlowFile is created/cloned from another flow file, the child span will be set as a link and not a direct child. Useful for split use cases |

## Embed extensions in the OpenTelemetry Agent

To simplify deployment, you can embed extensions into the OpenTelemetry Java Agent to produce a single jar file. With an integrated extension, you no longer need the `-Dotel.javaagent.extensions` command line option.

For more information, see the `extendedAgent` task in [build.gradle](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/examples/extension/build.gradle#:~:text=extendedAgent).
