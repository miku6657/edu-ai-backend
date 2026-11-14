package com.edu.ai.task.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DdlTaskStatusResolverTest {

    private final DdlTaskStatusResolver resolver =
            new DdlTaskStatusResolver();

    private final LocalDateTime now =
            LocalDateTime.of(2026, 11, 14, 10, 0);

    @Test
    void vagueTimeShouldBePendingTime() {
        assertEquals(
                "PENDING_TIME",
                resolver.resolve(
                        "VAGUE_OR_MISSING",
                        null,
                        now
                )
        );
    }

    @Test
    void invalidTimeShouldBePendingConfirm() {
        assertEquals(
                "PENDING_CONFIRM",
                resolver.resolve(
                        "INVALID_OR_PAST",
                        now.minusDays(1),
                        now
                )
        );
    }

    @Test
    void futureDateOnlyShouldBeActive() {
        assertEquals(
                "ACTIVE",
                resolver.resolve(
                        "DATE_ONLY",
                        now.plusDays(2)
                                .withHour(23)
                                .withMinute(59)
                                .withSecond(59),
                        now
                )
        );
    }

    @Test
    void pastDeadlineShouldBePendingConfirm() {
        assertEquals(
                "PENDING_CONFIRM",
                resolver.resolve(
                        "EXACT_DATETIME",
                        now.minusHours(1),
                        now
                )
        );
    }

    @Test
    void futureDeadlineShouldBeActive() {
        assertEquals(
                "ACTIVE",
                resolver.resolve(
                        "EXACT_DATETIME",
                        now.plusDays(1),
                        now
                )
        );
    }

    @Test
    void missingDueAtShouldBePendingTime() {
        assertEquals(
                "PENDING_TIME",
                resolver.resolve(
                        "EXACT_DATETIME",
                        null,
                        now
                )
        );
    }
}
