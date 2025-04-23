package obro1961.chatpatches;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.Identifier;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.Flags;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.function.Supplier;

public class ChatPatches implements ClientModInitializer {
	public static final Supplier<String> TIME_FORMATTER = () -> new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Chat Patches");
	public static final String MOD_ID = "chatpatches";

	public static Config config = Config.create();
	/** Contains the sender and timestamp data of the last received chat message. */
	public static ChatUtils.MessageData msgData = ChatUtils.NIL_MSG_DATA;

	private static String lastWorld = "";

	public static Identifier id(String path) {
		// unfortunately this method in 1.20.6 is method_43902
		// but in 1.21 it's method_60655, making it incompatible
		// this is grinding my gears bc the code is identical ToT
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		/*
		* ChatLog saving events, run if config.chatlog is true:
		* 	CLIENT_STOPPING - Always saves
		* 	SCREEN_AFTER_INIT - Saves if there is no save interval AND if the screen is the OptionsScreen (paused)
		* 	START_CLIENT_TICK - Ticks the save counter, saves if the counter is 0, resets if <0
		*/
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ChatLog.serialize());
		ScreenEvents.AFTER_INIT.register((client, screen, sW, sH) -> ChatLog.saveIfPaused(screen));
		ClientTickEvents.START_CLIENT_TICK.register(client -> ChatLog.tickSaveCounter());

		// registers the cached message file importer and boundary sender
		ClientPlayConnectionEvents.JOIN.register((network, packetSender, client) -> {
			ChatLog.load();

			ChatHudAccessor chatHud = ChatHudAccessor.from(client);
			String current = currentWorldName(client);
			// continues if the boundary line is enabled, >0 messages sent, and if the last and current worlds were servers, that they aren't the same
			if( config.boundary && !chatHud.chatpatches$getMessages().isEmpty() && (!current.startsWith("S_") || !lastWorld.startsWith("S_") || !current.equals(lastWorld)) ) {
				try {
					String levelName = (lastWorld = current).substring(2); // makes a variable to update lastWorld in a cleaner way

					Flags.BOUNDARY_LINE.raise();
					client.inGameHud.getChatHud().addMessage( config.makeBoundaryLine(levelName) );
					Flags.BOUNDARY_LINE.lower();

				} catch(Exception e) {
					LOGGER.warn("[ChatPatches.boundary] An error occurred while adding the boundary line:", e);
				}
			}

			// sets all messages (restored and boundary line) to a addedTime of 0 to prevent instant rendering (#42)
			if(ChatLog.loaded && Flags.INIT.isRaised()) {
				chatHud.chatpatches$getVisibleMessages().replaceAll(ln -> new ChatHudLine.Visible(0, ln.content(), ln.indicator(), ln.endOfEntry()));
				Flags.INIT.lower();
			}
		});

		LOGGER.info("[ChatPatches()] Finished setting up!");
	}


	/**
	 * Returns the current ClientWorld's name. For singleplayer,
	 * returns the level name. For multiplayer, returns the
	 * server entry name. Falls back on the IP if it was
	 * direct-connect. Leads with "C_" or "S_" depending
	 * on the source of the ClientWorld.
	 * @param client A non-null MinecraftClient that must be in-game.
	 * @return (C or S) + "_" + (current world name)
	 */
	public static String currentWorldName(@NotNull MinecraftClient client) {
		Objects.requireNonNull(client, "MinecraftClient must exist to access client data:");
		String entryName;

		return client.isIntegratedServerRunning()
			? "C_" + client.getServer().getSaveProperties().getLevelName()
			: (entryName = client.getCurrentServerEntry().name) == null || entryName.isBlank() // check if null/empty then use IP
			? "S_" + client.getCurrentServerEntry().address
			: "S_" + client.getCurrentServerEntry().name;
	}

	/**
	 * Logs an error-level message telling the user to report
	 * the given error. The class and method of the caller is
	 * provided from a {@link StackWalker}.
	 * <br><br>
	 * Outputs the following message:
	 * <pre>
	 * [$class.$method] /!\ Please report this error on GitHub or Discord with the full log file attached! /!\
	 * (error)
	 * </pre>
	 */
	public static void logReportMsg(Throwable error) {
		StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
		String clazz = walker.getCallerClass().getSimpleName();
		String method = walker.walk(frames -> frames.skip(1).findFirst().orElseThrow().getMethodName());
		method = method.isBlank() ? error.getStackTrace()[0].getMethodName() : method;
		LOGGER.error("[%s.%s] /!\\ Please report this error on GitHub or Discord with the full log file attached! /!\\".formatted(clazz, method), error);
	}

	/**
	 * Executes {@link #logReportMsg(Throwable)}
	 * and throws the passed error.
	 */
	public static <X extends Throwable> X logAndThrowReportMsg(@NotNull X error) throws X {
		logReportMsg(error);
		throw error;
	}

	/**
	 * Returns a {@link JsonOps#INSTANCE} wrapped by a {@link DynamicRegistryManager.Immutable}
	 * (provided by the ClientWorld) to not throw crashes when using {@link Codec}s.
	 *
	 * <p>Fixes <a href="https://github.com/mrbuilder1961/ChatPatches/issues/180">#180</a>.
	 * Thanks to
	 * <a href="https://discord.com/channels/507304429255393322/721100785936760876/1278519812628156528">arkosammy12</a>
	 * for help on the Fabric Discord!
	 *
	 * @since 1.20.5 introduced the necessity
	 * of wrapping with the {@link RegistryWrapper.WrapperLookup}
	 */
	public static RegistryOps<JsonElement> jsonOps() throws NullPointerException {
		if(MinecraftClient.getInstance().world instanceof ClientWorld world)
			return world.getRegistryManager().getOps(JsonOps.INSTANCE);
		else
			throw logAndThrowReportMsg(new NullPointerException("[ChatPatches#jsonOps] Expected existing ClientWorld"));
	}
}