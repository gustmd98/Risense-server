package com.risense.taskapi;

import com.risense.domain.task.TaskSize;
import java.util.List;

public enum TaskTemplate {
    PRESENTATION("발표", List.of(
            new Item("발표 주제 및 구성 정리", TaskSize.S), new Item("발표 자료 조사", TaskSize.M),
            new Item("발표 자료 작성", TaskSize.L), new Item("발표 대본 작성", TaskSize.M),
            new Item("발표 연습 및 최종 점검", TaskSize.M))),
    REPORT("보고서", List.of(
            new Item("보고서 목차 및 역할 정리", TaskSize.S), new Item("참고 자료 조사", TaskSize.M),
            new Item("보고서 초안 작성", TaskSize.L), new Item("내용 검토 및 수정", TaskSize.M),
            new Item("형식 및 참고 문헌 정리", TaskSize.S))),
    DEVELOPMENT("개발", List.of(
            new Item("요구사항 정리", TaskSize.M), new Item("구조 및 API 설계", TaskSize.L),
            new Item("핵심 기능 구현", TaskSize.XL), new Item("통합 테스트 및 오류 수정", TaskSize.L),
            new Item("배포 및 사용 문서 작성", TaskSize.M))),
    DESIGN("디자인", List.of(
            new Item("디자인 요구사항 및 참고 자료 정리", TaskSize.M), new Item("화면 흐름 및 와이어프레임 작성", TaskSize.L),
            new Item("시안 제작", TaskSize.L), new Item("피드백 반영", TaskSize.M),
            new Item("최종 디자인 및 자산 정리", TaskSize.M))),
    RESEARCH("조사", List.of(
            new Item("조사 목적 및 범위 정리", TaskSize.S), new Item("조사 방법 및 항목 설계", TaskSize.M),
            new Item("자료 수집", TaskSize.L), new Item("자료 분석", TaskSize.L),
            new Item("조사 결과 정리", TaskSize.M)));

    public record Item(String title, TaskSize size) {}
    public record Summary(TaskTemplate template, String name, List<Item> tasks) {}
    private final String name;
    private final List<Item> items;

    TaskTemplate(String name, List<Item> items) { this.name = name; this.items = items; }
    public List<Item> items() { return items; }
    public Summary summary() { return new Summary(this, name, items); }
}
