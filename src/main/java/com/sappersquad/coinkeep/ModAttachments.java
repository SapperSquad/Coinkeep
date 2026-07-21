package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Coinkeep.MODID);

    // .sync() mirrors the balance to the owning client so the Task/Shop
    // screens can display it (and grey out what you can't afford) without
    // any custom packet of our own - NeoForge handles the sync itself.
    public static final Supplier<AttachmentType<Long>> BALANCE = ATTACHMENT_TYPES.register("balance",
            () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .copyOnDeath()
                    .build());

    // Marks that the starter book has been handed out. Serialized and
    // copyOnDeath so it survives death, relog and dimension change - without
    // it a player would be handed a new book on every single login.
    public static final Supplier<AttachmentType<Boolean>> GOT_STARTER_BOOK =
            ATTACHMENT_TYPES.register("got_starter_book",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL)
                            .copyOnDeath()
                            .build());

    // Synced so the sell screen can price live as demand saturates, without
    // a custom packet. Deliberately NOT copyOnDeath: dying resets your
    // buyers' appetite, which is a small mercy rather than a punishment.
    public static final Supplier<AttachmentType<MarketData>> MARKET = ATTACHMENT_TYPES.register("market",
            () -> AttachmentType.builder(MarketData::empty)
                    .serialize(MarketData.CODEC)
                    .sync(MarketData.STREAM_CODEC)
                    .build());

    // Synced so the quest book can render live progress, lock states and
    // per-chapter completion counts without a menu or any custom packet.
    public static final Supplier<AttachmentType<QuestProgressData>> QUEST_PROGRESS = ATTACHMENT_TYPES.register("quest_progress",
            () -> AttachmentType.builder(QuestProgressData::empty)
                    .serialize(QuestProgressData.CODEC)
                    .sync(QuestProgressData.STREAM_CODEC)
                    .copyOnDeath()
                    .build());
}
