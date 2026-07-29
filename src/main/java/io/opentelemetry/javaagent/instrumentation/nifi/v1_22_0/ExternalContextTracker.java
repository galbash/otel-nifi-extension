package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.nifi.processor.ProcessSession;

import java.util.List;

public class ExternalContextTracker {
  @SuppressWarnings("rawtypes")
  private static final VirtualField<ProcessSession, List> contextMap =
          VirtualField.find(ProcessSession.class, List.class);

  private ExternalContextTracker() {}

  /**
   * set null to clear
   */
  public static void set(ProcessSession session, List<Context> contexts) {
    contextMap.set(session, contexts);
  }

  /**
   * also resets context
   */
  @SuppressWarnings("unchecked")
  public static List<Context> pop(ProcessSession session) {
    List<Context> saved = (List<Context>) contextMap.get(session);
    set(session, null);
    return saved;
  }

  public static List<Context> pop(ProcessSession session, List<Context> defaultContexts) {
    List<Context> saved = pop(session);

    if (saved == null || saved.isEmpty()) {
      return defaultContexts;
    }

    return saved;
  }
}
