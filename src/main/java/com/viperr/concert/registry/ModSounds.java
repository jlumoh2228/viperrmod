package com.viperr.concert.registry;

import com.viperr.concert.ViperrMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ViperrMod.MOD_ID);

    public static final RegistryObject<SoundEvent> CONCERT_TRACK = SOUND_EVENTS.register(
            "heavy_metal_2_concert",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ViperrMod.MOD_ID, "heavy_metal_2_concert"))
    );
}
