# JPA Fetch Lens

IntelliJ IDEA 플러그인. Spring Data JPA repository **메서드에 마우스를 올리면**, 그 메서드를
실행했을 때 실제로 로딩되는 연관 엔티티를 **fetch 전략별 색**으로 보여준다.

| 색 | 의미 |
|----|------|
| 🔴 빨강 (LAZY) | 연관이 LAZY인데 이 메서드가 안 당김 → 프록시 / N+1 / `LazyInitializationException` 위험 |
| 🟢 초록 (FETCH) | 이 메서드가 `@Query` join fetch 또는 `@EntityGraph`로 명시적으로 당김 |
| 🟡 노랑 (EAGER) | 매핑 자체가 EAGER → 쿼리와 무관하게 항상 로딩 |
| ⚪ 회색 (UNKNOWN) | 분석 불가 (native query 등) |

> IntelliJ IDEA **Ultimate** 대상. (JPA 지원이 Ultimate 번들이기 때문)

## 개발

```bash
# 샌드박스 IDE를 띄워 플러그인을 실제로 확인 (첫 실행은 플랫폼 다운로드로 오래 걸림)
./gradlew runIde

# 분석 로직 자동 테스트 (IDE 안 띄움)
./gradlew test

# 마켓플레이스 배포용 zip 빌드
./gradlew buildPlugin
```

## 상태

v0.1 스캐폴딩. hover 진입점(`FetchGraphDocumentationProvider`)은 동작하며 현재는 데모 출력을
보여준다. 핵심 분석기(`RepositoryMethodAnalyzer`)는 구현 예정.
