package io.github.jeongdonghee.jpafetchlens.doc;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiUtil;
import io.github.jeongdonghee.jpafetchlens.analyzer.RepositoryMethodAnalyzer;
import io.github.jeongdonghee.jpafetchlens.model.FetchColor;
import io.github.jeongdonghee.jpafetchlens.model.FetchEdge;
import io.github.jeongdonghee.jpafetchlens.model.FetchGraph;
import io.github.jeongdonghee.jpafetchlens.settings.FetchLensSettings;
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
        // 호출 지점의 구체 리포지토리를 넘겨, findById 같은 상속 기본 메서드도 도메인 타입을 얻게 한다.
        FetchGraph graph = analyzer.analyze(method, callSiteRepository(originalElement));
        if (graph == null) {
            return null; // repository 메서드가 아니면 기본 문서에 맡긴다.
        }
        String base = hover
            ? javaDoc.generateHoverDoc(element, originalElement)
            : javaDoc.generateDoc(element, originalElement);
        String section = renderFetchSection(graph);
        return base == null ? section : base + section;
    }

    /**
     * hover 지점이 {@code repo.findById(..)} 같은 호출식이면, 한정자({@code repo})의 타입인
     * 구체 리포지토리를 돌려준다. 상속 기본 메서드는 선언 클래스(CrudRepository)만으론 엔티티를
     * 못 풀기에, 이 문맥이 있어야 한다. 한정자가 없거나(리포지토리 내부에서 직접 hover) 타입이
     * 안 풀리면 null.
     */
    private @Nullable PsiClass callSiteRepository(@Nullable PsiElement originalElement) {
        if (originalElement == null) {
            return null;
        }
        PsiElement parent = originalElement.getParent();
        if (!(parent instanceof PsiReferenceExpression ref)) {
            return null;
        }
        PsiExpression qualifier = ref.getQualifierExpression();
        if (qualifier == null) {
            return null;
        }
        return PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
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
        return sb.toString();
    }

    /**
     * 엣지 목록을 depth 만큼 들여써 트리로 렌더. FETCH/EAGER 로 펼쳐진 하위도 따라 그린다.
     * 각 줄: "ㄴ 카디널리티 [대상엔티티: 필드]" — 대상+필드를 fetch 색 배경으로 감싼다(흰 글씨).
     */
    private void renderEdges(StringBuilder sb, List<FetchEdge> edges, int depth) {
        for (FetchEdge edge : edges) {
            String label = "<b>" + escape(edge.targetEntity()) + "</b>: " + escape(edge.associationName());
            sb.append("<tr><td>").append(indent(depth))
                .append("<span style=\"color:#888\">").append(edge.kind().label()).append("</span> ");
            if (edge.backReference()) {
                // 역참조는 이미 로딩된 상위로 되돌아가는 것(실제 로딩 관심사 아님) → 색 없이 흐리게.
                sb.append("<span style=\"color:#888\">").append(label).append("</span>");
            } else {
                sb.append(chipText(edge.color(), label));
                // 선언 LAZY지만 실제 EAGER (@OneToOne 비소유 함정) 경고.
                if (edge.lazyButEager() && edge.color() == FetchColor.EAGER) {
                    sb.append(" <span style=\"color:#E53935\">LAZY ignored &rarr; loads EAGER</span>");
                }
            }
            sb.append("</td></tr>");
            renderEdges(sb, edge.children(), depth + 1);
        }
    }

    private String indent(int depth) {
        return "&nbsp;".repeat((depth + 1) * 5) + "ㄴ&nbsp;";
    }

    /** 텍스트를 fetch 색(사용자 설정) 배경으로 감싼다. 글자색은 배경 밝기로 자동 대비. */
    private String chipText(FetchColor color, String text) {
        String bg = FetchLensSettings.getInstance().hex(color);
        return "<span style=\"background-color:" + bg + ";color:" + contrastText(bg) + "\">&nbsp;"
            + text + "&nbsp;</span>";
    }

    /** 배경 hex 의 밝기로 검정/흰색 글자색 결정 (밝으면 검정). */
    private String contrastText(String hex) {
        try {
            java.awt.Color c = java.awt.Color.decode(hex);
            double luminance = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
            return luminance > 150 ? "#000000" : "#ffffff";
        } catch (NumberFormatException e) {
            return "#ffffff";
        }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
