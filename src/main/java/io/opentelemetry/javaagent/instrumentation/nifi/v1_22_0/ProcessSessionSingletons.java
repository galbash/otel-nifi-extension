package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class ProcessSessionSingletons {
  private static final Logger logger =
      Logger.getLogger(ProcessSessionSingletons.class.getName());
  static Tracer tracer = GlobalOpenTelemetry.getTracer("nifi");
  static List<String> externalPropagationProcessors = InstrumentationConfig.get().getList(
          "otel.instrumentation.nifi.external-propagation-processors",
          Collections.singletonList("GetWMQ")
        );

  private ProcessSessionSingletons() {}

  private static SpanBuilder createSpanBuilder() {
    ActiveConnectableConfig pConfig = ActiveConnectableSaver.get();
    if (pConfig.processContext != null && pConfig.connectable != null) {
      return tracer.spanBuilder(
              pConfig.connectable.getComponentType() + ":" + pConfig.processContext.getName())
          .setAttribute("nifi.component.name", pConfig.processContext.getName())
          .setAttribute("nifi.component.type", pConfig.connectable.getComponentType())
          .setAttribute("nifi.processgroup.name", pConfig.connectable.getProcessGroup().getName())
          .setAttribute("nifi.component.id", pConfig.connectable.getIdentifier());
    } else if (Thread.currentThread().getName().startsWith("ListenHTTP")) {
      return tracer.spanBuilder("ListenHTTP");
    }
    return tracer.spanBuilder("Handle Flow File");
  }

  public static Context getDefaultContext() {
    ActiveConnectableConfig pConfig = ActiveConnectableSaver.get();
    if (pConfig.connectable != null) {
      for (String processorName: externalPropagationProcessors) {
        if (pConfig.connectable.getComponentType().equals(processorName)) {
          return Java8BytecodeBridge.currentContext();
        }
      }
      // ListenHTTP is on a different thread so won't be saved as the active processor
    } else if (Thread.currentThread().getName().startsWith("ListenHTTP")) {
      return Java8BytecodeBridge.currentContext();
    }

    return Java8BytecodeBridge.rootContext();
  }


  public static void startProcessSessionSpan(ProcessSession session, FlowFile flowFile) {
    // if no external context was found, use root context since current context may be spam
    Context externalContext = ExternalContextTracker.pop(session, getDefaultContext());
    Context extractedContext = GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(
            externalContext,
            // using root context because we want only the extracted context if exists
            flowFile.getAttributes(),
            FlowFileAttributesTextMapGetter.INSTANCE
        );
    Span span = createSpanBuilder()
        .setParent(extractedContext)
        .startSpan();
    Scope scope = span.makeCurrent();
    ProcessSpanTracker.set(session, flowFile, span, scope);
  }

  public static void startProcessSessionSpan(
      ProcessSession session,
      Collection<FlowFile> flowFiles) {
    for (FlowFile flowFile : flowFiles) {
      // in case of multiple files, only the last will be "active"
      startProcessSessionSpan(session, flowFile);
    }
  }

  public static void startMergeProcessSessionSpan(
      ProcessSession session,
      Collection<FlowFile> inputFlowFiles,
      FlowFile outputFlowFile

  ) {

    SpanBuilder spanBuilder = createSpanBuilder();
    List<Context> parentContexts = inputFlowFiles.stream()
        .map(flowFile -> GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Java8BytecodeBridge.currentContext(), flowFile.getAttributes(),
                FlowFileAttributesTextMapGetter.INSTANCE)).collect(Collectors.toList());

    for (Context context : parentContexts) {
      spanBuilder.addLink(Span.fromContext(context).getSpanContext());
    }

    Span span = spanBuilder.setNoParent().startSpan();
    Scope scope = span.makeCurrent();
    ProcessSpanTracker.set(session, outputFlowFile, span, scope);
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
      logger.warning("No active span for flow file found");
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
}
