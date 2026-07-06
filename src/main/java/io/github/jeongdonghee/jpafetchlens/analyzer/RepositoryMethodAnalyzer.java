package io.github.jeongdonghee.jpafetchlens.analyzer;

import com.intellij.psi.PsiMethod;
import io.github.jeongdonghee.jpafetchlens.model.FetchGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 플러그인의 두뇌.
 * Spring Data repository 메서드 하나를 받아서 "이 메서드를 실행하면 어떤 연관이
 * 어떤 전략으로 로딩되는가"를 계산해 {@link FetchGraph} 로 돌려준다.
 *
 * <p>=== v1 구현 계획 (아직 미구현 — 여기가 우리가 같이 채울 핵심) ===
 *
 * <p>1) 이 메서드가 repository 메서드인지 판별
 *    - {@code method.getContainingClass()} 가 interface 이고,
 *      상위 타입 중 org.springframework.data.repository.Repository 를 상속하는지.
 *
 * <p>2) 뿌리 엔티티 찾기
 *    - {@code JpaRepository<User, Long>} 의 첫 번째 제네릭 인자(User)를 PSI 로 해석.
 *      (PsiClass 의 extends 목록 -> PsiClassType -> 타입 인자)
 *
 * <p>3) 뿌리 엔티티의 1단계 연관 수집
 *    - 필드(또는 게터)에 붙은 @OneToMany / @ManyToOne / @OneToOne / @ManyToMany 읽기.
 *    - 각 연관의 fetch 속성. 명시가 없으면 기본값 계산:
 *        @ManyToOne, @OneToOne   -> EAGER
 *        @OneToMany, @ManyToMany -> LAZY
 *
 * <p>4) 이 메서드의 fetch 오버라이드 수집
 *    - @EntityGraph(attributePaths = {...}) 의 경로들.
 *    - @Query("... join fetch x.team ...") 의 join fetch 대상.
 *      (v1 2단계에서 com.intellij.jpa 의존을 추가하고 InjectedLanguageManager 로 JPQL 을 읽는다)
 *
 * <p>5) 3 + 4 를 결합해 각 연관의 색 결정:
 *        오버라이드로 당겨짐   -> FETCH_JOINED (초록)
 *        아니고 매핑 EAGER     -> EAGER (노랑)
 *        아니고 매핑 LAZY      -> LAZY (빨강)
 *        판별 불가(native 등)  -> UNKNOWN (회색)
 */
public final class RepositoryMethodAnalyzer {

    /**
     * @param method hover 대상 메서드
     * @return 분석 대상 repository 메서드면 {@link FetchGraph}, 아니면 {@code null}
     */
    public @Nullable FetchGraph analyze(@NotNull PsiMethod method) {
        // TODO(1~5): 위 계획대로 구현.
        //  지금은 미구현이라 null 을 돌려주고, DocumentationProvider 가 데모 출력으로 대체한다.
        return null;
    }
}
