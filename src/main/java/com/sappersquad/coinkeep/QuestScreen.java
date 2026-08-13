package com.sappersquad.coinkeep;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The book. Two top-level tabs: Quests (chapters + quests) and Shop (the
 * purchase catalog). Quest progress and the balance both arrive on synced
 * attachments, so this is a plain Screen - no menu, no ContainerData, no
 * custom packets.
 *
 * There is one book item. The K keybind is a shortcut that opens this same
 * screen already on the Shop tab - not a second screen. An earlier separate
 * ShopScreen drifted out of sync and never gained the Sell mode, which is
 * why it was deleted rather than kept alongside this one.
 */
public class QuestScreen extends Screen {

    private enum Tab {
        QUESTS("Quests"),
        SHOP("Shop"),
        CASH("Cash"),
        GUIDE("Guide");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int MAX_PANEL_W = 640;
    private static final int MIN_PANEL_W = 360;
    private static final int MAX_PANEL_H = 400;
    private static final int MIN_PANEL_H = 200;
    /** Gap between the header, tab strip, search field and list. */
    private static final int TAB_GAP = 3;
    private static final int ROW_GAP = 5;
    /** Height of the search row above the list. */
    private static final int SEARCH_H = 20;
    private static final int HEADER_H = 24;
    private static final int TOPTAB_H = 18;
    /** Floor for the sidebar; the real width is measured from its labels. */
    private static final int MIN_SIDEBAR_W = 112;
    // Taller than it looks like it needs: the detail strip carries the tier
    // ladder AND the chapter ladder, not just a description.
    private static final int DETAIL_H = 88;
    private static final int LINE_ROW_H = 26;
    private static final int QUEST_ROW_H = 24;
    private static final int SHOP_ROW_H = 24;
    private static final int PAD = 8;

    /** Buy and Sell are two views of one market, not separate screens. */
    private enum ShopMode {
        BUY("Buy"),
        SELL("Sell");

        private final String label;

        ShopMode(String label) {
            this.label = label;
        }
    }

    private final Map<Tab, int[]> topTabBounds = new EnumMap<>(Tab.class);
    private final Map<ShopMode, int[]> modeBounds = new EnumMap<>(ShopMode.class);

    // Hit boxes for the ladder pips, rebuilt each frame as they're drawn, so
    // hovering one can explain what that step actually asks for and pays.
    private final Map<Integer, int[]> tierPipBounds = new LinkedHashMap<>();
    private final Map<String, int[]> chapterPipBounds = new LinkedHashMap<>();
    private ShopMode shopMode = ShopMode.BUY;

    private int panelLeft;
    private int panelTop;
    private int panelW = MAX_PANEL_W;
    private int panelH = MAX_PANEL_H;
    private int searchTop;

    /**
     * Filters the list on the Quests and Shop tabs. On Quests it deliberately
     * searches EVERY chapter, not just the open one - with a modpack's worth of
     * quests, "which chapter is this in?" is the actual problem, so a search
     * that only looked in the current chapter would not solve it.
     */
    @Nullable
    private net.minecraft.client.gui.components.EditBox searchBox;

    private boolean tabHasSearch() {
        return activeTab == Tab.QUESTS || activeTab == Tab.SHOP;
    }

    private String searchText() {
        return searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean searching() {
        return tabHasSearch() && !searchText().isEmpty();
    }

    /** @return true if there was a query to clear. */
    private boolean clearSearch() {
        if (searchBox == null || searchBox.getValue().isEmpty()) {
            return false;
        }
        searchBox.setValue("");
        sortedCache = null;
        return true;
    }
    private int contentTop;
    private int contentBottom;
    private int listLeft;
    private int listRight;

    private Tab activeTab = Tab.QUESTS;

    // Quests tab state
    private String selectedLine;
    private String selectedQuest;
    private int scroll;
    private int sidebarScroll;
    private List<Quest> sortedCache;
    private String sortedCacheLine;
    private long sortedCacheSignature = Long.MIN_VALUE;

    // Shop tab state. The selection is the category's ID, not the record:
    // categories come from a datapack registry now, so a /reload hands out
    // fresh ShopCategory objects and an identity-held reference would go
    // stale (or, worse, quietly compare unequal to its own replacement).
    private String selectedCategory;

    /**
     * Grid layout for the shop: icon on top, name and price beneath, several
     * entries per row - straight from player feedback that a modpack's worth
     * of custom items is easier to scan as tiles than as a list. Static so
     * the choice survives closing the book; it is a preference, not per-visit
     * state.
     */
    private static boolean shopGrid;

    private static final int GRID_CELL_W = 86;
    private static final int GRID_CELL_H = 46;
    private static final int GRID_GAP = 4;

    /** Hit box for the Grid toggle; null except on the Shop tab. */
    @Nullable
    private int[] gridToggleBounds;

    /** Measured per tab in layout() - see the note there. */
    private int sidebarW = MIN_SIDEBAR_W;

    /** The labels the active tab's sidebar will actually draw. */
    private List<String> sidebarLabels() {
        return switch (activeTab) {
            case QUESTS -> lines().stream().map(QuestLine::name).toList();
            case SHOP -> shopCategories().stream().map(ShopCategory::getLabel).toList();
            case GUIDE -> GuideTopic.ALL.stream().map(GuideTopic::title).toList();
            case CASH -> List.of();
        };
    }
    private int shopScroll;
    private int shopSidebarScroll;

    // Guide tab state
    private int guideTopic;
    private int guideScroll;
    private int guideSidebarScroll;

    /**
     * Content now comes from the synced datapack registries, which are only
     * reachable through a level - so it is resolved in init(), not the
     * constructor, and every lookup goes through the helpers below.
     */
    private net.minecraft.core.RegistryAccess access;

    public QuestScreen(Component title) {
        this(title, Tab.QUESTS);
    }

    private QuestScreen(Component title, Tab initialTab) {
        super(title);
        this.activeTab = initialTab;
    }

    /**
     * The K keybind opens this same screen already on the Shop tab, rather
     * than a second shop implementation.
     */
    public static QuestScreen onShopTab(Component title) {
        return new QuestScreen(title, Tab.SHOP);
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        access = mc.level == null ? null : mc.level.registryAccess();

        if (selectedLine == null) {
            selectedLine = lines().stream().findFirst().map(QuestLine::id).orElse(null);
        }
        if (selectedCategory == null) {
            selectedCategory = shopCategories().stream().findFirst().map(ShopCategory::id).orElse(null);
        }
        layout();
        buildSearchBox();
    }

    /**
     * Rebuilt on every layout because the panel is responsive - the box has to
     * follow the list column when the window resizes. The typed text survives,
     * so resizing mid-search does not wipe what you were looking for.
     */
    private void buildSearchBox() {
        String previous = searchBox == null ? "" : searchBox.getValue();
        if (searchBox != null) {
            removeWidget(searchBox);
            searchBox = null;
        }
        if (!tabHasSearch()) {
            return;
        }
        int w = listRight - listLeft - 10;
        searchBox = new net.minecraft.client.gui.components.EditBox(
                this.font, listLeft + 4, searchTop + 3, w, SEARCH_H - 5, Component.literal("Search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setTextColor(MoneyUI.TEXT);
        searchBox.setHint(Component.literal(activeTab == Tab.QUESTS
                ? "Search all quests..." : "Search the shop...").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setValue(previous);
        // Any edit invalidates the cached, sorted list.
        searchBox.setResponder(text -> {
            sortedCache = null;
            scroll = 0;
            shopScroll = 0;
        });
        addRenderableWidget(searchBox);
    }

    private List<QuestLine> lines() {
        return access == null ? List.of() : QuestRegistry.lines(access);
    }

    private List<Quest> questsIn(String lineId) {
        return access == null || lineId == null ? List.of() : QuestRegistry.questsIn(access, lineId);
    }

    private List<ShopCategory> shopCategories() {
        return access == null ? List.of() : ShopRegistry.categories(access);
    }

    /** Recomputed on tab switch: the Shop tab has no detail strip, so its
     *  list runs all the way to the bottom of the panel. */
    private void layout() {
        // Grow with the window instead of sitting at a fixed size. Modpacks add
        // hundreds of quests with long modded item names, so both the row count
        // and the room per row matter. Clamped so it still fits at GUI scale 4
        // on 1080p (~480x270 usable) and never sprawls on an ultrawide.
        panelW = Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, this.width - 60));
        panelH = Math.max(MIN_PANEL_H, Math.min(MAX_PANEL_H, this.height - 40));
        panelLeft = (this.width - panelW) / 2;
        panelTop = (this.height - panelH) / 2;

        // Deliberate breathing room. The header bar, the tab strip, the search
        // field and the list were previously butted straight together with no
        // gap at all, which read as cramped.
        int tabsBottom = panelTop + HEADER_H + TAB_GAP + TOPTAB_H;
        searchTop = tabsBottom + ROW_GAP;
        contentTop = (tabHasSearch() ? searchTop + SEARCH_H : tabsBottom) + ROW_GAP;
        contentBottom = panelTop + panelH - (activeTab == Tab.QUESTS ? DETAIL_H : PAD);
        // Cash has no categories, so it uses the full panel width rather than
        // leaving an empty sidebar column. Guide keeps the sidebar for its
        // topic list.
        // The sidebar sizes itself to its widest label instead of truncating.
        // "Vaults & Robbery" did not fit the old fixed 112px, and a modpack's
        // chapter names can be anything. Clamped to 40% of the panel so a
        // pathological name squeezes itself (ellipsis) rather than the list.
        sidebarW = MIN_SIDEBAR_W;
        for (String label : sidebarLabels()) {
            sidebarW = Math.max(sidebarW, this.font.width(label) + 28 + 10);
        }
        sidebarW = Math.min(sidebarW, panelW * 2 / 5);
        listLeft = activeTab == Tab.CASH ? panelLeft + 1 : panelLeft + sidebarW;
        listRight = panelLeft + panelW - 1;

        topTabBounds.clear();
        int x = panelLeft + PAD;
        int y = panelTop + HEADER_H + TAB_GAP;
        for (Tab tab : Tab.values()) {
            int w = this.font.width(tab.label) + 24;
            topTabBounds.put(tab, new int[]{x, y, w, TOPTAB_H});
            x += w + 2;
        }

        // Buy/Sell sits on the same strip, right-aligned, and only exists
        // while the Shop tab is open. The Grid toggle sits just left of it.
        modeBounds.clear();
        gridToggleBounds = null;
        if (activeTab == Tab.SHOP) {
            int modeW = 44;
            int modeX = panelLeft + panelW - PAD - modeW * ShopMode.values().length - 2;
            for (ShopMode mode : ShopMode.values()) {
                modeBounds.put(mode, new int[]{modeX, y, modeW, TOPTAB_H});
                modeX += modeW + 2;
            }
            int toggleW = this.font.width("Grid") + 16;
            gridToggleBounds = new int[]{
                    panelLeft + panelW - PAD - modeW * ShopMode.values().length - 2 - toggleW - 8,
                    y, toggleW, TOPTAB_H};
        }
        clampAll();
    }

    private LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    private long balance() {
        LocalPlayer player = player();
        return player == null ? 0L : BalanceHelper.getBalance(player);
    }

    private int rowWidth() {
        return listRight - listLeft - 6;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ==================== quests tab data ====================

    /**
     * Actionable work first: quests you've started, then ones you haven't
     * touched, then anything still locked, and finally the finished ones.
     * Sorted per view rather than in the registry, since the order depends
     * entirely on this player's progress.
     *
     * Cached and rebuilt only when the line or the player's progress changes
     * - render() would otherwise re-sort several times a frame.
     */
    private List<Quest> quests() {
        String query = searchText();
        boolean filtering = !query.isEmpty();
        if (selectedLine == null && !filtering) {
            return List.of();
        }
        long signature = progressSignature();
        String cacheKey = (filtering ? "?" + query : String.valueOf(selectedLine));
        if (sortedCache == null || !cacheKey.equals(sortedCacheLine) || signature != sortedCacheSignature) {
            List<Quest> sorted = new ArrayList<>();
            if (filtering) {
                // Every chapter, not just the open one - see the searchBox note.
                for (QuestLine line : lines()) {
                    for (Quest quest : questsIn(line.id())) {
                        if (matchesQuest(quest, query)) {
                            sorted.add(quest);
                        }
                    }
                }
            } else {
                sorted.addAll(questsIn(selectedLine));
            }
            LocalPlayer player = player();
            sorted.sort(Comparator.comparingInt(quest -> sortRank(quest, player)));
            sortedCache = sorted;
            sortedCacheLine = cacheKey;
            sortedCacheSignature = signature;
        }
        return sortedCache;
    }

    /**
     * Matches the name, the description and the trigger target. Including the
     * target is what lets you find a quest by the block it is about -
     * "deepslate" or a modded id like "create:andesite" - which is exactly the
     * case players hit with a modpack's worth of custom blocks.
     */
    private boolean matchesQuest(Quest quest, String query) {
        if (quest.resolveName().toLowerCase(java.util.Locale.ROOT).contains(query)
                || quest.triggerTarget().toLowerCase(java.util.Locale.ROOT).contains(query)) {
            return true;
        }
        String description = quest.description();
        return description != null && description.toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    private int sortRank(Quest quest, LocalPlayer player) {
        if (player == null) {
            return 1;
        }
        QuestHelper.State state = QuestHelper.stateOf(player, quest);
        if (state == QuestHelper.State.COMPLETED) {
            return 3;
        }
        if (state == QuestHelper.State.LOCKED) {
            return 2;
        }
        return QuestHelper.getProgress(player, quest.id()) > 0 ? 0 : 1;
    }

    private long progressSignature() {
        LocalPlayer player = player();
        if (player == null) {
            return -1L;
        }
        QuestProgressData data = QuestHelper.data(player);
        long signature = data.completed().size() * 31L;
        for (Quest quest : questsIn(selectedLine)) {
            signature = signature * 31L + data.getProgress(quest.id());
        }
        return signature;
    }

    private List<ShopEntry> entries() {
        String query = searchText();
        List<ShopEntry> all;
        if (!query.isEmpty()) {
            // Across every category, same reasoning as the quest search.
            all = new ArrayList<>();
            for (ShopCategory category : shopCategories()) {
                for (ShopEntry entry : ShopRegistry.inCategory(access, category.id())) {
                    if (entry.displayName().toLowerCase(java.util.Locale.ROOT).contains(query)) {
                        all.add(entry);
                    }
                }
            }
        } else if (selectedCategory == null) {
            return List.of();
        } else {
            all = ShopRegistry.inCategory(access, selectedCategory);
        }
        if (shopMode == ShopMode.BUY) {
            return all;
        }
        // Sell view hides what can't be sold (enchanted books) rather than
        // showing dead rows you can never act on.
        List<ShopEntry> sellable = new ArrayList<>();
        for (ShopEntry entry : all) {
            if (entry.sellable()) {
                sellable.add(entry);
            }
        }
        return sellable;
    }

    /** How many of an entry's item the player is carrying. */
    private int heldCount(ShopEntry entry) {
        LocalPlayer player = player();
        if (player == null) {
            return 0;
        }
        int held = 0;
        for (ItemStack stack : player.getInventory().items) {
            // Must mirror ModCommands.isPlain() - enchanted or renamed items
            // are never sellable, so counting them here would show a sell
            // quantity the server then refuses.
            if (stack.is(entry.item())
                    && !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                    && stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                            net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty()
                    && stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                            net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty()
                    && stack.getOrDefault(ModDataComponents.VAULT_CONTENTS.get(), VaultContents.EMPTY).isEmpty()) {
                held += stack.getCount();
            }
        }
        return held;
    }

    private void sell(ShopEntry entry, int quantity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand("sell " + entry.id() + " " + quantity);
        }
    }

    // ==================== hit testing ====================

    private void clampAll() {
        int viewH = contentBottom - contentTop;
        scroll = clamp(scroll, quests().size() * QUEST_ROW_H - viewH);
        sidebarScroll = clamp(sidebarScroll, lines().size() * LINE_ROW_H - viewH);
        // Shop and Cash share shopScroll, so clamp against whichever list is
        // actually showing or Cash would scroll past its end.
        int shopContent = activeTab == Tab.CASH ? cashRowCount() * SHOP_ROW_H : shopContentHeight();
        shopScroll = clamp(shopScroll, shopContent - viewH);
        if (activeTab == Tab.GUIDE) {
            // Wrapping depends on panel width, so a resize changes the guide's
            // height - re-clamp or it can be left scrolled past the end.
            guideScroll = clamp(guideScroll, guideContentHeight() - viewH);
            guideSidebarScroll = clamp(guideSidebarScroll, GuideTopic.ALL.size() * LINE_ROW_H - viewH);
        }
        shopSidebarScroll = clamp(shopSidebarScroll, shopCategories().size() * LINE_ROW_H - viewH);
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }

    private boolean inSidebar(double mouseX, double mouseY) {
        return mouseX >= panelLeft && mouseX < listLeft && mouseY >= contentTop && mouseY < contentBottom;
    }

    private boolean inList(double mouseX, double mouseY) {
        return mouseX >= listLeft && mouseX <= listLeft + rowWidth()
                && mouseY >= contentTop && mouseY < contentBottom;
    }

    private int hoveredIndex(int mouseY, int scrollValue, int rowHeight, int size) {
        int index = (mouseY - contentTop + scrollValue) / rowHeight;
        return index >= 0 && index < size ? index : -1;
    }

    // ==================== shop layout (list vs grid) ====================

    private int gridColumns() {
        return Math.max(1, (rowWidth() - GRID_GAP) / (GRID_CELL_W + GRID_GAP));
    }

    /** Pixel height of the shop's content in whichever layout is active. */
    private int shopContentHeight() {
        int n = entries().size();
        if (!shopGrid) {
            return n * SHOP_ROW_H;
        }
        int rows = (n + gridColumns() - 1) / gridColumns();
        return rows * (GRID_CELL_H + GRID_GAP) + GRID_GAP;
    }

    /**
     * The entry under the cursor, list or grid - the single source for
     * clicks and tooltips, so the two layouts can never disagree about what
     * was clicked.
     */
    private int hoveredShopIndex(int mouseX, int mouseY) {
        if (!inList(mouseX, mouseY)) {
            return -1;
        }
        if (!shopGrid) {
            return hoveredIndex(mouseY, shopScroll, SHOP_ROW_H, entries().size());
        }
        int cols = gridColumns();
        int localX = mouseX - listLeft - GRID_GAP;
        int localY = mouseY - contentTop + shopScroll - GRID_GAP;
        if (localX < 0 || localY < 0) {
            return -1;
        }
        int col = localX / (GRID_CELL_W + GRID_GAP);
        int row = localY / (GRID_CELL_H + GRID_GAP);
        // Clicks in the gap between cells, or right of the last column,
        // belong to nothing.
        if (col >= cols || localX % (GRID_CELL_W + GRID_GAP) >= GRID_CELL_W
                || localY % (GRID_CELL_H + GRID_GAP) >= GRID_CELL_H) {
            return -1;
        }
        int index = row * cols + col;
        return index < entries().size() ? index : -1;
    }

    /**
     * The search field's backing plate plus a result count. The count matters
     * when searching across chapters: without it, an empty list is ambiguous
     * between "no matches" and "something is broken".
     */
    private void drawSearchRow(GuiGraphics g) {
        g.fill(listLeft, searchTop, listRight, searchTop + SEARCH_H - 1, MoneyUI.TAB_IDLE);
        g.fill(listLeft, searchTop + SEARCH_H - 1, listRight, searchTop + SEARCH_H, MoneyUI.DIVIDER);
    }

    /**
     * Drawn AFTER super.render(), which is what paints the EditBox - otherwise
     * a long query's text runs straight over the count. Opaque behind the label
     * so the two never interleave.
     */
    private void drawSearchCount(GuiGraphics g) {
        if (!searching()) {
            return;
        }
        int count = activeTab == Tab.QUESTS ? quests().size() : entries().size();
        String label = count == 0 ? "no matches" : count + (count == 1 ? " match" : " matches");
        int labelW = this.font.width(label);
        g.fill(listRight - labelW - 10, searchTop, listRight, searchTop + SEARCH_H - 1, MoneyUI.TAB_IDLE);
        g.drawString(this.font, label, listRight - labelW - 4, searchTop + 5,
                count == 0 ? MoneyUI.RED : MoneyUI.TEXT_FAINT, false);
    }

    // ==================== render ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, MoneyUI.BACKDROP);

        MoneyUI.panel(g, panelLeft, panelTop, panelW, panelH);
        MoneyUI.headerBar(g, panelLeft, panelTop, panelW, HEADER_H);
        g.drawString(this.font, this.title, panelLeft + PAD, panelTop + 8, MoneyUI.TEXT, false);

        long balance = balance();
        String balanceText = MoneyUI.money(balance);
        g.drawString(this.font, balanceText,
                panelLeft + panelW - PAD - this.font.width(balanceText), panelTop + 8, MoneyUI.GOLD, false);

        for (Map.Entry<Tab, int[]> tab : topTabBounds.entrySet()) {
            int[] b = tab.getValue();
            boolean hovered = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
            MoneyUI.tab(g, this.font, b[0], b[1], b[2], b[3], tab.getKey().label,
                    tab.getKey() == activeTab, hovered);
        }
        for (Map.Entry<ShopMode, int[]> mode : modeBounds.entrySet()) {
            int[] b = mode.getValue();
            boolean hovered = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
            MoneyUI.tab(g, this.font, b[0], b[1], b[2], b[3], mode.getKey().label,
                    mode.getKey() == shopMode, hovered);
        }
        if (gridToggleBounds != null) {
            int[] b = gridToggleBounds;
            boolean hovered = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
            // Lit while the grid is active, exactly like the mode tabs.
            MoneyUI.tab(g, this.font, b[0], b[1], b[2], b[3], "Grid", shopGrid, hovered);
        }

        if (tabHasSearch()) {
            drawSearchRow(g);
        }

        g.fill(listLeft - 1, contentTop, listLeft, contentBottom, MoneyUI.DIVIDER);

        if (activeTab == Tab.QUESTS) {
            drawQuestSidebar(g, mouseX, mouseY);
            drawQuestList(g, mouseX, mouseY);
            drawDetail(g);
        } else if (activeTab == Tab.SHOP) {
            drawShopSidebar(g, mouseX, mouseY);
            drawShopList(g, mouseX, mouseY, balance);
        } else if (activeTab == Tab.CASH) {
            drawCashList(g, mouseX, mouseY, balance);
        } else {
            drawGuideSidebar(g, mouseX, mouseY);
            drawGuideBody(g);
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (tabHasSearch()) {
            drawSearchCount(g);
        }

        if (activeTab == Tab.QUESTS) {
            drawLadderTooltips(g, mouseX, mouseY);
        }

        if (activeTab == Tab.SHOP && inList(mouseX, mouseY)) {
            int index = hoveredShopIndex(mouseX, mouseY);
            if (index >= 0) {
                ShopEntry entry = entries().get(index);
                if (shopMode == ShopMode.BUY) {
                    LocalPlayer buyer = player();
                    long cost = TaxHelper.buyCost(entry);
                    long tax = TaxHelper.buyTax(entry);
                    int remaining = buyer == null ? -1 : MarketHelper.remainingPurchases(buyer, entry);
                    boolean soldOut = remaining == 0;
                    boolean afford = !soldOut && balance >= cost;

                    // The vanilla tooltip of the EXACT stack a purchase builds -
                    // so signature gear lists its enchantments, books list what
                    // they store, and this can never drift from what /buy hands
                    // over, because both come from the same createStack().
                    List<Component> vanilla =
                            getTooltipFromItem(Minecraft.getInstance(), entry.createStack(access));

                    // Deliberately selective: a tooltip only appears when it
                    // adds something the row/tile does not already show -
                    // enchantments or modded stat lines (vanilla tooltip beyond
                    // the bare name), a purchase limit, or a name the grid tile
                    // had to ellipsize. Plain items get no box at all; their
                    // row already says name, price and affordability.
                    boolean nameTruncated = shopGrid
                            && this.font.width(entry.displayName()) > GRID_CELL_W - 8;
                    boolean worthShowing = vanilla.size() > 1
                            || entry.hasBuyLimit()
                            || nameTruncated;

                    if (worthShowing) {
                        List<Component> lines = new ArrayList<>(vanilla);
                        lines.add(Component.literal(MoneyUI.money(cost))
                                .withStyle(afford ? ChatFormatting.GREEN : ChatFormatting.RED));
                        // Broken out so a taxed price does not just look like a
                        // higher price with no explanation.
                        if (tax > 0) {
                            lines.add(Component.literal("  " + MoneyUI.money(entry.price()) + " + "
                                            + MoneyUI.money(tax) + " tax")
                                    .withStyle(ChatFormatting.DARK_GRAY));
                        }
                        if (entry.hasBuyLimit()) {
                            lines.add(Component.literal(remaining + " of " + entry.buyLimit() + " remaining")
                                    .withStyle(soldOut ? ChatFormatting.RED : ChatFormatting.GOLD));
                        }
                        if (soldOut) {
                            lines.add(Component.literal("You have bought your limit")
                                    .withStyle(ChatFormatting.RED));
                        }
                        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                    }
                } else {
                    LocalPlayer player = player();
                    int held = heldCount(entry);
                    long unit = player == null ? entry.baseSellPrice() : MarketHelper.sellPrice(player, entry);
                    int demandPercent = player == null ? 100
                            : (int) Math.round(MarketHelper.demand(player, entry) * 100);
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal(entry.displayName()).withStyle(ChatFormatting.WHITE));
                    lines.add(Component.literal(MoneyUI.money(unit) + " each")
                            .withStyle(held > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                    lines.add(Component.literal("Demand " + demandPercent + "%  (recovers daily)")
                            .withStyle(ChatFormatting.DARK_GRAY));
                    if (held > 0) {
                        lines.add(Component.literal("All " + held + " -> "
                                        + MoneyUI.money(MarketHelper.quoteBulk(player, entry, held)))
                                .withStyle(ChatFormatting.GOLD));
                        lines.add(Component.literal("Click sells 1  -  Shift-click sells all")
                                .withStyle(ChatFormatting.GRAY));
                    } else {
                        lines.add(Component.literal("You have none to sell").withStyle(ChatFormatting.GRAY));
                    }
                    g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                }
            }
        }
    }

    private void drawQuestSidebar(GuiGraphics g, int mouseX, int mouseY) {
        LocalPlayer player = player();
        int viewH = contentBottom - contentTop;
        int hovered = inSidebar(mouseX, mouseY)
                ? hoveredIndex(mouseY, sidebarScroll, LINE_ROW_H, lines().size()) : -1;

        g.enableScissor(panelLeft + 1, contentTop, listLeft - 1, contentBottom);
        int y = contentTop - sidebarScroll;
        int i = 0;
        for (QuestLine line : lines()) {
            if (y + LINE_ROW_H >= contentTop && y <= contentBottom) {
                boolean active = line.id().equals(selectedLine);
                if (active) {
                    g.fill(panelLeft + 1, y, listLeft - 1, y + LINE_ROW_H, MoneyUI.TAB_ACTIVE);
                    g.fill(panelLeft + 1, y, panelLeft + 3, y + LINE_ROW_H, MoneyUI.GOLD);
                } else if (i == hovered) {
                    g.fill(panelLeft + 1, y, listLeft - 1, y + LINE_ROW_H, MoneyUI.TAB_HOVER);
                }
                g.renderItem(new ItemStack(line.icon()), panelLeft + 8, y + 5);

                int total = questsIn(line.id()).size();
                int done = player == null ? 0 : QuestHelper.completedIn(player, line.id());
                g.drawString(this.font, MoneyUI.fit(this.font, line.name(), sidebarW - 34),
                        panelLeft + 28, y + 4, active ? MoneyUI.TEXT : MoneyUI.TEXT_DIM, false);
                g.drawString(this.font, done + "/" + total, panelLeft + 28, y + 14,
                        total > 0 && done >= total ? MoneyUI.GOLD : MoneyUI.TEXT_FAINT, false);
            }
            y += LINE_ROW_H;
            i++;
        }
        g.disableScissor();

        MoneyUI.scrollbar(g, listLeft - 6, contentTop, viewH,
                lines().size() * LINE_ROW_H, viewH, sidebarScroll);
    }

    private void drawQuestList(GuiGraphics g, int mouseX, int mouseY) {
        List<Quest> quests = quests();
        QuestLine line = selectedLine == null ? null : QuestRegistry.lineById(access, selectedLine);
        int viewH = contentBottom - contentTop;
        int hovered = inList(mouseX, mouseY)
                ? hoveredIndex(mouseY, scroll, QUEST_ROW_H, quests.size()) : -1;

        g.enableScissor(listLeft, contentTop, listRight, contentBottom);
        for (int i = 0; i < quests.size(); i++) {
            int rowY = contentTop - scroll + i * QUEST_ROW_H;
            if (rowY + QUEST_ROW_H >= contentTop && rowY <= contentBottom) {
                drawQuestRow(g, quests.get(i), line, rowY, i == hovered);
            }
        }
        g.disableScissor();

        MoneyUI.scrollbar(g, listRight - 5, contentTop, viewH, quests.size() * QUEST_ROW_H, viewH, scroll);
    }

    private void drawQuestRow(GuiGraphics g, Quest quest, QuestLine line, int y, boolean hovered) {
        LocalPlayer player = player();
        int w = rowWidth();
        QuestHelper.State state = player == null ? QuestHelper.State.LOCKED : QuestHelper.stateOf(player, quest);

        if (quest.id().equals(selectedQuest)) {
            g.fill(listLeft, y, listLeft + w, y + QUEST_ROW_H, MoneyUI.TAB_ACTIVE);
        } else if (hovered) {
            g.fill(listLeft, y, listLeft + w, y + QUEST_ROW_H, MoneyUI.ROW_HOVER);
        }

        g.renderItem(quest.resolveIcon(line), listLeft + 6, y + 4);

        int cleared = player == null ? 0 : QuestHelper.getTier(player, quest.id());
        int tier = quest.isMaxed(cleared) ? Math.max(1, quest.maxTier()) : cleared + 1;
        int required = quest.cumulativeTargetForTier(tier);
        int progress = player == null ? 0 : Math.min(required, QuestHelper.getProgress(player, quest.id()));

        String status;
        int nameColor;
        int barColor;
        switch (state) {
            case COMPLETED -> {
                status = "Complete";
                nameColor = MoneyUI.GOLD;
                barColor = MoneyUI.GOLD;
            }
            case LOCKED -> {
                status = "Locked";
                nameColor = MoneyUI.TEXT_FAINT;
                barColor = MoneyUI.TEXT_FAINT;
            }
            default -> {
                // Repeatable quests lead with the tier so the ladder is
                // visible without opening the detail panel.
                status = quest.repeatable()
                        ? "T" + tier + "   " + progress + " / " + required
                        : progress + " / " + required;
                nameColor = MoneyUI.TEXT;
                barColor = MoneyUI.GREEN;
            }
        }

        int statusW = this.font.width(status);
        g.drawString(this.font, MoneyUI.fit(this.font, quest.resolveName(), w - 30 - statusW - PAD - 6),
                listLeft + 28, y + 4, nameColor, false);
        g.drawString(this.font, status, listLeft + w - PAD - statusW, y + 4,
                state == QuestHelper.State.COMPLETED ? MoneyUI.GOLD : MoneyUI.TEXT_DIM, false);

        float pct = state == QuestHelper.State.COMPLETED ? 1.0F
                : (state == QuestHelper.State.LOCKED ? 0.0F : (float) progress / required);
        MoneyUI.progressBar(g, listLeft + 28, y + 16, w - 28 - PAD, 3, pct, barColor);
    }

    /** Bottom strip: what the selected quest is, and what it actually pays. */
    private void drawDetail(GuiGraphics g) {
        int top = contentBottom;
        g.fill(panelLeft + 1, top, panelLeft + panelW - 1, top + 1, MoneyUI.DIVIDER);

        Quest quest = selectedQuest == null ? null : QuestRegistry.byId(access, selectedQuest);
        if (quest == null) {
            g.drawString(this.font, "Select a quest to see its details and rewards.",
                    panelLeft + PAD, top + 10, MoneyUI.TEXT_FAINT, false);
            return;
        }

        g.drawString(this.font, MoneyUI.fit(this.font, quest.resolveName(), panelW - PAD * 2),
                panelLeft + PAD, top + 7, MoneyUI.TEXT, false);

        LocalPlayer player = player();
        QuestHelper.State state = player == null ? QuestHelper.State.LOCKED : QuestHelper.stateOf(player, quest);
        if (state == QuestHelper.State.LOCKED) {
            StringBuilder needs = new StringBuilder("Locked - requires: ");
            for (int i = 0; i < quest.dependencies().size(); i++) {
                Quest dependency = QuestRegistry.byId(access, quest.dependencies().get(i));
                if (dependency == null) {
                    continue;
                }
                if (i > 0) {
                    needs.append(", ");
                }
                needs.append(dependency.resolveName());
            }
            g.drawString(this.font, MoneyUI.fit(this.font, needs.toString(), panelW - PAD * 2),
                    panelLeft + PAD, top + 19, MoneyUI.RED, false);
        } else if (quest.description() != null && !quest.description().isBlank()) {
            g.drawString(this.font, MoneyUI.fit(this.font, quest.description(), panelW - PAD * 2),
                    panelLeft + PAD, top + 19, MoneyUI.TEXT_DIM, false);
        }

        g.drawString(this.font, "Rewards", panelLeft + PAD, top + 33, MoneyUI.TEXT_FAINT, false);
        int x = panelLeft + PAD + this.font.width("Rewards") + 8;
        int y = top + 31;
        int cleared = player == null ? 0 : QuestHelper.getTier(player, quest.id());
        int tier = quest.isMaxed(cleared) ? Math.max(1, quest.maxTier()) : cleared + 1;
        double multiplier = quest.rewardMultiplierForTier(tier);

        for (QuestReward reward : quest.rewards()) {
            QuestReward shown = reward;
            if (reward instanceof QuestReward.Money money) {
                shown = new QuestReward.Money(Math.max(1L, Math.round(money.amount() * multiplier)));
            } else if (tier > 1) {
                continue;  // one-off rewards already paid on tier 1
            }
            String label = shown.describe();
            int chunk = 20 + this.font.width(label) + 10;
            if (x + chunk > panelLeft + panelW - PAD) {
                break;
            }
            g.renderItem(shown.icon(), x, y);
            g.drawString(this.font, label, x + 20, y + 4, MoneyUI.GREEN, false);
            x += chunk;
        }

        drawTierLadder(g, quest, top + 48, player, cleared, tier);
        drawChapterLadder(g, quest, top + 66, player);
    }

    /**
     * The tier ladder for a repeatable quest. Shows a few tiers either side
     * of where you are, so the fact that it keeps going is obvious without
     * listing an infinite sequence.
     */
    private void drawTierLadder(GuiGraphics g, Quest quest, int y, LocalPlayer player, int cleared, int tier) {
        tierPipBounds.clear();
        if (!quest.repeatable()) {
            g.drawString(this.font, "One-time quest", panelLeft + PAD, y + 2, MoneyUI.TEXT_FAINT, false);
            return;
        }

        g.drawString(this.font, "Tiers", panelLeft + PAD, y + 2, MoneyUI.TEXT_FAINT, false);
        int x = panelLeft + PAD + this.font.width("Tiers") + 8;

        // Window of 6 tiers, sliding so the current one stays visible.
        int first = Math.max(1, tier - 2);
        for (int t = first; t < first + 6; t++) {
            boolean done = t <= cleared;
            boolean current = t == tier && !quest.isMaxed(cleared);
            int color = done ? MoneyUI.GOLD : (current ? MoneyUI.GREEN : MoneyUI.TEXT_FAINT);
            g.fill(x, y, x + 5, y + 8, color);
            if (current) {
                g.fill(x - 1, y + 9, x + 6, y + 10, MoneyUI.GREEN);
            }
            // Slightly padded hit box - a 5px target is hard to hit.
            tierPipBounds.put(t, new int[]{x - 1, y - 2, 8, 13});
            x += 8;
        }

        int nextTotal = quest.cumulativeTargetForTier(tier);
        long pay = 0L;
        for (QuestReward reward : quest.rewards()) {
            if (reward instanceof QuestReward.Money money) {
                pay = Math.round(money.amount() * quest.rewardMultiplierForTier(tier));
                break;
            }
        }
        String summary = "T" + tier + ": " + nextTotal + " total"
                + (pay > 0 ? "  ->  " + MoneyUI.money(pay) : "") + "   (repeats)";
        g.drawString(this.font, MoneyUI.fit(this.font, summary, panelLeft + panelW - PAD - (x + 6)),
                x + 6, y + 2, MoneyUI.TEXT_DIM, false);
    }

    /**
     * Where this quest sits among its chapter siblings, ordered by payout -
     * so a player looking at Iron can see Diamond and Ancient Debris exist
     * further up the ladder.
     */
    private void drawChapterLadder(GuiGraphics g, Quest quest, int y, LocalPlayer player) {
        chapterPipBounds.clear();
        List<Quest> ladder = chapterLadder(quest.lineId());
        if (ladder.isEmpty()) {
            return;
        }
        QuestLine line = QuestRegistry.lineById(access, quest.lineId());
        String label = line == null ? "Chapter" : line.name();
        g.drawString(this.font, label, panelLeft + PAD, y + 2, MoneyUI.TEXT_FAINT, false);

        int x = panelLeft + PAD + this.font.width(label) + 8;
        int done = 0;
        for (Quest sibling : ladder) {
            boolean isThis = sibling.id().equals(quest.id());
            int color;
            if (player != null && QuestHelper.getTier(player, sibling.id()) > 0) {
                color = MoneyUI.GOLD;
                done++;
            } else if (player != null && QuestHelper.getProgress(player, sibling.id()) > 0) {
                color = MoneyUI.GREEN;
            } else {
                color = MoneyUI.TEXT_FAINT;
            }
            g.fill(x, y, x + 5, y + 8, color);
            if (isThis) {
                // Marker under the current quest's pip.
                g.fill(x - 1, y + 9, x + 6, y + 10, MoneyUI.TEXT);
            }
            chapterPipBounds.put(sibling.id(), new int[]{x - 1, y - 2, 7, 13});
            x += 7;
        }

        String summary = done + " of " + ladder.size() + " started";
        int summaryX = panelLeft + panelW - PAD - this.font.width(summary);
        if (summaryX > x + 6) {
            g.drawString(this.font, summary, summaryX, y + 2, MoneyUI.TEXT_DIM, false);
        }
    }

    private static boolean within(int[] bounds, int mouseX, int mouseY) {
        return mouseX >= bounds[0] && mouseX < bounds[0] + bounds[2]
                && mouseY >= bounds[1] && mouseY < bounds[1] + bounds[3];
    }

    /**
     * Hovering a ladder pip explains that step: what it asks for and what it
     * pays. The pips alone show position but not the numbers, which is the
     * thing you actually want when deciding whether to keep going.
     */
    private void drawLadderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        Quest quest = selectedQuest == null ? null : QuestRegistry.byId(access, selectedQuest);
        if (quest == null) {
            return;
        }
        LocalPlayer player = player();
        int cleared = player == null ? 0 : QuestHelper.getTier(player, quest.id());
        int lifetime = player == null ? 0 : QuestHelper.getProgress(player, quest.id());

        for (Map.Entry<Integer, int[]> pip : tierPipBounds.entrySet()) {
            if (!within(pip.getValue(), mouseX, mouseY)) {
                continue;
            }
            int t = pip.getKey();
            int thisTier = quest.targetForTier(t);
            int cumulative = quest.cumulativeTargetForTier(t);
            long pay = 0L;
            for (QuestReward reward : quest.rewards()) {
                if (reward instanceof QuestReward.Money money) {
                    pay = Math.max(1L, Math.round(money.amount() * quest.rewardMultiplierForTier(t)));
                    break;
                }
            }

            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("Tier " + t).withStyle(ChatFormatting.WHITE));
            lines.add(Component.literal(thisTier + " more  (" + cumulative + " lifetime)")
                    .withStyle(ChatFormatting.GRAY));
            if (pay > 0) {
                lines.add(Component.literal("Pays " + MoneyUI.money(pay)).withStyle(ChatFormatting.GREEN));
            }
            if (t <= cleared) {
                lines.add(Component.literal("Cleared").withStyle(ChatFormatting.GOLD));
            } else if (t == cleared + 1) {
                lines.add(Component.literal("In progress - " + Math.min(lifetime, cumulative)
                        + " / " + cumulative).withStyle(ChatFormatting.AQUA));
            } else {
                lines.add(Component.literal((cumulative - lifetime) + " to go")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }

        for (Map.Entry<String, int[]> pip : chapterPipBounds.entrySet()) {
            if (!within(pip.getValue(), mouseX, mouseY)) {
                continue;
            }
            Quest sibling = QuestRegistry.byId(access, pip.getKey());
            if (sibling == null) {
                continue;
            }
            int siblingTier = player == null ? 0 : QuestHelper.getTier(player, sibling.id());
            long base = baseMoney(sibling);

            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(sibling.resolveName()).withStyle(ChatFormatting.WHITE));
            if (base > 0) {
                lines.add(Component.literal("Tier 1 pays " + MoneyUI.money(base))
                        .withStyle(ChatFormatting.GREEN));
            }
            if (siblingTier > 0) {
                lines.add(Component.literal(sibling.repeatable()
                                ? "Tier " + siblingTier + " cleared" : "Completed")
                        .withStyle(ChatFormatting.GOLD));
            } else if (player != null && QuestHelper.getProgress(player, sibling.id()) > 0) {
                lines.add(Component.literal("Started").withStyle(ChatFormatting.AQUA));
            } else {
                lines.add(Component.literal("Not started").withStyle(ChatFormatting.DARK_GRAY));
            }
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
    }

    /** Chapter siblings ordered by payout - the implied difficulty ladder. */
    private List<Quest> chapterLadder(String lineId) {
        List<Quest> ladder = new ArrayList<>(questsIn(lineId));
        ladder.sort(Comparator.comparingLong(this::baseMoney).thenComparing(Quest::id));
        return ladder;
    }

    private long baseMoney(Quest quest) {
        for (QuestReward reward : quest.rewards()) {
            if (reward instanceof QuestReward.Money money) {
                return money.amount();
            }
        }
        return 0L;
    }

    // ==================== shop tab ====================

    private void drawShopSidebar(GuiGraphics g, int mouseX, int mouseY) {
        List<ShopCategory> categories = shopCategories();
        int viewH = contentBottom - contentTop;
        int hovered = inSidebar(mouseX, mouseY)
                ? hoveredIndex(mouseY, shopSidebarScroll, LINE_ROW_H, categories.size()) : -1;

        g.enableScissor(panelLeft + 1, contentTop, listLeft - 1, contentBottom);
        int y = contentTop - shopSidebarScroll;
        for (int i = 0; i < categories.size(); i++) {
            ShopCategory category = categories.get(i);
            if (y + LINE_ROW_H >= contentTop && y <= contentBottom) {
                boolean active = category.id().equals(selectedCategory);
                if (active) {
                    g.fill(panelLeft + 1, y, listLeft - 1, y + LINE_ROW_H, MoneyUI.TAB_ACTIVE);
                    g.fill(panelLeft + 1, y, panelLeft + 3, y + LINE_ROW_H, MoneyUI.GOLD);
                } else if (i == hovered) {
                    g.fill(panelLeft + 1, y, listLeft - 1, y + LINE_ROW_H, MoneyUI.TAB_HOVER);
                }

                // A category may name its own icon (1.1.0); when it does not,
                // the cheapest entry doubles as one - free, always
                // representative, and what every 1.0.0 category still does.
                List<ShopEntry> inCategory = ShopRegistry.inCategory(access, category.id());
                if (category.icon().isPresent()) {
                    g.renderItem(new ItemStack(category.icon().get()), panelLeft + 8, y + 5);
                } else if (!inCategory.isEmpty()) {
                    ShopEntry first = inCategory.get(0);
                    g.renderItem(new ItemStack(first.item()), panelLeft + 8, y + 5);
                }
                g.drawString(this.font, MoneyUI.fit(this.font, category.getLabel(), sidebarW - 34),
                        panelLeft + 28, y + 4, active ? MoneyUI.TEXT : MoneyUI.TEXT_DIM, false);
                g.drawString(this.font, inCategory.size() + " items", panelLeft + 28, y + 14,
                        MoneyUI.TEXT_FAINT, false);
            }
            y += LINE_ROW_H;
        }
        g.disableScissor();

        MoneyUI.scrollbar(g, listLeft - 6, contentTop, viewH,
                categories.size() * LINE_ROW_H, viewH, shopSidebarScroll);
    }

    private void drawShopList(GuiGraphics g, int mouseX, int mouseY, long balance) {
        List<ShopEntry> entries = entries();
        int viewH = contentBottom - contentTop;
        int hovered = hoveredShopIndex(mouseX, mouseY);

        g.enableScissor(listLeft, contentTop, listRight, contentBottom);
        if (shopGrid) {
            int cols = gridColumns();
            for (int i = 0; i < entries.size(); i++) {
                int cellX = listLeft + GRID_GAP + (i % cols) * (GRID_CELL_W + GRID_GAP);
                int cellY = contentTop + GRID_GAP - shopScroll + (i / cols) * (GRID_CELL_H + GRID_GAP);
                if (cellY + GRID_CELL_H >= contentTop && cellY <= contentBottom) {
                    drawShopCell(g, entries.get(i), cellX, cellY, i == hovered, balance);
                }
            }
        } else {
            for (int i = 0; i < entries.size(); i++) {
                int rowY = contentTop - shopScroll + i * SHOP_ROW_H;
                if (rowY + SHOP_ROW_H >= contentTop && rowY <= contentBottom) {
                    if (shopMode == ShopMode.BUY) {
                        drawShopRow(g, entries.get(i), rowY, i == hovered, balance);
                    } else {
                        drawSellRow(g, entries.get(i), rowY, i == hovered);
                    }
                }
            }
        }
        g.disableScissor();

        MoneyUI.scrollbar(g, listRight - 5, contentTop, viewH, shopContentHeight(), viewH, shopScroll);
    }

    /**
     * One grid tile: icon on top, name and price beneath - the format the
     * feedback asked for. A tile carries less than a row, deliberately; the
     * tooltip is the detail surface, the tile is for scanning.
     */
    private void drawShopCell(GuiGraphics g, ShopEntry entry, int x, int y, boolean hovered, long balance) {
        LocalPlayer player = player();
        boolean selling = shopMode == ShopMode.SELL;

        long price;
        boolean actionable;
        if (selling) {
            int held = heldCount(entry);
            price = player == null ? entry.baseSellPrice() : MarketHelper.sellPrice(player, entry);
            actionable = held > 0;
        } else {
            price = TaxHelper.buyCost(entry);
            int remaining = player == null ? -1 : MarketHelper.remainingPurchases(player, entry);
            actionable = remaining != 0 && balance >= price;
        }

        g.fill(x, y, x + GRID_CELL_W, y + GRID_CELL_H, hovered ? MoneyUI.ROW_HOVER : MoneyUI.TAB_IDLE);
        if (hovered) {
            // A 1px outline reads better than a fill change alone at tile size.
            g.fill(x, y, x + GRID_CELL_W, y + 1, MoneyUI.GOLD);
            g.fill(x, y + GRID_CELL_H - 1, x + GRID_CELL_W, y + GRID_CELL_H, MoneyUI.GOLD);
            g.fill(x, y, x + 1, y + GRID_CELL_H, MoneyUI.GOLD);
            g.fill(x + GRID_CELL_W - 1, y, x + GRID_CELL_W, y + GRID_CELL_H, MoneyUI.GOLD);
        }

        // In sell mode the stack renders your held count in the corner, which
        // is the one number that matters there.
        ItemStack stack = new ItemStack(entry.item(),
                selling ? Math.max(1, heldCount(entry)) : Math.max(1, entry.count()));
        int iconX = x + (GRID_CELL_W - 16) / 2;
        g.renderItem(stack, iconX, y + 4);
        if (selling) {
            g.renderItemDecorations(this.font, stack, iconX, y + 4);
        }

        String name = MoneyUI.fit(this.font, entry.displayName(), GRID_CELL_W - 8);
        g.drawString(this.font, name, x + (GRID_CELL_W - this.font.width(name)) / 2, y + 24,
                actionable ? MoneyUI.TEXT : MoneyUI.TEXT_FAINT, false);

        String priceText = MoneyUI.fit(this.font, MoneyUI.money(price), GRID_CELL_W - 8);
        int priceColor = selling
                ? (actionable ? MoneyUI.GREEN : MoneyUI.TEXT_FAINT)
                : (actionable ? MoneyUI.GREEN : MoneyUI.RED);
        g.drawString(this.font, priceText, x + (GRID_CELL_W - this.font.width(priceText)) / 2, y + 35,
                priceColor, false);
    }

    private void drawShopRow(GuiGraphics g, ShopEntry entry, int y, boolean hovered, long balance) {
        int w = rowWidth();
        if (hovered) {
            g.fill(listLeft, y, listLeft + w, y + SHOP_ROW_H, MoneyUI.ROW_HOVER);
        }

        ItemStack stack = new ItemStack(entry.item(), Math.max(1, entry.count()));
        g.renderItem(stack, listLeft + 6, y + 4);
        g.renderItemDecorations(this.font, stack, listLeft + 6, y + 4);

        // The tax-inclusive figure, so the row quotes what /buy will charge.
        long cost = TaxHelper.buyCost(entry);
        LocalPlayer player = player();
        int remaining = player == null ? -1 : MarketHelper.remainingPurchases(player, entry);
        boolean soldOut = remaining == 0;
        boolean afford = !soldOut && balance >= cost;

        String price = soldOut ? "Limit reached" : MoneyUI.money(cost);
        int priceW = this.font.width(price);
        int nameX = listLeft + 30;

        // A limited entry says so on the row; without it "Limit reached" would
        // be the first a player heard of the restriction.
        String name = entry.displayName();
        if (remaining > 0) {
            name = name + "  (" + remaining + " left)";
        }

        g.drawString(this.font, MoneyUI.fit(this.font, name,
                        (listLeft + w - PAD - priceW - 10) - nameX),
                nameX, y + 8, afford ? MoneyUI.TEXT : MoneyUI.TEXT_FAINT, false);
        g.drawString(this.font, price, listLeft + w - PAD - priceW, y + 8,
                soldOut ? MoneyUI.TEXT_FAINT : (afford ? MoneyUI.GREEN : MoneyUI.RED), false);
    }

    /**
     * A sell row shows everything needed to make the call without opening a
     * tooltip: what it is, how many you hold, what it pays right now, and how
     * saturated your buyers are for it (the bar). Rows you hold none of are
     * dimmed rather than hidden - seeing the price is the point.
     */
    private void drawSellRow(GuiGraphics g, ShopEntry entry, int y, boolean hovered) {
        LocalPlayer player = player();
        int w = rowWidth();
        if (hovered) {
            g.fill(listLeft, y, listLeft + w, y + SHOP_ROW_H, MoneyUI.ROW_HOVER);
        }

        ItemStack stack = new ItemStack(entry.item());
        g.renderItem(stack, listLeft + 6, y + 4);

        int held = heldCount(entry);
        boolean has = held > 0;
        long unit = player == null ? entry.baseSellPrice() : MarketHelper.sellPrice(player, entry);
        double demand = player == null ? 1.0 : MarketHelper.demand(player, entry);

        String price = MoneyUI.money(unit);
        int priceW = this.font.width(price);
        int nameX = listLeft + 30;
        int barW = 34;
        int priceX = listLeft + w - PAD - priceW;

        g.drawString(this.font, MoneyUI.fit(this.font, entry.displayName(), (priceX - 8) - nameX),
                nameX, y + 3, has ? MoneyUI.TEXT : MoneyUI.TEXT_FAINT, false);
        g.drawString(this.font, has ? "you have " + held : "none held", nameX, y + 13,
                has ? MoneyUI.TEXT_DIM : MoneyUI.TEXT_FAINT, false);
        g.drawString(this.font, price, priceX, y + 3, has ? MoneyUI.GREEN : MoneyUI.TEXT_FAINT, false);

        // Demand bar: full and green when your buyers are fresh, shrinking
        // and amber as you saturate them.
        int barX = listLeft + w - PAD - barW;
        int barColor = demand > 0.66 ? MoneyUI.GREEN : (demand > 0.33 ? MoneyUI.GOLD : MoneyUI.RED);
        MoneyUI.progressBar(g, barX, y + 16, barW, 3, (float) demand, barColor);
    }

    // ==================== guide tab ====================

    private static final int GUIDE_LINE_H = 10;

    private void drawGuideSidebar(GuiGraphics g, int mouseX, int mouseY) {
        List<GuideTopic> topics = GuideTopic.ALL;
        int viewH = contentBottom - contentTop;
        int hovered = inSidebar(mouseX, mouseY)
                ? hoveredIndex(mouseY, guideSidebarScroll, LINE_ROW_H, topics.size()) : -1;

        g.enableScissor(panelLeft + 1, contentTop, listLeft - 1, contentBottom);
        int y = contentTop - guideSidebarScroll;
        for (int i = 0; i < topics.size(); i++) {
            if (y + LINE_ROW_H >= contentTop && y <= contentBottom) {
                boolean active = i == guideTopic;
                if (active) {
                    g.fill(panelLeft + 1, y, listLeft - 1, y + LINE_ROW_H, MoneyUI.TAB_ACTIVE);
                    g.fill(panelLeft + 1, y, panelLeft + 3, y + LINE_ROW_H, MoneyUI.GOLD);
                } else if (i == hovered) {
                    g.fill(panelLeft + 1, y, listLeft - 1, y + LINE_ROW_H, MoneyUI.TAB_HOVER);
                }
                g.renderItem(new ItemStack(topics.get(i).icon()), panelLeft + 8, y + 5);
                g.drawString(this.font, MoneyUI.fit(this.font, topics.get(i).title(), sidebarW - 34),
                        panelLeft + 28, y + 9, active ? MoneyUI.TEXT : MoneyUI.TEXT_DIM, false);
            }
            y += LINE_ROW_H;
        }
        g.disableScissor();
        MoneyUI.scrollbar(g, listLeft - 6, contentTop, viewH, topics.size() * LINE_ROW_H, viewH, guideSidebarScroll);
    }

    /**
     * Wrapped body text for the selected topic. Wrapping is done with
     * Font.split rather than by hand so it respects the actual glyph widths
     * and stays correct in any language.
     */
    private List<net.minecraft.util.FormattedCharSequence> guideLines(int wrapWidth) {
        List<net.minecraft.util.FormattedCharSequence> out = new ArrayList<>();
        for (String raw : GuideTopic.ALL.get(guideTopic).lines()) {
            if (raw.isEmpty()) {
                out.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            } else if (raw.startsWith("#")) {
                // Headings are short by construction, so they never wrap.
                out.add(Component.literal(raw.substring(1))
                        .withStyle(ChatFormatting.GOLD).getVisualOrderText());
            } else {
                out.addAll(this.font.split(Component.literal(raw), wrapWidth));
            }
        }
        return out;
    }

    private void drawGuideBody(GuiGraphics g) {
        int w = rowWidth();
        int viewH = contentBottom - contentTop;
        List<net.minecraft.util.FormattedCharSequence> lines = guideLines(w - PAD * 2);

        g.enableScissor(listLeft, contentTop, listRight, contentBottom);
        int y = contentTop + 2 - guideScroll;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            if (y + GUIDE_LINE_H >= contentTop && y <= contentBottom) {
                g.drawString(this.font, line, listLeft + PAD, y, MoneyUI.TEXT_DIM, false);
            }
            y += GUIDE_LINE_H;
        }
        g.disableScissor();

        MoneyUI.scrollbar(g, listRight - 5, contentTop, viewH,
                lines.size() * GUIDE_LINE_H + 4, viewH, guideScroll);
    }

    private int guideContentHeight() {
        return guideLines(rowWidth() - PAD * 2).size() * GUIDE_LINE_H + 4;
    }

    // ==================== cash tab ====================

    /** Row 0 is "deposit all"; rows 1..n are the denominations. */
    private int cashRowCount() {
        return ModItems.billIds().size() + 1;
    }

    private int heldBills(String denomination) {
        LocalPlayer player = player();
        if (player == null) {
            return 0;
        }
        long value = ModItems.billValue(denomination);
        int held = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CurrencyItem currency && currency.getValue() == value) {
                held += stack.getCount();
            }
        }
        return held;
    }

    /** Total face value of every bill carried. */
    private long carriedCash() {
        LocalPlayer player = player();
        if (player == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CurrencyItem currency) {
                total += currency.getValue() * stack.getCount();
            }
        }
        return total;
    }

    private void drawCashList(GuiGraphics g, int mouseX, int mouseY, long balance) {
        List<String> denominations = ModItems.billIds();
        int viewH = contentBottom - contentTop;
        int hovered = inList(mouseX, mouseY)
                ? hoveredIndex(mouseY, shopScroll, SHOP_ROW_H, cashRowCount()) : -1;
        int w = rowWidth();

        g.enableScissor(listLeft, contentTop, listRight, contentBottom);

        // Deposit-all row.
        int y = contentTop - shopScroll;
        long carried = carriedCash();
        if (y + SHOP_ROW_H >= contentTop && y <= contentBottom) {
            if (hovered == 0) {
                g.fill(listLeft, y, listLeft + w, y + SHOP_ROW_H, MoneyUI.ROW_HOVER);
            }
            g.drawString(this.font, "Deposit all bills", listLeft + 8, y + 3,
                    carried > 0 ? MoneyUI.TEXT : MoneyUI.TEXT_FAINT, false);
            g.drawString(this.font, carried > 0 ? "cash on hand" : "no bills carried",
                    listLeft + 8, y + 13, MoneyUI.TEXT_FAINT, false);
            String value = MoneyUI.money(carried);
            g.drawString(this.font, value, listLeft + w - PAD - this.font.width(value), y + 8,
                    carried > 0 ? MoneyUI.GREEN : MoneyUI.TEXT_FAINT, false);
        }

        for (int i = 0; i < denominations.size(); i++) {
            int rowY = contentTop - shopScroll + (i + 1) * SHOP_ROW_H;
            if (rowY + SHOP_ROW_H < contentTop || rowY > contentBottom) {
                continue;
            }
            String id = denominations.get(i);
            long faceValue = ModItems.billValue(id);
            boolean affordable = balance >= faceValue;

            if (hovered == i + 1) {
                g.fill(listLeft, rowY, listLeft + w, rowY + SHOP_ROW_H, MoneyUI.ROW_HOVER);
            }
            g.renderItem(new ItemStack(ModItems.BILLS.get(id).get()), listLeft + 6, rowY + 4);

            String label = MoneyUI.money(faceValue) + " Bill";
            g.drawString(this.font, label, listLeft + 30, rowY + 3,
                    affordable ? MoneyUI.TEXT : MoneyUI.TEXT_FAINT, false);

            int held = heldBills(id);
            g.drawString(this.font, held > 0 ? "carrying " + held : "", listLeft + 30, rowY + 13,
                    MoneyUI.TEXT_FAINT, false);

            String action = affordable ? "Withdraw" : "Too expensive";
            g.drawString(this.font, action, listLeft + w - PAD - this.font.width(action), rowY + 8,
                    affordable ? MoneyUI.GOLD : MoneyUI.TEXT_FAINT, false);
        }
        g.disableScissor();

        MoneyUI.scrollbar(g, listRight - 5, contentTop, viewH, cashRowCount() * SHOP_ROW_H, viewH, shopScroll);
    }

    private void withdraw(String denomination, int quantity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand("withdraw " + denomination + " " + quantity);
        }
    }

    private void depositAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand("depositall");
        }
    }

    private void buy(ShopEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand("buy " + entry.id());
        }
    }

    // ==================== input ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Map.Entry<Tab, int[]> tab : topTabBounds.entrySet()) {
                int[] b = tab.getValue();
                if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                    if (activeTab != tab.getKey()) {
                        activeTab = tab.getKey();
                        // Shop and Cash share this scroll offset.
                        shopScroll = 0;
                        // Quests and Shop search different things, so the query
                        // does not carry across - a stale filter on the tab you
                        // just opened reads as "the tab is empty".
                        sortedCache = null;
                        layout();
                        buildSearchBox();
                    }
                    click();
                    return true;
                }
            }
            for (Map.Entry<ShopMode, int[]> mode : modeBounds.entrySet()) {
                int[] b = mode.getValue();
                if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                    if (shopMode != mode.getKey()) {
                        shopMode = mode.getKey();
                        shopScroll = 0;
                    }
                    click();
                    return true;
                }
            }
            if (gridToggleBounds != null
                    && mouseX >= gridToggleBounds[0] && mouseX < gridToggleBounds[0] + gridToggleBounds[2]
                    && mouseY >= gridToggleBounds[1] && mouseY < gridToggleBounds[1] + gridToggleBounds[3]) {
                shopGrid = !shopGrid;
                // The two layouts measure scroll differently, so a carried-over
                // offset would land somewhere arbitrary.
                shopScroll = 0;
                click();
                return true;
            }

            if (activeTab == Tab.QUESTS) {
                if (inSidebar(mouseX, mouseY)) {
                    List<QuestLine> lines = List.copyOf(lines());
                    int index = hoveredIndex((int) mouseY, sidebarScroll, LINE_ROW_H, lines.size());
                    if (index >= 0) {
                        // Picking a chapter means "show me this chapter", so it
                        // drops any active search - otherwise the click looks
                        // like it did nothing, because the cross-chapter results
                        // would still be filling the list.
                        boolean cleared = clearSearch();
                        if (cleared || !lines.get(index).id().equals(selectedLine)) {
                            selectedLine = lines.get(index).id();
                            selectedQuest = null;
                            scroll = 0;
                            sortedCache = null;
                            click();
                        }
                    }
                    return true;
                }
                if (inList(mouseX, mouseY)) {
                    int index = hoveredIndex((int) mouseY, scroll, QUEST_ROW_H, quests().size());
                    if (index >= 0) {
                        selectedQuest = quests().get(index).id();
                        click();
                    }
                    return true;
                }
            } else if (activeTab == Tab.GUIDE) {
                if (inSidebar(mouseX, mouseY)) {
                    int index = hoveredIndex((int) mouseY, guideSidebarScroll, LINE_ROW_H, GuideTopic.ALL.size());
                    if (index >= 0 && index != guideTopic) {
                        guideTopic = index;
                        guideScroll = 0;
                        click();
                    }
                    return true;
                }
            } else if (activeTab == Tab.CASH) {
                if (inList(mouseX, mouseY)) {
                    int index = hoveredIndex((int) mouseY, shopScroll, SHOP_ROW_H, cashRowCount());
                    if (index == 0) {
                        if (carriedCash() > 0) {
                            depositAll();
                            click();
                        }
                    } else if (index > 0) {
                        String id = ModItems.billIds().get(index - 1);
                        long faceValue = ModItems.billValue(id);
                        long balance = balance();
                        if (balance >= faceValue) {
                            // Click withdraws one; shift-click takes as many
                            // as the balance covers, capped at a stack.
                            int max = (int) Math.min(64, balance / faceValue);
                            withdraw(id, hasShiftDown() ? Math.max(1, max) : 1);
                            click();
                        }
                    }
                    return true;
                }
            } else {
                if (inSidebar(mouseX, mouseY)) {
                    List<ShopCategory> categories = shopCategories();
                    int index = hoveredIndex((int) mouseY, shopSidebarScroll, LINE_ROW_H, categories.size());
                    if (index >= 0 && !categories.get(index).id().equals(selectedCategory)) {
                        selectedCategory = categories.get(index).id();
                        shopScroll = 0;
                        click();
                    }
                    return true;
                }
                if (inList(mouseX, mouseY)) {
                    int index = hoveredShopIndex((int) mouseX, (int) mouseY);
                    if (index >= 0) {
                        ShopEntry entry = entries().get(index);
                        if (shopMode == ShopMode.BUY) {
                            buy(entry);
                            click();
                        } else {
                            // Click sells one; shift-click dumps everything
                            // you hold (at the saturating per-unit price).
                            int held = heldCount(entry);
                            if (held > 0) {
                                sell(entry, hasShiftDown() ? held : 1);
                                click();
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewH = contentBottom - contentTop;
        if (activeTab == Tab.QUESTS) {
            if (inList(mouseX, mouseY)) {
                scroll = clamp(scroll - (int) (scrollY * QUEST_ROW_H), quests().size() * QUEST_ROW_H - viewH);
                return true;
            }
            if (inSidebar(mouseX, mouseY)) {
                sidebarScroll = clamp(sidebarScroll - (int) (scrollY * LINE_ROW_H),
                        lines().size() * LINE_ROW_H - viewH);
                return true;
            }
        } else if (activeTab == Tab.GUIDE) {
            if (inList(mouseX, mouseY)) {
                guideScroll = clamp(guideScroll - (int) (scrollY * GUIDE_LINE_H * 2),
                        guideContentHeight() - viewH);
                return true;
            }
            if (inSidebar(mouseX, mouseY)) {
                guideSidebarScroll = clamp(guideSidebarScroll - (int) (scrollY * LINE_ROW_H),
                        GuideTopic.ALL.size() * LINE_ROW_H - viewH);
                return true;
            }
        } else if (activeTab == Tab.CASH) {
            if (inList(mouseX, mouseY)) {
                shopScroll = clamp(shopScroll - (int) (scrollY * SHOP_ROW_H),
                        cashRowCount() * SHOP_ROW_H - viewH);
                return true;
            }
        } else {
            if (inList(mouseX, mouseY)) {
                int step = shopGrid ? GRID_CELL_H + GRID_GAP : SHOP_ROW_H;
                shopScroll = clamp(shopScroll - (int) (scrollY * step), shopContentHeight() - viewH);
                return true;
            }
            if (inSidebar(mouseX, mouseY)) {
                shopSidebarScroll = clamp(shopSidebarScroll - (int) (scrollY * LINE_ROW_H),
                        shopCategories().size() * LINE_ROW_H - viewH);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Deliberately empty - see render() above.
    }
}
