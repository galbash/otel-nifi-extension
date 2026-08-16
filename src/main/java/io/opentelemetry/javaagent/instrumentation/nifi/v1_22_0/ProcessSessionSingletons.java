package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

public final class ProcessSessionSingletons {
  private static final Logger logger =
      Logger.getLogger(ProcessSessionSingletons.class.getName());
  static Tracer tracer = GlobalOpenTelemetry.getTracer("nifi");
  static List<String> externalPropagationProcessors = AgentInstrumentationConfig.get().getList(
          "otel.instrumentation.nifi.external-propagation-processors",
          Collections.singletonList("GetWMQ")
        );

  static List<String> useLinksProcessors = AgentInstrumentationConfig.get().getList(
          "otel.instrumentation.nifi.use-links-processors",
          Collections.emptyList()
  );

  static List<String> externalPropagationThreadPrefixes = AgentInstrumentationConfig.get().getList(
          "otel.instrumentation.nifi.external-propagation-thread-prefixes",
          Collections.singletonList("ListenHTTP")
  );

  static List<String> blacklistProcessorsByName = AgentInstrumentationConfig.get().getList(
          "otel.instrumentation.nifi.blacklist-processors-by-name",
          Collections.emptyList()
  );

  static List<String> blacklistProcessorsByType = AgentInstrumentationConfig.get().getList(
          "otel.instrumentation.nifi.blacklist-processors-by-type",
          Collections.emptyList()
  );

  private ProcessSessionSingletons() {}

  private static Optional<SpanBuilder> createSpanBuilder() {
    ActiveConnectableConfig pConfig = ActiveConnectableSaver.get();
    ArrayList<ConfigTagEnum> configTagEnums = pConfig.processContext != null
        ? getProcessorTags(pConfig.processContext.getName())
        : new ArrayList<>();
    if (pConfig.processContext != null && pConfig.connectable != null) {
      if (blacklistProcessorsByName.contains(pConfig.processContext.getName())
          || blacklistProcessorsByType.contains(pConfig.connectable.getComponentType())
          || configTagEnums.contains(ConfigTagEnum.NoOTEL)) {
        return Optional.empty();
      }
      return Optional.of(tracer.spanBuilder(
              pConfig.connectable.getComponentType() + ":" + pConfig.processContext.getName())
          .setAttribute("nifi.component.name", pConfig.processContext.getName())
          .setAttribute("nifi.component.type", pConfig.connectable.getComponentType())
          .setAttribute("nifi.processgroup.name", pConfig.connectable.getProcessGroup().getName())
          .setAttribute("nifi.component.id", pConfig.connectable.getIdentifier()));
    } else {
      for (String prefix : externalPropagationThreadPrefixes) {
        if (Thread.currentThread().getName().startsWith(prefix)
            || configTagEnums.contains(ConfigTagEnum.ThreadPrefixesExternalPropagation)) {
          return Optional.of(tracer.spanBuilder(prefix));
        }
      }
    }
    return Optional.of(tracer.spanBuilder("Handle Flow File"));
  }

  public static Context getDefaultContext() {
    ActiveConnectableConfig pConfig = ActiveConnectableSaver.get();
    ArrayList<ConfigTagEnum> configTagEnums = pConfig.processContext != null
        ? getProcessorTags(pConfig.processContext.getName())
        : new ArrayList<>();
    if (pConfig.connectable != null) {
      if (externalPropagationProcessors.contains(pConfig.connectable.getComponentType())
          || configTagEnums.contains(ConfigTagEnum.ExternalPropagation)) {
        return Java8BytecodeBridge.currentContext();
      }
    } else {
      for (String prefix : externalPropagationThreadPrefixes) {
        if (Thread.currentThread().getName().startsWith(prefix)
            || configTagEnums.contains(ConfigTagEnum.ThreadPrefixesExternalPropagation)) {
          return Java8BytecodeBridge.currentContext();
        }
      }
    }

    return Java8BytecodeBridge.rootContext();
  }


  public static void startFileHandlingSpan(ProcessSession session, FlowFile flowFile) {
    // if no external context was found, use root context since current context may be spam
    List<Context> externalContext = ExternalContextTracker.pop(session, singletonList(getDefaultContext()));
    Optional<SpanBuilder> spanBuilderOpt = createSpanBuilder();
    if (!spanBuilderOpt.isPresent()) {
      return;
    }
    SpanBuilder spanBuilder = spanBuilderOpt.get();
    Span span;
    if (externalContext.size() == 1) {
      Context extractedContext = GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .extract(
              externalContext.get(0),
              // using root context because we want only the extracted context if exists
              flowFile.getAttributes(),
              FlowFileAttributesTextMapGetter.INSTANCE
          );
      span = spanBuilder.setParent(extractedContext).startSpan();
    } else {
      for (Context context : externalContext) {
        SpanContext spanContext = Span.fromContext(context).getSpanContext();
        if (spanContext.isValid()) {
          spanBuilder.addLink(spanContext);
        }
      }
      span = spanBuilder.setNoParent().startSpan();
    }
    Scope scope = span.makeCurrent();
    ProcessSpanTracker.set(session, flowFile, span, scope);
  }

  public static void startFileHandlingSpan(
      ProcessSession session,
      Collection<FlowFile> flowFiles) {
    for (FlowFile flowFile : flowFiles) {
      // in case of multiple files, only the last will be "active"
      startFileHandlingSpan(session, flowFile);
    }
  }

  /**
   * Creates a link to parents instead of setting as direct parent, allowing more then one parent.
   */
  public static void startMergeFilesSpan(
      ProcessSession session,
      Collection<FlowFile> inputFlowFiles,
      FlowFile outputFlowFile
  ) {
    Optional<SpanBuilder> spanBuilderOpt = createSpanBuilder();
    if (!spanBuilderOpt.isPresent()) {
      return;
    }
    SpanBuilder spanBuilder = spanBuilderOpt.get();
    List<Context> parentContexts = inputFlowFiles.stream()
        .map(flowFile -> GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Java8BytecodeBridge.currentContext(), flowFile.getAttributes(),
                FlowFileAttributesTextMapGetter.INSTANCE)).collect(Collectors.toList());

    Span span = spanBuilder.setNoParent().startSpan();

    for (Context context : parentContexts) {
      SpanLinkTracker.addLink(span, Span.fromContext(context).getSpanContext());
    }

    Scope scope = span.makeCurrent();
    ProcessSpanTracker.set(session, outputFlowFile, span, scope);
  }

  public static void startCreateFromFileSpan(
          ProcessSession session,
          FlowFile inputFile,
          FlowFile createdFile
  ) {
    ActiveConnectableConfig pConfig = ActiveConnectableSaver.get();
    ArrayList<ConfigTagEnum> configTagEnums = pConfig.processContext != null
        ? getProcessorTags(pConfig.processContext.getName())
        : new ArrayList<>();
    if (useLinksProcessors.contains(pConfig.connectable.getComponentType())
        || configTagEnums.contains(ConfigTagEnum.UseLinks)) {
      startMergeFilesSpan(session, Collections.singletonList(inputFile), createdFile);
    } else {
      startFileHandlingSpan(session, createdFile);
    }
  }

  /**
   * 1. Injects span context to flow file, creates new file
   * 2. records attributes to span
   */
  public static FlowFile handleTransferFlowFile(
      FlowFile flowFile,
      Relationship relationship,
      ProcessSession processSession
  ) {

    Span span = ProcessSpanTracker.getSpan(processSession, flowFile);
    if (span == null) {
      return flowFile;
    }
    for (Map.Entry<String, String> entry : flowFile.getAttributes().entrySet()) {
      span.setAttribute("nifi.attributes." + entry.getKey(), entry.getValue());
    }
    span.setAttribute("nifi.relationship.target", relationship.getName());
    Map<String, String> carrier = new HashMap<>();
    TextMapSetter<Map<String, String>> setter = FlowFileAttributesTextMapSetter.INSTANCE;
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(Java8BytecodeBridge.currentContext().with(span), carrier, setter);
    return processSession.putAllAttributes(flowFile, carrier);
  }

  public static List<FlowFile> handleTransferFlowFiles(
      Collection<FlowFile> flowFiles,
      Relationship relationship,
      ProcessSession processSession
  ) {
    return flowFiles.stream()
        .map(flowFile -> handleTransferFlowFile(flowFile, relationship, processSession))
        .collect(Collectors.toList());
  }

  public static ArrayList<ConfigTagEnum> getProcessorTags(String processorName) {
    ArrayList<ConfigTagEnum> configTagEnums = new ArrayList<>();
    for (ConfigTagEnum configTag : ConfigTagEnum.values()) {
      if (processorName.contains(configTag.name())) {
        configTagEnums.add(configTag);
      }
    }
    return configTagEnums;
  }
}
