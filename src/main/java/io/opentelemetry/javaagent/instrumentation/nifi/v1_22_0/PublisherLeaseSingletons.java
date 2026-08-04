package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.apache.nifi.flowfile.FlowFile;

import java.util.logging.Logger;

public final class PublisherLeaseSingletons {
  private static final Logger logger =
      Logger.getLogger(PublisherLeaseSingletons.class.getName());

  private PublisherLeaseSingletons() {}


  public static Scope makeFlowFileSpanCurrent(FlowFile flowFile) {
    if (flowFile == null) {
      return null;
    }
    Span span = ProcessSpanTracker.getSpanForCurrentThread(flowFile);
    if (span == null) {
      logger.fine("No tracked span found for flow file being published; leaving context as-is");
      return null;
    }
    logger.fine("Activating flow file span for kafka publish");
    return span.makeCurrent();
  }
}
