package org.opensha.sha.earthquake.faultSysSolution.modules;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.ModuleHelper;
import org.opensha.commons.util.modules.OpenSHA_Module;

/**
 * Tagging interface to indicate that this {@link AverageableModule} can and should be branch-averaged across
 * multiple {@link LogicTreeBranch} instances. 
 * 
 * @author kevin
 *
 * @param <E>
 */
@ModuleHelper
public interface BranchAverageableModule<E extends OpenSHA_Module> extends AverageableModule<E> {

}
