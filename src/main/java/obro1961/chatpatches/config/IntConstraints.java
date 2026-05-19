package obro1961.chatpatches.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Allows specifying some constraints on an int field to ensure it is being
 * properly configured. By default, this annotation requires that an int {@code
 * x} satisfy <code>0 <= x <= {@value Integer#MAX_VALUE}</code>, and provides
 * that its slider interval is {@code 1}.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface IntConstraints {
	int min() default 0;

	int max() default Integer.MAX_VALUE;

	int interval() default 1;
}