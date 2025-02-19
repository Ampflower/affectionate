/*
 * Copyright (c) 2022 LambdAurora <email@lambdaurora.dev>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.lambdaurora.affectionate;

import dev.lambdaurora.affectionate.entity.AffectionatePlayerEntity;
import dev.lambdaurora.affectionate.entity.LapSeatEntity;
import dev.lambdaurora.affectionate.network.SendHeartsPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class Affectionate implements ModInitializer {
	public static final String NAMESPACE = "affectionate";

	/* Tags */
	public static final TagKey<EntityType<?>> DISALLOWED_SEATS_FOR_LAP = TagKey.of(RegistryKeys.ENTITY_TYPE, id("disallowed_seats_for_lap"));
	public static final TagKey<EntityType<?>> ALLOWED_SEATS_FOR_LAP = TagKey.of(RegistryKeys.ENTITY_TYPE, id("allowed_seats_for_lap"));

	/* Packets */
	public static final Identifier SEND_HEARTS_PACKET = id("send_hearts");


	/* Entities */
	public static final EntityType<LapSeatEntity> LAP_SEAT_ENTITY_TYPE = Registry.register(Registries.ENTITY_TYPE, id("lap_seat"),
			EntityType.Builder.create(LapSeatEntity::new, SpawnGroup.MISC)
					.setDimensions(0.f, 0.f)
					.disableSaving()
					.disableSummon()
					.maxTrackingRange(10)
					.build()
	);

	public static final int SENDING_HEARTS_TICKS = 10;

	@Override
	public void onInitialize() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient() && entity instanceof PlayerEntity otherPlayer
					&& otherPlayer.getPassengerList().stream().noneMatch(e -> e instanceof LapSeatEntity)) {
				var vehicle = otherPlayer.getVehicle();
				if (vehicle == null || (vehicle.getType().isIn(DISALLOWED_SEATS_FOR_LAP) && !vehicle.getType().isIn(ALLOWED_SEATS_FOR_LAP))) {
					return ActionResult.PASS;
				}

				var lapSeat = LAP_SEAT_ENTITY_TYPE.create(world);
				if (lapSeat == null)
					return ActionResult.PASS;

				// Track player and set position before spawning.
				lapSeat.setTrackedOwner(otherPlayer);
				world.spawnEntity(lapSeat);
				player.startRiding(lapSeat, true);

				return ActionResult.SUCCESS;
			}

			return ActionResult.PASS;
		});

		PayloadTypeRegistry.playS2C().register(SendHeartsPayload.ID, SendHeartsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SendHeartsPayload.ID, SendHeartsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SendHeartsPayload.ID, (payload, ctx) -> {
			ctx.server().execute(() -> {
				var player = ctx.player();
				var affectionatePlayer = (AffectionatePlayerEntity) player;

				if (!affectionatePlayer.affectionate$isSendingHeart()) {
					affectionatePlayer.affectionate$startSendHeart();

					var newPayload = new SendHeartsPayload(player.getId());

					for (final var tracking : PlayerLookup.tracking(player)) {
						ServerPlayNetworking.send(tracking, newPayload);
					}
				}
			});
		});

		final var mod = FabricLoader.getInstance().getModContainer(NAMESPACE).orElseThrow();

		ResourceManagerHelper.registerBuiltinResourcePack(id("recursive_sitting"), mod,
				Text.literal("Affectionate").formatted(Formatting.LIGHT_PURPLE)
						.append(Text.literal(" - ").formatted(Formatting.GRAY))
						.append(Text.literal("Recursive Lap Sitting").formatted(Formatting.RED)),
				ResourcePackActivationType.NORMAL);
	}

	public static Identifier id(String path) {
		return Identifier.of(NAMESPACE, path);
	}

	public static float getEffectiveBodyYaw(LivingEntity entity) {
		float bodyYaw = entity.bodyYaw;
		if (entity.hasVehicle() && entity.getVehicle() instanceof LivingEntity vehicle) {
			bodyYaw = vehicle.bodyYaw;

			float delta = entity.headYaw - bodyYaw;
			float deltaDegrees = MathHelper.wrapDegrees(delta);
			if (deltaDegrees < -85.0F) {
				deltaDegrees = -85.0F;
			}

			if (deltaDegrees >= 85.0F) {
				deltaDegrees = 85.0F;
			}

			bodyYaw = entity.headYaw - deltaDegrees;
			if (deltaDegrees * deltaDegrees > 2500.0F) {
				bodyYaw += deltaDegrees * 0.2F;
			}
		}

		return bodyYaw;
	}
}
