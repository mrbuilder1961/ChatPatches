package obro1961.chatpatches.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Util;

import java.util.Locale;
import java.util.Optional;

/**
 * Eases compatibility, both between different Minecraft versions and also between
 * the legacy {@link ChatFormatting} and modern {@link TextColor}. Also provides
 * methods to access functionality from one class using the other.
 */
@SuppressWarnings("unused")
public class Colors {
	/* {@link TextColor#NAMED_COLORS} maps name strings to {@link TextColor} objects. */
	/**
	 * Maps name strings to their respective {@link ChatFormatting} objects.
	 *
	 * @apiNote This map contains non-color (modifier) objects.
	 */
	public static final Object2ObjectMap<String, ChatFormatting> NAME_TO_LEGACY = Util.make(new Object2ObjectArrayMap<>(22), me -> {
		for(ChatFormatting legacy : ChatFormatting.values()) {
			me.put(legacy.name().toLowerCase(Locale.ROOT), legacy);
		}
	});
	/**
	 * Maps int color values to their respective {@link TextColor} objects.
	 */
	public static final Int2ObjectMap<TextColor> VALUE_TO_TEXTCOLOR = Util.make(new Int2ObjectArrayMap<>(16), me -> {
		for(TextColor color : TextColor.NAMED_COLORS.values()) {
			me.put(color.getValue(), color);
		}
	});
	/**
	 * Maps int color values to their respective {@link ChatFormatting} objects.
	 */
	public static final Int2ObjectMap<ChatFormatting> VALUE_TO_LEGACY = Util.make(new Int2ObjectArrayMap<>(16), me -> {
		for(ChatFormatting legacy : ChatFormatting.values()) {
			TextColor textColor = TextColor.fromLegacyFormat(legacy);
			if(textColor != null) {
				me.put(textColor.getValue(), legacy);
			}
		}
	});

	//~ if >26.1.2 'ChatFormatting' -> 'TextColor' {
	//~ if >26.1.2 'getColor' -> 'getValue' {
	public static final int BLACK = TextColor.BLACK.getValue();
	public static final int DARK_BLUE = TextColor.DARK_BLUE.getValue();
	public static final int DARK_GREEN = TextColor.DARK_GREEN.getValue();
	public static final int DARK_AQUA = TextColor.DARK_AQUA.getValue();
	public static final int DARK_RED = TextColor.DARK_RED.getValue();
	public static final int DARK_PURPLE = TextColor.DARK_PURPLE.getValue();
	public static final int GOLD = TextColor.GOLD.getValue();
	public static final int GRAY = TextColor.GRAY.getValue();
	public static final int DARK_GRAY = TextColor.DARK_GRAY.getValue();
	public static final int BLUE = TextColor.BLUE.getValue();
	public static final int GREEN = TextColor.GREEN.getValue();
	public static final int AQUA = TextColor.AQUA.getValue();
	public static final int RED = TextColor.RED.getValue();
	public static final int LIGHT_PURPLE = TextColor.LIGHT_PURPLE.getValue();
	public static final int YELLOW = TextColor.YELLOW.getValue();
	public static final int WHITE = TextColor.WHITE.getValue();
	//~}
	//~}

	/**
	 * Ensures the returned {@code TextColor} contains a proper name field if
	 * the color is a named color. This is necessary because
	 * {@link TextColor#fromRgb(int)} can be passed a named color (ex.
	 * {@code #55FF55}) but will not attach its name (ex. {@code green}). If
	 * the parameter does not correspond to a named color, it simply returns
	 * the parameter.
	 */
	public static TextColor simplify(TextColor color) {
		if(color == null) {
			return null;
		}

		TextColor named = VALUE_TO_TEXTCOLOR.get(color.getValue());
		// if `color` is in the map then `color` must be named! but if color.serialize() doesn't
		// start with `CUSTOM_COLOR_PREFIX` then it already knows it's named. otherwise return the
		// named TextColor object
		return named != null && color.serialize().startsWith("#") ? named : color;
	}

	/**
	 * If {@code color} is a named color, returns its formatting code.
	 * Otherwise, returns an empty optional.
	 */
	public static Optional<String> getCode(TextColor color) {
		ChatFormatting chat = VALUE_TO_LEGACY.get(color.getValue());
		return chat != null ? Optional.of("" + chat.code) : Optional.empty();
	}
}