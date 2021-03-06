package org.zalando.riptide.autoconfigure;

import com.google.gag.annotation.remark.Hack;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.apiguardian.api.API;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.apiguardian.api.API.Status.INTERNAL;
import static org.apiguardian.api.API.Status.STABLE;
import static org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.DEFAULT_TASK_SCHEDULER_BEAN_NAME;

@API(status = STABLE)
@Configuration
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
        "org.zalando.logbook.spring.LogbookAutoConfiguration",
        "org.zalando.tracer.autoconfigure.TracerAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
})
@AutoConfigureBefore(name = {
        "org.springframework.scheduling.annotation.SchedulingConfiguration",
        "org.zalando.actuate.autoconfigure.failsafe.CircuitBreakersEndpointAutoConfiguration"
})
public class RiptideAutoConfiguration {

    @API(status = INTERNAL)
    @Bean
    public static RiptidePostProcessor restClientPostProcessor() {
        return new RiptidePostProcessor(DefaultRiptideRegistrar::new);
    }

    @Hack("We need to 'rename' the existing meter registry so we can find it by name...")
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsAutoConfiguration {

        @Bean
        @Primary
        @ConditionalOnBean({MeterRegistry.class, Clock.class})
        @ConditionalOnMissingBean(name = "meterRegistry")
        public CompositeMeterRegistry meterRegistry(final Clock clock, final List<MeterRegistry> registries) {
            return new CompositeMeterRegistry(clock, registries);
        }

    }

    // needed because @Scheduled would silently use the single-thread scheduler needed for retries/circuit breakers
    // see https://github.com/zalando/riptide/issues/319 for details
    @Configuration
    @ConditionalOnClass(Scheduled.class)
    static class SchedulingAutoConfiguration {

        @Bean(name = DEFAULT_TASK_SCHEDULER_BEAN_NAME, destroyMethod = "shutdown")
        @ConditionalOnMissingBean(name = DEFAULT_TASK_SCHEDULER_BEAN_NAME)
        public ScheduledExecutorService taskScheduler() {
            final int corePoolSize = Runtime.getRuntime().availableProcessors();
            return Executors.newScheduledThreadPool(corePoolSize);
        }

    }

}
