package com.docvin.alet;

import com.docvin.alet.client.AletScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Only the two client conversion tools are ported; LT handles server-side importing. */
@Mod(value="alet",dist=Dist.CLIENT)
public final class ALET {
    public static final Logger LOG=LoggerFactory.getLogger("ALET Images & Text");
    private final KeyMapping open=new KeyMapping("alet.key.open",GLFW.GLFW_KEY_F9,"alet.key.category");
    private boolean pending;
    public ALET(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent event) -> event.register(open));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
            Commands.literal("alet").executes(context -> { pending=true; return 1; })));
        NeoForge.EVENT_BUS.addListener(this::tick);
        if (Boolean.getBoolean("alet.smoke")) com.docvin.alet.tools.ClientSmokeChecks.install();
    }
    private void tick(ClientTickEvent.Post event) {
        var minecraft=Minecraft.getInstance();
        if (minecraft.player!=null && (open.consumeClick() || pending)) {
            pending=false; minecraft.setScreen(new AletScreen());
        }
    }
}
