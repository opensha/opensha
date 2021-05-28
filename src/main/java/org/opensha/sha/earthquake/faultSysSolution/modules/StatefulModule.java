package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.Named;

/**
 * Interface that makes a RupSetModule or SolutionModule stateful, i.e., allows it to be written to and loaded
 * from a zip file. Classes that implement this interface *must* have a public no-argument constructor that will be
 * called to instantiate the class upon load. Then, the rupture set or solution will be set, and then finally the
 * initFromArchive(...) class will be called.
 * 
 * @author kevin
 *
 */
public interface StatefulModule extends Named {
	
	/**
	 * Stores any information needed to re-instantiate this module to the fault system zip file
	 * 
	 * @param zout zip output stream for the fault system (rupture set or solution) archive
	 * @param entryPrefix prefix for entries, used in a compound solution file that bundles multiple solutions per
	 * zip file. If non-empty, then each asset should be stored with this prefix
	 * @throws IOException
	 */
	public abstract void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException;
	
	/**
	 * Initializes this module from the given fault system zip file
	 * 
	 * @param archive fault system (rupture set or solution) zip file archive
	 * @param entryPrefix prefix for entries, used in a compound solution file that bundles multiple solutions per
	 * zip file. If non-empty, then each asset should be stored with this prefix
	 * @throws IOException
	 */
	public abstract void initFromArchive(ZipFile zip, String entryPrefix) throws IOException;
	
	/**
	 * Modules can have different implementations when they are created vs when they are loaded from a zip file
	 * (e.g., gridded seismicity). This specifies the module class that should be used to load this information from
	 * a zip file, and defaults to the module itself. 
	 * 
	 * @return the class that should be used to load this module from an archive
	 */
	public default Class<? extends StatefulModule> getLoadingClass() {
		return this.getClass();
	}
	
	public static String getEntryName(String entryPrefix, String fileName) {
		if (entryPrefix == null)
			return fileName;
		return entryPrefix+fileName;
	}
	
}
