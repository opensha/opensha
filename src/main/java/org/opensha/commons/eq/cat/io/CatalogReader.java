package org.opensha.commons.eq.cat.io;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.eq.cat.*;

/**
 * Interface implemented by classes that can process text files into earthquake
 * data for a <code>Catalog</code>.
 * 
 * @author Peter Powers
 * @version $Id: CatalogReader.java 7478 2011-02-15 04:56:25Z pmpowers $
 * 
 */
public interface CatalogReader {

	/**
	 * Reads data from a given file into a specified catalog
	 * 
	 * @param file to process
	 * @param catalog for file data
	 * @throws IOException if IO or data reading problem encountered
	 * @throws NullPointerException if <code>file</code> or <code>catalog</code>
	 *         are <code>null</code>
	 */
	public void process(File file, Catalog catalog) throws IOException;

	/**
	 * Returns a general description of this catalog reader and/or the types of
	 * files it operates on.
	 * 
	 * @return the description of this reader
	 */
	public String description();

}
