package com.eu.habbo.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DependencyCircuitBreakers {

    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyCircuitBreakers.class);

    private final RuntimeResilienceController.Mode mode;
    private final CircuitBreakerConfig config;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public DependencyCircuitBreakers(RuntimeResilienceController.Mode mode, Settings settings) {
        this.mode = Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(settings, "settings");
        this.config = CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.failureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                .waitDurationInOpenState(settings.openDuration())
                .permittedNumberOfCallsInHalfOpenState(settings.halfOpenCalls())
                .build();
    }

    public <T> T execute(String dependency, CheckedSupplier<T> primary, CheckedSupplier<T> fallback) {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(fallback, "fallback");
        Entry entry = this.entry(dependency);
        entry.attempted.incrementAndGet();

        boolean permitted = entry.breaker.tryAcquirePermission();
        if (!permitted) {
            entry.wouldShortCircuit.incrementAndGet();
            if (this.mode == RuntimeResilienceController.Mode.ENFORCE) {
                entry.shortCircuited.incrementAndGet();
                return useFallback(entry, fallback);
            }
            return callWithoutBreakerFeedback(entry, primary, fallback);
        }

        long startedAt = System.nanoTime();
        try {
            T value = primary.get();
            entry.breaker.onSuccess(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            return value;
        } catch (Exception exception) {
            entry.failures.incrementAndGet();
            entry.breaker.onError(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS, exception);
            LOGGER.warn("External dependency '{}' failed; using configured fallback", dependency, exception);
            return useFallback(entry, fallback);
        }
    }

    public Snapshot snapshot(String dependency) {
        Entry entry = this.entry(dependency);
        return new Snapshot(
                entry.breaker.getState().name(),
                entry.attempted.get(),
                entry.failures.get(),
                entry.wouldShortCircuit.get(),
                entry.shortCircuited.get(),
                entry.fallbacks.get());
    }

    private Entry entry(String dependency) {
        if (dependency == null || dependency.isBlank()) {
            throw new IllegalArgumentException("dependency name must not be blank");
        }
        return this.entries.computeIfAbsent(dependency, name -> new Entry(CircuitBreaker.of(name, this.config)));
    }

    private static <T> T callWithoutBreakerFeedback(
            Entry entry, CheckedSupplier<T> primary, CheckedSupplier<T> fallback) {
        try {
            return primary.get();
        } catch (Exception exception) {
            entry.failures.incrementAndGet();
            return useFallback(entry, fallback);
        }
    }

    private static <T> T useFallback(Entry entry, CheckedSupplier<T> fallback) {
        entry.fallbacks.incrementAndGet();
        try {
            return fallback.get();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DependencyFallbackException(exception);
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    public record Settings(
            float failureRateThreshold,
            int slidingWindowSize,
            int minimumNumberOfCalls,
            Duration openDuration,
            int halfOpenCalls) {
        public Settings {
            if (!Float.isFinite(failureRateThreshold) || failureRateThreshold <= 0F || failureRateThreshold > 100F) {
                throw new IllegalArgumentException("failure rate threshold must be between 0 and 100");
            }
            if (slidingWindowSize < 1
                    || minimumNumberOfCalls < 1
                    || minimumNumberOfCalls > slidingWindowSize
                    || halfOpenCalls < 1) {
                throw new IllegalArgumentException("circuit breaker call limits are invalid");
            }
            if (openDuration == null || openDuration.isZero() || openDuration.isNegative()) {
                throw new IllegalArgumentException("open duration must be positive");
            }
        }
    }

    public record Snapshot(
            String state, long attempted, long failures, long wouldShortCircuit, long shortCircuited, long fallbacks) {}

    public static final class DependencyFallbackException extends RuntimeException {
        public DependencyFallbackException(Throwable cause) {
            super("Dependency fallback failed", cause);
        }
    }

    private static final class Entry {
        private final CircuitBreaker breaker;
        private final AtomicLong attempted = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong wouldShortCircuit = new AtomicLong();
        private final AtomicLong shortCircuited = new AtomicLong();
        private final AtomicLong fallbacks = new AtomicLong();

        private Entry(CircuitBreaker breaker) {
            this.breaker = breaker;
        }
    }
}
