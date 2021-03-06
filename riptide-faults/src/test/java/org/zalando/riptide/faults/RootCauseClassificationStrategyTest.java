package org.zalando.riptide.faults;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

final class RootCauseClassificationStrategyTest {

    private final FaultClassifier unit = new DefaultFaultClassifier(new RootCauseClassificationStrategy());

    @Test
    void shouldClassifyAsTransientWithoutCause() {
        assertTransient(new IOException());
    }

    @Test
    void shouldNotClassifyAsTransientWithNonTransientCause() {
        assertNotTransient(new IOException(new IllegalArgumentException()));
    }

    @Test
    void shouldClassifyAsTransientWithTransientCause() {
        assertTransient(new IllegalArgumentException(new IOException()));
    }

    @Test
    void shouldNotClassifyAsTransientWithTransientIntermediateCause() {
        assertNotTransient(new IllegalStateException(new IOException(new IllegalArgumentException())));
    }

    @Test
    void shouldNotClassifyAsTransient() {
        assertNotTransient(new IllegalArgumentException());
    }

    private void assertTransient(final Exception e) {
        assertThat(unit.classify(e), is(instanceOf(TransientFaultException.class)));
    }

    private void assertNotTransient(final Exception e) {
        assertThat(unit.classify(e), is(sameInstance(e)));
    }

}
