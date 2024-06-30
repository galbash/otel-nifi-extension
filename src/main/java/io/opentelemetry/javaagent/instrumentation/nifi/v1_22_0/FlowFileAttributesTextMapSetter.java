package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Map;

public enum FlowFileAttributesTextMapSetter implements TextMapSetter<Map<String, String>> {
  INSTANCE;

  @Override
  public void set(Map<String, String> carrier, String key, String value) {
    if (carrier != null) {
      carrier.put(key, value);
    }
  }
}
