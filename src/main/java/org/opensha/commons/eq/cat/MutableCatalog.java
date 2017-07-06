package org.opensha.commons.eq.cat;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.eq.cat.io.CatalogReader;

/**
 * Mutable version of a <code>DefaultCatalog</code>. Calls to
 * <code>getData()</code> will return references to this catalogs underlying
 * data arrays. Likewise, methods such as <code>writeCatalog()</code> which rely
 * on <code>getData()</code> will be faster.
 * 
 * @author Peter Powers
 * @version $Id: MutableCatalog.java 7478 2011-02-15 04:56:25Z pmpowers $
 * 
 */
public class MutableCatalog extends DefaultCatalog {

	/**
	 * Constructs a new empty mutable catalog. Catalog size (event count) is set
	 * using the first data array added.
	 */
	public MutableCatalog() {
		setReadable(true);
	}

	/**
	 * Constructs a new mutable catalog from the given file using the specified
	 * reader.
	 * 
	 * @param file to read
	 * @param reader to process file
	 * @throws IOException if any IO or data processing related error occurs
	 */
	public MutableCatalog(File file, CatalogReader reader) throws IOException {
		super(file, reader);
		setReadable(true);
	}

}
