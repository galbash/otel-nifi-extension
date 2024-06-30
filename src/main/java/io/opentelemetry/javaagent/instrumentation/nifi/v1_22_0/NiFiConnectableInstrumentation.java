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
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.connectable.Connectable;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class NiFiConnectableInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.nifi.connectable.Connectable");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.implementsInterface(
        namedOneOf("org.apache.nifi.connectable.Connectable"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(namedOneOf("onTrigger")
            .and(takesArguments(ProcessContext.class, ProcessSessionFactory.class)),
        NiFiConnectableInstrumentation.class.getName() + "$OnTriggerAdvice");
  }

  @SuppressWarnings("unused")
  public static class OnTriggerAdvice {

    @Advice.OnMethodEnter()
    public static void onEnter(
        @Advice.This Connectable connectable,
        @Advice.Argument(0) ProcessContext processContext) {
      ActiveConnectableSaver.set(connectable, processContext);
    }

    @Advice.OnMethodExit()
    public static void onExit() {
      ActiveConnectableSaver.remove();
    }
  }
}
