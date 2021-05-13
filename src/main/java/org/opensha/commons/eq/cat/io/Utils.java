package org.opensha.commons.eq.cat.io;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.eq.cat.MutableCatalog;

@Deprecated
public class Utils {

	// TODO kill class
	
    /**
     * Utility method to load a catalog by searching a fileneame for proper reader
     * class to use for import.
     * 
     * @param file to import
     * @param arraySize initial array size hint (reduces allocation overhead)
     * @return the loaded catalog
     */
	@Deprecated
    public static MutableCatalog loadCatalog(File file, int arraySize) throws
    	IOException {
        String name = file.getName().toLowerCase();
        CatalogReader reader;
        if (StringUtils.contains(name,"_basic")) {
            reader = new Reader_Basic(arraySize);
        } else if (StringUtils.contains(name,"_ncedc")) {
            reader = new Reader_NCEDC(arraySize);
        } else if (StringUtils.contains(name,"_scedc")) {
            reader = new Reader_SCEDC(arraySize);
        } else if (StringUtils.contains(name,"_scsn")) {
            reader = new Reader_SCSN(arraySize);
        } else {
            throw new IllegalArgumentException("Can't determine CatalogReader to use.");
        }
        
        try {
            MutableCatalog catalog = new MutableCatalog(file, reader);
            return catalog;
        } catch (IOException ce) {
            throw ce;
        }
    }
    
}
