package com.viperr.concert.registry;

import com.viperr.concert.ViperrMod;
import com.viperr.concert.entity.PerformerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ViperrMod.MOD_ID);

    public static final RegistryObject<EntityType<PerformerEntity>> KAI_ANGEL = ENTITY_TYPES.register(
            "kai_angel",
            () -> EntityType.Builder.of(PerformerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(64)
                    .build("kai_angel")
    );

    public static final RegistryObject<EntityType<PerformerEntity>> NINE_MICE = ENTITY_TYPES.register(
            "nine_mice",
            () -> EntityType.Builder.of(PerformerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(64)
                    .build("nine_mice")
    );
}
