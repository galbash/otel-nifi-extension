/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.nifi.flowfile.FlowFile;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instrumenting NiFi Kafka publisher so each flow file is published with its own trace context.
 *
 * <p>When a PublishKafka processor reads a batch of flow files, only the last flow file's span is
 * left active on the thread. Without this instrumentation, the standard Kafka producer
 * instrumentation would inject that single (last) context into every produced record. Here we make
 * each flow file's own span current for the duration of its publish call.
 */
public class NiFiPublisherLeaseInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.nifi.processors.kafka.pubsub.PublisherLease");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.hasSuperType(
        named("org.apache.nifi.processors.kafka.pubsub.PublisherLease"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    // NOTE: none of PublisherLease#publish is public (they are package-private / protected), so we
    // must NOT restrict on visibility. The overload that actually calls producer.send(...) is the
    // protected publish(FlowFile, List<Header>, ...). All overloads take the FlowFile as arg 0;
    // matching all of them is harmless (re-activating the same span nests scopes safely) and
    // guarantees the send is wrapped by the flow file's own context.
    typeTransformer.applyAdviceToMethod(
        named("publish").and(takesArgument(0, FlowFile.class)),
        NiFiPublisherLeaseInstrumentation.class.getName() + "$PublishAdvice");
  }

  @SuppressWarnings("unused")
  public static class PublishAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(@Advice.Argument(0) FlowFile flowFile) {
      return PublisherLeaseSingletons.makeFlowFileSpanCurrent(flowFile);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
