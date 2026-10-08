package com.risense.taskapi;

import static org.assertj.core.api.Assertions.assertThat;
import com.risense.domain.task.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class TaskGraphTest {
    @Test void rejectsDirectAndIndirectCycles() {
        assertThat(TaskGraph.hasCycle(List.of(edge(2, 1)), 1, List.of(2L))).isTrue();
        assertThat(TaskGraph.hasCycle(List.of(edge(2, 3), edge(3, 1)), 1, List.of(2L))).isTrue();
    }
    @Test void diamondDependenciesAreNotCycles() {
        assertThat(TaskGraph.hasCycle(List.of(edge(2, 4), edge(3, 4)), 1, List.of(2L, 3L))).isFalse();
    }
    @Test void replacesOldOutgoingEdgesBeforeChecking() {
        assertThat(TaskGraph.hasCycle(List.of(edge(1, 2), edge(2, 1)), 2, List.of())).isFalse();
    }
    @Test void handlesLongChainWithoutRecursion() {
        List<TaskPrerequisite> edges = new ArrayList<>();
        for (long i = 1; i < 10000; i++) edges.add(edge(i, i + 1));
        assertThat(TaskGraph.hasCycle(edges, 10000, List.of(1L))).isTrue();
        assertThat(TaskGraph.hasCycle(edges, 10000, List.of())).isFalse();
    }
    private TaskPrerequisite edge(long from, long to) {
        var a = Task.create(null, "a", TaskSize.S, null, 0, OffsetDateTime.now()); a.setId(from);
        var b = Task.create(null, "b", TaskSize.S, null, 0, OffsetDateTime.now()); b.setId(to);
        return TaskPrerequisite.of(a, b);
    }
}
