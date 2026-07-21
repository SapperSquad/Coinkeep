package com.sappersquad.coinkeep;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * In-game documentation, shown on the book's Guide tab.
 *
 * Deliberately a tab rather than a written-book item or a Patchouli
 * dependency: the book is already the single hub for everything, so help
 * lives where the player already is, works offline of any other mod, and
 * can never be lost or left in a chest.
 *
 * A line starting with '#' renders as a heading; a blank line is a spacer.
 */
public record GuideTopic(String id, String title, Item icon, List<String> lines) {

    public static final List<GuideTopic> ALL = List.of(
            new GuideTopic("start", "Getting Started", Items.PAPER, List.of(
                    "#What this is",
                    "Coinkeep gives the world an economy. You earn money by completing quests and by selling what you gather, then spend it in the shop.",
                    "",
                    "#Opening the book",
                    "Press J at any time, or right-click the Coinkeep book. You do not need to be anywhere in particular - it works everywhere.",
                    "",
                    "#The tabs",
                    "Quests - what to do and what it pays.",
                    "Shop - buy things, or sell what you have.",
                    "Cash - turn your balance into banknotes and back.",
                    "Guide - this page.",
                    "",
                    "#Your balance",
                    "Shown in gold at the top right of every tab. Type /balance in chat for the same number.",
                    "Your banked balance is safe when you die. Banknotes you are carrying are NOT - see the Cash page."
            )),

            new GuideTopic("quests", "Quests", Items.WRITABLE_BOOK, List.of(
                    "#Chapters",
                    "The left column lists chapters - Mining, Combat, Farming and so on, plus one for each vanilla advancement tab. The number under each is how many quests you have finished there.",
                    "",
                    "#Doing quests",
                    "You do not need to accept anything. Just play - mine, fight, craft, earn advancements - and progress is tracked automatically. Rewards pay out the moment a quest completes.",
                    "",
                    "#Tiers",
                    "Most gathering quests repeat forever. Clearing a tier raises the bar for the next one instead of resetting you: if you have mined 40 iron, all 40 still count toward the next target.",
                    "Each tier pays more in total, but slightly less per item - so it is worth spreading out rather than grinding one block forever.",
                    "",
                    "#Reading the ladders",
                    "Click a quest to see two rows of markers at the bottom. The top row is that quest's tiers; the bottom row is every quest in the chapter, ordered by payout.",
                    "Hover any marker to see exactly what it needs and what it pays.",
                    "",
                    "#Order",
                    "Quests sort by what you can act on: in progress first, then untouched, then locked, then finished.",
                    "Locked quests are rare. When one is locked the detail panel names exactly what it is waiting on."
            )),

            new GuideTopic("market", "The Market", Items.EMERALD, List.of(
                    "#Buying",
                    "Open Shop and pick a category on the left. Prices are green if you can afford them, red if you cannot. Click a row to buy it.",
                    "",
                    "#Selling",
                    "Switch to Sell using the button in the top right of the Shop tab. Click a row to sell one, or shift-click to sell everything you are carrying of it.",
                    "",
                    "#Why prices fall",
                    "Each item has its own demand, shown as the small bar on the right of a sell row. Every sale uses some of it up, so the tenth stack of iron is worth less than the first.",
                    "Demand recovers on its own over time. Selling a variety of things always beats hammering one item.",
                    "",
                    "#Things you cannot sell",
                    "Anything enchanted or renamed is excluded. That is deliberate - it stops a valuable named tool being cashed in at the price of a plain one.",
                    "",
                    "#Buying back",
                    "Selling always pays less than buying. You can never make money by buying something and selling it straight back."
            )),

            new GuideTopic("cash", "Cash & Banknotes", Items.EMERALD_BLOCK, List.of(
                    "#Banknotes",
                    "Your balance is a number. Banknotes are real items you can hold, drop, store in a chest, and hand to other players.",
                    "",
                    "#Withdrawing",
                    "Open the Cash tab and click a denomination to withdraw one note. Shift-click takes as many as your balance covers.",
                    "",
                    "#Depositing",
                    "Right-click any note to bank it, or use Deposit all bills at the top of the Cash tab to bank everything you are carrying at once.",
                    "",
                    "#Risk",
                    "Banknotes are ordinary items: if you die carrying them, they drop. Your banked balance is normally safe.",
                    "Some servers turn on a setting where your banked money also drops when you die. If so, banking is no longer a guarantee - carry and store carefully.",
                    "",
                    "#Paying other players",
                    "Use /pay <player> <amount> to send money directly. Handing over notes works too, and is the only way to trade money without commands."
            )),

            new GuideTopic("vault", "Vaults & Robbery", Items.IRON_DOOR, List.of(
                    "#What a vault is for",
                    "Your balance is convenient but exposed. A vault is a block you place and store money in, and money inside it is never lost when you die - even on servers where your balance is.",
                    "The trade is that it stays where you put it. To spend from it you have to come back.",
                    "",
                    "#Building one",
                    "Four iron blocks in the corners, four gold blocks on the edges, and a diamond block in the middle. Right-click it to deposit or withdraw in fixed amounts.",
                    "They are deliberately expensive, because spreading your money across several is the main defence against being robbed - and that defence should cost you something.",
                    "",
                    "#Only you can open it",
                    "Whoever places a vault owns it. Nobody else can open it, and nobody else can break it either. Server operators can open any vault, so a lost one can be recovered.",
                    "",
                    "#Moving a vault",
                    "Break your own vault and you get the whole thing back as an item, money still inside - the tooltip shows how much. Place it again anywhere and the money is still there, still yours.",
                    "Be careful carrying one. It is an ordinary item in your inventory, so if you die it drops, and whoever picks it up gets a locked vault with your savings in it.",
                    "",
                    "#Nobody can destroy your money",
                    "A thief cannot break your vault, and cannot make the money inside disappear. Their only option is to crack it open, which costs them.",
                    "",
                    "#The Vault Cracker",
                    "The only way to rob someone. Right-click another player's vault holding one and the contents spill out as banknotes.",
                    "It is consumed on use, so every robbery costs you one. You cannot see how much is inside before you commit - that is the gamble.",
                    "Cracking also breaks the claim on the vault, so once it is empty anyone can break it and keep the block.",
                    "Craft it from a netherite ingot, diamonds and redstone blocks, or buy one from the Rare shop tab.",
                    "The owner is told immediately if they are online.",
                    "",
                    "#Defending yourself",
                    "Spread your money across several vaults so no single crack takes everything. Hide them, bury them, and defend the room they are in.",
                    "Think twice about moving a loaded vault across open ground - that is the one moment all of it can be taken at once.",
                    "",
                    "#Server settings",
                    "Servers can switch cracking off entirely, which makes vaulted money completely untakeable. If robbery is disabled you will be told when you try."
            )),

            new GuideTopic("reference", "Keys & Commands", Items.COMPASS, List.of(
                    "#Keys",
                    "J - open the book on Quests",
                    "K - open the book on Shop",
                    "Both can be rebound under Options, Controls, Coinkeep.",
                    "",
                    "#Commands",
                    "/balance - show your balance",
                    "/buy <id> - buy a shop entry",
                    "/sell <id> [amount] - sell items at the current price",
                    "/pay <player> <amount> - send money to someone",
                    "/withdraw <note> [amount] - take out banknotes",
                    "/depositall - bank every note you are carrying",
                    "",
                    "#Tips",
                    "Hover anything in the shop or on a quest ladder - almost every number has a tooltip explaining it.",
                    "Rewards always say what you actually get, so you never have to guess before finishing a quest."
            ))
    );
}
