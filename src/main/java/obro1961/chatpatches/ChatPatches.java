package obro1961.chatpatches;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.util.Identifier;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatPatches implements ClientModInitializer {
	public static final String MOD_ID = "chatpatches";
	public static final Logger LOGGER = LoggerFactory.getLogger("Chat Patches");

	public static Config config = Config.create();

	public static Identifier id(String path) {
		// unfortunately this method in 1.20.6 is method_43902
		// but in 1.21 it's method_60655, making it incompatible
		// this is grinding my gears bc the code is identical ToT
		return Identifier.of(MOD_ID, path);
	}


	@Override
	public void onInitializeClient() {
		//stonecutter: arch api - put all these callbacks in another class for splitting by loader.. unless i can just use arch callbacks

		// -- chat log saving events --
		// according to my testing, this event works as needed when the game disconnects and on crashes if the game is functional at that point
		// testing details (server=hypixel): normal disconnects work on both world and server, manual F3+C crash works on world but NOT server
		// honestly I don't care if it fails on crashes, its fixable A) through the save interval or B) by fixing the crash's source
		ClientPlayConnectionEvents.DISCONNECT.register((network, client) -> ChatLog.serialize());
		ScreenEvents.AFTER_INIT.register((client, screen, sW, sH) -> ChatLog.saveIfPaused(screen));
		ClientTickEvents.END_WORLD_TICK.register(world -> ChatLog.tickSaveCounter());

		// -- chat log loader and boundary sender --
		ClientPlayConnectionEvents.JOIN.register((network, packetSender, client) -> {
			ChatLog.load();

			config.sendBoundaryLine();

			// sets all messages (restored and boundary line) to an addedTime of -200 to prevent instant rendering (#42)
			// only replaces messages that would render instantly to save performance on large chat logs
			int t = client.inGameHud.getTicks();
			((ChatHudAccessor) client.inGameHud.getChatHud()).chatpatches$getVisibleMessages()
				.replaceAll(ln -> (t - ln.addedTime() < 200) ? new ChatHudLine.Visible(-200, ln.content(), ln.indicator(), ln.endOfEntry()) : ln);
		});

		LOGGER.info("[ChatPatches()] Finished setup!");
	}


	/**
	 * Logs an error-level message telling the user to report
	 * the given error. The class and method of the caller is
	 * provided from a {@link StackWalker}.
	 *
	 * <p>Outputs the following message:
	 * <pre>
	 * [$class.$method] /!\ Please report this error on GitHub or Discord with the full log file attached! /!\
	 * $error
	 * </pre>
	 */
	public static void logReportMsg(@NotNull Throwable error) {
		StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
		String clazz = walker.getCallerClass().getSimpleName();
		String method = walker.walk(frames -> frames.skip(1).findFirst().orElseThrow().getMethodName());
//prepub: test out walking back further if the error is thrown from a lambda/anon class, and keep going but then add a (lambda$12/33) or wtv it says to the class
		if(method.isBlank())
			method = error.getStackTrace()[0].getMethodName();

		//noinspection StringConcatenationArgumentToLogCall: not an issue...
		LOGGER.error("[" + clazz + "." + method + "] /!\\ Please report this error on GitHub or Discord with the full log file attached! /!\\", error);
	}

	/**
	 * Executes {@link #logReportMsg(Throwable)}
	 * and throws the passed error.
	 */
	public static <X extends Throwable> X logAndThrowReportMsg(@NotNull X error) throws X {
		logReportMsg(error);
		throw error;
	}

	// 1.20.5+ needs the RegistryOps instance
	public static DynamicOps<JsonElement> jsonOps() {
		return JsonOps.INSTANCE;
	}
}