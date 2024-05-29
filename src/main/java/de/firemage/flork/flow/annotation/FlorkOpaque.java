package de.firemage.flork.flow.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface FlorkOpaque {
}
