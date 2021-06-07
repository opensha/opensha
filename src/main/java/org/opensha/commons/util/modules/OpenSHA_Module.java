package org.opensha.commons.util.modules;

import org.opensha.commons.data.Named;

/**
 * Interface for modules that can be added to a {@link ModuleContainer}, currently just a tagging interface that
 * extends {@link Named}.
 * <p>
 * TODO: should this exist, or should we remove and make the containers accept anything that extends Named?
 * Is there any future functionality that we want here?
 * 
 * @author kevin
 *
 */
@ModuleHelper // don't map this class to any implementation in ModuleContainer
public interface OpenSHA_Module extends Named {

}
