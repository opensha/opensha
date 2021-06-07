package org.opensha.commons.util.modules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for interfaces or classes that may extend {@link OpenSHA_Module} but should not be mapped as retrievable
 * super-interfaces when a {@link OpenSHA_Module} is added to a {@link ModuleContainer}.
 * <p>
 * For example, a module may implement {@link ArchivableModule}, but we don't want to retrieve it when calling
 * the {@link ModuleContainer#getModule(Class)} method with {@link ArchivableModule} as an argument.
 * 
 * @author kevin
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleHelper {

}
