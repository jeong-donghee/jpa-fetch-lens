package io.github.jeongdonghee.jpafetchlens.model;

/**
 * 연관관계 엣지 하나의 "이 메서드 기준 실효 로딩 전략" → 팝업에 칠할 색.
 */
public enum FetchColor {
    /** 매핑이 LAZY이고 이 메서드가 당기지 않음 → 프록시 / N+1 위험. */
    LAZY("#E53935", "LAZY"),
    /** 이 메서드가 @Query join fetch / @EntityGraph 로 명시적으로 당김. */
    FETCH_JOINED("#43A047", "FETCH"),
    /** 매핑 자체가 EAGER → 쿼리와 무관하게 항상 로딩. */
    EAGER("#FDD835", "EAGER"),
    /** 분석 불가 (native query 등). */
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
