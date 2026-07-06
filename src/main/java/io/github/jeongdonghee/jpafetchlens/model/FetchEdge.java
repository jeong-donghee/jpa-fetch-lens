package io.github.jeongdonghee.jpafetchlens.model;

import java.util.List;

/**
 * 뿌리(또는 상위) 엔티티 → 연관 대상으로 향하는 엣지 하나.
 * fetch join 으로 당겨진 엣지는 대상 엔티티의 연관들을 {@code children} 으로 재귀적으로 펼친다.
 *
 * @param associationName 상위 엔티티의 연관 필드명 (예: "equipment")
 * @param kind            카디널리티 (N-1 / 1-N / 1-1 / N-M)
 * @param targetEntity    연관 대상 엔티티의 단순 이름 (예: "Equipment")
 * @param color           이 메서드 기준 실효 로딩 전략(색)
 * @param lazyButEager    @OneToOne 비소유 쪽 + 선언 LAZY 처럼, 선언은 LAZY지만 실제로는 EAGER 로딩되는 함정
 * @param backReference   대상이 현재 경로에서 이미 지나온 엔티티면 true (양방향 역참조)
 * @param children        펼쳐진 경우 대상 엔티티의 하위 엣지들, 아니면 빈 리스트
 */
public record FetchEdge(
    String associationName,
    RelationKind kind,
    String targetEntity,
    FetchColor color,
    boolean lazyButEager,
    boolean backReference,
    List<FetchEdge> children
) {
}
