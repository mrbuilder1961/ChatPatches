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
    public void saveName(MessageType type, Text msg, UUID sender, CallbackInfo ci) {
        boolean player = false; // throws a NPE if the UUID can't be resolved
        try { player = client.getNetworkHandler().getPlayerListEntry(sender).getProfile().isComplete(); }
        catch(NullPointerException e) {}

        // IF sender is a player AND message type is chat THEN cache the data
        WMCH.msgSender = (player && type == MessageType.CHAT) ? client.getNetworkHandler().getPlayerListEntry(sender).getProfile() : new GameProfile(sender, "");
    }
}