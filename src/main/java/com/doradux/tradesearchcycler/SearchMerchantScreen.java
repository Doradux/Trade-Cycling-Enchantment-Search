package com.doradux.tradesearchcycler;

import de.maxhenkel.tradecycling.TradeCyclingClientMod;
import de.maxhenkel.tradecycling.gui.CycleTradesButton;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Keeps vanilla trading, slots, tooltips and Trade Cycling's original button. */
final class SearchMerchantScreen extends MerchantScreen {
    private static final int ROWS = 4;
    private static final int INK = 0xFFEAE5DA;
    private static final int MUTED = 0xFFA3ABA7;
    private static final int ACCENT = 0xFF80D6AD;
    private final List<AbstractWidget> panelWidgets = new ArrayList<>();
    private final List<BookButton> rows = new ArrayList<>();
    private final CycleGate gate = new CycleGate();
    private List<BookCatalog.Book> catalog = List.of();
    private List<BookCatalog.Book> filtered = List.of();
    private BookCatalog.Book selected;
    private EditBox search;
    private Button previous, next, stop, toggle;
    private SearchLayout layout;
    private int offset, actualWidth, badgeY, badgeHeight;
    private boolean panelOpen = true, running, found, panelPress;
    private String state = "idle";
    private long lastAttemptMillis;

    SearchMerchantScreen(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("trade_search_cycler." + key, args);
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        super.init();
        actualWidth = width;
        layout = SearchLayout.of(width, imageWidth);
        int shift = layout.merchantX() - leftPos;
        leftPos = layout.merchantX();
        // Vanilla offer hitboxes must move together with the inventory slots/background.
        for (var child : children()) {
            if (child instanceof AbstractWidget widget) widget.setX(widget.getX() + shift);
        }
        panelWidgets.clear();
        rows.clear();
        catalog = BookCatalog.build();
        panelOpen = !layout.compact();
        int x = layout.panelX(), w = layout.panelWidth();
        search = new EditBox(font, x + 8, topPos + 25, w - 16, 18, tr("search"));
        search.setHint(tr("search"));
        search.setMaxLength(100);
        search.setTextColor(INK);
        search.setResponder(this::filter);
        addPanelWidget(search);
        for (int i = 0; i < ROWS; i++) {
            BookButton row = new BookButton(x + 6, topPos + 57 + i * 21, w - 12);
            rows.add(row);
            addPanelWidget(row);
        }
        previous = Button.builder(Component.literal("<"), button -> page(-ROWS))
                .bounds(x + 6, topPos + imageHeight - 21, 18, 15).build();
        next = Button.builder(Component.literal(">"), button -> page(ROWS))
                .bounds(x + w - 24, topPos + imageHeight - 21, 18, 15).build();
        previous.setTooltip(Tooltip.create(tr("previous")));
        next.setTooltip(Tooltip.create(tr("next")));
        addPanelWidget(previous);
        addPanelWidget(next);

        int below = height - (topPos + imageHeight + 6) - 4;
        badgeHeight = below >= 48 ? 48 : 26;
        badgeY = below >= badgeHeight ? topPos + imageHeight + 6 : Math.max(2, topPos - badgeHeight - 6);
        stop = Button.builder(tr("stop"), button -> stopSearch("cancelled"))
                .bounds(x + w - 52, badgeY + 6, 46, 16).build();
        addPanelWidget(stop);
        toggle = addRenderableWidget(Button.builder(tr("books"), button -> {
            panelOpen = !panelOpen;
            updateRows();
        }).bounds(4, 4, 92, 18).build());
        toggle.visible = layout.compact();
        toggle.setTooltip(Tooltip.create(tr("compact")));
        search.setValue(query);
        filter(query);
    }

    private <T extends AbstractWidget> T addPanelWidget(T widget) {
        panelWidgets.add(widget);
        return addWidget(widget);
    }

    private void filter(String query) {
        String needle = BookCatalog.normalize(query);
        filtered = catalog.stream().filter(book -> book.searchText().contains(needle)).toList();
        offset = 0;
        updateRows();
    }

    private void page(int delta) {
        int max = Math.max(0, ((filtered.size() - 1) / ROWS) * ROWS);
        offset = Math.max(0, Math.min(max, offset + delta));
        updateRows();
    }

    private void updateRows() {
        if (search == null || previous == null) return;
        search.visible = panelOpen;
        for (int i = 0; i < rows.size(); i++) {
            BookButton row = rows.get(i);
            row.book = offset + i < filtered.size() ? filtered.get(offset + i) : null;
            row.visible = panelOpen && row.book != null;
            row.active = row.visible;
            row.setMessage(row.book == null ? Component.empty() : Component.literal(row.book.label()));
        }
        previous.visible = next.visible = panelOpen;
        previous.active = offset > 0;
        next.active = offset + ROWS < filtered.size();
        stop.visible = panelOpen && running;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.20.1 MerchantScreen recalculates its centre in render/renderBg instead of using leftPos.
        // Give only vanilla rendering the corresponding virtual width; restore before Forge/JEI events.
        width = imageWidth + 2 * leftPos;
        boolean overPanel = inPanel(mouseX, mouseY);
        try { super.render(graphics, overPanel ? -1000 : mouseX, overPanel ? -1000 : mouseY, partialTick); }
        finally { width = actualWidth; }
        if (!panelOpen) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        int x = layout.panelX(), w = layout.panelWidth();
        frame(graphics, x, topPos, w, imageHeight);
        graphics.drawString(font, tr("books"), x + 8, topPos + 9, INK, false);
        graphics.drawString(font, tr("available", filtered.size()), x + 8, topPos + 47, MUTED, false);
        String page = filtered.isEmpty() ? "0 / 0"
                : (offset + 1) + "–" + Math.min(offset + ROWS, filtered.size()) + " / " + filtered.size();
        graphics.drawCenteredString(font, page, x + w / 2, topPos + imageHeight - 17, MUTED);
        if (filtered.isEmpty()) {
            graphics.drawWordWrap(font, tr("no_results"), x + 10, topPos + 72, w - 20, MUTED);
        }
        renderBadge(graphics);
        for (AbstractWidget widget : panelWidgets) widget.render(graphics, mouseX, mouseY, partialTick);
        for (BookButton row : rows) {
            if (row.visible && row.isHoveredOrFocused() && row.book != null) {
                // Use actual stack tooltips, including mod-provided enchantment descriptions.
                graphics.renderTooltip(font, row.book.icon(), mouseX, mouseY);
                break;
            }
        }
        if (mouseX >= x && mouseX < x + w && mouseY >= badgeY && mouseY < badgeY + badgeHeight) {
            List<Component> lines = new ArrayList<>();
            if (selected != null) lines.add(Component.literal(selected.label()));
            lines.add(tr("help." + state));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    private void renderBadge(GuiGraphics graphics) {
        int x = layout.panelX(), w = layout.panelWidth();
        frame(graphics, x, badgeY, w, badgeHeight);
        int color = found ? ACCENT : running ? 0xFFEACD85 : MUTED;
        graphics.drawString(font, tr(state), x + 8, badgeY + 6, color, false);
        if (badgeHeight >= 48) {
            float pulse = Math.max(0, 1f - (Util.getMillis() - lastAttemptMillis) / 220f);
            smallText(graphics, Integer.toString(gate.attempts()), x + 8, badgeY + 23, INK, 1.25f + pulse * 0.15f);
            int numberWidth = (int) (font.width(Integer.toString(gate.attempts())) * 1.4f);
            smallText(graphics, tr("attempts").getString(), x + numberWidth + 14, badgeY + 28, MUTED, 0.8f);
        } else {
            smallText(graphics, tr("count", gate.attempts()).getString(), x + 8, badgeY + 16, INK, 0.7f);
        }
        if (running) {
            int travel = w - 28;
            int position = (int) ((Util.getMillis() % 1600) / 1600.0 * travel);
            graphics.fill(x + 4, badgeY + badgeHeight - 3, x + w - 4, badgeY + badgeHeight - 2, 0xFF30463E);
            graphics.fill(x + 4 + position, badgeY + badgeHeight - 3,
                    x + 24 + position, badgeY + badgeHeight - 2, ACCENT);
        }
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x66000000);
        g.fill(x, y, x + w, y + h, 0xFF131B1A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF26332F);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF1B2522);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0xFF668576);
    }

    private void smallText(GuiGraphics g, String text, int x, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    boolean handleSearchKey(int key, int scan, int modifiers) {
        if (search == null || !panelOpen || !search.isFocused()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            search.setFocused(false);
            setFocused(null);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!filtered.isEmpty()) select(filtered.get(offset));
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN) { page(ROWS); return true; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP) { page(-ROWS); return true; }
        if (key == GLFW.GLFW_KEY_TAB) {
            search.setFocused(false);
            if (!rows.isEmpty() && rows.get(0).visible) setFocused(rows.get(0));
            return true;
        }
        // EditBox returns false for printable key-downs (characters arrive separately).
        // Still consume them: otherwise E closes the menu, Q drops and number keys move items.
        search.keyPressed(key, scan, modifiers);
        return true;
    }

    boolean handleSearchCharacter(char character, int modifiers) {
        if (search == null || !panelOpen || !search.isFocused()) return false;
        search.charTyped(character, modifiers);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return handleSearchKey(key, scan, modifiers) || super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return handleSearchCharacter(character, modifiers) || super.charTyped(character, modifiers);
    }

    private boolean inPanel(double x, double y) {
        return panelOpen && x >= layout.panelX() && x < layout.panelX() + layout.panelWidth()
                && ((y >= topPos && y < topPos + imageHeight) || (y >= badgeY && y < badgeY + badgeHeight));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        panelPress = inPanel(x, y);
        if (panelPress) {
            for (AbstractWidget widget : panelWidgets) {
                if (widget.mouseClicked(x, y, button)) {
                    setFocused(widget);
                    return true;
                }
            }
            search.setFocused(false);
            setFocused(null);
            return true; // Never drop a carried stack through the catalogue's background.
        }
        search.setFocused(false);
        width = imageWidth + 2 * leftPos;
        try { return super.mouseClicked(x, y, button); }
        finally { width = actualWidth; }
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (panelPress) {
            panelPress = false;
            for (AbstractWidget widget : panelWidgets) widget.mouseReleased(x, y, button);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (panelPress) {
            if (search.isFocused()) search.mouseDragged(x, y, button, dx, dy);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (inPanel(x, y)) {
            if (delta != 0) page(delta > 0 ? -ROWS : ROWS);
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    private void select(BookCatalog.Book book) {
        if (!book.enchantment().isTradeable()) {
            catalog = BookCatalog.build();
            filter(search.getValue());
            return;
        }
        selected = book;
        found = false;
        running = true;
        state = "searching";
        gate.restartCount();
        search.setFocused(false);
        setFocused(null);
        updateSearch();
        updateRows();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (search != null) search.tick();
        if (running) {
            gate.tick();
            updateSearch();
        }
    }

    private void updateSearch() {
        if (!running || selected == null) return;
        var offers = menu.getOffers();
        if (!selected.enchantment().isTradeable()) { stopSearch("unavailable"); return; }
        if (gate.pending(offers)) {
            if (gate.timedOut(offers)) {
                gate.expire();
                stopSearch("timeout");
            }
            return;
        }
        if (BookCatalog.contains(offers, selected)) {
            found = true;
            stopSearch("found");
            return;
        }
        // Trade Cycling disallows experienced/non-villager merchants and active trade inputs.
        if (!CycleTradesButton.canCycle(menu)) {
            state = "blocked";
            return;
        }
        state = "searching";
        if (gate.ready(offers)) {
            TradeCyclingClientMod.sendCycleTradesPacket();
            gate.sent(offers);
            lastAttemptMillis = Util.getMillis();
        }
    }

    private void stopSearch(String reason) {
        running = false;
        state = reason;
        updateRows();
    }

    @Override
    public void removed() {
        running = false;
        super.removed();
    }

    private final class BookButton extends AbstractButton {
        private BookCatalog.Book book;
        BookButton(int x, int y, int width) { super(x, y, width, 20, Component.empty()); }
        @Override public void onPress() { if (book != null) select(book); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (book == null) return;
            boolean chosen = selected != null && selected.id().equals(book.id()) && selected.level() == book.level();
            int background = isHoveredOrFocused() ? 0xFF394F44 : chosen ? 0xFF30463C : 0xFF25312D;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            if (chosen) g.fill(getX(), getY(), getX() + 2, getY() + getHeight(), ACCENT);
            g.renderItem(book.icon(), getX() + 3, getY() + 2);
            String title = font.plainSubstrByWidth(book.label(), getWidth() - 28);
            if (!title.equals(book.label())) title = font.plainSubstrByWidth(book.label(), getWidth() - 35) + "…";
            g.drawString(font, title, getX() + 23, getY() + 2, chosen ? ACCENT : INK, false);
            String source = font.plainSubstrByWidth(book.modName(), (int) ((getWidth() - 27) / 0.65f));
            smallText(g, source, getX() + 23, getY() + 12, MUTED, 0.65f);
        }
    }
}
