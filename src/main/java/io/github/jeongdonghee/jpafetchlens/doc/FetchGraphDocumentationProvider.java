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
import io.github.jeongdonghee.jpafetchlens.model.GraphMode;
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
        renderEdges(sb, graph.edges(), 0, graph.mode());
        sb.append("</table>");

        // 색 범례(캡션)는 트리 맨 아래, 한 줄 띄우고 둔다. 색 이름 대신 실제 설정 색으로 칠한 칩을 쓴다.
        sb.append("<br>");
        sb.append("<div style=\"color:#888\">").append(caption(graph.mode())).append("</div>");
        return sb.toString();
    }

    /**
     * 트리가 무엇을 뜻하는지 한 줄 설명 (모드별). 색은 이름 대신 <b>실제 설정된 색으로 칠한 칩</b>으로
     * 보여주므로, 사용자가 설정에서 색을 바꿔도 캡션과 트리가 항상 일치한다.
     */
    private String caption(GraphMode mode) {
        return switch (mode) {
            case FETCH -> "On load: "
                + chipText(FetchColor.FETCH_JOINED, FetchColor.FETCH_JOINED.label()) + " "
                + chipText(FetchColor.EAGER, FetchColor.EAGER.label()) + " "
                + chipText(FetchColor.LAZY, FetchColor.LAZY.label());
            case SAVE_CASCADE -> "On save (cascade PERSIST/MERGE): "
                + chipText(FetchColor.SAVE_CASCADE, "SAVE");
            case DELETE_CASCADE -> "On delete (cascade REMOVE/orphanRemoval): "
                + chipText(FetchColor.DELETE_CASCADE, "DELETE");
        };
    }

    /**
     * 엣지 목록을 depth 만큼 들여써 트리로 렌더. 펼쳐진(로딩/전파) 하위도 따라 그린다.
     * 각 줄: "ㄴ 카디널리티 [대상엔티티: 필드]". 색이 UNKNOWN(회색)이면 영향 밖이라 칩 없이 흐리게.
     */
    private void renderEdges(StringBuilder sb, List<FetchEdge> edges, int depth, GraphMode mode) {
        for (FetchEdge edge : edges) {
            String label = "<b>" + escape(edge.targetEntity()) + "</b>: " + escape(edge.associationName());
            sb.append("<tr><td>").append(indent(depth))
                .append("<span style=\"color:#888\">").append(edge.kind().label()).append("</span> ");
            if (edge.backReference() || edge.color() == FetchColor.UNKNOWN) {
                // 역참조(이미 로딩된 상위) / cascade 전파 밖 → 색 없이 흐리게.
                sb.append("<span style=\"color:#888\">").append(label).append("</span>");
            } else {
                sb.append(chipText(edge.color(), label));
                // 조회 모드: 선언 LAZY지만 실제 EAGER (@OneToOne 비소유 함정) 경고.
                if (mode == GraphMode.FETCH && edge.lazyButEager() && edge.color() == FetchColor.EAGER) {
                    sb.append(" <span style=\"color:#E53935\">LAZY ignored &rarr; loads EAGER</span>");
                }
            }
            sb.append("</td></tr>");
            renderEdges(sb, edge.children(), depth + 1, mode);
        }
    }

    private String indent(int depth) {
        // 좁은 hover 팝업에서 깊은 줄이 개행되지 않도록 들여쓰기 폭을 절제한다(레벨당 3칸).
        return "&nbsp;".repeat((depth + 1) * 3) + "ㄴ&nbsp;";
    }

    /** 텍스트를 fetch 색(사용자 설정) 배경으로 감싼다. 글자색은 배경 밝기로 자동 대비. */
    private String chipText(FetchColor color, String text) {
        String bg = FetchLensSettings.getInstance().hex(color);
        return "<span style=\"background-color:" + bg + ";color:" + contrastText(bg) + "\">&nbsp;"
            + text + "&nbsp;</span>";
    }

    /**
     * 배경 hex 위에 흰/검 중 <b>지각 대비(APCA)</b> 가 더 큰 글자색을 고른다.
     * APCA(Accessible Perceptual Contrast Algorithm)는 WCAG 3 초안이 채택한 지각 기반 모델로,
     * 채도 높은 중간톤(빨강·파랑 등) 위에서 흰 글씨의 가독성을 WCAG 2.x 보다 잘 반영한다.
     * 검은 글씨(Lc 양수)와 흰 글씨(Lc 음수)의 |Lc| 를 비교해 큰 쪽을 쓴다.
     *
     * @see <a href="https://git.apcacontrast.com/documentation/APCAeasyIntro">APCA 문서 (Myndex)</a>
     */
    private String contrastText(String hex) {
        try {
            double bg = apcaScreenLuminance(java.awt.Color.decode(hex));
            double lcOnBlack = Math.abs(apcaLc(0.0, bg)); // 검은 글씨
            double lcOnWhite = Math.abs(apcaLc(1.0, bg)); // 흰 글씨
            return lcOnBlack >= lcOnWhite ? "#000000" : "#ffffff";
        } catch (NumberFormatException e) {
            return "#ffffff";
        }
    }

    /** APCA 화면 luminance: 각 채널을 감마 2.4 로 편 뒤 가중합. */
    private double apcaScreenLuminance(java.awt.Color c) {
        return 0.2126729 * Math.pow(c.getRed()   / 255.0, 2.4)
            + 0.7151522 * Math.pow(c.getGreen() / 255.0, 2.4)
            + 0.0721750 * Math.pow(c.getBlue()  / 255.0, 2.4);
    }

    /**
     * APCA(W3 0.1.9 상수) Lc 대비. txtY/bgY 는 화면 luminance(0~1).
     * 부호: 양수 = 밝은 배경 위 어두운 글씨, 음수 = 어두운 배경 위 밝은 글씨.
     */
    private double apcaLc(double txtY, double bgY) {
        final double blkThrs = 0.022, blkClmp = 1.414;
        final double normBg = 0.56, normTxt = 0.57, revTxt = 0.62, revBg = 0.65;
        final double scale = 1.14, loClip = 0.1, loOffset = 0.027, deltaYmin = 0.0005;

        // 아주 어두운 값은 부드럽게 눌러 보정(soft clamp).
        if (txtY <= blkThrs) {
            txtY += Math.pow(blkThrs - txtY, blkClmp);
        }
        if (bgY <= blkThrs) {
            bgY += Math.pow(blkThrs - bgY, blkClmp);
        }
        if (Math.abs(bgY - txtY) < deltaYmin) {
            return 0.0;
        }

        double output;
        if (bgY > txtY) { // 밝은 배경 + 어두운 글씨
            double sapc = (Math.pow(bgY, normBg) - Math.pow(txtY, normTxt)) * scale;
            output = sapc < loClip ? 0.0 : sapc - loOffset;
        } else {          // 어두운 배경 + 밝은 글씨
            double sapc = (Math.pow(bgY, revBg) - Math.pow(txtY, revTxt)) * scale;
            output = sapc > -loClip ? 0.0 : sapc + loOffset;
        }
        return output * 100.0;
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
