package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


public class ProcessSpanTracker {
  public static final String SPAN_SUFFIX = "-span";
  public static final String SCOPE_SUFFIX = "-scope";
  private static final VirtualField<ProcessSession, ConcurrentHashMap<String, Object>> processMap =
      VirtualField.find(ProcessSession.class, ConcurrentHashMap.class);
  private static final Logger logger = Logger.getLogger(ProcessSpanTracker.class.getName());


  private ProcessSpanTracker() {}

  public static void set(ProcessSession session, FlowFile file, Span span, Scope scope) {
    String id = file.getAttribute(CoreAttributes.UUID.key());
    ConcurrentHashMap<String, Object> map = getOrCreateMap(session);
    map.put(genSpanKey(id), span);
    map.put(genScopeKey(id), scope);
  }

  private static String genSpanKey(String id) {
    return id + SPAN_SUFFIX;
  }

  private static String genScopeKey(String id) {
    return id + SCOPE_SUFFIX;
  }

  private static ConcurrentHashMap<String, Object> getOrCreateMap(
      ProcessSession session
  ) {
    ConcurrentHashMap<String, Object> map = processMap.get(session);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      processMap.set(session, map);
    }
    return map;
  }

  public static Span getSpan(ProcessSession session, FlowFile file) {
    String id = file.getAttribute(CoreAttributes.UUID.key());
    ConcurrentHashMap<String, Object> map = getOrCreateMap(session);
    Object value = map.get(genSpanKey(id));
    if (!(value instanceof Span)) {
      return null;
    }
    return (Span) value;
  }

  public static void close(ProcessSession session) {
    ConcurrentHashMap<String, Object> map = getOrCreateMap(session);
    map.values().forEach(value -> {
      if (value instanceof Scope) {
        ((Scope) value).close();
      } else if (value instanceof Span) {
        ((Span) value).end();
      } else {
        logger.warning("Got a non-scope/span value: " + value);
      }
    });
    map.clear();
  }

  public static void migrate(ProcessSession oldSession, ProcessSession newSession,
      Collection<FlowFile> flowFiles) {
    ConcurrentHashMap<String, Object> oldMap = getOrCreateMap(oldSession);
    ConcurrentHashMap<String, Object> newMap = getOrCreateMap(newSession);
    for (FlowFile file : flowFiles) {
      String id = file.getAttribute(CoreAttributes.UUID.key());
      String spanId = genSpanKey(id);
      if (oldMap.containsKey(spanId)) {
        newMap.put(spanId, oldMap.remove(spanId));
      }
      String scopeId = genScopeKey(id);
      if (oldMap.containsKey(scopeId)) {
        newMap.put(scopeId, oldMap.remove(scopeId));
      }
    }
  }
}
