package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.nifi.processor.ProcessSession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;

public final class ConsumerLeaseSingletons {
  private ConsumerLeaseSingletons() {}

  public static void setContext(
      ProcessSession session,
      ConsumerRecord<byte[], byte[]> record
  ) {
    if (record == null) return;
    setContext(session, singletonList(record));
  }
  public static void setContext(
      ProcessSession session,
      List<ConsumerRecord<byte[], byte[]>> records
  ) {
    if (records == null || records.isEmpty()) return;

    List<Context> extractedContexts = new ArrayList<>(records.size());
    Set<SpanContext> seenSpanContexts = new HashSet<>();

    for (ConsumerRecord<byte[], byte[]> record : records) {
      Context context = extractContextFromConsumerRecord(record);
      SpanContext spanContext = Span.fromContext(context).getSpanContext();

      if (spanContext.isValid() && seenSpanContexts.add(spanContext)) {
        extractedContexts.add(context);
      }
    }

    ExternalContextTracker.set(session, extractedContexts);
  }

  private static Context extractContextFromConsumerRecord(ConsumerRecord<byte[], byte[]> record) {
    return GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .extract(
              // using root context because we want only the extracted context if exists
              Java8BytecodeBridge.rootContext(),
              record.headers(),
              KafkaHeadersGetter.INSTANCE
          );
  }
}
