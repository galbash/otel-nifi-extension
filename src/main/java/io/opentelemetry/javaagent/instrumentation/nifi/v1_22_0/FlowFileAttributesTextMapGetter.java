package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.context.propagation.TextMapGetter;

import javax.annotation.Nullable;
import java.util.Map;

public enum FlowFileAttributesTextMapGetter implements TextMapGetter<Map<String, String>> {
  INSTANCE;

  @Nullable
  @Override
  public String get(Map<String, String> carrier, String key) {
    try {
      return carrier.get(key);
    } catch (NullPointerException e) {
      return null;
    }
  }

  @Override
  public Iterable<String> keys(Map<String, String> carrier) {
    return carrier.keySet();
  }
}
