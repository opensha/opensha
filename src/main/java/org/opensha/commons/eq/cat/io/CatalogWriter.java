package org.opensha.commons.eq.cat.io;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.eq.cat.Catalog;

/**
 * Interface implemented by classes that can process <code>Catalog</code>'s into
 * text files.
 * 
 * @author Peter Powers
 * @version $Id: CatalogWriter.java 7478 2011-02-15 04:56:25Z pmpowers $
 * 
 */
public interface CatalogWriter {

	/**
	 * Writes data from a given catalog to a specified file.
	 * 
	 * @param file output file
	 * @param catalog to write
	 * @throws IOException if IO or data writing problem encountered
	 */
	public void process(Catalog catalog, File file) throws IOException;

	/**
	 * Returns a general description of this catalog writer and/or the types of
	 * file it produces.
	 * 
	 * @return the description of this writer
	 */
	public String description();

}
