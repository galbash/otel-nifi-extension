/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.nifi.flowfile.FlowFile;

import java.util.List;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

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
    typeTransformer.applyAdviceToMethod(
        named("publish")
            .and(takesArgument(0, FlowFile.class))
            .and(takesArgument(1, List.class)),
        NiFiPublisherLeaseInstrumentation.class.getName() + "$PublishAdvice");
  }

  @SuppressWarnings("unused")
  public static class PublishAdvice {
    // NOTE (experimental): the onExit scope.close() was removed on purpose to test behavior without
    // it. The context activated here is intentionally NOT restored after publish returns.
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) FlowFile flowFile) {
      PublisherLeaseSingletons.makeFlowFileContextCurrent(flowFile);
    }
  }
}
