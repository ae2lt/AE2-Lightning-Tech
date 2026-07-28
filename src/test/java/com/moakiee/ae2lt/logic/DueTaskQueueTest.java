package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class DueTaskQueueTest {
    @Test
    void returnsOnlyDueEntriesInDeadlineOrder() {
        var queue = new DueTaskQueue<String>();
        queue.schedule("late", 20);
        queue.schedule("first", 5);
        queue.schedule("second", 5);

        assertNull(queue.pollDue(4));
        assertEquals("first", queue.pollDue(5));
        assertEquals("second", queue.pollDue(5));
        assertEquals(20, queue.nextDueTick());
        assertEquals("late", queue.pollDue(20));
        assertEquals(Long.MAX_VALUE, queue.nextDueTick());
    }

    @Test
    void rescheduleInvalidatesThePreviousDeadline() {
        var queue = new DueTaskQueue<String>();
        queue.schedule("target", 5);
        queue.schedule("target", 40);

        assertNull(queue.pollDue(5));
        assertEquals(40, queue.nextDueTick());
        assertEquals("target", queue.pollDue(40));
    }

    @Test
    void retainAndRemoveDiscardQueuedEntriesLazily() {
        var queue = new DueTaskQueue<String>();
        queue.schedule("keep", 3);
        queue.schedule("drop", 1);
        queue.schedule("remove", 2);

        queue.retainAll(Set.of("keep", "remove"));
        queue.remove("remove");

        assertFalse(queue.contains("drop"));
        assertFalse(queue.contains("remove"));
        assertTrue(queue.contains("keep"));
        assertEquals("keep", queue.pollDue(3));
        assertEquals(0, queue.size());
    }
}
