package com.viperr.concert;

import com.viperr.concert.client.PerformerRenderer;
import com.viperr.concert.command.ConcertCommand;
import com.viperr.concert.concert.ConcertManager;
import com.viperr.concert.entity.PerformerEntity;
import com.viperr.concert.registry.ModEntities;
import com.viperr.concert.registry.ModSounds;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ViperrMod.MOD_ID)
public class ViperrMod {
    public static final String MOD_ID = "viperr";

    public ViperrMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);

        modEventBus.addListener(this::registerAttributes);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.KAI_ANGEL.get(), PerformerEntity.createAttributes().build());
        event.put(ModEntities.NINE_MICE.get(), PerformerEntity.createAttributes().build());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ConcertCommand.register(event.getDispatcher());
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ConcertManager.tick(event.getServer());
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.KAI_ANGEL.get(),
                    ctx -> new PerformerRenderer(ctx, "textures/entity/kai_angel.png"));
            event.registerEntityRenderer(ModEntities.NINE_MICE.get(),
                    ctx -> new PerformerRenderer(ctx, "textures/entity/nine_mice.png"));
        }
    }
}
