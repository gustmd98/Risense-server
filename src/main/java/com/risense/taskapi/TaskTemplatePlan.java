package com.risense.taskapi;

import com.risense.api.error.ApiException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;

final class TaskTemplatePlan {
    record Item(TaskTemplate.Item definition, LocalDate startDate, LocalDate dueDate, List<String> prerequisites) {}
    static List<Item> resolve(TaskTemplateRequest request) {
        var catalog = request.template().items();
        if (request.items() == null) return catalog.stream().map(i -> new Item(i, null, request.dueDate(), List.of())).toList();
        if (request.dueDate() != null || request.items().isEmpty() || request.items().size() > catalog.size()) invalid();
        Map<String, TaskTemplate.Item> definitions = new HashMap<>(); catalog.forEach(i -> definitions.put(i.id(), i));
        Map<String, Item> selected = new LinkedHashMap<>();
        for (var choice : request.items()) {
            if (choice == null || !definitions.containsKey(choice.itemId()) || selected.containsKey(choice.itemId())) invalid();
            TaskDates.validate(choice.startDate(), choice.dueDate());
            if (choice.prerequisiteItemIds() != null && choice.prerequisiteItemIds().stream().anyMatch(Objects::isNull)) invalid();
            var edges = choice.prerequisiteItemIds() == null ? List.<String>of() : List.copyOf(choice.prerequisiteItemIds());
            if (edges.contains(choice.itemId()) || new HashSet<>(edges).size() != edges.size()) invalid();
            selected.put(choice.itemId(), new Item(definitions.get(choice.itemId()), choice.startDate(), choice.dueDate(), edges));
        }
        Map<String, Integer> degrees = new HashMap<>(); selected.keySet().forEach(id -> degrees.put(id, 0));
        for (var item : selected.values()) for (var prerequisite : item.prerequisites()) {
            if (!selected.containsKey(prerequisite)) invalid();
            degrees.merge(prerequisite, 1, Integer::sum);
        }
        var ready = new ArrayDeque<String>(); degrees.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        int count = 0;
        while (!ready.isEmpty()) {
            var id = ready.remove(); count++;
            for (var prerequisite : selected.get(id).prerequisites())
                if (degrees.merge(prerequisite, -1, Integer::sum) == 0) ready.add(prerequisite);
        }
        if (count != selected.size()) invalid();
        return List.copyOf(selected.values());
    }
    private static void invalid() {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE_ITEMS", "템플릿 항목 ID·중복·날짜·선행 관계를 확인해주세요. items와 공통 dueDate는 함께 사용할 수 없습니다.");
    }
}
