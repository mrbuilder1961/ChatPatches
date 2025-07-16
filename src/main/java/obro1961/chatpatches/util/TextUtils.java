package obro1961.chatpatches.util;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.*;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A class containing various string and {@link Component} related utilities.
 */
public class TextUtils {
	/**
	 * @see ChatFormatting#PREFIX_CODE
	 */
	public static final String AMPERSAND_REGEX = "(?im)&([0-9a-fk-or])";
	/**
	 * {@link #AMPERSAND_REGEX} that explicitly does not match any
	 * formatting codes following backslashes
	 */
	public static final String NO_BACKSLASH_AMPERSAND_REGEX = "(?im)(?<!\\\\)&([0-9a-fk-or])";
	/** <a href="https://regex101.com/r/D9x2yv/1">Examples</a>*/
	public static final String DUPLICATE_COLOR_AMPERSAND_REGEX = "(?im)&(?:#[\\da-f]{6}|[\\da-f])(\\s*)&(#[\\da-f]{6}|[\\da-f])";
	public static final Int2ObjectMap<ChatFormatting> COLOR_TO_FORMATTING = Util.make(() -> {
		Int2ObjectMap<ChatFormatting> map = new Int2ObjectArrayMap<>(16); // array map bc it's only 16 elements, forever
		for(ChatFormatting f : ChatFormatting.values()) {
			if(f.isColor())
				map.put(f.getColor().intValue(), f);
		}
		return map;
	});

	/**
	 * Returns a {@link Codec} for {@link Component} objects.
	 * Used for the stonecutter system so different
	 * versions can all access the correct codec in a
	 * simple and short way.
	 */
	public static Codec<Component> textCodec() { // stonecutter: replace with a swap to del unnecessary method (* micro optimization :D *)
		return
			//? if <=1.20.2 {
			/*net.minecraft.util.ExtraCodecs.COMPONENT;
			*///?} else {
			net.minecraft.network.chat.ComponentSerialization.CODEC;
 			//?}
	}


	/**
	 * Replaces all {@code $} characters in {@code str} with {@code variable}.
	 * Also replaces intended newline characters with {@code \n} to fix (#36).
	 */
	public static String fillVars(String str, String variable) {
		return str.replace("$", variable).replace("\\n", "\n");
	}

	/**
	 * Parses the given object into a {@link MutableComponent}.
	 */
	public static MutableComponent asText(Object o) {
		if(o == null) {
			return Component.empty();
		}

		return switch(o) {
			case Component t -> (MutableComponent) t;
			case FormattedText sv -> Component.literal(sv.getString());
			case String s -> Component.literal(s);
			default -> Component.literal(String.valueOf(o));
		};
	}

	public static MutableComponent truncate(Component text, int max) {
		if(ChatFormatting.stripFormatting(text.getString()).length() <= max) {
			return (MutableComponent) text;
		}

		var truncated = Component.empty();
		int[] len = {0};

		text.visit((style, str) -> {
			if(str.length() + len[0] > max) {
				if(len[0] >= max) {
					return Optional.empty(); // if the text is the perfect length, don't add anything
				}
				// warning: might throw an error but the math seems right?
				str = str.substring(0, max - len[0]); // truncate the string to the max length
			}

			truncated.append(Component.literal(str).setStyle(style));

			len[0] += str.length();

			return Optional.empty();
		}, Style.EMPTY);

		return truncated;
	}

	/**
	 * Creates a new MutableText object with explicit
	 * sibling and style data specified. Behaves
	 * effectively the same as the private constructor
	 * {@link MutableComponent#MutableComponent(ComponentContents, List, Style)}.
	 */
	public static MutableComponent newText(ComponentContents content, List<Component> siblings, Style style) {
		MutableComponent text = MutableComponent.create(content).setStyle(style);
		siblings.forEach(text::append);
		return text;
	}

	/**
	 * Returns a copy of {@code text} with the specified {@code siblings}
	 * parameter replacing the original siblings. The passed {@code text}'s
	 * content and style are preserved.
	 */
	public static MutableComponent newSiblings(Component text, List<Component> siblings) {
		return newText(text.getContents(), siblings, text.getStyle());
	}

	/**
	 * Returns a copy of {@code text} with an empty content.
	 * Useful for comparing {@link Component} objects'
	 * metadata (style and siblings) only.
	 * */
	public static MutableComponent withoutContent(Component text) {
		//stonecutter: remove qualifier when import optimizer fix is available
		return newText(/*? if <=1.20.2 {*//*ComponentContents.EMPTY*//*?} else {*/net.minecraft.network.chat.contents.PlainTextContents.EMPTY/*?}*/, text.getSiblings(), text.getStyle());
	}


	public static ClickEvent/*? if >=1.21.5 {*/.OpenUrl/*?}*/ openUrl(String url) {
		return new
			//? if >=1.21.5 {
			ClickEvent.OpenUrl(java.net.URI.create(url));
			//?} else {
			/*ClickEvent(ClickEvent.Action.OPEN_URL, url);
 			*///?}
	}

	public static ClickEvent/*? if >=1.21.5 {*/.SuggestCommand/*?}*/ suggestCommand(String command) {
		return new
			//? if >=1.21.5 {
			ClickEvent.SuggestCommand(command);
			//?} else {
			/*ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
			*///?}
	}

	public static HoverEvent/*? if >=1.21.5 {*/.ShowText/*?}*/ showText(Component text) {
		return new
			//? if >=1.21.5 {
			HoverEvent.ShowText(text);
			//?} else {
			/*HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
			*///?}
	}


	/**
	 * Formats a String with {@code &} formatting codes into a {@link Component}.
	 * First replaces all {@code &<?>} codes with a section symbol ({@code §}),
	 * then deletes the backslash from all {@code \&<?>} instances. Doesn't
	 * support hex colors.
	 *
	 * @apiNote Hex colors could be supported with the Placeholder API, but using
	 * an entire library just for this one feature seems excessive.
	 */
	public static MutableComponent text(String unformatted) {
		return Component.literal(
			unformatted
				.replaceAll(NO_BACKSLASH_AMPERSAND_REGEX, "§$1")
				.replaceAll(AMPERSAND_REGEX, "&$2")
		);
	}

	/**
	 * Converts a {@link Component} into a {@link String} with {@code &<?>} codes.
	 * Strips any complex style data, including hover events, fonts, insertions,
	 * etc. Hex colors are represented in the format {@code &#RRGGBB}.
	 */
	public static String toCodedString(Component text, boolean fancyCodes) {
		StringBuilder builder = new StringBuilder(); // required for the lambda expression
		AtomicReference<Style> lastStyle = new AtomicReference<>(Style.EMPTY); // ensures that the first equality check returns false

		text.visit((style, str) -> {
			// if style is different from last, add any formatting codes
			if(!style.equals(lastStyle.get())) {
				if(fancyCodes)
					builder.append(ChatFormatting.AQUA); // adds a pop of color to the codes to make them more visible

				builder.append(getFormattingCodes(style, lastStyle.get()));

				if(fancyCodes)
					builder.append(ChatFormatting.RESET); // adding colors breaks some (whitespace separated) functionality of DUPE_COLOR_AMPERSAND_REGEX

				lastStyle.set(style);
			}

			builder.append( str.replace(ChatFormatting.PREFIX_CODE, '&') ); // sometimes section signs leak and I WANT THEM OUT

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
	 * (ex. {@code #55FF55} for {@link ChatFormatting#GREEN}) will return as the formatting
	 * code (ex. {@code &a}).
	 *
	 * @see TextColor#formatValue()
	 */
	public static String getFormattingCodes(Style style, Style last) {
		StringJoiner joiner = new StringJoiner("&", "&", "").setEmptyValue(""); // adds the & at the start of the string
		TextColor color = style.getColor();
		ChatFormatting formatting = color != null ? ChatFormatting.getByName(color.serialize()) : ChatFormatting.RESET;

		// only add the color code if one was explicitly specified (reset is not a color ^) and if it's different from the last color
		if(formatting != ChatFormatting.RESET && (last.getColor() == null || color.getValue() != last.getColor().getValue())) {
			if(formatting != null)
				joiner.add("" + formatting.getChar()); // default colors and reset codes
			else if( COLOR_TO_FORMATTING.containsKey(color.getValue()) )
				joiner.add("" + COLOR_TO_FORMATTING.get(color.getValue()).getChar()); // hex colors that exist as formatting codes
			else
				joiner.add(color.formatValue()); // custom hex colors
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