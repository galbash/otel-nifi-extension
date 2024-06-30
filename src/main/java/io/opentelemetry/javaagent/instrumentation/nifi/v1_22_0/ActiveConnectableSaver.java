package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.nifi.connectable.Connectable;
import org.apache.nifi.processor.ProcessContext;

import java.util.Arrays;
import java.util.logging.Logger;

public class ActiveConnectableSaver {
  private static final VirtualField<Thread, Connectable> activeConnectableMap =
      VirtualField.find(Thread.class, Connectable.class);
  private static final VirtualField<Thread, ProcessContext> activeProcessContextMap =
      VirtualField.find(Thread.class, ProcessContext.class);
  private static final Logger logger =
      Logger.getLogger(ActiveConnectableSaver.class.getName());

  private ActiveConnectableSaver() {}

  public static void set(Connectable connectable, ProcessContext processContext) {
    activeConnectableMap.set(Thread.currentThread(), connectable);
    activeProcessContextMap.set(Thread.currentThread(), processContext);
  }

  public static ActiveConnectableConfig get() {
    Connectable connectable = activeConnectableMap.get(Thread.currentThread());
    ProcessContext processContext = activeProcessContextMap.get(Thread.currentThread());
    if (connectable == null || processContext == null) {
      logger.warning("active connectable config is null");
    }
    return new ActiveConnectableConfig(connectable, processContext);
  }

  public static void remove() {
    activeConnectableMap.set(Thread.currentThread(), null);
    activeProcessContextMap.set(Thread.currentThread(), null);
  }
}
