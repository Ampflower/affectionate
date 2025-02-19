package dev.lambdaurora.affectionate.network;

import dev.lambdaurora.affectionate.Affectionate;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.payload.CustomPayload;

/**
 * @author Ampflower
 * @since 1.2
 **/
public record SendHeartsPayload(int entityId) implements CustomPayload {
    public static final PacketCodec<PacketByteBuf, SendHeartsPayload> CODEC =
            CustomPayload.create(SendHeartsPayload::write, SendHeartsPayload::new);
    public static final Id<SendHeartsPayload> ID = new CustomPayload.Id<>(Affectionate.SEND_HEARTS_PACKET);

    private SendHeartsPayload(PacketByteBuf buf) {
        this(buf.readVarInt());
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(entityId());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
