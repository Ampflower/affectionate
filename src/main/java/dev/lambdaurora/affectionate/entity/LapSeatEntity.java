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

package dev.lambdaurora.affectionate.entity;

import dev.lambdaurora.affectionate.Affectionate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathConstants;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Represents a placeholder entity to make another entity seat on the laps of a player.
 *
 * @author LambdAurora
 * @version 1.0.0
 * @since 1.0.0
 */
public class LapSeatEntity extends Entity {
	private static final TrackedData<Integer> OWNER = DataTracker.registerData(LapSeatEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private LivingEntity trackedOwner;

	public LapSeatEntity(EntityType<?> type, World world) {
		super(type, world);

		this.noClip = true;
	}

	public void setTrackedOwner(LivingEntity trackedOwner) {
		this.dataTracker.set(OWNER, trackedOwner == null ? 0 : trackedOwner.getId());
		updateTrackedPosition(Entity::setPosition);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		if (OWNER.equals(data)) {
			int ownerId = this.dataTracker.get(OWNER);
			var owner = this.getWorld().getEntityById(ownerId);

			if (owner instanceof LivingEntity player) {
				this.trackedOwner = player;
				this.startRiding(this.trackedOwner, true);
			} else {
				this.trackedOwner = null;
			}
		}
	}

	public void updateTrackedPosition(Entity.PositionUpdater positionUpdater) {
		if (this.trackedOwner == null) return;

		var relativePos = new Vec3d(0.d, .7d, .55d);
		Vec3d transformedPos = relativePos
				.multiply(this.trackedOwner.getScaleFactor())
				.rotateY(Affectionate.getEffectiveBodyYaw(this.trackedOwner) * -MathConstants.RADIANS_PER_DEGREE);

		var newPos = this.trackedOwner.getPos().add(transformedPos);
		positionUpdater.accept(this, newPos.getX(), newPos.getY(), newPos.getZ());

		this.setYaw(this.getVisualYaw());
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		this.setTrackedOwner(null);
	}

	@Override
	protected void initDataTracker(final DataTracker.Builder builder) {
		builder.add(OWNER, 0);
	}

	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		var vec = super.updatePassengerForDismount(passenger);

		if (this.getWorld().getBlockState(this.getBlockPos().up()).isAir()) {
			return new Vec3d(vec.x, this.getBlockY() + 1, vec.z);
		}

		return vec;
	}

	@Override
	public boolean hasNoGravity() {
		return true;
	}

	/* Serialization */

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}

	/* Ticking */

	@Override
	public void tick() {
		super.tick();

		if (!this.getWorld().isClient()) {
			if (!this.hasPassengers() || this.trackedOwner == null || this.trackedOwner.isRemoved() || !this.trackedOwner.hasVehicle()) {
				this.discard();
			}
		}
	}
}
