package org.zalando.riptide.autoconfigure.metrics;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.zalando.logbook.spring.LogbookAutoConfiguration;
import org.zalando.riptide.Http;
import org.zalando.riptide.autoconfigure.MetricsTestAutoConfiguration;
import org.zalando.riptide.autoconfigure.OpenTracingTestAutoConfiguration;
import org.zalando.riptide.autoconfigure.RiptideClientTest;
import org.zalando.riptide.faults.TransientFaultException;
import org.zalando.tracer.autoconfigure.TracerAutoConfiguration;

import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Ordering.from;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.zalando.riptide.Bindings.anyStatus;
import static org.zalando.riptide.Bindings.on;
import static org.zalando.riptide.Navigators.status;
import static org.zalando.riptide.PassRoute.pass;
import static org.zalando.riptide.failsafe.RetryRoute.retry;

@RiptideClientTest
@ActiveProfiles("default")
final class MetricsTest {

    @Configuration
    @ImportAutoConfiguration({
            JacksonAutoConfiguration.class,
            LogbookAutoConfiguration.class,
            TracerAutoConfiguration.class,
            OpenTracingTestAutoConfiguration.class,
            MetricsTestAutoConfiguration.class,
    })
    static class ContextConfiguration {

    }

    @Autowired
    @Qualifier("foo")
    private Http foo;

    @Autowired
    @Qualifier("bar")
    private Http bar;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private SimpleMeterRegistry registry;

    @Test
    void shouldRecordRequests() {
        server.expect(requestTo("http://foo")).andRespond(withSuccess());
        server.expect(requestTo("http://bar")).andRespond(withSuccess());

        foo.get().call(pass()).join();
        bar.get().call(pass()).join();

        final List<Timer> timers = timers("http.client.requests");

        assertEquals(2, timers.size());

        final Timer first = timers.get(0);
        assertEquals("GET", first.getId().getTag("method"));
        assertEquals("bar", first.getId().getTag("clientId"));


        final Timer second = timers.get(1);
        assertEquals("GET", second.getId().getTag("method"));
        assertEquals("foo", second.getId().getTag("clientId"));
    }

    @Test
    void shouldRecordRetries() {
        server.expect(requestTo("http://foo")).andRespond(withStatus(SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://foo")).andRespond(withStatus(SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://foo")).andRespond(withSuccess());

        foo.get()
                .dispatch(status(),
                        on(SERVICE_UNAVAILABLE).call(retry()),
                        anyStatus().call(pass()))
                .join();

        final List<Timer> timers = timers("http.client.retries");

        assertEquals(2, timers.size());

        final Timer first = timers.get(0);
        assertEquals("1", first.getId().getTag("retries"));
        assertEquals("foo", first.getId().getTag("clientId"));

        final Timer second = timers.get(1);
        assertEquals("2", second.getId().getTag("retries"));
        assertEquals("foo", second.getId().getTag("clientId"));
    }

    @Test
    void shouldRecordCircuitBreakers() {
        server.expect(requestTo("http://bar")).andRespond(withStatus(SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://bar")).andRespond(withStatus(SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://bar")).andRespond(withStatus(SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://bar")).andRespond(withStatus(SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://bar")).andRespond(withSuccess());

        bar.get()
                .dispatch(status(),
                        on(SERVICE_UNAVAILABLE).call(() -> {
                            throw new TransientFaultException();
                        }),
                        anyStatus().call(pass()))
                .join();

        final List<Timer> timers = timers("http.client.circuit-breakers");

        assertEquals(2, timers.size());

        final Timer halfOpen = timers.get(0);
        assertEquals("HALF_OPEN", halfOpen.getId().getTag("state"));
        assertEquals("bar", halfOpen.getId().getTag("clientId"));
        assertEquals(4, halfOpen.count());

        final Timer open = timers.get(1);
        assertEquals("OPEN", open.getId().getTag("state"));
        assertEquals("bar", open.getId().getTag("clientId"));
        assertEquals(4, open.count());
    }

    private List<Timer> timers(final String name) {
        return registry.find(name).timers().stream()
                .sorted(comparing(this::tags, from(comparing(Tag::getKey)
                        .thenComparing(Tag::getValue)).lexicographical()))
                .collect(Collectors.toList());
    }

    private List<Tag> tags(final Timer timer) {
        return timer.getId().getTags();
    }

}
