package io.github.jeongdonghee.jpafetchlens.analyzer;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import io.github.jeongdonghee.jpafetchlens.model.FetchGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 플러그인의 두뇌.
 * Spring Data repository 메서드 하나를 받아서 "이 메서드를 실행하면 어떤 연관이
 * 어떤 전략으로 로딩되는가"를 계산해 {@link FetchGraph} 로 돌려준다.
 *
 * <p>진행: 1) repository 메서드 판별, 2) 뿌리 엔티티 해석 = 구현됨.
 * 3~5) 연관 수집 + fetch 오버라이드 결합 = 아직.
 */
public final class RepositoryMethodAnalyzer {

    /** 모든 Spring Data repository 의 최상위 마커 인터페이스. */
    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";

    /**
     * @param method hover 대상 메서드
     * @return 분석 대상 repository 메서드면 {@link FetchGraph}, 아니면 {@code null}
     */
    public @Nullable FetchGraph analyze(@NotNull PsiMethod method) {
        // 1) repository 메서드인가?
        PsiClass repositoryInterface = repositoryInterfaceOf(method);
        if (repositoryInterface == null) {
            return null;
        }

        // 2) 이 repository 가 다루는 도메인 엔티티(뿌리)는?
        PsiClass rootEntity = resolveDomainType(repositoryInterface);
        if (rootEntity == null) {
            return null;
        }

        // 3~5) TODO: rootEntity 의 1단계 연관을 수집하고, 이 메서드의 @EntityGraph /
        //            @Query join fetch 오버라이드와 결합해 엣지 색을 계산한다.
        //            지금은 뿌리 엔티티만 채우고 엣지는 비워 둔다.
        return new FetchGraph(rootEntity.getName(), List.of());
    }

    /**
     * method 가 Spring Data repository 인터페이스에 선언돼 있으면 그 인터페이스를, 아니면 null.
     *
     * <p>주의: 사용자는 보통 {@code JpaRepository} 를 상속하고, 그게 다시
     * {@code CrudRepository} → {@code Repository} 로 이어진다. 따라서 직접 상속이 아니라
     * <b>전이적(transitive)</b> 상속을 확인해야 한다 → {@link InheritanceUtil#isInheritor}.
     */
    private @Nullable PsiClass repositoryInterfaceOf(@NotNull PsiMethod method) {
        PsiClass containing = method.getContainingClass();
        if (containing == null || !containing.isInterface()) {
            return null;
        }
        if (!InheritanceUtil.isInheritor(containing, SPRING_DATA_REPOSITORY)) {
            return null;
        }
        return containing;
    }

    /**
     * {@code JpaRepository<PromConfig, Long>} 같은 상속 구조에서 첫 타입 인자(도메인 엔티티)를 해석.
     *
     * <p>원리: 마커 {@code Repository<T, ID>} 의 첫 타입 파라미터 T 가,
     * 이 repository 인터페이스 관점에서 무엇으로 치환(substitute)되는지를 구한다.
     */
    private @Nullable PsiClass resolveDomainType(@NotNull PsiClass repositoryInterface) {
        PsiClass marker = JavaPsiFacade.getInstance(repositoryInterface.getProject())
            .findClass(SPRING_DATA_REPOSITORY, repositoryInterface.getResolveScope());
        if (marker == null || marker.getTypeParameters().length == 0) {
            return null;
        }

        PsiSubstitutor substitutor =
            TypeConversionUtil.getClassSubstitutor(marker, repositoryInterface, PsiSubstitutor.EMPTY);
        if (substitutor == null) {
            return null;
        }

        PsiType domainType = substitutor.substitute(marker.getTypeParameters()[0]);
        return PsiUtil.resolveClassInClassTypeOnly(domainType);
    }
}
