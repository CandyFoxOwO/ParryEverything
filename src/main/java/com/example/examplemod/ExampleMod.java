package com.example.examplemod;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

@Mod("parryeverything")
public class ExampleMod {
    public static final String MODID = "parryeverything";

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );
    private static int nextId = 0;

    private static void registerPackets() {
        CHANNEL.messageBuilder(ParryMsg.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ParryMsg::encode)
                .decoder(ParryMsg::decode)
                .consumer(ParryMsg::handle)
                .add();
    }

    public static class ParryMsg {
        public ParryMsg() {}
        public static void encode(ParryMsg msg, PacketBuffer buf) {}
        public static ParryMsg decode(PacketBuffer buf) { return new ParryMsg(); }
        public static void handle(ParryMsg msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                PARRYUUUUU.parry();
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final RegistryObject<SoundEvent> EXAMPLE_SOUND =
            SOUND_EVENTS.register("parry", () -> new SoundEvent(new ResourceLocation(MODID, "parry")));

    public ExampleMod() {
        SOUND_EVENTS.register(FMLJavaModLoadingContext.get().getModEventBus());
        registerPackets();

        MinecraftForge.EVENT_BUS.register(this); 
    }

    public static class PARRYUUUUU {
        @OnlyIn(Dist.CLIENT)
        public static void parry() {
            ClientParryFx.start();
        }
    }

    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientParryFx {
        private static long overlayUntilMs = 0L;
        private static float lockYaw, lockPitch;

        @OnlyIn(Dist.CLIENT)
        private static class HalfSecondPauseScreen extends Screen {
            private final long closeAtMs;
            protected HalfSecondPauseScreen() {
                super(new StringTextComponent(""));
                this.closeAtMs = Util.getMillis() + 500L;
            }
            @Override public boolean isPauseScreen() { return true; }
            @Override
            public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
                Minecraft mc = Minecraft.getInstance();
                MainWindow win = mc.getWindow();
                AbstractGui.fill(ms, 0, 0, win.getGuiScaledWidth(), win.getGuiScaledHeight(), 0x80FFFFFF);
                if (Util.getMillis() >= closeAtMs) onClose(); else super.render(ms, mouseX, mouseY, partialTicks);
            }
            @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
        }

        public static void start() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            lockYaw = mc.player.yRot; lockPitch = mc.player.xRot;

            mc.player.setDeltaMovement(0, 0, 0);
            mc.player.input.forwardImpulse = 0;
            mc.player.input.leftImpulse = 0;
            mc.player.input.jumping = false;
            mc.player.input.shiftKeyDown = false;
            mc.player.setSprinting(false);
            mc.player.yRot = lockYaw; mc.player.xRot = lockPitch;
            mc.setScreen(new HalfSecondPauseScreen());

            overlayUntilMs = Util.getMillis() + 500L;

            mc.getSoundManager().play(SimpleSound.forUI(EXAMPLE_SOUND.get(), 1.0F));
        }

        @SubscribeEvent
        public static void onOverlay(RenderGameOverlayEvent.Post e) {
            if (overlayUntilMs <= 0) return;
            if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

            long now = Util.getMillis();
            if (now > overlayUntilMs) { overlayUntilMs = 0; return; }

            MatrixStack ms = e.getMatrixStack();
            Minecraft mc = Minecraft.getInstance();
            MainWindow win = mc.getWindow();
            AbstractGui.fill(ms, 0, 0, win.getGuiScaledWidth(), win.getGuiScaledHeight(), 0x40FFFFFF);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class AttackEvents {
        @SubscribeEvent
        public static void onPlayerAttack(AttackEntityEvent event) {
            PlayerEntity player = event.getPlayer();
            Entity target = event.getTarget();
            if (player instanceof ServerPlayerEntity) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player), new ParryMsg());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class BlockEvents {
        @SubscribeEvent
        public static void onBreak(BlockEvent.BreakEvent e) {
            PlayerEntity player = e.getPlayer();
            if (player instanceof ServerPlayerEntity) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player), new ParryMsg());
            }
        }
        @SubscribeEvent
        public static void onPlace(BlockEvent.EntityPlaceEvent e) {
            if (e.getEntity() instanceof ServerPlayerEntity) {
                ServerPlayerEntity player = (ServerPlayerEntity) e.getEntity();
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ParryMsg());
            }
        }
    }
}
