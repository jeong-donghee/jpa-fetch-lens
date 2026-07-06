# JPA Fetch Lens

IntelliJ IDEA 플러그인. Spring Data JPA **repository 메서드에 마우스를 올리면**, 그 메서드를
실행했을 때 실제로 로딩되는 연관 엔티티를 **fetch 전략별 색 트리**로 보여준다.

메서드 이름만 봐서는 안 보이는 "이 쿼리가 뭘 같이 당겨오는지 / 어떤 게 LAZY라 N+1 위험인지"를
hover 한 번으로 드러내는 것이 목표다.

## 보이는 것

기본 Java 문서(시그니처·Javadoc) **아래에** fetch 트리가 붙는다. 예:

```
PromConfig
     ㄴ 1-N  [ServerConfig: servers]                         (초록)
          ㄴ N-1 PromConfig: promConfig                       (회색, 역참조)
          ㄴ 1-N  [ServerSshConnectionChain: sshConnectionChains]  (빨강)
          ㄴ 1-N  [GpuConfig: gpuConfigs]                     (노랑, EAGER면 펼침)
               ㄴ N-1  [GpuConfig: parentGpuConfig]           (자기참조, 실제 색 유지)
```

- 루트 = repository 의 도메인 엔티티. 그 아래로 `카디널리티 대상엔티티: 필드`.
- 카디널리티: `N-1`(@ManyToOne) · `1-N`(@OneToMany) · `1-1`(@OneToOne) · `N-M`(@ManyToMany)

### 색의 의미

| 색 | 뜻 |
|----|----|
| 🟢 초록 | 이 메서드가 `@Query` join fetch 또는 `@EntityGraph`로 **명시적으로 당김** |
| 🟡 노랑 | 매핑이 **EAGER** → 쿼리와 무관하게 항상 로딩 |
| 🔴 빨강 | **LAZY** → 프록시. 접근 시 추가 쿼리 (N+1 위험) |
| ⚪ 회색(색 없음) | **역참조** — 방금 타고 온 상위로 되돌아감. 이미 로딩돼 있어 접근해도 쿼리 없음 |

- **로딩되는(초록·노랑) 연관만 그 하위까지 재귀로 펼친다.** LAZY(빨강)는 잎으로만.
- `LAZY ignored → loads EAGER` 경고: `@OneToOne`의 **비소유(mappedBy) 쪽**에 `fetch=LAZY`를
  줘도 Hibernate는 이를 무시하고 EAGER로 로딩한다. 이 함정을 노랑 + 경고로 표시한다.

## 무엇을 읽나

- 뿌리 엔티티의 `@ManyToOne`/`@OneToOne`/`@OneToMany`/`@ManyToMany` (jakarta·javax 모두)
- 각 연관의 `fetch` 명시값, 없으면 매핑 기본값(to-one EAGER / to-many LAZY)
- 메서드의 `@EntityGraph(attributePaths = ...)`
- 메서드의 `@Query` JPQL 안 `join fetch` (별칭 체인 해석해 다단계 경로까지)

## 한계 (정적 분석의 경계)

- **런타임 요인은 반영 못 함**: 1차/2차 캐시에 이미 있으면 LAZY여도 쿼리가 안 나가고,
  `@BatchSize`·`default_batch_fetch_size`는 로딩 방식을 바꾼다. 실제 확인은 Hibernate SQL 로그로.
- `@Query(nativeQuery = true)` 는 분석하지 않는다 (SQL 이라 매핑 밖).
- `@NamedEntityGraph`(이름 참조 방식)는 아직 미지원 — 인라인 `attributePaths`만.
- 연관이 필드가 아니라 게터(property 접근)에 매핑된 경우는 아직 미지원.

## 개발

> IntelliJ IDEA **Ultimate** 대상. (JPA 지원이 Ultimate 번들)

```bash
./gradlew runIde       # 샌드박스 IDE로 실제 확인
./gradlew buildPlugin  # 마켓플레이스 배포용 zip
```
