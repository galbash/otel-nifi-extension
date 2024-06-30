package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.nifi.processor.ProcessSession;

public class ExternalContextTracker {
  private static final VirtualField<ProcessSession, Context> contextMap =
      VirtualField.find(ProcessSession.class, Context.class);

  private ExternalContextTracker() {}

  /**
   * set null to clear
   */
  public static void set(ProcessSession session, Context context) {
    contextMap.set(session, context);
  }

  /**
   * also resets context
   */
  public static Context pop(ProcessSession session) {
    Context saved = contextMap.get(session);
    set(session, null);
    return saved;
  }

  public static Context pop(ProcessSession session, Context defaultContext) {
    Context saved = pop(session);
    if (saved == null) {
      return defaultContext;
    }

    return saved;
  }

}
