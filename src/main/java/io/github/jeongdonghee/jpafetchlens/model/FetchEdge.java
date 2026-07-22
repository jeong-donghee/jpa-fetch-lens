package io.github.jeongdonghee.jpafetchlens.model;

import java.util.List;

/**
 * 뿌리(또는 상위) 엔티티 → 연관 대상으로 향하는 엣지 하나.
 * 로딩(fetch)되거나 cascade 로 전파되는 엣지는 대상 엔티티의 연관들을 {@code children} 으로
 * 재귀적으로 펼친다.
 *
 * @param associationName 상위 엔티티의 연관 필드명 (예: "orders")
 * @param kind            카디널리티 (N-1 / 1-N / 1-1 / N-M)
 * @param targetEntity    연관 대상 엔티티의 단순 이름 (예: "Order")
 * @param color           이 엣지의 표시 색. 조회 모드: 실효 로딩 전략(FETCH/EAGER/LAZY). cascade 모드:
 *                        전파되면 FETCH_JOINED(초록), 전파 밖이면 UNKNOWN(회색)
 * @param lazyButEager    조회 모드 전용 함정 표시: 비소유 @OneToOne + 선언 LAZY 인데 실제로는 EAGER 로딩
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
