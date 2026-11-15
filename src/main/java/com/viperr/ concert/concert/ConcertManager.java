package com.viperr.concert.concert;

import com.viperr.concert.entity.PerformerEntity;
import com.viperr.concert.registry.ModEntities;
import com.viperr.concert.registry.ModSounds;
import net.minecraft.commands.arguments.EntityAnchorArgument;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ConcertManager {
    private static boolean active = false;
    private static int ticksElapsed = 0;
    private static final int TOTAL_TICKS = 3600; // 3 минуты
    private static ServerLevel activeLevel = null;
    private static BlockPos stageCenter = null;
    private static final List<UUID> spawnedEntities = new ArrayList<>();
    private static final List<UUID> crowdEntities = new ArrayList<>();
    private static final List<BlockPos> stageBlocks = new ArrayList<>();
    private static final Random RANDOM = new Random();

    public static boolean startConcert(ServerPlayer player) {
        if (active) {
            return false;
        }

        active = true;
        ticksElapsed = 0;
        activeLevel = player.serverLevel();

        // Ставим центр гигантской сцены в 22 блоках перед игроком
        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0, look.z).normalize();
        if (horizontalLook.lengthSqr() < 0.01) {
            horizontalLook = new Vec3(0, 0, 1);
        }

        BlockPos playerPos = player.blockPosition();
        stageCenter = playerPos.offset((int) Math.round(horizontalLook.x * 22), 0, (int) Math.round(horizontalLook.z * 22));

        buildArenaStage(activeLevel, stageCenter);
        spawnPerformers(activeLevel, stageCenter, player.position());
        spawnCrowd(activeLevel, stageCenter);

        activeLevel.setDayTime(18000L);
        activeLevel.playSound(null, stageCenter, ModSounds.CONCERT_TRACK.get(), SoundSource.RECORDS, 6.0F, 1.0F);

        for (ServerPlayer p : activeLevel.players()) {
            p.sendSystemMessage(Component.literal("§d§l[VIPERR] §fHEAVY METAL 2 STADIUM SHOW STARTED!"));
        }

        return true;
    }

    public static void stopConcert(MinecraftServer server) {
        if (!active && spawnedEntities.isEmpty() && crowdEntities.isEmpty() && stageBlocks.isEmpty()) {
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

            for (UUID uuid : crowdEntities) {
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
        crowdEntities.clear();
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

        // Толпа фанатов подпрыгивает в такт биту
        int jumpInterval = hypeLevel == 3 ? 12 : (hypeLevel == 2 ? 16 : 20);
        if (ticksElapsed % jumpInterval == 0) {
            for (UUID uuid : crowdEntities) {
                Entity fan = activeLevel.getEntity(uuid);
                if (fan != null && fan.onGround()) {
                    fan.setDeltaMovement(fan.getDeltaMovement().add(0, 0.38, 0));
                    fan.hurtMarked = true;
                }
            }
        }

        // Мощная пиротехника по всей ширине сцены каждые 4 тика
        if (ticksElapsed % 4 == 0) {
            spawnArenaParticles(activeLevel, stageCenter, hypeLevel);
        }

        // Молнии бьют с самой 1-й секунды (каждые 30 тиков = 1.5 сек, на дропах чаще)
        int lightningInterval = hypeLevel >= 2 ? 20 : 30;
        if (ticksElapsed % lightningInterval == 0) {
            int offsetX = RANDOM.nextBoolean() ? (12 + RANDOM.nextInt(5)) : -(12 + RANDOM.nextInt(5));
            int offsetZ = -10 + RANDOM.nextInt(22);
            BlockPos strikePos = stageCenter.offset(offsetX, 1, offsetZ);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(activeLevel);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(strikePos));
                bolt.setVisualOnly(true);
                activeLevel.addFreshEntity(bolt);
            }
        }

        // Эффект левитации на кульминации (2:15) в радиусе 45 блоков
        if (ticksElapsed == 2700) {
            for (ServerPlayer p : activeLevel.players()) {
                if (p.blockPosition().closerThan(stageCenter, 45.0D)) {
                    p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 120, 0, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 220, 0, false, false));
                }
            }
        }
    }

    private static void buildArenaStage(ServerLevel level, BlockPos center) {
        stageBlocks.clear();

        // 1. Основная платформа 32x24 блока (X от -16 до 16, Z от -12 до 12)
        for (int x = -16; x <= 16; x++) {
            for (int z = -12; z <= 12; z++) {
                BlockPos pos = center.offset(x, 0, z);
                boolean isBorder = Math.abs(x) == 16 || Math.abs(z) == 12 || Math.abs(x) == 8;
                level.setBlockAndUpdate(pos, (isBorder ? Blocks.SEA_LANTERN : Blocks.BLACK_CONCRETE).defaultBlockState());
                stageBlocks.add(pos);
            }
        }

        // 2. Выдвижной подиум (язык) в фан-зону (ширина 6, длина 10 вперед по -Z)
        for (int x = -3; x <= 3; x++) {
            for (int z = -22; z < -12; z++) {
                BlockPos pos = center.offset(x, 0, z);
                boolean isEdge = Math.abs(x) == 3 || z == -22;
                level.setBlockAndUpdate(pos, (isEdge ? Blocks.SEA_LANTERN : Blocks.POLISHED_BLACKSTONE).defaultBlockState());
                stageBlocks.add(pos);
            }
        }

        // 3. Гигантский задний экран (ширина 32, высота 10)
        for (int x = -16; x <= 16; x++) {
            for (int y = 1; y <= 10; y++) {
                BlockPos backPos = center.offset(x, y, 12);
                boolean isFrame = Math.abs(x) == 16 || y == 10 || Math.abs(x) == 8;
                level.setBlockAndUpdate(backPos, (isFrame ? Blocks.CRYING_OBSIDIAN : Blocks.TINTED_GLASS).defaultBlockState());
                stageBlocks.add(backPos);
            }
        }

        // 4. Четыре световые башни по углам сцены высотой 14 блоков
        int[] towerX = {-16, 16};
        int[] towerZ = {-12, 12};
        for (int tx : towerX) {
            for (int tz : towerZ) {
                for (int y = 1; y <= 14; y++) {
                    BlockPos tPos = center.offset(tx, y, tz);
                    level.setBlockAndUpdate(tPos, (y % 3 == 0 ? Blocks.SEA_LANTERN : Blocks.OBSIDIAN).defaultBlockState());
                    stageBlocks.add(tPos);
                }
            }
        }
    }

    private static void spawnPerformers(ServerLevel level, BlockPos center, Vec3 viewerPos) {
        spawnedEntities.clear();

        // Ставим гигантских Kai Angel и 9mice ближе к авансцене
        PerformerEntity kai = ModEntities.KAI_ANGEL.get().create(level);
        if (kai != null) {
            kai.moveTo(center.getX() - 4.5, center.getY() + 1.0, center.getZ() - 4.0, 180.0F, 0.0F);
            kai.lookAt(EntityAnchorArgument.Anchor.EYES, viewerPos);
            level.addFreshEntity(kai);
            spawnedEntities.add(kai.getUUID());
        }

        PerformerEntity mice = ModEntities.NINE_MICE.get().create(level);
        if (mice != null) {
            mice.moveTo(center.getX() + 5.5, center.getY() + 1.0, center.getZ() - 4.0, 180.0F, 0.0F);
            mice.lookAt(EntityAnchorArgument.Anchor.EYES, viewerPos);
            level.addFreshEntity(mice);
            spawnedEntities.add(mice.getUUID());
        }
    }

    private static void spawnCrowd(ServerLevel level, BlockPos center) {
        crowdEntities.clear();
        VillagerType[] styles = {
                VillagerType.PLAINS, VillagerType.SNOW, VillagerType.TAIGA,
                VillagerType.SAVANNA, VillagerType.DESERT, VillagerType.JUNGLE
        };

        Vec3 stageTarget = new Vec3(center.getX() + 0.5, center.getY() + 3.0, center.getZ());

        // Спавним 25 фанатов в фан-зоне перед сценой и по бокам от подиума
        int spawned = 0;
        for (int attempt = 0; attempt < 60 && spawned < 25; attempt++) {
            int offsetX = -14 + RANDOM.nextInt(29);
            // Пропускаем сам центральный подиум (X от -4 до 4 при Z от -22 до -12)
            if (Math.abs(offsetX) <= 4) {
                offsetX += (offsetX >= 0 ? 5 : -5);
            }
            int offsetZ = -26 + RANDOM.nextInt(13); // Перед сценой (Z от -26 до -14)

            BlockPos fanPos = center.offset(offsetX, 0, offsetZ);
            Villager fan = EntityType.VILLAGER.create(level);
            if (fan != null) {
                fan.setVillagerData(fan.getVillagerData().setType(styles[RANDOM.nextInt(styles.length)]));
                fan.setNoAi(true);
                fan.setInvulnerable(true);
                fan.moveTo(fanPos.getX() + 0.5, fanPos.getY(), fanPos.getZ() + 0.5, 0.0F, 0.0F);
                fan.lookAt(EntityAnchorArgument.Anchor.EYES, stageTarget);
                level.addFreshEntity(fan);
                crowdEntities.add(fan.getUUID());
                spawned++;
            }
        }
    }

    private static void spawnArenaParticles(ServerLevel level, BlockPos center, int hypeLevel) {
        ParticleOptions mainParticle = hypeLevel == 3 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        int count = hypeLevel * 8;

        // 6 пиротехнических пушек вдоль переднего края сцены и подиума
        int[] flameX = {-14, -8, -3, 4, 9, 15};
        for (int fx : flameX) {
            level.sendParticles(mainParticle, center.getX() + fx, center.getY() + 1.3, center.getZ() - 11.5, count, 0.25, 1.2, 0.25, 0.06);
        }

        // Световой дождь над всей ареной на дропах
        if (hypeLevel >= 2) {
            level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 8.0, center.getZ(), 25, 12.0, 2.0, 8.0, 0.03);
        }
    }
}
