package au.id.villar.utils.beangen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Bean {

	String typeName() default "";

	boolean noArgsConstructor() default false;

	boolean setters() default true;
}
