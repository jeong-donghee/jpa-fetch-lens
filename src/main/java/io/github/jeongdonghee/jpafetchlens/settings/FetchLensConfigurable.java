package io.github.jeongdonghee.jpafetchlens.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.ColorPanel;
import com.intellij.util.ui.FormBuilder;
import io.github.jeongdonghee.jpafetchlens.model.FetchColor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;

/**
 * Settings → Tools → JPA Fetch Lens.
 * LAZY / EAGER / FETCH 색을 사용자가 직접 고른다.
 */
public final class FetchLensConfigurable implements Configurable {

    private ColorPanel lazyColor;
    private ColorPanel eagerColor;
    private ColorPanel fetchColor;

    @Override
    public @Nls String getDisplayName() {
        return "JPA Fetch Lens";
    }

    @Override
    public @Nullable JComponent createComponent() {
        lazyColor = new ColorPanel();
        eagerColor = new ColorPanel();
        fetchColor = new ColorPanel();
        JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("LAZY 색:", lazyColor)
            .addLabeledComponent("EAGER 색:", eagerColor)
            .addLabeledComponent("FETCH 색:", fetchColor)
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        FetchLensSettings.State s = FetchLensSettings.getInstance().getState();
        return changed(lazyColor, s.lazyHex)
            || changed(eagerColor, s.eagerHex)
            || changed(fetchColor, s.fetchJoinedHex);
    }

    @Override
    public void apply() {
        FetchLensSettings.State s = FetchLensSettings.getInstance().getState();
        s.lazyHex = hexOf(lazyColor, FetchColor.LAZY);
        s.eagerHex = hexOf(eagerColor, FetchColor.EAGER);
        s.fetchJoinedHex = hexOf(fetchColor, FetchColor.FETCH_JOINED);
    }

    @Override
    public void reset() {
        FetchLensSettings.State s = FetchLensSettings.getInstance().getState();
        lazyColor.setSelectedColor(toColor(s.lazyHex, FetchColor.LAZY));
        eagerColor.setSelectedColor(toColor(s.eagerHex, FetchColor.EAGER));
        fetchColor.setSelectedColor(toColor(s.fetchJoinedHex, FetchColor.FETCH_JOINED));
    }

    private boolean changed(ColorPanel panel, String savedHex) {
        String current = toHex(panel.getSelectedColor());
        return !current.equalsIgnoreCase(savedHex == null ? "" : savedHex);
    }

    private String hexOf(ColorPanel panel, FetchColor fallback) {
        Color c = panel.getSelectedColor();
        return c == null ? fallback.hex() : toHex(c);
    }

    private Color toColor(String hex, FetchColor fallback) {
        try {
            return Color.decode((hex == null || hex.isBlank()) ? fallback.hex() : hex);
        } catch (NumberFormatException e) {
            return Color.decode(fallback.hex());
        }
    }

    private String toHex(Color c) {
        return c == null ? "" : String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
