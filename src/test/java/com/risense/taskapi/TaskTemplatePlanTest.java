package com.risense.taskapi;

import static org.assertj.core.api.Assertions.*;
import com.risense.api.error.ApiException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskTemplatePlanTest {
    private TaskTemplateRequest.Selection choice(String id, String... prerequisites) {
        return new TaskTemplateRequest.Selection(id,LocalDate.of(2026,12,1),LocalDate.of(2026,12,5),List.of(prerequisites));
    }
    private TaskTemplateRequest request(TaskTemplateRequest.Selection... choices) {
        return new TaskTemplateRequest(TaskTemplate.PRESENTATION,null,List.of(choices));
    }
    @Test void selectedItemsKeepTheirOwnDatesAndDependencies() {
        var result=TaskTemplatePlan.resolve(request(choice("presentation-slides","presentation-research"),choice("presentation-research")));
        assertThat(result).hasSize(2);assertThat(result.getFirst().definition().id()).isEqualTo("presentation-slides");
        assertThat(result.getFirst().prerequisites()).containsExactly("presentation-research");
        assertThat(result.getFirst().startDate()).isEqualTo(LocalDate.of(2026,12,1));
    }
    @Test void rejectsDuplicateUnknownAndUnselectedPrerequisites() {
        for(var request:List.of(request(choice("bad")),request(choice("presentation-outline"),choice("presentation-outline")),
                request(choice("presentation-outline","presentation-research")),request(choice("presentation-outline","presentation-outline"))))
            assertThatThrownBy(()->TaskTemplatePlan.resolve(request)).isInstanceOf(ApiException.class);
    }
    @Test void rejectsCyclesBeforeAnyDatabaseWork() {
        assertThatThrownBy(()->TaskTemplatePlan.resolve(request(choice("presentation-outline","presentation-research"),
                choice("presentation-research","presentation-slides"),choice("presentation-slides","presentation-outline"))))
                .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("INVALID_TEMPLATE_ITEMS"));
    }
    @Test void rejectsMixedContractsAndReversedDates() {
        assertThatThrownBy(()->TaskTemplatePlan.resolve(new TaskTemplateRequest(TaskTemplate.PRESENTATION,LocalDate.now(),List.of(choice("presentation-outline")))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(()->TaskTemplatePlan.resolve(request(new TaskTemplateRequest.Selection("presentation-outline",LocalDate.of(2026,12,5),LocalDate.of(2026,12,1),null))))
                .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("INVALID_TASK_DATES"));
    }
    @Test void legacyContractAndStableCatalogIdsRemainAvailable() {
        var due=LocalDate.of(2026,12,20);
        for(var template:TaskTemplate.values()) {
            assertThat(template.items().stream().map(TaskTemplate.Item::id).distinct().count()).isEqualTo(template.items().size());
            assertThat(TaskTemplatePlan.resolve(new TaskTemplateRequest(template,due)))
                    .allSatisfy(i->{assertThat(i.startDate()).isNull();assertThat(i.dueDate()).isEqualTo(due);});
            assertThat(template.summary().projectType()).isEqualTo(template.name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
