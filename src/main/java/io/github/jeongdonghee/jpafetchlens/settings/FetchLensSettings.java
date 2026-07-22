package io.github.jeongdonghee.jpafetchlens.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import io.github.jeongdonghee.jpafetchlens.model.FetchColor;
import org.jetbrains.annotations.NotNull;

/**
 * fetch 색 사용자 설정을 저장하는 애플리케이션 서비스.
 * 값이 없으면 {@link FetchColor} 의 기본 hex 로 폴백한다.
 */
@Service(Service.Level.APP)
@State(name = "JpaFetchLensSettings", storages = @Storage("jpa-fetch-lens.xml"))
public final class FetchLensSettings implements PersistentStateComponent<FetchLensSettings.State> {

    /** XML 로 직렬화되는 상태. public 필드가 저장 대상. */
    public static final class State {
        public String lazyHex = FetchColor.LAZY.hex();
        public String eagerHex = FetchColor.EAGER.hex();
        public String fetchJoinedHex = FetchColor.FETCH_JOINED.hex();
        public String saveCascadeHex = FetchColor.SAVE_CASCADE.hex();
        public String deleteCascadeHex = FetchColor.DELETE_CASCADE.hex();
    }

    private State state = new State();

    public static FetchLensSettings getInstance() {
        return ApplicationManager.getApplication().getService(FetchLensSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        this.state = loaded;
    }

    /** 이 fetch 색의 설정값(없으면 기본값) hex. */
    public @NotNull String hex(@NotNull FetchColor color) {
        String value = switch (color) {
            case LAZY -> state.lazyHex;
            case EAGER -> state.eagerHex;
            case FETCH_JOINED -> state.fetchJoinedHex;
            case SAVE_CASCADE -> state.saveCascadeHex;
            case DELETE_CASCADE -> state.deleteCascadeHex;
            case UNKNOWN -> null;
        };
        return (value == null || value.isBlank()) ? color.hex() : value;
    }
}
