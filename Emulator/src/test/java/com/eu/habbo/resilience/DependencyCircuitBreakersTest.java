package com.eu.habbo.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DependencyCircuitBreakersTest {

    @Test
    void enforceModeUsesFallbackWithoutCallingAnOpenDependency() {
        DependencyCircuitBreakers breakers = breakers(RuntimeResilienceController.Mode.ENFORCE);
        AtomicInteger calls = new AtomicInteger();

        assertEquals("fallback", breakers.execute("mail", () -> fail(calls), () -> "fallback"));
        assertEquals("fallback", breakers.execute("mail", () -> fail(calls), () -> "fallback"));
        assertEquals("fallback", breakers.execute("mail", () -> fail(calls), () -> "fallback"));

        assertEquals(2, calls.get());
        DependencyCircuitBreakers.Snapshot snapshot = breakers.snapshot("mail");
        assertEquals("OPEN", snapshot.state());
        assertEquals(1L, snapshot.wouldShortCircuit());
        assertEquals(1L, snapshot.shortCircuited());
    }

    @Test
    void observeModeReportsAnOpenBreakerButStillCallsTheDependency() {
        DependencyCircuitBreakers breakers = breakers(RuntimeResilienceController.Mode.OBSERVE);
        AtomicInteger calls = new AtomicInteger();

        breakers.execute("turnstile", () -> fail(calls), () -> "fallback");
        breakers.execute("turnstile", () -> fail(calls), () -> "fallback");
        String result = breakers.execute(
                "turnstile",
                () -> {
                    calls.incrementAndGet();
                    return "live";
                },
                () -> "fallback");

        assertEquals("live", result);
        assertEquals(3, calls.get());
        DependencyCircuitBreakers.Snapshot snapshot = breakers.snapshot("turnstile");
        assertEquals(1L, snapshot.wouldShortCircuit());
        assertEquals(0L, snapshot.shortCircuited());
    }

    @Test
    void successfulCallsKeepTheBreakerClosed() {
        DependencyCircuitBreakers breakers = breakers(RuntimeResilienceController.Mode.ENFORCE);

        assertEquals("ok", breakers.execute("ip-ranges", () -> "ok", () -> "fallback"));

        DependencyCircuitBreakers.Snapshot snapshot = breakers.snapshot("ip-ranges");
        assertEquals("CLOSED", snapshot.state());
        assertEquals(1L, snapshot.attempted());
        assertEquals(0L, snapshot.failures());
        assertEquals(0L, snapshot.fallbacks());
    }

    private static DependencyCircuitBreakers breakers(RuntimeResilienceController.Mode mode) {
        return new DependencyCircuitBreakers(
                mode, new DependencyCircuitBreakers.Settings(50F, 2, 2, Duration.ofMinutes(1), 1));
    }

    private static String fail(AtomicInteger calls) {
        calls.incrementAndGet();
        throw new IllegalStateException("dependency unavailable");
    }
}
