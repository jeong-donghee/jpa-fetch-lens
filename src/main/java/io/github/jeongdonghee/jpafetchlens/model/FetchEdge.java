package io.github.jeongdonghee.jpafetchlens.model;

/**
 * 뿌리 엔티티 → 연관 대상 엔티티로 향하는 엣지 하나.
 *
 * @param associationName 뿌리 엔티티의 연관 필드명 (예: "team")
 * @param targetEntity    연관 대상 엔티티의 단순 이름 (예: "Team")
 * @param color           이 메서드 기준 실효 로딩 전략(색)
 */
public record FetchEdge(String associationName, String targetEntity, FetchColor color) {
}
