package com.adarsh.listeners;

import org.testng.IAnnotationTransformer;
import org.testng.IRetryAnalyzer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Attaches {@link RetryAnalyzer} to every test method in the suite.
 *
 * <p>The alternative is {@code @Test(retryAnalyzer = RetryAnalyzer.class)} on each method, which
 * is 24 places to forget today and more tomorrow. Applying it here means the policy is declared
 * once and cannot drift: a new test is covered the moment it is written.</p>
 *
 * <p>An analyzer someone set deliberately is left alone. Silently overwriting a test's own retry
 * policy would be the wrong kind of helpful.</p>
 */
public class RetryTransformer implements IAnnotationTransformer {

    /**
     * TestNG's placeholder for "no analyzer set". Matched by name rather than by class literal
     * because the class lives in {@code org.testng.internal}, and compiling against another
     * library's internals is how a minor-version bump breaks your build.
     */
    private static final String NO_ANALYZER = "DisabledRetryAnalyzer";

    /** Raw parameter types are TestNG's own signature; generifying them stops it overriding. */
    @Override
    @SuppressWarnings("rawtypes")
    public void transform(
            ITestAnnotation annotation,
            Class testClass,
            Constructor testConstructor,
            Method testMethod) {

        Class<? extends IRetryAnalyzer> existing = annotation.getRetryAnalyzerClass();
        if (existing == null || NO_ANALYZER.equals(existing.getSimpleName())) {
            annotation.setRetryAnalyzer(RetryAnalyzer.class);
        }
    }
}
