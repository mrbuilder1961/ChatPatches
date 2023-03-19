package obro1961.chatpatches.util;

import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Random utilities that I think are useful but
 * don't fit in any other util class well enough.
 */
public class RandomUtils {

	// arrays and lists

	/** Adds or sets {@code object} at {@code index} in {@code list} depending on the length of {@code list}. */
	public static <T> void setOrAdd(List<T> list, final int index, T object) {
		if(list.size() > index)
			list.set(index, object);
		else
			list.add(index, object);
	}


	// whatever category this falls under?

	/**
	 * Returns the current ClientWorld's name. For singleplayer,
	 * returns the level name. For multiplayer, returns the
	 * server entry name. Falls back to the IP if it was a direct
	 * connect. Leads with "C_" or "S_" depending on the
	 * source of the ClientWorld.
	 * @param client A non-null MinecraftClient that must be in-game.
	 * @return A 2 item list consisting of the world name and whether
	 * it was a ClientWorld or not.
	 */
	@SuppressWarnings("DataFlowIssue") // getServer and getCurrentServerEntry are not null if isIntegratedServerRunning is true
	public static String currentWorldName(@NotNull MinecraftClient client) {
		Objects.requireNonNull(client, "MinecraftClient must exist to access client data:");
		String entryName;

		return client.isIntegratedServerRunning()
			? "C_" + client.getServer().getSaveProperties().getLevelName()
			: (entryName = client.getCurrentServerEntry().name) == null || entryName.isBlank() // check if null/empty then use IP
				? "S_" + client.getCurrentServerEntry().address
				: "S_" + client.getCurrentServerEntry().name;
	}
}
