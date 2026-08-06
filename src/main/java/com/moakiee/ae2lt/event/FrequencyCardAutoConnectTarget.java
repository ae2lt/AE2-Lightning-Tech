package com.moakiee.ae2lt.event;

record FrequencyCardAutoConnectTarget(FrequencyCardAutoConnectTarget.GridPos pos, String sideName) {
    static FrequencyCardAutoConnectTarget fromPartPlacement(
            GridPos clickedPos,
            String clickedSideName,
            GridPos placementPos,
            String placementSideName) {
        return new FrequencyCardAutoConnectTarget(placementPos, placementSideName);
    }

    record GridPos(int x, int y, int z) {
    }
}
