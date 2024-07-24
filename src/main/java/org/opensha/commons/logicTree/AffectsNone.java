package org.opensha.commons.logicTree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.opensha.commons.logicTree.LogicTreeLevel.EnumBackedLevel;

/**
 * Tagging interface for use with {@link LogicTreeNode} instances, to specify that a node affects all properties
 * and files.
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
public @interface AffectsNone {

}
