package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.apache.nifi.flowfile.FlowFile;

import java.util.logging.Logger;

public final class PublisherLeaseSingletons {
  private static final Logger logger =
      Logger.getLogger(PublisherLeaseSingletons.class.getName());

  private PublisherLeaseSingletons() {}

  /**
   * Makes the span that belongs to the given flow file the current context for the duration of the
   * publish call. This ensures the standard Kafka producer instrumentation injects the flow file's
   * own trace context into the produced record, instead of whatever context happens to be active on
   * the thread (which, for a batch, is the last flow file that was read).
   *
   * @return the opened {@link Scope}, or {@code null} if no span is tracked for the flow file. The
   *     caller must close the returned scope.
   */
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
