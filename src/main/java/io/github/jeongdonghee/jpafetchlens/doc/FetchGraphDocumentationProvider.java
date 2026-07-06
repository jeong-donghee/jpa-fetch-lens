package io.github.jeongdonghee.jpafetchlens.doc;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import io.github.jeongdonghee.jpafetchlens.analyzer.RepositoryMethodAnalyzer;
import io.github.jeongdonghee.jpafetchlens.model.FetchColor;
import io.github.jeongdonghee.jpafetchlens.model.FetchEdge;
import io.github.jeongdonghee.jpafetchlens.model.FetchGraph;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 메서드에 hover(빠른 문서) 했을 때 fetch 그래프를 그려주는 진입점.
 *
 * <p>IntelliJ 는 hover 시 {@link #generateHoverDoc} 을 호출하고,
 * 반환된 HTML 문자열을 그대로 팝업에 렌더한다.
 * 팝업은 JEditorPane 기반이라 HTML/CSS 가 제한적이므로 색은 인라인 style 로 준다.
 */
public final class FetchGraphDocumentationProvider extends AbstractDocumentationProvider {

    private final RepositoryMethodAnalyzer analyzer = new RepositoryMethodAnalyzer();

    // hover 팝업용 경로.
    @Override
    public @Nullable String generateHoverDoc(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
        return build(element);
    }

    // ⌘Q(빠른 문서) 경로. 플랫폼 버전마다 hover 가 어느 쪽을 부르는지 애매해서 둘 다 커버한다.
    @Override
    public @Nullable String generateDoc(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
        return build(element);
    }

    /** 메서드면 fetch 그래프(현재는 데모)를, 아니면 null 을 만든다. */
    private @Nullable String build(@Nullable PsiElement element) {
        if (!(element instanceof PsiMethod method)) {
            return null; // 메서드 위가 아니면 기본 문서에 맡긴다.
        }

        FetchGraph graph = analyzer.analyze(method);
        if (graph != null) {
            return renderGraph(graph); // 분석기 구현 후 이 경로가 쓰인다.
        }

        // 분석기가 아직 null 을 준다.
        // 스캐폴딩이 살아있는지 눈으로 확인하기 위해, interface 안의 메서드면 데모를 보여준다.
        PsiClass containing = method.getContainingClass();
        if (containing != null && containing.isInterface()) {
            return renderDemo(method.getName());
        }
        return null;
    }

    /** 실제 분석 결과 렌더. */
    private String renderGraph(FetchGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>JPA Fetch Lens</b><br>");
        for (FetchEdge edge : graph.edges()) {
            sb.append(edgeLine(graph.rootEntity(), edge.targetEntity(), edge.associationName(), edge.color()));
        }
        return sb.toString();
    }

    /** 임시 데모 (분석기 미구현 동안 runIde 검증용). */
    private String renderDemo(String methodName) {
        List<FetchEdge> demo = List.of(
            new FetchEdge("team", "Team", FetchColor.LAZY),
            new FetchEdge("orders", "Order", FetchColor.FETCH_JOINED),
            new FetchEdge("profile", "Profile", FetchColor.EAGER)
        );
        StringBuilder sb = new StringBuilder();
        sb.append("<b>JPA Fetch Lens</b> <i>(데모 출력 &mdash; 분석기 미구현)</i><br>");
        sb.append("<code>").append(escape(methodName)).append("</code><br>");
        for (FetchEdge edge : demo) {
            sb.append(edgeLine("User", edge.targetEntity(), edge.associationName(), edge.color()));
        }
        return sb.toString();
    }

    /** "User —(LAZY)→ Team .team" 한 줄을 색 입혀 만든다. */
    private String edgeLine(String from, String to, String association, FetchColor color) {
        return "<span style=\"color:" + color.hex() + "\">"
            + escape(from) + " &mdash;(" + color.label() + ")&rarr; " + escape(to)
            + "</span> <span style=\"color:#888\">." + escape(association) + "</span><br>";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
