package org.opensha.sha.calc.IM_EventSet;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.FileUtils;

/**
 * Reads site files into memory.
 * <p>
 * A site file consists of a site per line, each line starting with coordinates,
 * followed optionally by a collection of site data values.
 * </p>
 */
public class SiteFileLoader {
	
	private ArrayList<Location> locs;
	private ArrayList<ArrayList<SiteDataValue<?>>> valsList;
	
	private final String measurementType;
	private final boolean lonFirst;
	private final ArrayList<String> siteDataTypes; // Types we are loading

    // List of all site data types that can be loaded
    // See SiteTranslator for other site data types
    public static String[] allSiteDataTypes = {
            SiteData.TYPE_VS30,
            SiteData.TYPE_DEPTH_TO_1_0,
            SiteData.TYPE_DEPTH_TO_2_5,
    };
    public static String COM = "#"; // Comment character

    /**
     * Loads the site data coordinates and optional site data params from files
     * into memory.
     * @param lonFirst true if longitude is before latitude
     * @param measurementType Inferred or Measured
     * @param siteDataTypes list of types to try loading in order after site coordinates
     */
	public SiteFileLoader(boolean lonFirst, String measurementType, ArrayList<String> siteDataTypes) {
		this.lonFirst = lonFirst;
		this.measurementType = measurementType;
		this.siteDataTypes = siteDataTypes;
	}
	
	public void loadFile(File file) throws IOException, ParseException {
		ArrayList<String> lines = FileUtils.loadFile(file.getAbsolutePath());

        locs = new ArrayList<>();
		valsList = new ArrayList<>();

        // Each line corresponds to a site to load
		for (int i=0; i<lines.size(); i++) {
			String line = lines.get(i).trim();
			// skip comments
			if (line.startsWith(COM))
				continue;
			StringTokenizer tok = new StringTokenizer(line);
			if (tok.countTokens() < 2)
				throw new ParseException("Line " + (i+1) + " has less than 2 fields!", 0);
			double lat, lon;
            ArrayList<SiteDataValue<?>> vals = new ArrayList<>();
			try {
                // Load location coordinates
				if (lonFirst) {
					lon = Double.parseDouble(tok.nextToken());
					lat = Double.parseDouble(tok.nextToken());
				} else {
					lat = Double.parseDouble(tok.nextToken());
					lon = Double.parseDouble(tok.nextToken());
				}
				Location loc = new Location(lat, lon);
                // Load all site data values from this line
				for (String type : siteDataTypes) {
					if (!tok.hasMoreTokens())
						break;
                    SiteDataValue<?> siteDataValue = getValue(type, measurementType, tok.nextToken());
                    vals.add(siteDataValue);
				}
				locs.add(loc);
				valsList.add(vals);
			} catch (NumberFormatException e) {
				throw new NumberFormatException("Error parsing number at line " + (i+1));
			}
		}
	}
	
	public ArrayList<Location> getLocs() {
		return locs;
	}

	public ArrayList<ArrayList<SiteDataValue<?>>> getValsList() {
		return valsList;
	}

    private SiteDataValue<?> getValue(String type, String measType, String valStr) {
        valStr = valStr.trim();
        Object val;

        try {
            val = Double.parseDouble(valStr);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("'" + valStr + "' cannot be parsed into a numerical value!");
        }
        return new SiteDataValue<>(type, measType, val);
    }
}
