package obro1961.chatpatches.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.network.chat.*;
//? if >1.20.2 {
//import net.minecraft.network.chat.contents.PlainTextContents;
//?}
import obro1961.chatpatches.mixin.security.ClickEvent$ActionMixin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class containing various string and {@link Component} related utilities.
 */
public class TextUtil {
	/**
	 * @see ChatFormatting#PREFIX_CODE
	 */
	public static final Matcher AMPERSAND_REGEX = Pattern.compile("(?im)&([0-9a-fk-or])").matcher("");
	/**
	 * {@link #AMPERSAND_REGEX} that explicitly does not match any
	 * formatting codes following backslashes
	 */
	public static final Matcher NO_BACKSLASH_AMPERSAND_REGEX = Pattern.compile("(?im)(?<!\\\\)&([0-9a-fk-or])").matcher("");
	/** <a href="https://regex101.com/r/D9x2yv/latest">Examples</a>*/
	public static final Matcher DUPLICATE_COLOR_AMPERSAND_REGEX = Pattern.compile("(?im)&(?:#[\\da-f]{6}|[\\da-f])(\\s*)&(#[\\da-f]{6}|[\\da-f])").matcher("");
	public static final Int2ObjectMap<ChatFormatting> COLOR_TO_FORMATTING = Util.make(() -> {
		Int2ObjectMap<ChatFormatting> map = new Int2ObjectArrayMap<>(16); // array map bc it's only 16 elements, forever
		for(ChatFormatting f : ChatFormatting.values()) {
			if(f.isColor()) {
				map.put(f.getColor().intValue(), f);
			}
		}
		return map;
	});

	/**
	 * A wrapped {@link Codec} for {@link Component} objects that will not
	 * throw an exception when serializing click events with unsafe
	 * ({@link ClickEvent.Action#OPEN_FILE}) actions. Done by disabling the
	 * {@linkplain #safeCodec safety serialization check} that is used by
	 * {@link ClickEvent$ActionMixin} to temporarily allow all
	 * messages to be serialized.
	 *
	 * @see ClickEvent$ActionMixin#allowConditionalSerialization(boolean)
	 * @see obro1961.chatpatches.ChatLog#CODEC
	 */
	public static final Codec<Component> UNSAFE_CODEC = new Codec<>() {
		/*~ if <=1.20.2 'ComponentSerialization.CODEC' -> 'net.minecraft.util.ExtraCodecs.COMPONENT' {*/
		@Override
		public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
			safeCodec.set(false);
			var result = ComponentSerialization.CODEC.encode(input, ops, prefix);
			safeCodec.set(true);
			return result;
		}

		@Override
		public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
			safeCodec.set(false);
			var result = ComponentSerialization.CODEC.decode(ops, input);
			safeCodec.set(true);
			return result;
		}

		@Override
		public String toString() {
			return "UnsafeTextCodec[safe=" + safeCodec.get() + ", codec=" + ComponentSerialization.CODEC + "]";
		}
		/*~}*/
	};

	/**
	 * A boolean-wrapped holder that is {@code true} when safety mechanisms
	 * <b>should</b> be used when serializing text components, and {@code false}
	 * otherwise. Allows for the serialization of potentially unsafe objects
	 * like {@link ClickEvent}s.
	 *
	 * @see ClickEvent$ActionMixin#allowConditionalSerialization(boolean)
	 *
	 * @implNote Thread-local because
	 * <a href="https://discord.com/channels/507304429255393322/721100785936760876/1387226885867704401">
	 * TheWhyEvenHow</a> suggested this and its (successful) implementation.
	 */
	public static final ThreadLocal<Boolean> safeCodec = ThreadLocal.withInitial(() -> true);


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
		} else if(o instanceof Component) {
			return (MutableComponent) o;
		} else {
			return Component.literal(o instanceof FormattedText ft ? ft.getString() : String.valueOf(o));
		}
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

				str = str.substring(0, max - len[0]); // truncate the string to the max length
			}

			truncated.append(Component.literal(str).setStyle(style));

			len[0] += str.length();

			return Optional.empty();
		}, Style.EMPTY);

		return truncated;
	}

	/**
	 * Returns a copy of {@code text} with the specified {@code siblings}
	 * parameter replacing the original siblings. The passed {@code text}'s
	 * content and style are preserved.
	 */
	public static MutableComponent newSiblings(Component text, List<Component> siblings) {
		return new MutableComponent(text.getContents(), siblings, text.getStyle());
	}

	/**
	 * Returns a copy of {@code text} with an empty content.
	 * Useful for comparing {@link Component} objects'
	 * metadata (style and siblings) only.
	 * */
	/*public static MutableComponent withoutContent(Component text) {
		return new MutableComponent(*//*-? if >1.20.2 {*//*PlainTextContents*//*-?} else {*//**//*ComponentContents*//**//*-?}*//*.EMPTY, text.getSiblings(), text.getStyle());
	}*/ // currently unused


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
		String s = NO_BACKSLASH_AMPERSAND_REGEX.reset(unformatted).replaceAll("§$1");
		s = AMPERSAND_REGEX.reset(s).replaceAll("&$2");

		return Component.literal(s);
	}


	/**
	 * @return {@code true} if `a` and `b` are virtually equal; that is, a
	 * styled color visitor produces the same sequence for both components.
	 * This is intended to compare the string and style data together, although
	 * this is a convoluted task that will always have edge cases and frustrating
	 * edge cases.
	 *
	 * todo note unsolvable issues here.
	 */
	public static <T> boolean virtuallyEqual(Component a, Component b) {
		// this is presumed true due to where this is called in tryCondenseDupes
		/*if(!a.getString().equals(b.getString())) {
			return false; // obviously the strings themselves need to be the same (case-insensitive tho..?)
		}*/

		Function<List<it.unimi.dsi.fastutil.Pair<String, Style>>, FormattedText.StyledContentConsumer<T>> visitMaker = list -> ((style, contents) -> {
			list.add(new ObjectObjectImmutablePair<>(
				contents.toLowerCase(Locale.ROOT),
				// forcibly clears any invisible style data - otherwise this will always fail due to the differing insertions from the timestamp
				style.withClickEvent(null).withHoverEvent(null).withInsertion(null)
			));
			return Optional.empty();
		});

		ObjectList<it.unimi.dsi.fastutil.Pair<String, Style>> aColors = new ObjectArrayList<>(), bColors = new ObjectArrayList<>();
		a.visit(visitMaker.apply(aColors), Style.EMPTY);
		b.visit(visitMaker.apply(bColors), Style.EMPTY);

		return aColors.equals(bColors);
	}

	/**
	 * Converts a {@link Component} into a {@link String} with {@code &<?>} codes.
	 * Strips any complex style data, including hover events, fonts, insertions,
	 * etc. Hex colors are represented in the format {@code &#RRGGBB}.
	 */
	public static String toCodedString(Component text) {
		StringBuilder builder = new StringBuilder(); // required for the lambda expression
		AtomicReference<Style> lastStyle = new AtomicReference<>(Style.EMPTY); // ensures that the first equality check returns false

		text.visit((style, str) -> {
			// if style is different from last, add any formatting codes
			if(!style.equals(lastStyle.get())) {
				builder.append(ChatFormatting.AQUA); // adds a pop of color to the codes to make them more visible
				builder.append(getFormattingCodes(style, lastStyle.get()));
				builder.append(ChatFormatting.RESET); // adding colors breaks some (whitespace separated) functionality of DUPE_COLOR_AMPERSAND_REGEX

				lastStyle.set(style);
			}

			builder.append( str.replace(ChatFormatting.PREFIX_CODE, '&') ); // sometimes section signs leak and I WANT THEM OUT

			return Optional.empty();
		}, Style.EMPTY);

		while(builder.toString().startsWith("&r")) { // removes any leading reset codes
			builder.delete(0, 2);
		}

		while(builder.toString().endsWith("&r")) { // removes duplicate trailing reset codes (leaves one if it exists just in case it's intended)
			builder.setLength(builder.length() - 2);
		}

		// removes the redundant code in a pair of color codes, optionally separated by whitespace, even including hex codes
		// ex. '&a&9' -> '&9', '&b   &4' -> '   &4', '&c&#123ABC' -> '&#123ABC', '&#00FF22\t&f' -> '\t&f'
		return DUPLICATE_COLOR_AMPERSAND_REGEX.reset(builder.toString()).replaceAll("$1&$2");
	}

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
			if(formatting != null) {
				joiner.add("" + formatting.getChar()); // default colors and reset codes
			} else if( COLOR_TO_FORMATTING.containsKey(color.getValue()) ) {
				joiner.add("" + COLOR_TO_FORMATTING.get(color.getValue()).getChar()); // hex colors that exist as formatting codes
			} else {
				joiner.add(color.formatValue()); // custom hex colors
			}
		} else if(style.equals(Style.EMPTY) && !last.equals(Style.EMPTY)) { // can't use isEmpty() bc it's a reference check -_-
			return "&r"; // if the current style is empty and the last style wasn't, we've reset!
		}

		if(style.isBold() && !last.isBold()) {
			joiner.add("l");
		}
		if(style.isItalic() && !last.isItalic()) {
			joiner.add("o");
		}
		if(style.isUnderlined() && !last.isUnderlined()) {
			joiner.add("n");
		}
		if(style.isStrikethrough() && !last.isStrikethrough()) {
			joiner.add("m");
		}
		if(style.isObfuscated() && !last.isObfuscated()) {
			joiner.add("k");
		}

		return joiner.toString();
	}
}