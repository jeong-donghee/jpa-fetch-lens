package io.github.jeongdonghee.jpafetchlens.model;

/**
 * 연관의 카디널리티(관계 종류).
 */
public enum RelationKind {
    MANY_TO_ONE("N-1"),
    ONE_TO_MANY("1-N"),
    ONE_TO_ONE("1-1"),
    MANY_TO_MANY("N-M");

    private final String label;

    RelationKind(String label) {
        this.label = label;
    }

    /** 화면 표기 (예: "n-1"). */
    public String label() {
        return label;
    }
}
