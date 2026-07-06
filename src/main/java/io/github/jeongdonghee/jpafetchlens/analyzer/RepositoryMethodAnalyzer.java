package io.github.jeongdonghee.jpafetchlens.analyzer;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import io.github.jeongdonghee.jpafetchlens.model.FetchColor;
import io.github.jeongdonghee.jpafetchlens.model.FetchEdge;
import io.github.jeongdonghee.jpafetchlens.model.FetchGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 플러그인의 두뇌.
 * Spring Data repository 메서드 하나를 받아서 "이 메서드를 실행하면 어떤 연관이
 * 어떤 전략으로 로딩되는가"를 계산해 {@link FetchGraph} 로 돌려준다.
 *
 * <p>진행: 1) repository 메서드 판별, 2) 뿌리 엔티티 해석, 3) 1단계 연관 수집(매핑 기준 fetch) = 구현됨.
 * 4~5) 이 메서드의 @EntityGraph / @Query join fetch 오버라이드로 초록(FETCH_JOINED) 반영 = 아직.
 */
public final class RepositoryMethodAnalyzer {

    /** 모든 Spring Data repository 의 최상위 마커 인터페이스. */
    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";

    // JPA 연관 애노테이션 — jakarta(Spring Boot 3+) / javax(2.x) 둘 다 대응.
    private static final String[] MANY_TO_ONE  = {"jakarta.persistence.ManyToOne",  "javax.persistence.ManyToOne"};
    private static final String[] ONE_TO_ONE   = {"jakarta.persistence.OneToOne",   "javax.persistence.OneToOne"};
    private static final String[] ONE_TO_MANY  = {"jakarta.persistence.OneToMany",  "javax.persistence.OneToMany"};
    private static final String[] MANY_TO_MANY = {"jakarta.persistence.ManyToMany", "javax.persistence.ManyToMany"};

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

        // 3) 뿌리 엔티티의 1단계 연관을 매핑 기준 fetch 로 수집.
        List<FetchEdge> edges = collectAssociations(rootEntity);

        // 4~5) TODO: 이 메서드의 @EntityGraph(attributePaths) / @Query join fetch 로
        //            당겨지는 연관은 FETCH_JOINED(초록)로 덮어쓴다.
        return new FetchGraph(rootEntity.getName(), edges);
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

    /** 엔티티의 (상속 포함) 필드 중 JPA 연관 애노테이션이 붙은 것들을 엣지로 수집. */
    private @NotNull List<FetchEdge> collectAssociations(@NotNull PsiClass entity) {
        List<FetchEdge> edges = new ArrayList<>();
        for (PsiField field : entity.getAllFields()) {
            Association assoc = associationOf(field);
            if (assoc == null) {
                continue;
            }
            String targetName = assoc.target() != null ? assoc.target().getName() : "?";
            edges.add(new FetchEdge(field.getName(), targetName, assoc.color()));
        }
        return edges;
    }

    /** 필드 하나가 연관이면 (색, 대상 엔티티) 를, 아니면 null. */
    private @Nullable Association associationOf(@NotNull PsiField field) {
        PsiAnnotation ann;
        boolean toMany;
        boolean defaultEager;

        if ((ann = find(field, MANY_TO_ONE)) != null) {
            toMany = false;
            defaultEager = true;   // @ManyToOne 기본 EAGER
        } else if ((ann = find(field, ONE_TO_ONE)) != null) {
            toMany = false;
            defaultEager = true;   // @OneToOne 기본 EAGER
        } else if ((ann = find(field, ONE_TO_MANY)) != null) {
            toMany = true;
            defaultEager = false;  // @OneToMany 기본 LAZY
        } else if ((ann = find(field, MANY_TO_MANY)) != null) {
            toMany = true;
            defaultEager = false;  // @ManyToMany 기본 LAZY
        } else {
            return null;
        }

        FetchColor color = fetchColor(ann, defaultEager);
        // to-one 은 필드 타입 자체가 엔티티, to-many 는 Collection<Entity> 의 첫 타입 인자.
        PsiClass target = toMany
            ? firstTypeArgClass(field.getType())
            : PsiUtil.resolveClassInClassTypeOnly(field.getType());
        return new Association(color, target);
    }

    /** 애노테이션의 fetch 속성(명시값) 을 읽어 색으로. 못 읽으면 매핑 규칙 기본값을 쓴다. */
    private @NotNull FetchColor fetchColor(@NotNull PsiAnnotation ann, boolean defaultEager) {
        PsiAnnotationMemberValue value = ann.findAttributeValue("fetch");
        String text = value != null ? value.getText() : null;
        boolean eager;
        if (text != null && text.contains("EAGER")) {
            eager = true;
        } else if (text != null && text.contains("LAZY")) {
            eager = false;
        } else {
            eager = defaultEager;
        }
        return eager ? FetchColor.EAGER : FetchColor.LAZY;
    }

    /** Collection<Entity> 타입에서 첫 타입 인자(Entity) 를 해석. */
    private @Nullable PsiClass firstTypeArgClass(@Nullable PsiType type) {
        if (type instanceof PsiClassType classType) {
            PsiType[] args = classType.getParameters();
            if (args.length > 0) {
                return PsiUtil.resolveClassInClassTypeOnly(args[0]);
            }
        }
        return null;
    }

    /** 여러 후보 FQN 중 실제로 붙어 있는 애노테이션을 반환. */
    private @Nullable PsiAnnotation find(@NotNull PsiField field, @NotNull String[] fqns) {
        for (String fqn : fqns) {
            PsiAnnotation ann = field.getAnnotation(fqn);
            if (ann != null) {
                return ann;
            }
        }
        return null;
    }

    private record Association(FetchColor color, PsiClass target) {
    }
}
