package obro1961.chatpatches;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.Flags;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.function.Supplier;

public class ChatPatches implements ClientModInitializer {
	public static final String MOD_ID = "chatpatches";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Supplier<String> TIME_FORMATTER = () -> new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());

	public static boolean usingUnsafeCodec = false;
	public static Config config = Config.create();
	/** Contains the sender and timestamp data of the last received chat message. */
	public static ChatUtils.MessageData msgData = ChatUtils.NIL_MSG_DATA;

	private static String lastWorld = "";

	/**
	 * Creates a new Identifier using the ChatPatches mod ID.
	 */
	public static Identifier id(String path) {
		// unfortunately this method in 1.20.6 is method_43902
		// but in 1.21 it's method_60655, making it incompatible
		// this is grinding my gears bc the code is identicalS
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		/*
		* ChatLog saving events, run if config.chatlog is true:
		* 	DISCONNECT - Always saves EXCEPT on (most?) server crashes
		* 	SCREEN_AFTER_INIT - Saves if the save interval is enabled AND if the screen is paused (GameMenuScreen)
		* 	END_WORLD_TICK - Ticks the save counter and saves if it's enabled and the internal counter equals zero
		*/


		// according to my testing, this event works as needed when the game disconnects and on crashes if the game is functional at that point
		// testing details (server=hypixel): normal disconnects work on both world and server, manual F3+C crash works on world but NOT server
		// honestly I don't care if it fails on crashes, its fixable a) through the save interval or b) by fixing the crash's source
		ClientPlayConnectionEvents.DISCONNECT.register((network, client) -> ChatLog.serialize());
		ScreenEvents.AFTER_INIT.register((client, screen, sW, sH) -> ChatLog.saveIfPaused(screen));
		ClientTickEvents.END_WORLD_TICK.register(world -> ChatLog.tickSaveCounter());

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
			// only replaces messages that would render instantly to save performance on large chat logs
			// no longer ran once per game, but once per join (#151) (note: if you open the chat and then close it, the messages will reappear)
			int t = client.inGameHud.getTicks();
			chatHud.chatpatches$getVisibleMessages().replaceAll(ln -> (t - ln.addedTime() < 200) ? new ChatHudLine.Visible(0, ln.content(), ln.indicator(), ln.endOfEntry()) : ln);
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

        	return client.isIntegratedServerRunning() // this check prevents NPEs for both if branches
            		? "C_" + client.getServer().getSaveProperties().getLevelName()
            		: client.getCurrentServerEntry() instanceof ServerInfo entry
               			? "S_" + (entry.name.isBlank() ? entry.address : entry.name) // if the name is blank, uses the address instead
                		: "";
	}

	/**
	 * Logs an error-level message telling the user to report
	 * the given error. The class and method of the caller is
	 * provided from a {@link StackWalker}.
	 *
	 * <p>Outputs the following message:
	 * <pre>
	 * [$class.$method] /!\ Please report this error on GitHub or Discord with the full log file attached! /!\
	 * (error)
	 * </pre>
	 */
	public static <X extends Throwable> void logReportMsg(@NotNull X error) {
		StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
		String clazz = walker.getCallerClass().getSimpleName();
		String method = walker.walk(frames -> frames.skip(1).findFirst().orElseThrow().getMethodName());

		if(method.isBlank())
			method = error.getStackTrace()[0].getMethodName();

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
			throw new NullPointerException("[ChatPatches#jsonOps] Expected existing ClientWorld");
	}
}
