package com.moakiee.ae2lt.client.compat;

import com.illusivesoulworks.polymorph.api.client.PolymorphWidgets;
import com.moakiee.ae2lt.client.TianshuPatternEncodingTermScreen;

/** Client registration kept separate so Polymorph remains a genuinely optional dependency. */
final class TianshuPolymorphClientCompat {
    static void register() {
        PolymorphWidgets.getInstance().registerWidget(screen ->
                screen instanceof TianshuPatternEncodingTermScreen<?> tianshu
                        ? new TianshuPatternTerminalWidget(tianshu)
                        : null);
    }

    private TianshuPolymorphClientCompat() {
    }
}
