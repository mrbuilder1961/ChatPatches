package obro1961.mixins;

import static net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND;

import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.MessageType;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import obro1961.WMCH;
import obro1961.config.Config;

@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class)
public class ChatHudMixin {
    @Shadow @Final MinecraftClient client;
    @Shadow @Final List<ChatHudLine<Text>> messages;
    @Shadow @Final List<ChatHudLine<OrderedText>> visibleMessages;
    @Shadow @Final Deque<Text> messageQueue;

    /** Prevents the game from clearing chat history;  */
    @Inject(method = "clear(Z)V", at = @At("HEAD"), cancellable = true)
    public void clear(boolean clearHistory, CallbackInfo ci) { ci.cancel(); }

    /** Increases the amount of maximum chat messages, configurable */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int moreMsgs(int oldMax) {
        final long gb = 1073741824; final long mem = Runtime.getRuntime().freeMemory();
        return WMCH.config.maxMsgs != Config.MAXMSGS ? WMCH.config.maxMsgs : ( mem<gb*9 ? 2048 : mem<gb*13 ? 4096 : mem<gb*17 ? 8192 : mem>gb*23 ? 16384 : 1024 );
    };

    /** Adds a configurable timestamp. Also, this poor method doing all this just to modify one variable lol */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"))
    public Text modifyMessage(Text m) {
        Date now = new Date(); Config c = WMCH.config;
        boolean isBoundary = m.asString()==c.boundaryStr && c.boundary;

        // looks through accessible players to get sufficent player data, if needed
        PlayerEntity[] last = {client.player};
        Consumer<? super PlayerEntity> find = player -> {
            if(!client.options.advancedItemTooltips) return;
            last[0] = player.getEntityName()==WMCH.lastMDat[0] ? player : last[0];
        };
        try { client.world.getPlayers().forEach(find); } catch(Exception e) {} // searches in the client
        try { client.world.getServer().getPlayerManager().getPlayerList().forEach(find); } catch(Exception e) {} // then in the server

        WMCH.log.info(m.getString());
        return (Text)new LiteralText("").setStyle(m.getStyle())
            .append( isBoundary||!c.time // only adds the timestamp if enabled or not a boundary message
                ? Text.of("")
                : (Text)new LiteralText(c.getTimeF(now))
                    .setStyle( m.getStyle()
                        .withFormatting(c.timeFormatting)
                        //* adds the hover and click events if enabled; however they don't show up if re-enabled later. might want to fix that
                        .withHoverEvent( c.hover ? new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(c.getHoverF(now))) : null )
                        .withClickEvent( c.hover ? new ClickEvent(SUGGEST_COMMAND, c.getHoverF(now) ) : null )
                    )
                )
            .append(isBoundary||!c.time ? Text.of("") : new LiteralText(" ").setStyle(m.getStyle())) // adds a space if the timestamp was enabled
            .append( c.nameStr!=Config.NAMESTR && !isBoundary && WMCH.lastMDat[1] == MessageType.CHAT
                // reconstructs the message, with proper styling
                ? (LiteralText)(new LiteralText("").setStyle(m.getStyle())
                    .append(new LiteralText(c.getNameF()).setStyle( m.getStyle()
                        .withHoverEvent(client.options.advancedItemTooltips? new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityContent(EntityType.PLAYER, last[0].getUuid(), last[0].getDisplayName())): null) // recreating this HoverEvent was a real pain in the ass, i tell ya!
                        .withClickEvent(client.options.advancedItemTooltips? new ClickEvent(SUGGEST_COMMAND, "/tell "+WMCH.lastMDat[0]): null)
                )).append( (Text)new LiteralText(m.getString().replace(WMCH.lastMDat[0]+" "," ")).setStyle(m.getStyle()) ))
                : m
            );
        //* HH:mm:ss [$]:PLAYERNAME
        //? Text.Serializer.fromJson(Text.Serializer.toJson(m).replaceFirst( "(,\"text\":\")\\w{3,16}(\"},)", String.format("$0%s$1",c.getNameF()) ))
    }


    public int shiftPos() {
        return 0;
    }
}