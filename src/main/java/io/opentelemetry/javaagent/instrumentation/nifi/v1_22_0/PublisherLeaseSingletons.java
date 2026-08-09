package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import org.apache.nifi.flowfile.FlowFile;

public final class PublisherLeaseSingletons {
  private PublisherLeaseSingletons() {}


  public static Scope makeFlowFileContextCurrent(FlowFile flowFile) {
    if (flowFile == null) {
      return null;
    }
    Context extractedContext = GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(
            Java8BytecodeBridge.rootContext(),
            flowFile.getAttributes(),
            FlowFileAttributesTextMapGetter.INSTANCE);
    if (!Span.fromContext(extractedContext).getSpanContext().isValid()) {
      return null;
    }
    return extractedContext.makeCurrent();
  }
}
