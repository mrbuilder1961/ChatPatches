package obro1961.chatpatches.util;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.dynamic.Codecs;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A class containing various string and {@link Text} related utilities.
 */
public class TextUtils {
	/**
	 * @see Formatting#FORMATTING_CODE_PATTERN
	 */
	public static final String AMPERSAND_REGEX = "(?im)&([0-9a-fk-or])";
	/**
	 * {@link #AMPERSAND_REGEX} that explicitly does not match any
	 * formatting codes following backslashes
	 */
	public static final String NO_BACKSLASH_AMPERSAND_REGEX = "(?im)(?<!\\\\)&([0-9a-fk-or])";
	/** <a href="https://regex101.com/r/D9x2yv/1">Examples</a>*/
	public static final String DUPLICATE_COLOR_AMPERSAND_REGEX = "(?im)&(?:#[\\da-f]{6}|[\\da-f])(\\s*)&(#[\\da-f]{6}|[\\da-f])";
	public static final Int2ObjectMap<Formatting> COLOR_TO_FORMATTING = Util.make(() -> {
		Int2ObjectMap<Formatting> map = new Int2ObjectArrayMap<>(16); // array map bc it's only 16 elements, forever
		for(Formatting f : Formatting.values()) {
			if(f.isColor())
				map.put(f.getColorValue().intValue(), f);
		}
		return map;
	});

	/**
	 * Returns a {@link Codec} for {@link Text} objects.
	 * Used for the stonecutter system so different
	 * versions can all access the correct codec in a
	 * simple and short way.
	 */
	public static Codec<Text> textCodec() {
		return Codecs.TEXT;
	}


	/**
	 * Replaces all {@code $} characters in {@code str} with {@code variable}.
	 * Also replaces intended newline characters with {@code \n} to fix (#36).
	 */
	public static String fillVars(String str, String variable) {
		return str.replace("$", variable).replace("\\n", "\n");
	}

	/**
	 * Creates a new MutableText object with explicit
	 * sibling and style data specified. Behaves
	 * effectively the same as the private constructor
	 * {@link MutableText#MutableText(TextContent, List, Style)}.
	 */
	public static MutableText newText(TextContent content, List<Text> siblings, Style style) {
		MutableText text = MutableText.of(content).setStyle(style);
		siblings.forEach(text::append);
		return text;
	}

	/**
	 * Returns a copy of {@code text} with the specified {@code siblings}
	 * parameter replacing the original siblings. The passed {@code text}'s
	 * content and style are preserved.
	 */
	public static MutableText newSiblings(Text text, List<Text> siblings) {
		return newText(text.getContent(), siblings, text.getStyle());
	}

	/**
	 * Returns a copy of {@code text} with an empty content.
	 * Useful for comparing {@link Text} objects'
	 * metadata (style and siblings) only.
	 * */
	public static MutableText withoutContent(Text text) {
		return newText(TextContent.EMPTY, text.getSiblings(), text.getStyle());
	}


	/**
	 * Formats a String with {@code &} formatting codes into a {@link Text}.
	 * First replaces all {@code &<?>} codes with a section symbol ({@code §}),
	 * then deletes the backslash from all {@code \&<?>} instances. Doesn't
	 * support hex colors.
	 *
	 * @apiNote Hex colors could be supported with the Placeholder API, but using
	 * an entire library just for this one feature seems excessive.
	 */
	public static MutableText text(String unformatted) {
		return Text.literal(
			unformatted
				.replaceAll(NO_BACKSLASH_AMPERSAND_REGEX, "§$1")
				.replaceAll(AMPERSAND_REGEX, "&$2")
		);
	}

	/**
	 * Converts a {@link Text} into a {@link String} with {@code &<?>} codes.
	 * Strips any complex style data, including hover events, fonts, insertions,
	 * etc. Hex colors are represented in the format {@code &#RRGGBB}.
	 */
	public static String toCodedString(Text text, boolean fancyCodes) {
		StringBuilder builder = new StringBuilder(); // required for the lambda expression
		AtomicReference<Style> lastStyle = new AtomicReference<>(Style.EMPTY); // ensures that the first equality check returns false

		text.visit((style, str) -> {
			// if style is different from last, add any formatting codes
			if(!style.equals(lastStyle.get())) {
				if(fancyCodes)
					builder.append(Formatting.AQUA); // adds a pop of color to the codes to make them more visible

				builder.append(getFormattingCodes(style, lastStyle.get()));

				if(fancyCodes)
					builder.append(Formatting.RESET); // adding colors breaks some (whitespace separated) functionality of DUPE_COLOR_AMPERSAND_REGEX

				lastStyle.set(style);
			}

			builder.append( str.replace(Formatting.FORMATTING_CODE_PREFIX, '&') ); // sometimes section signs leak and i want them out

			return Optional.empty();
		}, Style.EMPTY);

		while(builder.toString().startsWith("&r")) // removes any leading reset codes
			builder.delete(0, 2);

		while(builder.toString().endsWith("&r&r")) // removes duplicate trailing reset codes (leaves one if it exists just in case it's intended)
			builder.setLength(builder.length() - 4);

		// removes the redundant code in a pair of color codes, optionally separated by whitespace, even including hex codes
		// ex. '&a&9' -> '&9', '&b   &4' -> '   &4', '&c&#123ABC' -> '&#123ABC', '&#00FF22\t&f' -> '\t&f'
		return builder.toString().replaceAll(DUPLICATE_COLOR_AMPERSAND_REGEX, "$1&$2");
	}

	// prepub: alright here is the deal. this method is always gonna have some issue bc of lots of edge cases and etc etc.
	//  so we're switching to QuickText/MiniMessage and if players want the old style, i'll just convert the QT to the old style
	//  aka strip complex styles and convert hex & formatting colors to ampersand codes
	/**
	 * Returns the formatting codes of the {@link Style} provided, excluding any already
	 * applied ones according to {@code last}. Returns an empty string if the style is
	 * empty or blank. If any hex colors are specified, they will be returned in the
	 * format {@code &#RRGGBB}. Additionally, any color that exists as a formatting code
	 * (ex. {@code #55FF55} for {@link Formatting#GREEN}) will return as the formatting
	 * code (ex. {@code &a}).
	 *
	 * @see TextColor#getHexCode()
	 */
	public static String getFormattingCodes(Style style, Style last) {
		StringJoiner joiner = new StringJoiner("&", "&", "").setEmptyValue(""); // adds the & at the start of the string
		TextColor color = style.getColor();
		Formatting formatting = color != null ? Formatting.byName(color.getName()) : Formatting.RESET;

		// only add the color code if one was explicitly specified (reset is not a color ^) and if it's different from the last color
		if(formatting != Formatting.RESET && (last.getColor() == null || color.getRgb() != last.getColor().getRgb())) {
			if(formatting != null)
				joiner.add("" + formatting.getCode()); // default colors and reset codes
			else if( COLOR_TO_FORMATTING.containsKey(color.getRgb()) )
				joiner.add("" + COLOR_TO_FORMATTING.get(color.getRgb()).getCode()); // hex colors that exist as formatting codes
			else
				joiner.add(color.getHexCode()); // custom hex colors
		} else if(style.equals(Style.EMPTY) && !last.equals(Style.EMPTY)) { // can't use isEmpty() bc it's a reference check -_-
			return "&r"; // if the current style is empty and the last style wasn't, we've reset!
		}

		if(style.isBold() && !last.isBold())
			joiner.add("l");
		if(style.isItalic() && !last.isItalic())
			joiner.add("o");
		if(style.isUnderlined() && !last.isUnderlined())
			joiner.add("n");
		if(style.isStrikethrough() && !last.isStrikethrough())
			joiner.add("m");
		if(style.isObfuscated() && !last.isObfuscated())
			joiner.add("k");

		return joiner.toString();
	}
}