package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.Processor;

import java.util.logging.Logger;

public class ActiveProcessorSaver {
  private static final VirtualField<Thread, Processor> activeProcessorMap =
      VirtualField.find(Thread.class, Processor.class);
  private static final VirtualField<Thread, ProcessContext> activeProcessContextMap =
      VirtualField.find(Thread.class, ProcessContext.class);
  private static final Logger logger =
      Logger.getLogger(ActiveProcessorSaver.class.getName());

  private ActiveProcessorSaver() {}

  public static void set(Processor processor, ProcessContext processContext) {
    activeProcessorMap.set(Thread.currentThread(), processor);
    activeProcessContextMap.set(Thread.currentThread(), processContext);
  }

  public static ActiveProcessorConfig get() {
    Processor processor = activeProcessorMap.get(Thread.currentThread());
    ProcessContext processContext = activeProcessContextMap.get(Thread.currentThread());
    if (processor == null && processContext == null) {
      logger.warning("active processor config is null");
    }
    return new ActiveProcessorConfig(processor, processContext);
  }

  public static void remove() {
    activeProcessorMap.set(Thread.currentThread(), null);
    activeProcessContextMap.set(Thread.currentThread(), null);
  }
}
