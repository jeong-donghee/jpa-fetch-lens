package io.github.jeongdonghee.jpafetchlens.model;

/**
 * 트리에서 한 연관 엣지에 칠할 색. 모드에 따라 의미가 다르다:
 * 조회는 실효 로딩 전략(FETCH/EAGER/LAZY), 저장/삭제는 cascade 전파(SAVE_CASCADE/DELETE_CASCADE).
 * 전파/로딩 밖은 {@link #UNKNOWN}(회색).
 */
public enum FetchColor {
    /** 매핑이 LAZY이고 이 메서드가 당기지 않음 → 프록시 / N+1 위험. */
    LAZY("#E53935", "LAZY"),
    /** 이 메서드가 @Query join fetch / @EntityGraph 로 명시적으로 당김. */
    FETCH_JOINED("#43A047", "FETCH"),
    /** 매핑 자체가 EAGER → 쿼리와 무관하게 항상 로딩. */
    EAGER("#FDD835", "EAGER"),
    /** save 시 cascade(PERSIST/MERGE)로 함께 저장됨. (기본 파랑) */
    SAVE_CASCADE("#1E88E5", "cascade"),
    /** delete 시 cascade(REMOVE)/orphanRemoval 로 함께 삭제됨. (기본 주황) */
    DELETE_CASCADE("#FB8C00", "cascade"),
    /** 로딩/전파 밖 (역참조·cascade 안 됨 등). */
    UNKNOWN("#9E9E9E", "UNKNOWN");

    private final String hex;
    private final String label;

    FetchColor(String hex, String label) {
        this.hex = hex;
        this.label = label;
    }

    /** 인라인 CSS 용 색상값 (예: "#E53935"). */
    public String hex() {
        return hex;
    }

    /** 화살표 위에 표시할 짧은 라벨 (예: "LAZY"). */
    public String label() {
        return label;
    }
}
