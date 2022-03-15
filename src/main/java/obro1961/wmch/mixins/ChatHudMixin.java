package obro1961.wmch.mixins;

import static net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND;
import static net.minecraft.text.HoverEvent.Action.SHOW_ENTITY;
import static net.minecraft.text.HoverEvent.Action.SHOW_TEXT;

import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

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
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.MessageType;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import obro1961.wmch.WMCH;
import obro1961.wmch.config.Config;

@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class)
public class ChatHudMixin {
    @Shadow @Final MinecraftClient client;
    @Shadow @Final List<String> messageHistory;
    @Shadow @Final List<ChatHudLine<Text>> messages;
    @Shadow @Final List<ChatHudLine<OrderedText>> visibleMessages;
    @Shadow @Final Deque<Text> messageQueue;

    /** Prevents the game from clearing chat history */
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    public void clear(boolean delHist, CallbackInfo ci) {
        if(!delHist) {
            visibleMessages.clear();
            messageQueue.clear();
            messages.clear();
        }
        ci.cancel();
    }

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

        // gets message sender
        UUID uuid = (UUID) WMCH.lastMsgData[0];
        Text name = (Text) new LiteralText("SYSTEM").formatted(Formatting.GRAY);
        for(AbstractClientPlayerEntity player : client.world.getPlayers()) {
            GameProfile prof = player.getGameProfile();
            if( prof.getId().equals(WMCH.lastMsgData[0]) ) {
                uuid = prof.getId();
                name = player.getDisplayName();
            }
        }

        Text formatted = new LiteralText("")
        .append(!isBoundary && c.time // only adds the timestamp if it's enabled and if it isn't a boundary line
            ? (Text)new LiteralText( c.getTimeF(now)+" " )
                .setStyle(Style.EMPTY
                    .withFormatting(c.timeFormatting)
                    .withHoverEvent( !c.hover ? null : new HoverEvent(SHOW_TEXT, Text.of(c.getHoverF(now))) )
                    .withClickEvent( new ClickEvent(SUGGEST_COMMAND, c.getHoverF(now) ) )
                    .withColor(c.timeColor)
                )
            : Text.of("") //? null
            )
        .append( c.nameStr!=Config.NAMESTR && !isBoundary && WMCH.lastMsgData[1]==MessageType.CHAT && !m.getString().startsWith("[Debug]")
            // reconstructs the message, with proper styling
            ? (LiteralText)(new LiteralText("").setStyle(m.getStyle())
                .append(new LiteralText( c.getNameF(name.getString())+" " ).setStyle( m.getStyle()
                    .withHoverEvent( new HoverEvent(SHOW_ENTITY, new HoverEvent.EntityContent(EntityType.PLAYER, uuid, name)) )
                    .withClickEvent( new ClickEvent(SUGGEST_COMMAND, "/tell "+name.getString()) )
            )).append( (Text)new LiteralText(m.getString().replace( name.getString()+" ", "")).setStyle(m.getStyle()) ))
            : m
        );

        return (Text)formatted;
    }

    // this is all going to be worked on and finished in the next update
    /*
    @ModifyArg(method = "render", at = @At(
        value = "INVOKE",
        desc = @Desc(value="drawWithShadow", owner=TextRenderer.class, args={MatrixStack.class, OrderedText.class, float.class, float.class, int.class}, ret=int.class)
    ))
    public OrderedText applyTransformations(OrderedText toModify) {
        if(new Date(System.currentTimeMillis()).getSeconds()%60==0) {
            WMCH.log.info(Util.fromOrderedText(toModify).getString());
        }

        return toModify;
    } */
}