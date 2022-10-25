package org.opensha.commons.util;

import java.io.File;
import java.text.Collator;
import java.util.Comparator;

/**
 * <code>Comparator</code> for filenames that promotes directories and sorts
 * by filename for the current <code>Locale</code>.
 *
 * @author Peter Powers
 * @version $Id: FileNameComparator.java 6514 2010-04-02 17:54:13Z pmpowers $
 */
public class FilePathComparator implements Comparator<File> {
	
	// A Collator for String comparisons
	private Collator c = Collator.getInstance();
	
	@Override
	public int compare(File f1, File f2) {
		if (f1 == f2) return 0;
		
		// use Collator for file-file and dir-dir pairs
		return c.compare(f1.getAbsolutePath(), f2.getAbsolutePath());
	}
}
