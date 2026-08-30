package com.century.civilization.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WorldSeedPayload(long seed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorldSeedPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("century", "world_seed"));
    
    public static final StreamCodec<FriendlyByteBuf, WorldSeedPayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> buf.writeLong(payload.seed()),
        buf -> new WorldSeedPayload(buf.readLong())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
