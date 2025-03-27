package org.opensha.commons.util;

import java.io.IOException;
import java.text.Normalizer;

import org.opensha.commons.data.Site;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader;

public class FileNameUtils {
	
	/**
	 * Simplifies strings for use in file names. It first removes any accent (or other diacritic) marks
	 * (e.g., replacing ñ with n), then replaces everything that's NOT a simple alphanumeric character with an uncerscore.
	 * Leading, trailing, and duplicate underscores are stripped.
	 * 
	 * @param name
	 * @return
	 */
	public static String simplify(String name) {
		// first normalize it to remove any accents or special characters
		String ret = Normalizer.normalize(name, Normalizer.Form.NFD);
		ret = ret.replaceAll("\\p{M}", ""); // Remove diacritic marks
		
		// now replace any non-alphanumeric characters with underscores
		ret = ret.replaceAll("\\W+", "_");
		
		// remove any leading or trailing underscores
		while (ret.startsWith("_"))
			ret = ret.substring(1);
		while (ret.endsWith("_"))
			ret = ret.substring(0, ret.length()-1);
		
		// remove any duplicate underscores
		while (ret.contains("__"))
			ret = ret.replace("__", "_");
		return ret;
	}
	
	public static void main(String[] args) throws IOException {
		for (Site site : PRVI25_RegionLoader.loadHazardSites()) {
			System.out.println(site.getName()+"\t->\t"+simplify(site.getName()));
		}
	}

}
