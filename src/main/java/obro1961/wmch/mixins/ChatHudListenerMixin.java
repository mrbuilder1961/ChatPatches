package obro1961.wmch.mixins;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudListener;
import net.minecraft.network.MessageType;
import net.minecraft.text.Text;
import obro1961.wmch.WMCH;

@Environment(EnvType.CLIENT)
@Mixin(ChatHudListener.class)
public class ChatHudListenerMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onChatMessage", at = @At("HEAD"))
    public void saveName(MessageType t, Text m, UUID from, CallbackInfo ci) {
        //WMCH.log.info("{}|{} ({})",t.name(),from.toString(),m.getString());
        WMCH.lastMsgData[1] = t;

        if(t == MessageType.CHAT)
            // only modify player messages
            WMCH.lastMsgData[0] =
                new GameProfile(from, (String)WMCH.lastMsgData[0]).getName()!=null ? new GameProfile(from, (String)WMCH.lastMsgData[0]).getName()
                : WMCH.lastMsgData[0]!=null && WMCH.lastMsgData[0]!="" ? WMCH.lastMsgData[0]
                : client.getSession().getUsername()
            ;
    }
}