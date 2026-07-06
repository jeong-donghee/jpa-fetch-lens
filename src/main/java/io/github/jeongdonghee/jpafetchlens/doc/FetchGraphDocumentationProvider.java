package io.github.jeongdonghee.jpafetchlens.doc;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import io.github.jeongdonghee.jpafetchlens.analyzer.RepositoryMethodAnalyzer;
import io.github.jeongdonghee.jpafetchlens.model.FetchColor;
import io.github.jeongdonghee.jpafetchlens.model.FetchEdge;
import io.github.jeongdonghee.jpafetchlens.model.FetchGraph;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 메서드에 hover / 빠른 문서 시 fetch 그래프를 그려주는 진입점.
 *
 * <p>기본 Java 문서(시그니처·Javadoc)를 그대로 두고, 그 <b>아래에</b> fetch 섹션을 덧붙인다.
 * 팝업은 JEditorPane 기반이라 임의 이미지는 어려워, 색 채운 뱃지·범례로 다이어그램처럼 꾸민다.
 */
public final class FetchGraphDocumentationProvider extends AbstractDocumentationProvider {

    private final RepositoryMethodAnalyzer analyzer = new RepositoryMethodAnalyzer();
    // 기본 Java 문서를 얻어와 우리 섹션 앞에 붙이기 위해 직접 위임 호출한다.
    private final JavaDocumentationProvider javaDoc = new JavaDocumentationProvider();

    @Override
    public @Nullable String generateHoverDoc(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
        return build(element, originalElement, true);
    }

    @Override
    public @Nullable String generateDoc(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
        return build(element, originalElement, false);
    }

    private @Nullable String build(@Nullable PsiElement element, @Nullable PsiElement originalElement, boolean hover) {
        if (!(element instanceof PsiMethod method)) {
            return null;
        }
        FetchGraph graph = analyzer.analyze(method);
        if (graph == null) {
            return null; // repository 메서드가 아니면 기본 문서에 맡긴다.
        }
        String base = hover
            ? javaDoc.generateHoverDoc(element, originalElement)
            : javaDoc.generateDoc(element, originalElement);
        String section = renderFetchSection(graph);
        return base == null ? section : base + section;
    }

    private String renderFetchSection(FetchGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("<hr>");

        if (graph.edges().isEmpty()) {
            sb.append("<p><b>").append(escape(graph.rootEntity()))
                .append("</b> <span style=\"color:#888\"><i>(연관 없음)</i></span></p>");
            return sb.toString();
        }

        // 루트 엔티티를 맨 위에 두고, 그 아래로 "ㄴ 카디널리티 [대상: 필드]" 를 트리로.
        sb.append("<table cellpadding=\"2\" cellspacing=\"0\">");
        sb.append("<tr><td><b>").append(escape(graph.rootEntity())).append("</b></td></tr>");
        renderEdges(sb, graph.edges(), 0);
        sb.append("</table>");

        // 범례: 색 배경 + 흰 글씨의 LAZY / EAGER / FETCH.
        sb.append("<p>")
            .append(chip(FetchColor.LAZY)).append(" &nbsp; ")
            .append(chip(FetchColor.EAGER)).append(" &nbsp; ")
            .append(chip(FetchColor.FETCH_JOINED))
            .append("</p>");
        return sb.toString();
    }

    /**
     * 엣지 목록을 depth 만큼 들여써 트리로 렌더. FETCH/EAGER 로 펼쳐진 하위도 따라 그린다.
     * 각 줄: "ㄴ 카디널리티 [대상엔티티: 필드]" — 대상+필드를 fetch 색 배경으로 감싼다(흰 글씨).
     */
    private void renderEdges(StringBuilder sb, List<FetchEdge> edges, int depth) {
        for (FetchEdge edge : edges) {
            String label = escape(edge.targetEntity()) + ": " + escape(edge.associationName());
            sb.append("<tr><td>").append(indent(depth))
                .append("<span style=\"color:#888\">").append(edge.kind().label()).append("</span> ");
            if (edge.backReference()) {
                // 역참조는 이미 로딩된 상위로 되돌아가는 것(실제 로딩 관심사 아님) → 색 없이 흐리게.
                sb.append("<span style=\"color:#888\">").append(label).append("</span>");
            } else {
                sb.append(chipText(edge.color(), label));
            }
            sb.append("</td></tr>");
            renderEdges(sb, edge.children(), depth + 1);
        }
    }

    private String indent(int depth) {
        return "&nbsp;".repeat((depth + 1) * 5) + "ㄴ&nbsp;";
    }

    /** 범례용: 색 배경 + 흰 글씨로 감싼 라벨 단어. */
    private String chip(FetchColor color) {
        return chipText(color, color.label());
    }

    /** 텍스트를 fetch 색 배경 + 흰 글씨로 감싼다. */
    private String chipText(FetchColor color, String text) {
        return "<span style=\"background-color:" + color.hex() + ";color:#ffffff\">&nbsp;"
            + text + "&nbsp;</span>";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
