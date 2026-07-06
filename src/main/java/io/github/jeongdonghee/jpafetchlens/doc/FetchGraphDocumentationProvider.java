package io.github.jeongdonghee.jpafetchlens.doc;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import io.github.jeongdonghee.jpafetchlens.analyzer.RepositoryMethodAnalyzer;
import io.github.jeongdonghee.jpafetchlens.model.FetchColor;
import io.github.jeongdonghee.jpafetchlens.model.FetchEdge;
import io.github.jeongdonghee.jpafetchlens.model.FetchGraph;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 메서드에 hover 했을 때 fetch 그래프를 그려주는 진입점.
 *
 * <p>IntelliJ 는 hover / 빠른 문서 시 이 provider 를 호출하고, 반환된 HTML 을 팝업에 렌더한다.
 * 팝업은 JEditorPane 기반이라 HTML/CSS 가 제한적이므로 색은 인라인 style 로 준다.
 */
public final class FetchGraphDocumentationProvider extends AbstractDocumentationProvider {

    private final RepositoryMethodAnalyzer analyzer = new RepositoryMethodAnalyzer();

    // 연관 분석(3~5단계) 전까지 색상 렌더가 살아있는지 확인하기 위한 임시 엣지.
    private static final List<FetchEdge> DEMO_EDGES = List.of(
        new FetchEdge("team", "Team", FetchColor.LAZY),
        new FetchEdge("orders", "Order", FetchColor.FETCH_JOINED),
        new FetchEdge("profile", "Profile", FetchColor.EAGER)
    );

    // hover 팝업 경로.
    @Override
    public @Nullable String generateHoverDoc(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
        return build(element);
    }

    // ⌘Q(빠른 문서) 경로. 플랫폼 버전마다 어느 쪽을 부르는지 애매해서 둘 다 커버한다.
    @Override
    public @Nullable String generateDoc(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
        return build(element);
    }

    /** repository 메서드면 fetch 그래프를, 아니면 null 을 만든다. */
    private @Nullable String build(@Nullable PsiElement element) {
        if (!(element instanceof PsiMethod method)) {
            return null;
        }
        FetchGraph graph = analyzer.analyze(method);
        if (graph == null) {
            return null; // repository 메서드가 아니면 기본 문서에 맡긴다.
        }
        return render(graph);
    }

    private String render(FetchGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>JPA Fetch Lens</b><br>");
        sb.append("root: <code>").append(escape(graph.rootEntity())).append("</code><br>");

        List<FetchEdge> edges = graph.edges();
        if (edges.isEmpty()) {
            // 3단계(연관 수집) 전까지는 데모 색상으로 대체.
            sb.append("<i>(연관 분석은 다음 단계 &mdash; 아래는 데모 색상)</i><br>");
            edges = DEMO_EDGES;
        }
        for (FetchEdge edge : edges) {
            sb.append(edgeLine(graph.rootEntity(), edge.targetEntity(), edge.associationName(), edge.color()));
        }
        return sb.toString();
    }

    /** "PromConfig —(LAZY)→ Team .team" 한 줄을 색 입혀 만든다. */
    private String edgeLine(String from, String to, String association, FetchColor color) {
        return "<span style=\"color:" + color.hex() + "\">"
            + escape(from) + " &mdash;(" + color.label() + ")&rarr; " + escape(to)
            + "</span> <span style=\"color:#888\">." + escape(association) + "</span><br>";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
