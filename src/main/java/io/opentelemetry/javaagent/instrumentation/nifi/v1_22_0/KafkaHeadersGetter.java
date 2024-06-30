/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

enum KafkaHeadersGetter implements TextMapGetter<Headers> {
  INSTANCE;

  @Override
  public Iterable<String> keys(@Nullable Headers carrier) {
    if (carrier == null) {
      return new LinkedList<>();
    }
    return StreamSupport.stream(carrier.spliterator(), false)
        .map(Header::key)
        .collect(Collectors.toList());
  }

  @Override
  @Nullable
  public String get(@Nullable Headers carrier, String key) {
    if (carrier == null) {
      return null;
    }
    Header header = carrier.lastHeader(key);
    if (header == null) {
      return null;
    }
    byte[] value = header.value();
    if (value == null) {
      return null;
    }
    return new String(value, StandardCharsets.UTF_8);
  }
}
