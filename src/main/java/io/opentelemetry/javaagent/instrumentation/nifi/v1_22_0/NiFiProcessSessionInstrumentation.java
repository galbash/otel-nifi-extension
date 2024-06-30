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
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;

import java.util.Collection;
import java.util.List;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * Open a span on get / create with single file for each of the files
 * Close the span on commit
 * * Maybe we want to put them aside, deal with the batch and close only at the end at the merge
 * on create with list create new trace and add links
 * Inject *right* context on transfer (in case of batch find correct one)
 */
public class NiFiProcessSessionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.nifi.controller.repository.StandardProcessSession");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.hasSuperType(
        namedOneOf("org.apache.nifi.controller.repository.StandardProcessSession"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        namedOneOf("get").and(takesNoArguments()).and(returns(FlowFile.class)),
        this.getClass().getName() + "$NiFiProcessGetAdvice");

    typeTransformer.applyAdviceToMethod(
        namedOneOf("get").and(takesArguments(2)).and(returns(List.class)),
        this.getClass().getName() + "$NiFiProcessGetListAdvice");

    typeTransformer.applyAdviceToMethod(
        namedOneOf("create").and(takesNoArguments()),
        this.getClass().getName() + "$NiFiProcessGetAdvice"); // if no input file is given, create is the same as get since it's always a child of, never a link

    typeTransformer.applyAdviceToMethod(
            namedOneOf("create").and(takesArguments(FlowFile.class)),
            this.getClass().getName() + "$NiFiProcessCreateFromFileAdvice");

    typeTransformer.applyAdviceToMethod(namedOneOf("create").and(takesArguments(Collection.class)),
            this.getClass().getName() + "$NiFiProcessCreateMergeAdvice");

    typeTransformer.applyAdviceToMethod(
        namedOneOf("clone").and(takesArgument(0, FlowFile.class)).and(takesArguments(3)),
        this.getClass().getName() + "$NiFiProcessCreateFromFileAdvice"); // cloning is similar to create with a FlowFile parameter

    typeTransformer.applyAdviceToMethod(
        namedOneOf("transfer").and(takesArguments(FlowFile.class)),
        this.getClass().getName() + "$NiFiProcessTransferAdvice");

    typeTransformer.applyAdviceToMethod(
        namedOneOf("transfer").and(takesArguments(FlowFile.class, Relationship.class)),
        this.getClass().getName() + "$NiFiProcessTransferWithRelationshipAdvice");

    typeTransformer.applyAdviceToMethod(
        namedOneOf("transfer").and(takesArguments(Collection.class, Relationship.class)),
        this.getClass().getName() + "$NiFiProcessTransferListAdvice");

    typeTransformer.applyAdviceToMethod(namedOneOf("checkpoint").and(takesArguments(boolean.class)),
        this.getClass().getName() + "$NiFiProcessCheckpointAdvice");
    typeTransformer.applyAdviceToMethod(
        namedOneOf("migrate").and(isSynchronized()).and(isPrivate()),
        this.getClass().getName() + "$NiFiProcessMigrateAdvice");
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessGetAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ProcessSession session,
        @Advice.Return FlowFile flowFile
    ) {
      if (flowFile != null) {
        ProcessSessionSingletons.startFileHandlingSpan(session, flowFile);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessGetListAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ProcessSession session,
        @Advice.Return List<FlowFile> flowFiles
    ) {
      if (flowFiles != null) {
        ProcessSessionSingletons.startFileHandlingSpan(session, flowFiles);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessCreateFromFileAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
            @Advice.This ProcessSession session,
            @Advice.Argument(value = 0) FlowFile inFlowFile,
            @Advice.Return FlowFile createdFlowFile
    ) {
      if (createdFlowFile!= null) {
        ProcessSessionSingletons.startCreateFromFileSpan(session, inFlowFile, createdFlowFile);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessCreateMergeAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ProcessSession session,
        @Advice.Return FlowFile createFlowFile,
        @Advice.Argument(0) Collection<FlowFile> inputFlowFiles
    ) {
      ProcessSessionSingletons.startMergeFilesSpan(session, inputFlowFiles,
          createFlowFile);
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessTransferAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) FlowFile flowFile,
        @Advice.This ProcessSession processSession
    ) {
      flowFile = ProcessSessionSingletons.handleTransferFlowFile(
          flowFile,
          Relationship.SELF,
          processSession
      );
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessTransferWithRelationshipAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) FlowFile flowFile,
        @Advice.Argument(value = 1) Relationship relationship,
        @Advice.This ProcessSession processSession
    ) {
      flowFile = ProcessSessionSingletons.handleTransferFlowFile(
          flowFile,
          relationship,
          processSession
      );
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessTransferListAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) Collection<FlowFile> flowFiles,
        @Advice.Argument(value = 1) Relationship relationship,
        @Advice.This ProcessSession processSession
    ) {
      flowFiles = ProcessSessionSingletons.handleTransferFlowFiles(
          flowFiles,
          relationship,
          processSession
      );
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessCheckpointAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.This ProcessSession session) {
      ProcessSpanTracker.close(session);
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessMigrateAdvice {

    // not adding onThrowable - if migration failed we don't want to migrate spans
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ProcessSession oldSession,
        @Advice.Argument(0) ProcessSession newSession,
        @Advice.Argument(1) Collection<FlowFile> flowFiles
    ) {
      ProcessSpanTracker.migrate(
          oldSession,
          newSession,
          flowFiles
      );
    }
  }
}
