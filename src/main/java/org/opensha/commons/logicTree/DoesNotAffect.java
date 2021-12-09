package org.opensha.commons.logicTree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.opensha.commons.logicTree.LogicTreeLevel.EnumBackedLevel;

/**
 * Tagging interface for use with {@link EnumBackedLevel}, to allow logic tree node enums to specify files/properties
 * that they don't affect.
 * 
 * @author kevin
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(DoesNotAffect.NotAffected.class)
public @interface DoesNotAffect {
	public static final String NONE = "";
	
	public String value() default NONE;
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	@interface NotAffected {

		public DoesNotAffect[] value();
	}
}
