package com.edu.ai.chat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatStreamRegistryTest {

    @Test
    void shouldStartFromQueued() {

        ChatStreamRegistry.RunningStream stream =
                new ChatStreamRegistry.RunningStream();

        assertEquals(
                ChatStreamRegistry.StreamState.QUEUED,
                stream.getState()
        );

        assertTrue(stream.markStarted());

        assertEquals(
                ChatStreamRegistry.StreamState.RUNNING,
                stream.getState()
        );
    }

    @Test
    void shouldCancelQueuedTask() {

        ChatStreamRegistry.RunningStream stream =
                new ChatStreamRegistry.RunningStream();

        assertTrue(stream.cancel());

        assertEquals(
                ChatStreamRegistry.StreamState.CANCELLED,
                stream.getState()
        );

        assertFalse(stream.markStarted());
    }

    @Test
    void shouldCancelRunningTask() {

        ChatStreamRegistry.RunningStream stream =
                new ChatStreamRegistry.RunningStream();

        assertTrue(stream.markStarted());
        assertTrue(stream.cancel());

        assertEquals(
                ChatStreamRegistry.StreamState.CANCELLED,
                stream.getState()
        );
    }

    @Test
    void shouldNotCancelCompletingTask() {

        ChatStreamRegistry.RunningStream stream =
                new ChatStreamRegistry.RunningStream();

        assertTrue(stream.markStarted());
        assertTrue(stream.beginCompletion());

        assertFalse(stream.cancel());

        assertEquals(
                ChatStreamRegistry.StreamState.COMPLETING,
                stream.getState()
        );
    }

    @Test
    void shouldCompleteSuccessfully() {

        ChatStreamRegistry.RunningStream stream =
                new ChatStreamRegistry.RunningStream();

        assertTrue(stream.markStarted());
        assertTrue(stream.beginCompletion());
        assertTrue(stream.markSuccessful());

        assertEquals(
                ChatStreamRegistry.StreamState.SUCCEEDED,
                stream.getState()
        );

        assertFalse(stream.cancel());
    }

    @Test
    void shouldNotCancelSuccessfulTask() {

        ChatStreamRegistry.RunningStream stream =
                new ChatStreamRegistry.RunningStream();

        stream.markStarted();
        stream.beginCompletion();
        stream.markSuccessful();

        assertFalse(stream.cancel());

        assertEquals(
                ChatStreamRegistry.StreamState.SUCCEEDED,
                stream.getState()
        );
    }

    @Test
    void shouldMarkRunningTaskFailed() {

        ChatStreamRegistry.RunningStream stream =
                new ChatStreamRegistry.RunningStream();

        stream.markStarted();

        assertTrue(stream.markFailed());

        assertEquals(
                ChatStreamRegistry.StreamState.FAILED,
                stream.getState()
        );
    }

    @Test
    void shouldNotChangeCancelledTaskToFailed() {

        ChatStreamRegistry.RunningStream stream =
                new ChatStreamRegistry.RunningStream();

        stream.cancel();

        assertFalse(stream.markFailed());

        assertEquals(
                ChatStreamRegistry.StreamState.CANCELLED,
                stream.getState()
        );
    }

    @Test
    void shouldNotChangeSuccessfulTaskToFailed() {

        ChatStreamRegistry.RunningStream stream =
                new ChatStreamRegistry.RunningStream();

        stream.markStarted();
        stream.beginCompletion();
        stream.markSuccessful();

        assertFalse(stream.markFailed());

        assertEquals(
                ChatStreamRegistry.StreamState.SUCCEEDED,
                stream.getState()
        );
    }
}
