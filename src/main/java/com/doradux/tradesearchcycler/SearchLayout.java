package com.doradux.tradesearchcycler;

/** GUI pixels; independent of the window's physical pixel resolution. */
record SearchLayout(int panelX, int panelWidth, int merchantX, boolean compact) {
    static SearchLayout of(int width, int merchantWidth) {
        int available = width - merchantWidth - 18;
        if (available < 128) {
            return new SearchLayout(6, Math.min(190, width - 12), (width - merchantWidth) / 2, true);
        }
        int panelWidth = Math.min(190, available);
        int x = (width - merchantWidth - panelWidth - 6) / 2;
        return new SearchLayout(x, panelWidth, x + panelWidth + 6, false);
    }
}
