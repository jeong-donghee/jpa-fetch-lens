package io.github.jeongdonghee.jpafetchlens.model;

import java.util.List;

/**
 * 특정 repository 메서드에 hover 했을 때의 결과.
 * v1 은 뿌리 엔티티 + 1단계 연관 엣지들만 담는다. (확장은 v2)
 *
 * @param rootEntity 조회 뿌리 엔티티의 단순 이름 (예: "User")
 * @param edges      1단계 연관 엣지 목록
 */
public record FetchGraph(String rootEntity, List<FetchEdge> edges) {
}
