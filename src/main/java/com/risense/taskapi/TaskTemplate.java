package com.risense.taskapi;

import com.risense.domain.task.TaskSize;
import java.util.List;

public enum TaskTemplate {
    PRESENTATION("발표", List.of(
            new Item("presentation-outline", "발표 주제 및 구성 정리", TaskSize.S), new Item("presentation-research", "발표 자료 조사", TaskSize.M),
            new Item("presentation-slides", "발표 자료 작성", TaskSize.L), new Item("presentation-script", "발표 대본 작성", TaskSize.M),
            new Item("presentation-rehearsal", "발표 연습 및 최종 점검", TaskSize.M))),
    REPORT("보고서", List.of(
            new Item("report-outline", "보고서 목차 및 역할 정리", TaskSize.S), new Item("report-research", "참고 자료 조사", TaskSize.M),
            new Item("report-draft", "보고서 초안 작성", TaskSize.L), new Item("report-review", "내용 검토 및 수정", TaskSize.M),
            new Item("report-format", "형식 및 참고 문헌 정리", TaskSize.S))),
    DEVELOPMENT("개발", List.of(
            new Item("development-requirements", "요구사항 정리", TaskSize.M), new Item("development-design", "구조 및 API 설계", TaskSize.L),
            new Item("development-implementation", "핵심 기능 구현", TaskSize.XL), new Item("development-test", "통합 테스트 및 오류 수정", TaskSize.L),
            new Item("development-deploy", "배포 및 사용 문서 작성", TaskSize.M))),
    DESIGN("디자인", List.of(
            new Item("design-requirements", "디자인 요구사항 및 참고 자료 정리", TaskSize.M), new Item("design-wireframe", "화면 흐름 및 와이어프레임 작성", TaskSize.L),
            new Item("design-draft", "시안 제작", TaskSize.L), new Item("design-feedback", "피드백 반영", TaskSize.M),
            new Item("design-assets", "최종 디자인 및 자산 정리", TaskSize.M))),
    RESEARCH("조사", List.of(
            new Item("research-scope", "조사 목적 및 범위 정리", TaskSize.S), new Item("research-method", "조사 방법 및 항목 설계", TaskSize.M),
            new Item("research-collection", "자료 수집", TaskSize.L), new Item("research-analysis", "자료 분석", TaskSize.L),
            new Item("research-results", "조사 결과 정리", TaskSize.M)));

    public record Item(String id, String title, TaskSize size) {}
    public record Summary(TaskTemplate template, String name, List<Item> tasks, String projectType) {}
    private final String name;
    private final List<Item> items;

    TaskTemplate(String name, List<Item> items) { this.name = name; this.items = items; }
    public List<Item> items() { return items; }
    public Summary summary() { return new Summary(this, name, items, name().toLowerCase(java.util.Locale.ROOT)); }
}
