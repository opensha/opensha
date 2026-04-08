package org.opensha.commons.util.modules;

import java.io.IOException;

import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;

/**
 * Interface for an {@link OpenSHA_Module} module that can be written to and loaded from a zip archive.
 * Writing and loading to a zip file is handled by {@link ModuleArchive}.
 * 
 * @author kevin
 *
 */
@ModuleHelper // don't map this class to any implementation in ModuleContainer
public interface ArchivableModule extends OpenSHA_Module {
	
	/**
	 * Stores any information needed to re-instantiate this module to the archive
	 * 
	 * @param output output stream for the archive
	 * @param entryPrefix path prefix for entries, can be used to store nested module collections in a hierarchical file
	 * system. If non-empty, then each asset should be stored with this prefix.
	 * @throws IOException
	 */
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException;
	
	/**
	 * Initializes this module from the given archive
	 * 
	 * @param input input for the archive
	 * @param entryPrefix path prefix for entries, can be used to store nested module collections in a hierarchical file
	 * system. If non-empty, then each asset should be loaded with this prefix.
	 * @throws IOException
	 */
	public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException;
	
	/**
	 * Modules can have different implementations when they are created vs when they are loaded from a zip file.
	 * This specifies the module class that should be used to load this information from an archive file, and defaults
	 * to the module itself. 
	 * 
	 * @return the class that should be used to load this module from an archive
	 */
	public default Class<? extends ArchivableModule> getLoadingClass() {
		return this.getClass();
	}
	
	/**
	 * Convenience method that will concatenate the given entry prefix and file name, or just return the file name if
	 * the entry prefix is null
	 * 
	 * @param entryPrefix
	 * @param fileName
	 * @return
	 */
	public static String getEntryName(String entryPrefix, String fileName) {
		if (entryPrefix == null)
			return fileName;
		return entryPrefix+fileName;
	}

}
