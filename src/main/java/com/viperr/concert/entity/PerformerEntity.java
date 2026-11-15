package com.viperr.concert.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class PerformerEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> HYPE_LEVEL =
            SynchedEntityData.defineId(PerformerEntity.class, EntityDataSerializers.INT);

    public PerformerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HYPE_LEVEL, 1);
    }

    public void setHypeLevel(int level) {
        this.entityData.set(HYPE_LEVEL, level);
    }

    public int getHypeLevel() {
        return this.entityData.get(HYPE_LEVEL);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            int hype = getHypeLevel();
            if (hype >= 2 && this.onGround() && this.tickCount % (hype == 3 ? 12 : 20) == 0) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, 0.42, 0));
                this.hasImpulse = true;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
