package com.moakiee.ae2lt.menu;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.FakeSlot;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.MaintenanceEditorData;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import com.moakiee.ae2lt.network.tianshu.SaveGlobalReservePacket;
import com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Menu contract used by both wired/wireless terminal families and maintenance packets. */
public interface TianshuMaintenanceMenu {
    MEStorageMenu maintenanceMenu();
    TianshuMaintenanceSession getMaintenanceSession();
    int getTianshuSelectionRevision();
    boolean isMaintenanceAvailable();
    boolean isMaintainableView();
    void setMaintainableView(boolean enabled);
    FakeSlot getGlobalReserveMarkSlot();

    default void requestMaintenanceEditor(AEKey key) { getMaintenanceSession().requestMaintenanceEditor(key); }
    default void openMaintenanceEditor(int revision, AEKey key) { getMaintenanceSession().openMaintenanceEditor(revision, key); }
    default void runMaintenanceAction(UUID id, boolean cancel) { getMaintenanceSession().runMaintenanceAction(id, cancel); }
    default void sendGlobalReserve(AEKey key, long amount, ReservedStockMatchMode mode) { getMaintenanceSession().sendGlobalReserve(key, amount, mode); }
    default void saveGlobalReserve(SaveGlobalReservePacket packet) { getMaintenanceSession().saveGlobalReserve(packet); }
    default void sendMaintenanceSave(SaveMaintenanceRulePacket packet) { getMaintenanceSession().sendMaintenanceSave(packet); }
    default void saveMaintenanceRule(SaveMaintenanceRulePacket packet) { getMaintenanceSession().saveMaintenanceRule(packet); }
    default void receiveMaintenanceSummary(int selection, long revision, boolean overflow, List<MaintenanceSummarySyncPacket.Entry> entries) {
        getMaintenanceSession().receiveMaintenanceSummary(selection, revision, overflow, entries);
    }
    default void receiveMaintenanceEditorData(int selection, MaintenanceEditorData data) { getMaintenanceSession().receiveMaintenanceEditorData(selection, data); }
    default long getMaintenanceSummaryRevision() { return getMaintenanceSession().getMaintenanceSummaryRevision(); }
    default boolean isMaintenanceSummaryOverflow() { return getMaintenanceSession().isMaintenanceSummaryOverflow(); }
    default Map<AEKey, MaintenanceSummarySyncPacket.Entry> getMaintenanceSummary() { return getMaintenanceSession().getMaintenanceSummary(); }
    @Nullable default MaintenanceSummarySyncPacket.Entry getMaintenanceSummaryEntry(AEKey key) { return getMaintenanceSession().getMaintenanceSummaryEntry(key); }
    @Nullable default MaintenanceEditorData getMaintenanceEditorData() { return getMaintenanceSession().getMaintenanceEditorData(); }
    default int getMaintenanceEditorRevision() { return getMaintenanceSession().getMaintenanceEditorRevision(); }
    default void resetClientTianshuScopedState() { getMaintenanceSession().resetClientTianshuScopedState(); }
}
