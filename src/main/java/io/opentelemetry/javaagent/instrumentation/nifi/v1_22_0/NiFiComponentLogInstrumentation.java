/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.nifi.connectable.Connectable;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSessionFactory;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class NiFiComponentLogInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.nifi.logging.ComponentLog");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.implementsInterface(
        namedOneOf("org.apache.nifi.logging.ComponentLog"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(namedOneOf("error"),

        NiFiComponentLogInstrumentation.class.getName() + "$ErrorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ErrorAdvice {

    @Advice.OnMethodEnter()
    public static void onEnter() {
      Java8BytecodeBridge.currentSpan().setStatus(StatusCode.ERROR);
    }
  }
}
