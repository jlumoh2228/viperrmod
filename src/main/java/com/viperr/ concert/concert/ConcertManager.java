package com.viperr.concert.concert;

import com.viperr.concert.entity.PerformerEntity;
import com.viperr.concert.registry.ModEntities;
import com.viperr.concert.registry.ModSounds;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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
    private static final List<UUID> ambientEntities = new ArrayList<>();
    private static final List<BlockPos> stageBlocks = new ArrayList<>();
    private static final List<BlockPos> screenLights = new ArrayList<>();
    private static final List<BlockPos> beaconGlassPositions = new ArrayList<>();
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
        stageCenter = playerPos.offset((int) Math.round(horizontalLook.x * 26), 0, (int) Math.round(horizontalLook.z * 26));

        buildClosedArena(activeLevel, stageCenter);
        spawnPerformers(activeLevel, stageCenter, player.position());
        spawnMoshpitCrowd(activeLevel, stageCenter);
        spawnAmbientCreatures(activeLevel, stageCenter);

        activeLevel.setDayTime(18000L);
        activeLevel.playSound(null, stageCenter, ModSounds.CONCERT_TRACK.get(), SoundSource.RECORDS, 6.0F, 1.0F);

        for (ServerPlayer p : activeLevel.players()) {
            p.sendSystemMessage(Component.literal("§d§l[VIPERR] §fHEAVY METAL 2 FULL ARENA EXPERIENCE!"));
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

            discardList(spawnedEntities);
            discardList(crowdEntities);
            discardList(ambientEntities);

            // Удаляем блоки в обратном порядке (чтобы сначала убирались факелы/ковры)
            for (int i = stageBlocks.size() - 1; i >= 0; i--) {
                activeLevel.setBlockAndUpdate(stageBlocks.get(i), Blocks.AIR.defaultBlockState());
            }
        }

        spawnedEntities.clear();
        crowdEntities.clear();
        ambientEntities.clear();
        stageBlocks.clear();
        screenLights.clear();
        beaconGlassPositions.clear();
        active = false;
        ticksElapsed = 0;
        activeLevel = null;
        stageCenter = null;
    }

    private static void discardList(List<UUID> list) {
        if (activeLevel == null) return;
        for (UUID uuid : list) {
            Entity entity = activeLevel.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
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

        // 1. Анимация и движение Kai Angel и 9mice по сцене и подиуму
        Vec3 moshCenter = new Vec3(stageCenter.getX() + 0.5, stageCenter.getY() + 1.0, stageCenter.getZ() - 18.0);
        for (int i = 0; i < spawnedEntities.size(); i++) {
            Entity entity = activeLevel.getEntity(spawnedEntities.get(i));
            if (entity instanceof PerformerEntity performer) {
                performer.setHypeLevel(hypeLevel);

                // На дропах артисты выходят вперед к подиуму и двигаются вдоль сцены
                double sideSign = (i == 0) ? -1.0 : 1.0;
                double waveX = Math.sin(ticksElapsed * 0.04 + i * Math.PI) * 2.5;
                double advanceZ = (hypeLevel >= 2) ? (Math.sin(ticksElapsed * 0.03) * 4.5 - 4.0) : 0.0;

                double targetX = stageCenter.getX() + (sideSign * 4.5) + waveX;
                double targetZ = stageCenter.getZ() - 3.0 + advanceZ;

                Vec3 currentPos = performer.position();
                Vec3 moveVec = new Vec3(targetX - currentPos.x, 0, targetZ - currentPos.z);
                if (moveVec.length() > 0.3) {
                    Vec3 step = moveVec.normalize().scale(0.14);
                    performer.setDeltaMovement(step.x, performer.getDeltaMovement().y, step.z);
                    performer.hurtMarked = true;
                }

                if (hypeLevel >= 2 && ticksElapsed % 22 == 0 && performer.onGround()) {
                    performer.setDeltaMovement(performer.getDeltaMovement().add(0, 0.44, 0));
                    performer.hurtMarked = true;
                }

                performer.lookAt(EntityAnchorArgument.Anchor.EYES, moshCenter);
            }
        }

        // 2. Прыжки толпы и безумный мошпит в центре фан-зоны на дропах
        int jumpInterval = hypeLevel == 3 ? 10 : (hypeLevel == 2 ? 14 : 18);
        boolean doJump = (ticksElapsed % jumpInterval == 0);

        for (UUID uuid : crowdEntities) {
            Entity fan = activeLevel.getEntity(uuid);
            if (fan == null) continue;

            double vy = fan.getDeltaMovement().y;
            if (doJump && fan.onGround()) {
                vy = 0.40;
            }

            // На 2 и 3 фазе толпа в центре крутится в слэме вокруг точки (stageCenter.Z - 18)
            if (hypeLevel >= 2) {
                double dx = fan.getX() - moshCenter.x;
                double dz = fan.getZ() - moshCenter.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 1.5 && dist < 11.0) {
                    // Тангенциальное движение по кругу + легкое стягивание к центру слэма
                    double tangentX = -dz / dist;
                    double tangentZ = dx / dist;
                    double speed = hypeLevel == 3 ? 0.24 : 0.16;
                    fan.setDeltaMovement(tangentX * speed, vy, tangentZ * speed);
                    fan.hurtMarked = true;
                } else if (doJump) {
                    fan.setDeltaMovement(fan.getDeltaMovement().x, vy, fan.getDeltaMovement().z);
                    fan.hurtMarked = true;
                }
            } else if (doJump) {
                fan.setDeltaMovement(fan.getDeltaMovement().x, vy, fan.getDeltaMovement().z);
                fan.hurtMarked = true;
            }
        }

        // 3. Стробоскоп на экране и смена цвета лазерных лучей маяков
        if (ticksElapsed % 10 == 0) {
            updateStrobeAndLasers(activeLevel, hypeLevel);
        }

        // 4. Плотная пиротехника, дым и искры каждые 3 тика
        if (ticksElapsed % 3 == 0) {
            spawnArenaParticles(activeLevel, stageCenter, hypeLevel);
        }

        // 5. Салюты и фейерверки
        int fireworkInterval = hypeLevel >= 2 ? 20 : 38;
        if (ticksElapsed % fireworkInterval == 0) {
            launchStageFirework(activeLevel, stageCenter, hypeLevel);
        }

        // 6. Молнии с 1-й секунды по периметру арены и башням
        int lightningInterval = hypeLevel >= 2 ? 16 : 26;
        if (ticksElapsed % lightningInterval == 0) {
            int offsetX = RANDOM.nextBoolean() ? (14 + RANDOM.nextInt(4)) : -(14 + RANDOM.nextInt(4));
            int offsetZ = -22 + RANDOM.nextInt(34);
            BlockPos strikePos = stageCenter.offset(offsetX, 1, offsetZ);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(activeLevel);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(strikePos));
                bolt.setVisualOnly(true);
                activeLevel.addFreshEntity(bolt);
            }
        }

        // 7. Эффекты для зрителей (игроков на арене): ночное зрение, скорость, строб-вспышка и левитация
        if (ticksElapsed % 40 == 0) {
            for (ServerPlayer p : activeLevel.players()) {
                if (p.blockPosition().closerThan(stageCenter, 50.0D)) {
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
                    if (hypeLevel >= 2) {
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, false, false));
                    }
                }
            }
        }

        // Короткая стробоскоп-вспышка на дропе (45-я секунда) и левитация на кульминации (2:15)
        if (ticksElapsed == 900 || ticksElapsed == 2700) {
            for (ServerPlayer p : activeLevel.players()) {
                if (p.blockPosition().closerThan(stageCenter, 50.0D)) {
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 12, 0, false, false));
                    if (ticksElapsed == 2700) {
                        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 120, 0, false, false));
                        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 220, 0, false, false));
                    }
                }
            }
        }
    }

    private static void setStageBlock(ServerLevel level, BlockPos pos, Block block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        stageBlocks.add(pos);
    }

    private static boolean isLetterR(int x, int y, int startX) {
        int lx = x - startX;
        if (lx < 0 || lx > 5 || y < 2 || y > 9) {
            return false;
        }
        if (lx == 0) return true;
        if ((y == 9 || y == 6) && lx <= 4) return true;
        if (lx == 5 && (y == 7 || y == 8)) return true;
        return (y == 5 && lx == 2) || (y == 4 && lx == 3) || (y == 3 && lx == 4) || (y == 2 && lx == 5);
    }

    private static void buildClosedArena(ServerLevel level, BlockPos center) {
        stageBlocks.clear();
        screenLights.clear();
        beaconGlassPositions.clear();

        // 1. Пол всей арены (сцена + фан-зона: X от -18 до 18, Z от -28 до 12)
        for (int x = -18; x <= 18; x++) {
            for (int z = -28; z <= 12; z++) {
                BlockPos pos = center.offset(x, 0, z);
                boolean isStageArea = (z >= -12 && Math.abs(x) <= 16);
                if (isStageArea) {
                    boolean isNeon = Math.abs(x) == 16 || Math.abs(z) == 12 || Math.abs(x) == 8 || z == 0;
                    setStageBlock(level, pos, isNeon ? Blocks.SEA_LANTERN : ((x + z) % 2 == 0 ? Blocks.BLACK_CONCRETE : Blocks.POLISHED_BLACKSTONE));
                } else {
                    // Пол фан-зоны с неоновой сеткой мошпита
                    boolean isPitRing = (Math.abs(x) == 10 && z >= -24 && z <= -13) || (z == -18 && Math.abs(x) <= 10);
                    setStageBlock(level, pos, isPitRing ? Blocks.SEA_LANTERN : Blocks.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }

        // 2. Трёхъярусный подиум на сцене (ступеньки вверх к заднему экрану)
        for (int x = -12; x <= 12; x++) {
            for (int z = 2; z <= 10; z++) {
                setStageBlock(level, center.offset(x, 1, z), (z == 2 || Math.abs(x) == 12) ? Blocks.SEA_LANTERN : Blocks.OBSIDIAN);
            }
        }
        for (int x = -9; x <= 9; x++) {
            for (int z = 6; z <= 10; z++) {
                setStageBlock(level, center.offset(x, 2, z), (z == 6 || Math.abs(x) == 9) ? Blocks.CRYING_OBSIDIAN : Blocks.BLACK_CONCRETE);
            }
        }

        // 3. Выдвижной подиум-язык в фан-зону
        for (int x = -3; x <= 3; x++) {
            for (int z = -22; z < -12; z++) {
                boolean isEdge = Math.abs(x) == 3 || z == -22 || x == 0;
                setStageBlock(level, center.offset(x, 0, z), isEdge ? Blocks.SEA_LANTERN : Blocks.OBSIDIAN);
            }
        }

        // 4. Гигантский задний экран со светящимся логотипом "RR"
        for (int x = -16; x <= 16; x++) {
            for (int y = 1; y <= 11; y++) {
                BlockPos backPos = center.offset(x, y, 12);
                boolean isFrame = Math.abs(x) == 16 || y == 11;
                boolean isLogoRR = isLetterR(x, y, -7) || isLetterR(x, y, 2);

                if (isFrame) {
                    setStageBlock(level, backPos, Blocks.CRYING_OBSIDIAN);
                } else if (isLogoRR) {
                    setStageBlock(level, backPos, Blocks.SEA_LANTERN);
                } else {
                    setStageBlock(level, backPos, Blocks.TINTED_GLASS);
                    if (Math.abs(x) % 3 == 0 && y % 2 == 0) {
                        screenLights.add(backPos);
                    }
                }
            }
        }

        // 5. Многоуровневые трибуны и стены вокруг всей арены (слева, справа и сзади фан-зоны)
        for (int z = -28; z <= 12; z++) {
            for (int tier = 0; tier < 4; tier++) {
                int height = tier + 1;
                for (int y = 1; y <= height; y++) {
                    Block block = (y == height && tier % 2 == 1) ? Blocks.CRYING_OBSIDIAN : Blocks.POLISHED_BLACKSTONE;
                    setStageBlock(level, center.offset(-18 - tier, y, z), block);
                    setStageBlock(level, center.offset(18 + tier, y, z), block);
                }
                // Синие факелы душ на верхнем ярусе трибун
                if (tier == 3 && z % 3 == 0) {
                    setStageBlock(level, center.offset(-21, 5, z), Blocks.SOUL_TORCH);
                    setStageBlock(level, center.offset(21, 5, z), Blocks.SOUL_TORCH);
                }
            }
        }
        // Задняя стена трибун за спиной у толпы (Z = -28)
        for (int x = -18; x <= 18; x++) {
            for (int y = 1; y <= 4; y++) {
                setStageBlock(level, center.offset(x, y, -28), (y == 4 && x % 4 == 0) ? Blocks.SEA_LANTERN : Blocks.OBSIDIAN);
            }
        }

        // 6. Шесть рабочих лазерных маяков (бьют лучами прямо в ночное небо!)
        int[][] beaconCoords = {{-14, 9}, {14, 9}, {-14, -10}, {14, -10}, {-16, -24}, {16, -24}};
        for (int[] bc : beaconCoords) {
            int bx = bc[0];
            int bz = bc[1];
            // Пирамида 3x3 из железных блоков под полом (Y = -1)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    setStageBlock(level, center.offset(bx + dx, -1, bz + dz), Blocks.IRON_BLOCK);
                }
            }
            // Сам маяк на уровне пола (Y = 0) и цветное стекло сверху (Y = 1)
            setStageBlock(level, center.offset(bx, 0, bz), Blocks.BEACON);
            BlockPos glassPos = center.offset(bx, 1, bz);
            setStageBlock(level, glassPos, Blocks.PURPLE_STAINED_GLASS);
            beaconGlassPositions.add(glassPos);
        }

        // 7. Четыре световые башни + верхняя ферма прожекторов + сабвуферы + DJ пульт
        int[] towerX = {-16, 16};
        int[] towerZ = {-12, 12};
        for (int tx : towerX) {
            for (int tz : towerZ) {
                for (int y = 1; y <= 15; y++) {
                    setStageBlock(level, center.offset(tx, y, tz), (y % 2 == 0 ? Blocks.SEA_LANTERN : Blocks.OBSIDIAN));
                }
            }
        }
        for (int x = -16; x <= 16; x++) {
            setStageBlock(level, center.offset(x, 14, -12), (x % 2 == 0 ? Blocks.SEA_LANTERN : Blocks.IRON_BARS));
            setStageBlock(level, center.offset(x, 14, 0), (x % 3 == 0 ? Blocks.SEA_LANTERN : Blocks.IRON_BARS));
        }

        int[] speakerSide = {-11, 11};
        for (int sx : speakerSide) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 2; y <= 7; y++) {
                        boolean isDynamic = dz == -1 && (y == 3 || y == 6) && dx == 0;
                        setStageBlock(level, center.offset(sx + dx, y, 7 + dz), isDynamic ? Blocks.NOTE_BLOCK : Blocks.POLISHED_BLACKSTONE_BRICKS);
                    }
                }
            }
        }

        for (int x = -2; x <= 2; x++) {
            setStageBlock(level, center.offset(x, 1, -8), Blocks.CHISELED_POLISHED_BLACKSTONE);
            setStageBlock(level, center.offset(x, 2, -8), (x == 0 ? Blocks.SHROOMLIGHT : Blocks.BLACK_CARPET));
        }

        int[] pyroX = {-12, -8, -4, 4, 8, 12};
        for (int px : pyroX) {
            setStageBlock(level, center.offset(px, 1, -11), Blocks.MAGMA_BLOCK);
        }
    }

    private static void updateStrobeAndLasers(ServerLevel level, int hypeLevel) {
        Block[] screenPalette = hypeLevel == 3
                ? new Block[]{Blocks.SEA_LANTERN, Blocks.AMETHYST_BLOCK, Blocks.SHROOMLIGHT, Blocks.TINTED_GLASS}
                : new Block[]{Blocks.SEA_LANTERN, Blocks.TINTED_GLASS, Blocks.GLOWSTONE};

        for (BlockPos pos : screenLights) {
            if (RANDOM.nextInt(3) == 0) {
                level.setBlockAndUpdate(pos, screenPalette[RANDOM.nextInt(screenPalette.length)].defaultBlockState());
            }
        }

        // Меняем цвет лазерных лучей маяков в такт
        Block[] laserColors = {
                Blocks.PURPLE_STAINED_GLASS,
                Blocks.MAGENTA_STAINED_GLASS,
                Blocks.WHITE_STAINED_GLASS,
                Blocks.LIGHT_BLUE_STAINED_GLASS
        };
                Block chosenColor = laserColors[RANDOM.nextInt(laserColors.length)];
        for (BlockPos glassPos : beaconGlassPositions) {
            level.setBlockAndUpdate(glassPos, chosenColor.defaultBlockState());
        }
    }

    private static void launchStageFirework(ServerLevel level, BlockPos center, int hypeLevel) {
        int[] sideX = {-15, 15, -3, 3, -10, 10};
        int fx = sideX[RANDOM.nextInt(sideX.length)];
        int fz = RANDOM.nextBoolean() ? 10 : -11;

        ItemStack fireworkStack = new ItemStack(Items.FIREWORK_ROCKET);
        CompoundTag fireworksTag = fireworkStack.getOrCreateTagElement("Fireworks");
        fireworksTag.putByte("Flight", (byte) (1 + RANDOM.nextInt(2)));

        ListTag explosions = new ListTag();
        CompoundTag explosion = new CompoundTag();
        explosion.putByte("Type", (byte) (hypeLevel == 3 ? 1 : 0));
        explosion.putBoolean("Flicker", true);
        explosion.putBoolean("Trail", true);

        int[] colors = {0xB026FF, 0xFF007F, 0xFFFFFF, 0x00F0FF};
        explosion.putIntArray("Colors", new int[]{colors[RANDOM.nextInt(colors.length)], colors[RANDOM.nextInt(colors.length)]});
        explosions.add(explosion);
        fireworksTag.put("Explosions", explosions);

        FireworkRocketEntity rocket = new FireworkRocketEntity(
                level,
                center.getX() + fx + 0.5,
                center.getY() + 2.0,
                center.getZ() + fz + 0.5,
                fireworkStack
        );
        level.addFreshEntity(rocket);
    }

    private static void spawnPerformers(ServerLevel level, BlockPos center, Vec3 viewerPos) {
        spawnedEntities.clear();

        PerformerEntity kai = ModEntities.KAI_ANGEL.get().create(level);
        if (kai != null) {
            kai.moveTo(center.getX() - 4.5, center.getY() + 1.0, center.getZ() - 3.0, 180.0F, 0.0F);
            kai.lookAt(EntityAnchorArgument.Anchor.EYES, viewerPos);
            level.addFreshEntity(kai);
            spawnedEntities.add(kai.getUUID());
        }

        PerformerEntity mice = ModEntities.NINE_MICE.get().create(level);
        if (mice != null) {
            mice.moveTo(center.getX() + 5.5, center.getY() + 1.0, center.getZ() - 3.0, 180.0F, 0.0F);
            mice.lookAt(EntityAnchorArgument.Anchor.EYES, viewerPos);
            level.addFreshEntity(mice);
            spawnedEntities.add(mice.getUUID());
        }
    }

    private static void spawnMoshpitCrowd(ServerLevel level, BlockPos center) {
        crowdEntities.clear();
        VillagerType[] styles = {
                VillagerType.PLAINS, VillagerType.SNOW, VillagerType.TAIGA,
                VillagerType.SAVANNA, VillagerType.DESERT, VillagerType.JUNGLE
        };

        Vec3 stageTarget = new Vec3(center.getX() + 0.5, center.getY() + 3.5, center.getZ());

        int spawned = 0;
        for (int attempt = 0; attempt < 130 && spawned < 55; attempt++) {
            int offsetX = -15 + RANDOM.nextInt(31);
            if (Math.abs(offsetX) <= 4) {
                offsetX += (offsetX >= 0 ? 5 : -5);
            }
            int offsetZ = -26 + RANDOM.nextInt(13);

            BlockPos fanPos = center.offset(offsetX, 0, offsetZ);
            Mob fan;
            if (RANDOM.nextInt(4) == 0) {
                WitherSkeleton skel = EntityType.WITHER_SKELETON.create(level);
                if (skel != null) {
                    skel.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    skel.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
                }
                fan = skel;
            } else {
                Villager villager = EntityType.VILLAGER.create(level);
                if (villager != null) {
                    villager.setVillagerData(villager.getVillagerData().setType(styles[RANDOM.nextInt(styles.length)]));
                }
                fan = villager;
            }

            if (fan != null) {
                fan.setNoAi(true);
                fan.setInvulnerable(true);
                fan.moveTo(fanPos.getX() + 0.5, fanPos.getY() + 1.0, fanPos.getZ() + 0.5, 0.0F, 0.0F);
                fan.lookAt(EntityAnchorArgument.Anchor.EYES, stageTarget);
                level.addFreshEntity(fan);
                crowdEntities.add(fan.getUUID());
                spawned++;
            }
        }
    }

    private static void spawnAmbientCreatures(ServerLevel level, BlockPos center) {
        ambientEntities.clear();
        for (int i = 0; i < 8; i++) {
            GlowSquid squid = EntityType.GLOW_SQUID.create(level);
            if (squid != null) {
                squid.setNoGravity(true);
                squid.setNoAi(true);
                squid.setInvulnerable(true);
                double sx = center.getX() - 12 + RANDOM.nextInt(25);
                double sy = center.getY() + 10 + RANDOM.nextInt(5);
                double sz = center.getZ() - 15 + RANDOM.nextInt(22);
                squid.moveTo(sx, sy, sz, RANDOM.nextFloat() * 360.0F, 0.0F);
                level.addFreshEntity(squid);
                ambientEntities.add(squid.getUUID());
            }
        }
    }

    private static void spawnArenaParticles(ServerLevel level, BlockPos center, int hypeLevel) {
        ParticleOptions mainParticle = hypeLevel == 3 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        int count = hypeLevel * 10;

        int[] pyroX = {-12, -8, -4, 4, 8, 12};
        for (int px : pyroX) {
            level.sendParticles(mainParticle, center.getX() + px + 0.5, center.getY() + 2.1, center.getZ() - 10.5, count, 0.2, 1.4, 0.2, 0.07);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.getX() + px + 0.5, center.getY() + 1.5, center.getZ() - 10.5, 3, 0.6, 0.3, 0.6, 0.02);
        }

        level.sendParticles(ParticleTypes.DRAGON_BREATH, center.getX() - 3.5, center.getY() + 1.2, center.getZ() - 17.0, 12, 0.3, 0.5, 4.0, 0.03);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, center.getX() + 3.5, center.getY() + 1.2, center.getZ() - 17.0, 12, 0.3, 0.5, 4.0, 0.03);

        level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 13.0, center.getZ() - 4.0, 26, 14.0, 1.5, 11.0, 0.04);
        if (hypeLevel >= 2) {
            level.sendParticles(ParticleTypes.FIREWORK, center.getX() + 0.5, center.getY() + 10.0, center.getZ() - 6.0, 30, 13.0, 3.0, 10.0, 0.08);
        }
    }
}
