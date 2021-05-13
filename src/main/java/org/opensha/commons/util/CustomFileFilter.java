package org.opensha.commons.util;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.lang3.StringUtils;

/**
 * Custom FileFilter for use with a {@link JFileChooser}. Filters by file
 * sffix/extension and also permits directory navigation.
 * 
 * @author K. Milner, P. Powers
 * @version $Id: CustomFileFilter.java 8066 2011-07-22 23:59:04Z kmilner $
 */
public class CustomFileFilter extends FileFilter {

	private String extension;
	private String description;
	
	/**
	 * Creates a newcustom filter.
	 * @param extension to filter; period '.' is optional
	 * @param description of filter
	 */
	public CustomFileFilter(String extension, String description) {
		this.description = description;
		this.extension = (!extension.startsWith(".")) ?
			"." + extension : extension;
	}

	@Override
	public boolean accept(File f) {
		if (f.isDirectory()) return true;
		return StringUtils.endsWithIgnoreCase(f.getName(), extension);
	}

	@Override
	public String getDescription() {
		return description;
	}
	
	/**
	 * Returns the file extension associated with this filter. Extension will
	 * always start with a period '.'.
	 * @return the extension for this filter
	 */
	public String getExtension() {
		return extension;
	}

}
