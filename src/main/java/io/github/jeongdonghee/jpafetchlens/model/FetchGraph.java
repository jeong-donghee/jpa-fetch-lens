package io.github.jeongdonghee.jpafetchlens.model;

import java.util.List;

/**
 * 특정 repository 메서드에 hover 했을 때의 결과.
 * 뿌리 엔티티 + 그 아래로 펼쳐진 연관 엣지들, 그리고 이 트리가 어떤 영향(조회/저장/삭제)을
 * 나타내는지({@link GraphMode}).
 *
 * @param rootEntity 뿌리 엔티티의 단순 이름 (예: "Customer")
 * @param edges      연관 엣지 목록 (모드에 따라 로딩/cascade 로 펼쳐짐)
 * @param mode       이 트리의 의미 (조회 fetch / 저장 cascade / 삭제 cascade)
 */
public record FetchGraph(String rootEntity, List<FetchEdge> edges, GraphMode mode) {
}
