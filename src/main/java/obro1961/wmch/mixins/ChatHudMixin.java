package obro1961.wmch.mixins;

import static net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND;
import static net.minecraft.text.HoverEvent.Action.SHOW_ENTITY;
import static net.minecraft.text.HoverEvent.Action.SHOW_TEXT;

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
import net.minecraft.text.Style;
import net.minecraft.text.Text;
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

        // looks through accessible players to get sufficient player data, if needed
        PlayerEntity[] lastPlayer = {client.player};
        Consumer<? super PlayerEntity> find = player -> {
            if(!client.options.advancedItemTooltips) return;
            lastPlayer[0] = player.getEntityName()==WMCH.lastMsgData[0] ? player : lastPlayer[0];
        };
        try { client.world.getPlayers().forEach(find); } catch(Exception e) {} // searches in the client
        try { client.world.getServer().getPlayerManager().getPlayerList().forEach(find); } catch(Exception e) {} // then in the server

        Text formatted = new LiteralText("")
        .append(!isBoundary && c.time // only adds the timestamp if it's enabled and if it isn't a boundary line
            ? (Text)new LiteralText( c.getTimeF(now)+" " )
                .setStyle(Style.EMPTY
                    .withFormatting(c.timeFormatting)
                    //* adds the hover and click events if enabled; however they don't show up if re-enabled later. might want to fix that
                    .withHoverEvent( !c.hover ? null : new HoverEvent(SHOW_TEXT, Text.of(c.getHoverF(now))) )
                    .withClickEvent( new ClickEvent(SUGGEST_COMMAND, c.getHoverF(now) ) )
                    .withColor(c.timeColor)
                )
            : Text.of("") //? null
            )
        .append( c.nameStr!=Config.NAMESTR && !isBoundary && WMCH.lastMsgData[1]==MessageType.CHAT && !m.getString().startsWith("[Debug]")
            // reconstructs the message, with proper styling
            ? (LiteralText)(new LiteralText("").setStyle(m.getStyle())
                // recreating this player hover event was annoying af
                .append(new LiteralText( c.getNameF() ).setStyle( m.getStyle()
                    .withHoverEvent( new HoverEvent(SHOW_ENTITY, new HoverEvent.EntityContent(EntityType.PLAYER, lastPlayer[0].getUuid(), lastPlayer[0].getDisplayName())) )
                    .withClickEvent( new ClickEvent(SUGGEST_COMMAND, "/tell "+WMCH.lastMsgData[0]) )
            )).append( (Text)new LiteralText(m.getString().replace( (String)WMCH.lastMsgData[0], "")).setStyle(m.getStyle()) ))
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