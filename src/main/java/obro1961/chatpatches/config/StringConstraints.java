package obro1961.chatpatches.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows specifying some constraints on a string field to ensure it is being
 * properly configured. By default, this annotation requires that a string contain
 * {@value Config#PLACEHOLDER}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StringConstraints {
	/*Range length() default @Range(from = 0, to = Integer.MAX_VALUE);*/

	String[] mustContain() default { Config.PLACEHOLDER };

	/*String[] mustNotContain() default {};

	@RegExp
	String mustMatch() default "";*/

	/**
	 * Allows specifying a class that is used in practice to format the string
	 * contained within the annotated field. For Chat Patches, this most simply
	 * looks like {@link java.text.SimpleDateFormat} for -{@code Date} options.
	 * {@link String} is the default and signifies none exists.
	 */
	Class<?> formatTransformer() default String.class;
}