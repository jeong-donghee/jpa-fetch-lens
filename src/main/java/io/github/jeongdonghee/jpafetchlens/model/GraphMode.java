package io.github.jeongdonghee.jpafetchlens.model;

/**
 * 이 메서드가 어떤 "영향의 전파"를 보여주는 트리인지.
 *
 * <p>메서드 종류에 따라 트리의 의미가 다르다:
 * <ul>
 *   <li>{@link #FETCH} — 조회: 실행 시 <b>로딩되는</b> 연관 (fetch join / EAGER / LAZY)</li>
 *   <li>{@link #SAVE_CASCADE} — 저장(save): {@code cascade} PERSIST/MERGE 로 <b>함께 저장되는</b> 연관</li>
 *   <li>{@link #DELETE_CASCADE} — 삭제(delete): {@code cascade} REMOVE / {@code orphanRemoval} 로
 *       <b>함께 삭제되는</b> 연관</li>
 * </ul>
 * count/exists/스칼라·DTO 프로젝션처럼 엔티티를 materialize 하지 않는 메서드는 트리 자체가 없다(모드 없음).
 */
public enum GraphMode {
    FETCH,
    SAVE_CASCADE,
    DELETE_CASCADE
}
