package io.github.jeongdonghee.jpafetchlens.analyzer;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import io.github.jeongdonghee.jpafetchlens.model.FetchColor;
import io.github.jeongdonghee.jpafetchlens.model.FetchEdge;
import io.github.jeongdonghee.jpafetchlens.model.FetchGraph;
import io.github.jeongdonghee.jpafetchlens.model.GraphMode;
import io.github.jeongdonghee.jpafetchlens.model.RelationKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 플러그인의 두뇌.
 * repository 메서드 하나를 받아, 실행 시 <b>영향이 어디까지 미치는지</b>를 트리로 계산한다.
 *
 * <p>메서드 종류에 따라 트리의 의미가 갈린다({@link GraphMode}):
 * <ul>
 *   <li>조회(find/get/@Query 등, 엔티티를 반환) → FETCH(fetch join / @EntityGraph / EAGER 로 로딩되는 연관)</li>
 *   <li>저장(save*) → SAVE_CASCADE(cascade PERSIST/MERGE 로 함께 저장되는 연관)</li>
 *   <li>삭제(delete* / remove*) → DELETE_CASCADE(cascade REMOVE / orphanRemoval 로 함께 삭제되는 연관)</li>
 *   <li>count/exists/스칼라·DTO 프로젝션(엔티티를 materialize 하지 않음) → 트리 없음(null)</li>
 * </ul>
 * "조회인가/무엇을 반환하는가"는 메서드 이름이 아니라 <b>반환 타입</b>으로 판정한다. 이름이
 * {@code findByFoo} 여도 {@code @Query("select count(..)")} 로 {@code long} 을 반환하면 트리를 그리지 않는다.
 */
public final class RepositoryMethodAnalyzer {

    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";
    private static final String ENTITY_GRAPH = "org.springframework.data.jpa.repository.EntityGraph";
    private static final String QUERY = "org.springframework.data.jpa.repository.Query";

    private static final String[] ENTITY       = {"jakarta.persistence.Entity",     "javax.persistence.Entity"};
    private static final String[] MANY_TO_ONE  = {"jakarta.persistence.ManyToOne",  "javax.persistence.ManyToOne"};
    private static final String[] ONE_TO_ONE    = {"jakarta.persistence.OneToOne",   "javax.persistence.OneToOne"};
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
        Analysis analysis = classify(method, repositoryInterface);
        if (analysis == null) {
            return null; // count/exists/프로젝션 등 → 그릴 트리 없음
        }
        PsiClass rootEntity = analysis.root();

        // 조회 모드에서만 이 메서드가 명시적으로 당기는 연관 경로들(fetch join / @EntityGraph)을 읽는다.
        Set<String> fetchedPaths = analysis.mode() == GraphMode.FETCH ? fetchOverrides(method) : Set.of();

        // 순환(양방향 EAGER / cascade 등) 방지: 현재 경로에서 이미 펼친 엔티티는 다시 안 펼친다.
        Set<String> visited = new HashSet<>();
        String rootQName = rootEntity.getQualifiedName();
        if (rootQName != null) {
            visited.add(rootQName);
        }

        List<FetchEdge> edges = buildEdges(rootEntity, "", fetchedPaths, visited, 0, null, analysis.mode());
        return new FetchGraph(rootEntity.getName(), edges, analysis.mode());
    }

    // ---- 메서드 분류 (모드 + 뿌리 엔티티) --------------------------------------------------------

    /**
     * 메서드를 보고 어떤 트리를 그릴지 정한다. 그릴 게 없으면 null.
     *
     * <p>저장/삭제는 CrudRepository 의 동사이므로 이름 접두사(save/delete/remove)로 판정한다(파생
     * deleteByX 도 포함). 그 외에는 <b>반환 타입</b>으로 조회 여부를 가른다 — 엔티티를 materialize 하면
     * 조회, boolean/숫자/스칼라/DTO 면 트리 없음. getReferenceById/getOne 은 프록시만 주므로 제외.
     */
    private @Nullable Analysis classify(@NotNull PsiMethod method, @NotNull PsiClass repo) {
        PsiClass domain = resolveDomainType(repo);
        String name = method.getName();

        if (name.startsWith("save")) {
            return domain == null ? null : new Analysis(GraphMode.SAVE_CASCADE, domain);
        }
        if (name.startsWith("delete") || name.startsWith("remove")) {
            return domain == null ? null : new Analysis(GraphMode.DELETE_CASCADE, domain);
        }
        if (name.equals("getReferenceById") || name.equals("getOne")) {
            return null; // 프록시 반환, 아무것도 로딩하지 않음
        }
        PsiClass root = entityFromReturnType(method, domain);
        return root == null ? null : new Analysis(GraphMode.FETCH, root);
    }

    /**
     * 반환 타입에서 materialize 되는 엔티티를 뽑는다. 없으면(존재확인/집계/스칼라/DTO/void) null.
     * {@code Optional<T>} / {@code List<T>} / {@code Page<T>} / 배열 등 흔한 컨테이너는 한 겹 벗긴다.
     * 벗긴 타입이 타입변수(T)면(상속 findById/findAll) 리포지토리 도메인 엔티티로 대체한다.
     */
    private @Nullable PsiClass entityFromReturnType(@NotNull PsiMethod method, @Nullable PsiClass domain) {
        PsiType ret = method.getReturnType();
        if (ret == null || ret instanceof PsiPrimitiveType) {
            return null; // void / boolean / long / int ... → exists·count·집계
        }
        PsiType t = ret;
        if (t instanceof PsiArrayType arr) {
            t = arr.getComponentType();
        }
        if (t instanceof PsiClassType classType && classType.getParameters().length > 0) {
            t = classType.getParameters()[0]; // Optional<X>/List<X>/Page<X>/Stream<X> → X
        }
        PsiClass cls = PsiUtil.resolveClassInClassTypeOnly(t);
        if (cls == null) {
            return null;
        }
        if (cls instanceof PsiTypeParameter) {
            return domain; // 제네릭 T (상속 기본 메서드) → 구체 도메인 엔티티
        }
        return isEntity(cls) ? cls : null; // 구체 클래스: 엔티티면 뿌리, DTO/String/Number 면 트리 없음
    }

    private boolean isEntity(@NotNull PsiClass cls) {
        return find(cls, ENTITY) != null;
    }

    // ---- 트리 구성 -----------------------------------------------------------------------------

    /**
     * 엔티티의 연관들을 엣지로. 모드에 따라 "펼칠지/무슨 색인지"가 다르다({@link #styleFor}).
     * FETCH: 로딩(FETCH/EAGER)되는 엣지를 펼침. SAVE/DELETE: cascade 로 전파되는 엣지를 펼침.
     * {@code visited} 로 무한 순환을 막고, {@code trace} 로 진짜 역참조를 가려낸다.
     *
     * @param trace 이 엔티티로 내려오게 한 상위 연관 정보(역참조 판정용). 루트에선 null.
     */
    private @NotNull List<FetchEdge> buildEdges(@NotNull PsiClass entity, @NotNull String prefix,
                                                @NotNull Set<String> fetchedPaths,
                                                @NotNull Set<String> visited, int depth,
                                                @Nullable Trace trace, @NotNull GraphMode mode) {
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

            EdgeStyle style = styleFor(mode, assoc, fullPath, fetchedPaths);

            PsiClass target = assoc.target();
            String qName = target != null ? target.getQualifiedName() : null;
            // 방금 타고 온 연관의 실제 역방향(같은 부모 인스턴스로 되돌아감)만 진짜 역참조.
            boolean backReference = isBackReference(trace, name, assoc.mappedBy(), qName);

            List<FetchEdge> children = List.of();
            // 역참조가 아니고, 아직 안 지나온 엔티티일 때만 펼친다(순환 방지).
            if (style.expand() && target != null && !backReference && (qName == null || !visited.contains(qName))) {
                Set<String> next = new HashSet<>(visited);
                if (qName != null) {
                    next.add(qName);
                }
                Trace childTrace = new Trace(entity.getQualifiedName(), name, assoc.mappedBy());
                children = buildEdges(target, fullPath, fetchedPaths, next, depth + 1, childTrace, mode);
            }

            String targetName = target != null ? target.getName() : "?";
            edges.add(new FetchEdge(name, assoc.kind(), targetName, style.color(),
                style.lazyButEager(), backReference, children));
        }
        return edges;
    }

    /** 모드별로 이 엣지의 색·펼침 여부·부가표시를 정한다. */
    private @NotNull EdgeStyle styleFor(@NotNull GraphMode mode, @NotNull Association assoc,
                                        @NotNull String fullPath, @NotNull Set<String> fetchedPaths) {
        switch (mode) {
            case FETCH -> {
                boolean fetched = fetchedPaths.contains(fullPath);
                FetchColor color = fetched
                    ? FetchColor.FETCH_JOINED
                    : (assoc.eager() ? FetchColor.EAGER : FetchColor.LAZY);
                boolean expand = fetched || assoc.eager();
                return new EdgeStyle(color, expand, assoc.lazyButEager());
            }
            case SAVE_CASCADE -> {
                boolean propagated = cascadesSave(assoc.cascade());
                return new EdgeStyle(propagated ? FetchColor.SAVE_CASCADE : FetchColor.UNKNOWN,
                    propagated, false);
            }
            case DELETE_CASCADE -> {
                // 부모 삭제 시엔 cascade REMOVE 든 orphanRemoval 이든 자식이 삭제되는 결과는 같으므로
                // 둘을 동일하게 취급한다(orphanRemoval 만의 별도 표시는 하지 않음).
                boolean propagated = cascadesRemove(assoc.cascade()) || assoc.orphanRemoval();
                return new EdgeStyle(propagated ? FetchColor.DELETE_CASCADE : FetchColor.UNKNOWN,
                    propagated, false);
            }
        }
        return new EdgeStyle(FetchColor.UNKNOWN, false, false);
    }

    private boolean cascadesSave(@NotNull Set<String> cascade) {
        return cascade.contains("ALL") || cascade.contains("PERSIST") || cascade.contains("MERGE");
    }

    private boolean cascadesRemove(@NotNull Set<String> cascade) {
        return cascade.contains("ALL") || cascade.contains("REMOVE");
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

    // ---- 리포지토리 / 도메인 타입 해석 ---------------------------------------------------------

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

    // ---- 연관 애노테이션 읽기 ------------------------------------------------------------------

    /** 필드가 연관이면 (카디널리티, EAGER 여부, 대상, mappedBy, LAZY함정, cascade, orphanRemoval) 를, 아니면 null. */
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
        return new Association(kind, eager, target, mappedBy, lazyButEager, readCascade(ann), readOrphanRemoval(ann));
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

    /** cascade 속성의 CascadeType 상수 이름들(대문자). 예: {"PERSIST","MERGE"} 또는 {"ALL"}. 없으면 빈 set. */
    private @NotNull Set<String> readCascade(@NotNull PsiAnnotation ann) {
        Set<String> out = new HashSet<>();
        PsiAnnotationMemberValue value = ann.findAttributeValue("cascade");
        if (value == null) {
            return out;
        }
        List<PsiAnnotationMemberValue> elements = value instanceof PsiArrayInitializerMemberValue array
            ? List.of(array.getInitializers())
            : List.of(value);
        for (PsiAnnotationMemberValue element : elements) {
            String text = element.getText();
            if (text == null) {
                continue;
            }
            int dot = text.lastIndexOf('.');           // "jakarta...CascadeType.ALL" / "CascadeType.PERSIST"
            String constant = (dot >= 0 ? text.substring(dot + 1) : text).trim();
            if (!constant.isEmpty()) {
                out.add(constant.toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** orphanRemoval=true 인지 (@OneToMany/@OneToOne 만 가지며 기본 false). */
    private boolean readOrphanRemoval(@NotNull PsiAnnotation ann) {
        PsiAnnotationMemberValue value = ann.findAttributeValue("orphanRemoval");
        return value != null && "true".equals(value.getText());
    }

    // ---- fetch override (조회 모드 전용) -------------------------------------------------------

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

    private @Nullable PsiAnnotation find(@NotNull PsiClass cls, @NotNull String[] fqns) {
        for (String fqn : fqns) {
            PsiAnnotation ann = cls.getAnnotation(fqn);
            if (ann != null) {
                return ann;
            }
        }
        return null;
    }

    private record Association(RelationKind kind, boolean eager, PsiClass target, String mappedBy,
                               boolean lazyButEager, Set<String> cascade, boolean orphanRemoval) {
    }

    /** 분류 결과: 무슨 트리를(mode) 어떤 뿌리 엔티티(root)로 그릴지. */
    private record Analysis(GraphMode mode, PsiClass root) {
    }

    /** 한 엣지의 표시 방식: 색, 펼칠지, LAZY함정 여부. */
    private record EdgeStyle(FetchColor color, boolean expand, boolean lazyButEager) {
    }

    /** 자식 엔티티로 내려오게 한 상위 연관 정보 (진짜 역참조 판정용). */
    private record Trace(String parentQName, String assocName, String assocMappedBy) {
    }
}
