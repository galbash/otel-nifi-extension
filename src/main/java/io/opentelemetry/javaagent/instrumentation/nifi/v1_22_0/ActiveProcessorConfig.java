package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.Processor;

public class ActiveProcessorConfig {
  public Processor processor;
  public ProcessContext processContext;

  public ActiveProcessorConfig(Processor processor, ProcessContext processContext) {
    this.processor = processor;
    this.processContext = processContext;
  }
}
