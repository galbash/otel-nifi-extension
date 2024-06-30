package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.nifi.processor.ProcessSession;

public final class ConsumerLeaseSingletons {
  private ConsumerLeaseSingletons() {}

  public static void setContext(
      ProcessSession session,
      ConsumerRecord<byte[], byte[]> record
  ) {
    if (record != null) {
      Context extractedContext = GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .extract(
              // using root context because we want only the extracted context if exists
              Java8BytecodeBridge.rootContext(),
              record.headers(),
              KafkaHeadersGetter.INSTANCE
          );
      ExternalContextTracker.set(session, extractedContext);
    }
  }

}
