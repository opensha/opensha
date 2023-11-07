package org.opensha.commons.logicTree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.opensha.commons.logicTree.LogicTreeLevel.EnumBackedLevel;

/**
 * Tagging interface for use with {@link LogicTreeNode} instances, to allow them to specify files/properties that they
 * affect.
 * <p>
 * For {@link EnumBackedLevel} instances, this annotation should be added to the enum class. For other
 * instances, this annotation should be added to the {@link LogicTreeNode} class returned by
 * {@link LogicTreeLevel#getType()} (not the level itself).
 * 
 * @author kevin
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Affects.Affected.class)
public @interface Affects {
	public static final String NONE = "";
	
	public String value() default NONE;
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	@interface Affected {

		public Affects[] value();
	}
}
