package io.github.jeongdonghee.jpafetchlens.analyzer;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import io.github.jeongdonghee.jpafetchlens.model.FetchColor;
import io.github.jeongdonghee.jpafetchlens.model.FetchEdge;
import io.github.jeongdonghee.jpafetchlens.model.FetchGraph;
import io.github.jeongdonghee.jpafetchlens.model.RelationKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 플러그인의 두뇌.
 * repository 메서드 하나를 받아, 실행 시 로딩되는 연관을 <b>트리</b>로 계산한다.
 *
 * <p>규칙: 뿌리 엔티티의 1단계 연관을 나열하되, FETCH(fetch join / @EntityGraph)로 당겨진
 * 연관만 그 대상 엔티티의 연관들로 재귀적으로 펼친다. (LAZY/EAGER 는 잎으로만 표시)
 */
public final class RepositoryMethodAnalyzer {

    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";
    private static final String ENTITY_GRAPH = "org.springframework.data.jpa.repository.EntityGraph";
    private static final String QUERY = "org.springframework.data.jpa.repository.Query";

    private static final String[] MANY_TO_ONE  = {"jakarta.persistence.ManyToOne",  "javax.persistence.ManyToOne"};
    private static final String[] ONE_TO_ONE   = {"jakarta.persistence.OneToOne",   "javax.persistence.OneToOne"};
    private static final String[] ONE_TO_MANY  = {"jakarta.persistence.OneToMany",  "javax.persistence.OneToMany"};
    private static final String[] MANY_TO_MANY = {"jakarta.persistence.ManyToMany", "javax.persistence.ManyToMany"};

    // "from <Entity> [as] <alias>" 의 루트 별칭.
    private static final Pattern FROM = Pattern.compile("(?i)\\bfrom\\s+\\w+(?:\\s+as)?\\s+(\\w+)");
    // "join fetch <별칭>.<연관> [[as] <새별칭>]"
    private static final Pattern JOIN_FETCH =
        Pattern.compile("(?i)join\\s+fetch\\s+(\\w+)\\.(\\w+)(?:\\s+(?:as\\s+)?(\\w+))?");
    private static final Set<String> JPQL_KEYWORDS = Set.of(
        "join", "left", "right", "inner", "outer", "fetch", "where", "order",
        "group", "having", "on", "and", "or", "select", "from", "by", "distinct");

    private static final int MAX_DEPTH = 10;

    public @Nullable FetchGraph analyze(@NotNull PsiMethod method) {
        return analyze(method, null);
    }

    /**
     * @param contextRepository hover 지점의 구체 리포지토리(호출식 한정자 타입). 기본 메서드
     *                          (findById/findAll/save 등)는 선언 클래스가 Spring Data 내장
     *                          인터페이스라 도메인 타입이 안 풀리므로, 이 문맥으로 구체 타입을 얻는다.
     */
    public @Nullable FetchGraph analyze(@NotNull PsiMethod method, @Nullable PsiClass contextRepository) {
        PsiClass repositoryInterface = pickRepository(method, contextRepository);
        if (repositoryInterface == null) {
            return null;
        }
        PsiClass rootEntity = resolveDomainType(repositoryInterface);
        if (rootEntity == null) {
            return null;
        }
        // 이 메서드가 명시적으로 당기는 연관 경로들 (뿌리 기준 점 경로).
        Set<String> fetchedPaths = fetchOverrides(method);

        // 순환(양방향 EAGER 등) 방지: 현재 경로에서 이미 펼친 엔티티는 다시 안 펼친다.
        Set<String> visited = new HashSet<>();
        String rootQName = rootEntity.getQualifiedName();
        if (rootQName != null) {
            visited.add(rootQName);
        }

        List<FetchEdge> edges = buildEdges(rootEntity, "", fetchedPaths, visited, 0, null);
        return new FetchGraph(rootEntity.getName(), edges);
    }

    /**
     * 엔티티의 연관들을 엣지로. 실제로 <b>로딩되는</b>(FETCH 또는 EAGER) 엣지는 대상의 연관으로 재귀 확장.
     * LAZY 는 잎으로만. {@code visited} 로 무한 순환을 막고, {@code trace} 로 진짜 역참조를 가려낸다.
     *
     * @param trace 이 엔티티로 내려오게 한 상위 연관 정보(역참조 판정용). 루트에선 null.
     */
    private @NotNull List<FetchEdge> buildEdges(@NotNull PsiClass entity, @NotNull String prefix,
                                                @NotNull Set<String> fetchedPaths,
                                                @NotNull Set<String> visited, int depth,
                                                @Nullable Trace trace) {
        List<FetchEdge> edges = new ArrayList<>();
        if (depth > MAX_DEPTH) {
            return edges;
        }
        for (PsiField field : entity.getAllFields()) {
            Association assoc = associationOf(field);
            if (assoc == null) {
                continue;
            }
            String name = field.getName();
            String fullPath = prefix.isEmpty() ? name : prefix + "." + name;

            boolean fetched = fetchedPaths.contains(fullPath);
            FetchColor color = fetched
                ? FetchColor.FETCH_JOINED
                : (assoc.eager() ? FetchColor.EAGER : FetchColor.LAZY);

            PsiClass target = assoc.target();
            String qName = target != null ? target.getQualifiedName() : null;
            // 방금 타고 온 연관의 실제 역방향(같은 부모 인스턴스로 되돌아감)만 진짜 역참조.
            boolean backReference = isBackReference(trace, name, assoc.mappedBy(), qName);

            List<FetchEdge> children = List.of();
            boolean loaded = color == FetchColor.FETCH_JOINED || color == FetchColor.EAGER;
            // 역참조가 아니고, 아직 안 지나온 엔티티일 때만 펼친다(순환 방지).
            if (loaded && target != null && !backReference && (qName == null || !visited.contains(qName))) {
                Set<String> next = new HashSet<>(visited);
                if (qName != null) {
                    next.add(qName);
                }
                Trace childTrace = new Trace(entity.getQualifiedName(), name, assoc.mappedBy());
                children = buildEdges(target, fullPath, fetchedPaths, next, depth + 1, childTrace);
            }

            String targetName = target != null ? target.getName() : "?";
            edges.add(new FetchEdge(name, assoc.kind(), targetName, color, assoc.lazyButEager(), backReference, children));
        }
        return edges;
    }

    /**
     * 이 필드가 "방금 타고 온 연관의 역방향"(진짜 양방향 역참조)인지.
     * 자기참조(같은 타입 다른 인스턴스)는 역참조가 아니므로 false.
     */
    private boolean isBackReference(@Nullable Trace trace, @NotNull String fieldName,
                                    @Nullable String fieldMappedBy, @Nullable String targetQName) {
        if (trace == null || targetQName == null || !targetQName.equals(trace.parentQName())) {
            return false;
        }
        // 상위가 @OneToMany(mappedBy="x") 로 내려왔으면, 자식의 owning 필드 x 가 역참조.
        if (trace.assocMappedBy() != null && fieldName.equals(trace.assocMappedBy())) {
            return true;
        }
        // 상위가 owning to-one 이었으면, 그 이름을 mappedBy 로 가리키는 자식 컬렉션이 역참조.
        return trace.assocName() != null && fieldMappedBy != null && fieldMappedBy.equals(trace.assocName());
    }

    /**
     * 분석 기준이 될 리포지토리 인터페이스를 고른다.
     *
     * <p>문맥(호출 지점) 리포지토리에서 도메인 타입이 실제로 풀리면 그걸 우선한다. 기본 메서드
     * (findById 등)는 선언 클래스가 {@code CrudRepository<T,ID>} 라 T 를 구체 엔티티로 못 풀지만,
     * 호출식 {@code customerRepository.findById(..)} 의 한정자 타입인 구체 리포지토리에선 풀린다.
     * 문맥이 없거나 부적합하면(예: 리포지토리 인터페이스 안에서 직접 메서드에 hover) 선언 클래스로 대체.
     */
    private @Nullable PsiClass pickRepository(@NotNull PsiMethod method, @Nullable PsiClass contextRepository) {
        if (isRepositoryWithDomainType(contextRepository)) {
            return contextRepository;
        }
        PsiClass containing = method.getContainingClass();
        if (isRepository(containing)) {
            return containing;
        }
        return null;
    }

    private boolean isRepository(@Nullable PsiClass candidate) {
        return candidate != null
            && candidate.isInterface()
            && InheritanceUtil.isInheritor(candidate, SPRING_DATA_REPOSITORY);
    }

    private boolean isRepositoryWithDomainType(@Nullable PsiClass candidate) {
        return isRepository(candidate) && resolveDomainType(candidate) != null;
    }

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

    /** 필드가 연관이면 (카디널리티, 매핑상 EAGER 인지, 대상 엔티티) 를, 아니면 null. */
    private @Nullable Association associationOf(@NotNull PsiField field) {
        PsiAnnotation ann;
        RelationKind kind;
        boolean toMany;
        boolean defaultEager;

        if ((ann = find(field, MANY_TO_ONE)) != null) {
            kind = RelationKind.MANY_TO_ONE;  toMany = false; defaultEager = true;
        } else if ((ann = find(field, ONE_TO_ONE)) != null) {
            kind = RelationKind.ONE_TO_ONE;   toMany = false; defaultEager = true;
        } else if ((ann = find(field, ONE_TO_MANY)) != null) {
            kind = RelationKind.ONE_TO_MANY;  toMany = true;  defaultEager = false;
        } else if ((ann = find(field, MANY_TO_MANY)) != null) {
            kind = RelationKind.MANY_TO_MANY; toMany = true;  defaultEager = false;
        } else {
            return null;
        }

        String mappedBy = readMappedBy(ann);
        String fetchText = fetchText(ann);
        boolean explicitLazy = fetchText != null && fetchText.contains("LAZY");
        boolean explicitEager = fetchText != null && fetchText.contains("EAGER");

        // @OneToOne 비소유(mappedBy) 쪽 + 선언 LAZY = 실제로는 EAGER 로 로딩되는 함정.
        boolean lazyButEager = kind == RelationKind.ONE_TO_ONE && mappedBy != null && explicitLazy;

        boolean eager = explicitEager || lazyButEager || (!explicitLazy && defaultEager);

        PsiClass target = toMany
            ? firstTypeArgClass(field.getType())
            : PsiUtil.resolveClassInClassTypeOnly(field.getType());
        return new Association(kind, eager, target, mappedBy, lazyButEager);
    }

    /** 연관 애노테이션의 mappedBy 값 (비어 있으면 null = owning 측). */
    private @Nullable String readMappedBy(@NotNull PsiAnnotation ann) {
        String value = literalString(ann.findAttributeValue("mappedBy"));
        return (value == null || value.isEmpty()) ? null : value;
    }

    /** 연관 애노테이션의 fetch 속성 텍스트 (예: "FetchType.LAZY"), 없으면 null. */
    private @Nullable String fetchText(@NotNull PsiAnnotation ann) {
        PsiAnnotationMemberValue value = ann.findAttributeValue("fetch");
        return value != null ? value.getText() : null;
    }

    /** 이 메서드가 당기는 연관 경로들 (@EntityGraph attributePaths + @Query join fetch). */
    private @NotNull Set<String> fetchOverrides(@NotNull PsiMethod method) {
        Set<String> paths = new HashSet<>();
        addEntityGraphPaths(method, paths);
        addQueryJoinFetchPaths(method, paths);
        return paths;
    }

    private void addEntityGraphPaths(@NotNull PsiMethod method, @NotNull Set<String> out) {
        PsiAnnotation ann = method.getAnnotation(ENTITY_GRAPH);
        if (ann == null) {
            return;
        }
        PsiAnnotationMemberValue paths = ann.findAttributeValue("attributePaths");
        if (paths == null) {
            return;
        }
        for (String path : stringValues(paths)) {
            addWithPrefixes(out, path);
        }
    }

    private void addQueryJoinFetchPaths(@NotNull PsiMethod method, @NotNull Set<String> out) {
        PsiAnnotation ann = method.getAnnotation(QUERY);
        if (ann == null) {
            return;
        }
        PsiAnnotationMemberValue nativeVal = ann.findAttributeValue("nativeQuery");
        if (nativeVal != null && "true".equals(nativeVal.getText())) {
            return; // native SQL 은 분석 대상 아님
        }
        String jpql = literalString(ann.findAttributeValue("value"));
        if (jpql == null) {
            return;
        }

        // 별칭 → 뿌리 기준 점 경로. 루트 별칭은 빈 경로("").
        Map<String, String> aliasPath = new HashMap<>();
        Matcher from = FROM.matcher(jpql);
        if (from.find()) {
            aliasPath.put(from.group(1), "");
        }

        Matcher jf = JOIN_FETCH.matcher(jpql);
        while (jf.find()) {
            String srcAlias = jf.group(1);
            String assoc = jf.group(2);
            String newAlias = jf.group(3);

            String base = aliasPath.getOrDefault(srcAlias, "");
            String fullPath = base.isEmpty() ? assoc : base + "." + assoc;
            addWithPrefixes(out, fullPath);

            if (newAlias != null && !JPQL_KEYWORDS.contains(newAlias.toLowerCase())) {
                aliasPath.put(newAlias, fullPath);
            }
        }
    }

    /** "equipment.serial" → {"equipment", "equipment.serial"} 를 set 에 추가. */
    private void addWithPrefixes(@NotNull Set<String> set, @NotNull String path) {
        StringBuilder cur = new StringBuilder();
        for (String part : path.split("\\.")) {
            if (part.isEmpty()) {
                continue;
            }
            if (cur.length() > 0) {
                cur.append('.');
            }
            cur.append(part);
            set.add(cur.toString());
        }
    }

    private @NotNull List<String> stringValues(@NotNull PsiAnnotationMemberValue value) {
        List<String> out = new ArrayList<>();
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue element : array.getInitializers()) {
                String s = literalString(element);
                if (s != null) {
                    out.add(s);
                }
            }
        } else {
            String s = literalString(value);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    private @Nullable String literalString(@Nullable PsiAnnotationMemberValue value) {
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String s) {
            return s;
        }
        return null;
    }

    private @Nullable PsiClass firstTypeArgClass(@Nullable PsiType type) {
        if (type instanceof PsiClassType classType) {
            PsiType[] args = classType.getParameters();
            if (args.length > 0) {
                return PsiUtil.resolveClassInClassTypeOnly(args[0]);
            }
        }
        return null;
    }

    private @Nullable PsiAnnotation find(@NotNull PsiField field, @NotNull String[] fqns) {
        for (String fqn : fqns) {
            PsiAnnotation ann = field.getAnnotation(fqn);
            if (ann != null) {
                return ann;
            }
        }
        return null;
    }

    private record Association(RelationKind kind, boolean eager, PsiClass target, String mappedBy,
                               boolean lazyButEager) {
    }

    /** 자식 엔티티로 내려오게 한 상위 연관 정보 (진짜 역참조 판정용). */
    private record Trace(String parentQName, String assocName, String assocMappedBy) {
    }
}
