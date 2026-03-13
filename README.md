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
| System property                                                  | Type | Default                   | Description                                                                                                                                                                                                                                                    |
|------------------------------------------------------------------|------|---------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.nifi.external-propagation-processors`      | List | `GetWMQ`                  | A list of processors for which the external active context is used when a flow file is created / read without context                                                                                                                                          |
| `otel.instrumentation.nifi.use-links-processors`                 | List | []                        | A list of processors for which when a FlowFile is created/cloned from another flow file, the child span will be set as a link and not a direct child. Useful for split use cases                                                                               |
| `otel.instrumentation.nifi.external-propagation-thread-prefixes` | List | `ListenHTTP,Consume AMPQ` | A list of thread names for which the external active context is used when a flow file is created / read without context. Useful for input processors that get messages on a different thread for which the `external-propagation-processors` flag doesn't work |
| `otel.instrumentation.nifi.blacklist-processors-by-name`         | List | []                        | A list of processors-name that the agent will not create spans, example for this use case is when you have processors with specific name that "junk" your environment                                                                                          |
| `otel.instrumentation.nifi.blacklist-processors-by-type`         | List | []                        | A list of processors-types that the agent will not create spans, example for this use case is when you have processors with specific type like UpdateAttribute that "junk" your environment                                                                    |

## Tags Options
To simplify the deployment, you can during runtime add tags to your processors that will make them behave in a certain way.
Usage - add to your nifi processor name one of the following: <br><br>    ExternalPropagation - will make your processor act like external active context is used when a flow file is created / read without context <br>
UseLinks - FlowFile is created/cloned from another flow file, the child span will be set as a link and not a direct child. Useful for split use cases<br>
ThreadPrefixesExternalPropagation - thread names for which the external active context is used when a flow file is created / read without context<br>
NoOTEL - will not create spans, example for this use case is when you have processors with specific name that "junk" your environment
<br><br>
Example: "UpdateAttribute[NoOTEL]" will not generate spans
<br>

## Embed extensions in the OpenTelemetry Agent

To simplify deployment, you can embed extensions into the OpenTelemetry Java Agent to produce a single jar file. With an integrated extension, you no longer need the `-Dotel.javaagent.extensions` command line option.

For more information, see the `extendedAgent` task in [build.gradle](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/examples/extension/build.gradle#:~:text=extendedAgent).
