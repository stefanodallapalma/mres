package com.example;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Marker;

import java.util.UUID;

import static org.openrewrite.Tree.randomId;

public class OldToNewLogger extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate old to new logger API";
    }

    @Override
    public String getDescription() {
        return "...";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new LoggingMethodChainVisitor();
    }

    private static class LoggingMethodChainVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Value
        @With
        @AllArgsConstructor
        private static class AlreadyTransformed implements Marker {
            UUID id;

            public AlreadyTransformed() {
                id = randomId();
            }
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            method = super.visitMethodInvocation(method, ctx);

            if (method.getMarkers().findFirst(AlreadyTransformed.class).isPresent()) {
                return method;
            }

            if (isOldLoggerMethod(method) && isLoggingLevelMethod(method) && hasWithMethodInChain(method)) {
                J.MethodInvocation transformedMethod = transformToNewAPI(method);
                return transformedMethod.withMarkers(
                        transformedMethod.getMarkers().addIfAbsent(new AlreadyTransformed())
                );
            }

            return method;
        }

        private boolean isOldLoggerMethod(J.MethodInvocation method) {
            JavaType.Method methodType = method.getMethodType();

            if (methodType != null) {
                JavaType.FullyQualified declaringType = methodType.getDeclaringType();
                return TypeUtils.isAssignableTo("com.example.util.OldLogger", declaringType);
            }

            return false;
        }

        private boolean isLoggingLevelMethod(J.MethodInvocation method) {
            String methodName = method.getSimpleName();
            return "info".equals(methodName) ||
                    "warn".equals(methodName) ||
                    "error".equals(methodName) ||
                    "debug".equals(methodName) ||
                    "trace".equals(methodName);
        }

        /**
         * Check if this method is called on a result of .with() method
         */
        private boolean hasWithMethodInChain(J.MethodInvocation method) {
            if (method.getSelect() instanceof J.MethodInvocation select) {
                return "with".equals(select.getSimpleName());
            }
            return false;
        }

        /**
         * Extract the with() method invocation from the chain
         */
        private J.MethodInvocation extractWithMethod(J.MethodInvocation loggingMethod) {
            if (loggingMethod.getSelect() instanceof J.MethodInvocation select && "with".equals(select.getSimpleName())) {
                return select;
            }
            return null;
        }

        private J.MethodInvocation transformToNewAPI(J.MethodInvocation method) {
            // Extract the with() method and its arguments
            J.MethodInvocation withMethod = extractWithMethod(method);
            if (withMethod == null) {
                return method;
            }

            Expression logger = withMethod.getSelect();
            JavaTemplate template = buildTemplate();

            String capitalizedLogLevel = capitalize(method.getSimpleName()); // E.g. Info, Warn
            Expression withMethodArg = withMethod.getArguments().getFirst();
            Expression logMethodArg = method.getArguments().getFirst();

            return template.apply(
                    getCursor(),
                    method.getCoordinates().replace(),
                    logger, capitalizedLogLevel, withMethodArg, logMethodArg); // This gives "LST contains missing or invalid type information" if `dependsOn` below is commented
//                    logger, capitalizedLogLevel, logMethodArg, withMethodArg); // This generates no error, but args are swapped
        }

        private JavaTemplate buildTemplate() {
            return JavaTemplate.builder("#{any(com.example.util.OldLogger)}.at#{}()" +
                            ".with(#{})"+
                            ".log(#{})"
                    )
                    .contextSensitive()
                    .imports("com.example.util.OldLogger")
                    .javaParser(JavaParser.fromJavaVersion()
//                            .dependsOn( // UNCOMMENT ME TO MAKE THE TEST PASS
//                                    """
//                                    package com.example.util;
//                                    public class OldLogger {
//                                        public OldLogger atInfo() { return this; }
//                                        public OldLogger with(String a) { return this; }
//                                        public void log(String s) {}
//                                    }
//                                    """
//                            )
                    )
                    .build();
        }

        private String capitalize(String str) {
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
    }
}