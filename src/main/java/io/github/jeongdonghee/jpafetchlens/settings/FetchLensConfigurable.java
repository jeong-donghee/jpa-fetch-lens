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
    private ColorPanel saveColor;
    private ColorPanel deleteColor;

    @Override
    public @Nls String getDisplayName() {
        return "JPA Fetch Lens";
    }

    @Override
    public @Nullable JComponent createComponent() {
        lazyColor = new ColorPanel();
        eagerColor = new ColorPanel();
        fetchColor = new ColorPanel();
        saveColor = new ColorPanel();
        deleteColor = new ColorPanel();
        JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("LAZY (read):", lazyColor)
            .addLabeledComponent("EAGER (read):", eagerColor)
            .addLabeledComponent("FETCH (read):", fetchColor)
            .addLabeledComponent("Save cascade:", saveColor)
            .addLabeledComponent("Delete cascade:", deleteColor)
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
            || changed(fetchColor, s.fetchJoinedHex)
            || changed(saveColor, s.saveCascadeHex)
            || changed(deleteColor, s.deleteCascadeHex);
    }

    @Override
    public void apply() {
        FetchLensSettings.State s = FetchLensSettings.getInstance().getState();
        s.lazyHex = hexOf(lazyColor, FetchColor.LAZY);
        s.eagerHex = hexOf(eagerColor, FetchColor.EAGER);
        s.fetchJoinedHex = hexOf(fetchColor, FetchColor.FETCH_JOINED);
        s.saveCascadeHex = hexOf(saveColor, FetchColor.SAVE_CASCADE);
        s.deleteCascadeHex = hexOf(deleteColor, FetchColor.DELETE_CASCADE);
    }

    @Override
    public void reset() {
        FetchLensSettings.State s = FetchLensSettings.getInstance().getState();
        lazyColor.setSelectedColor(toColor(s.lazyHex, FetchColor.LAZY));
        eagerColor.setSelectedColor(toColor(s.eagerHex, FetchColor.EAGER));
        fetchColor.setSelectedColor(toColor(s.fetchJoinedHex, FetchColor.FETCH_JOINED));
        saveColor.setSelectedColor(toColor(s.saveCascadeHex, FetchColor.SAVE_CASCADE));
        deleteColor.setSelectedColor(toColor(s.deleteCascadeHex, FetchColor.DELETE_CASCADE));
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
