# JPA Fetch Lens

[English](README.md) | 한국어

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> Spring Data JPA repository 메서드에 마우스를 올리면, 그 메서드가 실제로 로딩하는 연관
> 엔티티를 fetch 전략별 **색 트리**로 보여준다.

![JPA Fetch Lens 미리보기](docs/preview.svg)

메서드 이름만 봐서는 안 보이는 것 — N+1 위험, 과도한 로딩, 또는 `LAZY`로 적었지만 Hibernate가
EAGER로 당겨오는 함정 — 이 편집기를 떠나지 않고 드러난다. 트리는 hover 시 메서드의 기본 Java
문서 **아래에** 붙는다.

## 기능

- **메서드별 fetch 트리** — 조회 엔티티와 실제로 로딩되는 연관을, 당겨지는 것 기준으로 중첩해 표시.
- **실효 fetch 색상** — 초록 = 이 쿼리가 당김(`join fetch` / `@EntityGraph`), 노랑 = EAGER 매핑,
  빨강 = LAZY(프록시, N+1 위험).
- **매핑과 쿼리를 함께 해석** — `@ManyToOne` / `@OneToOne` / `@OneToMany` / `@ManyToMany`
  (jakarta·javax), `@Query`의 `join fetch`(별칭 체인 해석), `@EntityGraph(attributePaths)`.
- **로딩되는 것은 펼침** — EAGER이거나 fetch된 연관은 그 대상의 연관까지 펼쳐, 실제 객체 그래프를
  hover 한 번으로 한 단계 따라간다.
- **함정 표시** — 비소유 `@OneToOne`에 `LAZY`를 줘도 Hibernate는 EAGER로 로딩하며, 이를 표시한다.
- **역참조** — 이미 로딩된 상위로 되돌아가는 연관은 접근해도 쿼리가 없으므로 색 없이 표시.
- **색 설정** — Settings | Tools | JPA Fetch Lens.

## 설치

- **IDE에서:** Settings/Preferences → Plugins → Marketplace → **JPA Fetch Lens** 검색 → Install.
- **수동:** 플러그인 ZIP을 받아 Plugins → ⚙ → *Install Plugin from Disk…*

IntelliJ IDEA **Community·Ultimate 모두** 동작한다 — Java PSI로 동작하므로 전용 JPA 플러그인이
필요 없고, 분석 대상 프로젝트에 JPA / Spring Data JPA 의존과 애노테이션만 있으면 된다.

## 사용법

repository 메서드에 hover 한다. 예를 들어:

```java
@Query("select o from Order o join fetch o.items i join fetch i.product")
List<Order> findAllWithItems();
```

```
Order
     └ N-1  Customer: customer                    (빨강, LAZY)
     └ 1-N  OrderItem: items                      (초록, FETCH)
          └ N-1  Order: order                     (회색, 역참조)
          └ N-1  Product: product                 (초록, FETCH)
     └ 1-1  ShippingInfo: shipping   LAZY ignored → loads EAGER   (노랑)
```

카디널리티: `N-1`(@ManyToOne), `1-N`(@OneToMany), `1-1`(@OneToOne), `N-M`(@ManyToMany).

### 색의 의미

| 색 | 뜻 |
|----|----|
| 🟢 초록 | 이 쿼리가 당김 (`@Query` join fetch 또는 `@EntityGraph`) |
| 🟡 노랑 | EAGER 매핑 — 쿼리와 무관하게 항상 로딩 |
| 🔴 빨강 | LAZY — 프록시. 접근 시 추가 쿼리 (N+1 위험) |
| ⚪ 색 없음 | 이미 로딩된 상위로의 역참조. 접근해도 쿼리 없음 |

로딩되는(초록·노랑) 연관만 그 대상의 연관까지 펼치고, LAZY는 잎으로만 표시한다.

## 설정

**Settings → Tools → JPA Fetch Lens** 에서 LAZY / EAGER / FETCH 색을 직접 고른다. 글자색은 배경
밝기에 따라 검정/흰색으로 자동 대비된다.

## 한계

- **런타임 요인은 반영하지 못한다.** 영속성 컨텍스트/2차 캐시에 이미 있으면 LAZY여도 쿼리가 안 나가고,
  `@BatchSize` / `default_batch_fetch_size`는 LAZY 로딩 방식을 바꾼다. 실제 동작은 Hibernate SQL
  로그로 확인해야 한다.
- `@Query(nativeQuery = true)`는 분석하지 않는다 (SQL이라 매핑 밖).
- `@NamedEntityGraph`(이름 참조)는 아직 미지원 — 인라인 `attributePaths`만.
- 게터(property 접근)에 매핑된 연관은 아직 미지원.

## 개발

```bash
./gradlew runIde       # 샌드박스 IDE로 실제 확인
./gradlew buildPlugin  # 마켓플레이스 배포용 ZIP
```

## 라이선스

[MIT](LICENSE) © jeong-donghee
