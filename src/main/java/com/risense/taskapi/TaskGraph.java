package com.risense.taskapi;

import com.risense.domain.task.TaskPrerequisite;
import java.util.*;

/** Iterative topological check, avoiding recursion depth limits on long dependency chains. */
final class TaskGraph {
    private TaskGraph() {}
    static boolean hasCycle(List<TaskPrerequisite> existing, long editedTask, List<Long> selected) {
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (var edge : existing) {
            if (edge.getId().getTaskId() != editedTask) {
                graph.computeIfAbsent(edge.getId().getTaskId(), ignored -> new HashSet<>()).add(edge.getId().getPrerequisiteTaskId());
            }
        }
        graph.put(editedTask, new HashSet<>(selected));
        Map<Long, Integer> indegree = new HashMap<>();
        graph.forEach((node, targets) -> {
            indegree.putIfAbsent(node, 0);
            targets.forEach(target -> indegree.merge(target, 1, Integer::sum));
        });
        Deque<Long> ready = new ArrayDeque<>();
        indegree.forEach((node, count) -> { if (count == 0) ready.add(node); });
        int processed = 0;
        while (!ready.isEmpty()) {
            long node = ready.remove(); processed++;
            for (long target : graph.getOrDefault(node, Set.of())) {
                if (indegree.merge(target, -1, Integer::sum) == 0) ready.add(target);
            }
        }
        return processed != indegree.size();
    }
}
