package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Every reward can describe itself in plain language AND supply an icon.
 * That is deliberate: a reward must never render as the bare word "command"
 * the way some quest mods do - the player should always read what they
 * actually get. Command rewards therefore carry a required human label.
 *
 * Common-side only (no client imports) so this is safe on a dedicated server.
 *
 * Serialised from JSON via a type-dispatched codec:
 *   {"type": "money",   "amount": 500}
 *   {"type": "item",    "item": "minecraft:diamond", "count": 3}
 *   {"type": "command", "command": "...", "label": "10 min Resistance II",
 *    "icon": "minecraft:potion"}
 */
public sealed interface QuestReward {

    Codec<QuestReward> CODEC = Codec.STRING.dispatch(
            "type", QuestReward::typeId, QuestReward::mapCodecFor);

    private static MapCodec<? extends QuestReward> mapCodecFor(String type) {
        return switch (type) {
            case "money" -> Money.MAP_CODEC;
            case "item" -> Loot.MAP_CODEC;
            case "command" -> Command.MAP_CODEC;
            default -> throw new IllegalArgumentException(
                    "Unknown quest reward type '" + type + "' (expected money, item or command)");
        };
    }

    /** Discriminator written to / read from the "type" field. */
    String typeId();

    /** Player-facing text, e.g. "$500", "3x Diamond", "Creative Flight". */
    String describe();

    ItemStack icon();

    void grant(ServerPlayer player);

    /** Pays into the player's balance. */
    record Money(long amount) implements QuestReward {

        static final MapCodec<Money> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.fieldOf("amount").forGetter(Money::amount)
        ).apply(instance, Money::new));

        @Override
        public String typeId() {
            return "money";
        }

        @Override
        public String describe() {
            return "$" + CurrencyItem.formatValue(amount);
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(ModItems.billFor(amount));
        }

        @Override
        public void grant(ServerPlayer player) {
            BalanceHelper.addBalance(player, amount);
        }
    }

    /** Hands over an item stack, dropping it if the inventory is full. */
    record Loot(Item item, int count) implements QuestReward {

        static final MapCodec<Loot> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Loot::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Loot::count)
        ).apply(instance, Loot::new));

        @Override
        public String typeId() {
            return "item";
        }

        @Override
        public String describe() {
            String name = item.getName(item.getDefaultInstance()).getString();
            return count > 1 ? count + "x " + name : name;
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(item, Math.max(1, count));
        }

        @Override
        public void grant(ServerPlayer player) {
            ItemStack stack = new ItemStack(item, Math.max(1, count));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /**
     * Runs a server command. {@code label} is mandatory and is what the
     * player sees - never the command string itself.
     */
    record Command(String command, String label, Item iconItem) implements QuestReward {

        static final MapCodec<Command> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("command").forGetter(Command::command),
                Codec.STRING.fieldOf("label").forGetter(Command::label),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(Command::iconItem)
        ).apply(instance, Command::new));

        @Override
        public String typeId() {
            return "command";
        }

        @Override
        public String describe() {
            return label;
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(iconItem);
        }

        @Override
        public void grant(ServerPlayer player) {
            MinecraftServer server = player.level().getServer();
            if (server == null) {
                return;
            }
            CommandSourceStack source = server.createCommandSourceStack()
                    .withEntity(player)
                    .withPosition(player.position())
                    .withLevel(player.level())
                    .withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER)
                    .withSuppressedOutput();
            server.getCommands().performPrefixedCommand(
                    source, command.replace("@p", player.getScoreboardName()));
        }
    }
}
