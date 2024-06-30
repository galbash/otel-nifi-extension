package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import org.apache.nifi.connectable.Connectable;
import org.apache.nifi.processor.ProcessContext;

public class ActiveConnectableConfig {
  public Connectable connectable;
  public ProcessContext processContext;

  public ActiveConnectableConfig(Connectable connectable, ProcessContext processContext) {
    this.connectable = connectable;
    this.processContext = processContext;
  }
}
