package com.example;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.java.Assertions.java;

class OldToNewLoggerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new OldToNewLogger());
    }

    private static final SourceSpecs OLD_LOGGER =
            // language=java
            java(
                    """
                            package com.example.util;
                            
                            public class OldLogger {
                                public static OldLogger getLog(Class<?> clazz) {
                                    return new OldLogger();
                                }
                                
                                public OldLogger atInfo() {
                                    return this;
                                }
                            
                                public OldLogger with(String referenceLabel) {
                                    return this;
                                }
                            
                                public void info(String info) {
                                    System.out.println(info);
                                }
                                
                                public void log(String text) {
                                    System.out.println(text);
                                }
                            }
                            """
            );

    @Test
    void methodThrowsIOExceptionShouldBeThrowingTargetException() {
        rewriteRun(
                // Dependency classes
                OLD_LOGGER,

                // language=java
                java(
                        """
                                package com.example.example;
                                
                                import com.example.util.OldLogger;
                                
                                public class Test {
                                    private static final OldLogger logger = OldLogger.getLog(Test.class);
                                
                                    public void foobar() {
                                        logger.with("LogField").info("Duplicated tx found by pspReference");
                                    }
                                }
                                """, """
                                package com.example.example;
                                
                                import com.example.util.OldLogger;
                                
                                public class Test {
                                    private static final OldLogger logger = OldLogger.getLog(Test.class);
                                
                                    public void foobar() {
                                        logger.atInfo().with("LogField").log("Duplicated tx found by pspReference");
                                    }
                                }
                                """
                )
        );
    }
}