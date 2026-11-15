package com.viperr.concert.concert;

import com.viperr.concert.entity.PerformerEntity;
import com.viperr.concert.registry.ModEntities;
import com.viperr.concert.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ConcertManager {
    private static boolean active = false;
    private static int ticksElapsed = 0;
    private static final int TOTAL_TICKS = 3600; // 3 минуты (180 сек * 20 тиков)
    private static ServerLevel activeLevel = null;
    private static BlockPos stageCenter = null;
    private static final List<UUID> spawnedEntities = new ArrayList<>();
    private static final List<BlockPos> stageBlocks = new ArrayList<>();
    private static final Random RANDOM = new Random();

    public static boolean startConcert(ServerPlayer player) {
        if (active) {
            return false;
        }

        active = true;
        ticksElapsed = 0;
        activeLevel = player.serverLevel();

        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0, look.z).normalize();
        if (horizontalLook.lengthSqr() < 0.01) {
            horizontalLook = new Vec3(0, 0, 1);
        }

        BlockPos playerPos = player.blockPosition();
        stageCenter = playerPos.offset((int) Math.round(horizontalLook.x * 8), 0, (int) Math.round(horizontalLook.z * 8));

        buildStage(activeLevel, stageCenter);
        spawnPerformers(activeLevel, stageCenter, player.position());

        activeLevel.setDayTime(18000L);
        activeLevel.playSound(null, stageCenter, ModSounds.CONCERT_TRACK.get(), SoundSource.RECORDS, 4.0F, 1.0F);

        for (ServerPlayer p : activeLevel.players()) {
            p.sendSystemMessage(Component.literal("§d§l[VIPERR] §fHeavy Metal 2 Live Show started!"));
        }

        return true;
    }

    public static void stopConcert(MinecraftServer server) {
        if (!active && spawnedEntities.isEmpty() && stageBlocks.isEmpty()) {
            return;
        }

        if (activeLevel != null) {
            ClientboundStopSoundPacket stopPacket = new ClientboundStopSoundPacket(
                    new ResourceLocation("viperr", "heavy_metal_2_concert"),
                    SoundSource.RECORDS
            );
            for (ServerPlayer player : activeLevel.players()) {
                player.connection.send(stopPacket);
            }

            for (UUID uuid : spawnedEntities) {
                Entity entity = activeLevel.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
            }

            for (BlockPos pos : stageBlocks) {
                activeLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }

        spawnedEntities.clear();
        stageBlocks.clear();
        active = false;
        ticksElapsed = 0;
        activeLevel = null;
        stageCenter = null;
    }

    public static void tick(MinecraftServer server) {
        if (!active || activeLevel == null || stageCenter == null) {
            return;
        }

        ticksElapsed++;

        if (ticksElapsed >= TOTAL_TICKS) {
            for (ServerPlayer p : activeLevel.players()) {
                p.sendSystemMessage(Component.literal("§d§l[VIPERR] §fShow finished!"));
            }
            stopConcert(server);
            return;
        }

        // Фазы концерта (0-45с интро, 45-135с основной дроп, 135-180с финал)
        int hypeLevel = 1;
        if (ticksElapsed >= 900 && ticksElapsed < 2700) {
            hypeLevel = 2;
        } else if (ticksElapsed >= 2700) {
            hypeLevel = 3;
        }

        for (UUID uuid : spawnedEntities) {
            Entity entity = activeLevel.getEntity(uuid);
            if (entity instanceof PerformerEntity performer) {
                performer.setHypeLevel(hypeLevel);
            }
        }

        // Пиротехника и частицы каждые 5 тиков
        if (ticksElapsed % 5 == 0) {
            spawnStageParticles(activeLevel, stageCenter, hypeLevel);
        }

        // Молнии на дропах (каждые 3 секунды во 2 и 3 фазе)
        if (hypeLevel >= 2 && ticksElapsed % 60 == 0) {
            int offsetX = RANDOM.nextBoolean() ? 6 : -6;
            int offsetZ = RANDOM.nextBoolean() ? 6 : -6;
            BlockPos strikePos = stageCenter.offset(offsetX, 1, offsetZ);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(activeLevel);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(strikePos));
                bolt.setVisualOnly(true);
                activeLevel.addFreshEntity(bolt);
            }
        }

        // Эффект левитации для игроков рядом со сценой на кульминации (2:15 - 2:25)
        if (ticksElapsed == 2700) {
            for (ServerPlayer p : activeLevel.players()) {
                if (p.blockPosition().closerThan(stageCenter, 25.0D)) {
                    p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 120, 0, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 220, 0, false, false));
                }
            }
        }
    }

    private static void buildStage(ServerLevel level, BlockPos center) {
        stageBlocks.clear();
        for (int x = -4; x <= 4; x++) {
            for (int z = -3; z <= 3; z++) {
                BlockPos pos = center.offset(x, 0, z);
                boolean isEdge = Math.abs(x) == 4 || Math.abs(z) == 3;
                level.setBlockAndUpdate(pos, (isEdge ? Blocks.SEA_LANTERN : Blocks.BLACK_CONCRETE).defaultBlockState());
                stageBlocks.add(pos);
            }
        }

        for (int x = -4; x <= 4; x++) {
            for (int y = 1; y <= 4; y++) {
                BlockPos backPos = center.offset(x, y, 3);
                boolean isFrame = Math.abs(x) == 4 || y == 4;
                level.setBlockAndUpdate(backPos, (isFrame ? Blocks.CRYING_OBSIDIAN : Blocks.TINTED_GLASS).defaultBlockState());
                stageBlocks.add(backPos);
            }
        }
    }

    private static void spawnPerformers(ServerLevel level, BlockPos center, Vec3 viewerPos) {
        spawnedEntities.clear();

        PerformerEntity kai = ModEntities.KAI_ANGEL.get().create(level);
        if (kai != null) {
            kai.moveTo(center.getX() - 1.5, center.getY() + 1.0, center.getZ() + 0.5, 180.0F, 0.0F);
            kai.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, viewerPos);
            level.addFreshEntity(kai);
            spawnedEntities.add(kai.getUUID());
        }

        PerformerEntity mice = ModEntities.NINE_MICE.get().create(level);
        if (mice != null) {
            mice.moveTo(center.getX() + 2.5, center.getY() + 1.0, center.getZ() + 0.5, 180.0F, 0.0F);
            mice.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, viewerPos);
            level.addFreshEntity(mice);
            spawnedEntities.add(mice.getUUID());
        }
    }

    private static void spawnStageParticles(ServerLevel level, BlockPos center, int hypeLevel) {
        ParticleOptions mainParticle = hypeLevel == 3 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        int count = hypeLevel * 6;

        level.sendParticles(mainParticle, center.getX() - 3.5, center.getY() + 1.2, center.getZ() - 2.5, count, 0.2, 0.8, 0.2, 0.05);
        level.sendParticles(mainParticle, center.getX() + 4.5, center.getY() + 1.2, center.getZ() - 2.5, count, 0.2, 0.8, 0.2, 0.05);

        if (hypeLevel >= 2) {
            level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 3.5, center.getZ(), 10, 3.0, 1.0, 2.0, 0.02);
        }
    }
}
