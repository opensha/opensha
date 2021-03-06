package org.opensha.sha.calc.IM_EventSet.v03;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.calc.IM_EventSet.v03.gui.AddSiteDataPanel;

public class SiteFileLoader {
	
	private ArrayList<Location> locs;
	private ArrayList<ArrayList<SiteDataValue<?>>> valsList;
	
	private String measurementType;
	private boolean lonFirst;
	private ArrayList<String> siteDataTypes;
	
	public SiteFileLoader(boolean lonFirst, String measurementType, ArrayList<String> siteDataTypes) {
		this.lonFirst = lonFirst;
		this.measurementType = measurementType;
		this.siteDataTypes = siteDataTypes;
	}
	
	public void loadFile(File file) throws IOException, ParseException {
		ArrayList<String> lines = FileUtils.loadFile(file.getAbsolutePath());
		
		String measType = measurementType;
		
		locs = new ArrayList<Location>();
		valsList = new ArrayList<ArrayList<SiteDataValue<?>>>();
		
		for (int i=0; i<lines.size(); i++) {
			String line = lines.get(i).trim();
			// skip comments
			if (line.startsWith("#"))
				continue;
			StringTokenizer tok = new StringTokenizer(line);
			if (tok.countTokens() < 2)
				throw new ParseException("Line " + (i+1) + " has less than 2 fields!", 0);
			double lat, lon;
			ArrayList<SiteDataValue<?>> vals = new ArrayList<SiteDataValue<?>>();
			try {
				if (lonFirst) {
					lon = Double.parseDouble(tok.nextToken());
					lat = Double.parseDouble(tok.nextToken());
				} else {
					lat = Double.parseDouble(tok.nextToken());
					lon = Double.parseDouble(tok.nextToken());
				}
				Location loc = new Location(lat, lon);
				for (String type : siteDataTypes) {
					if (!tok.hasMoreTokens())
						break;
					String valStr = tok.nextToken();
					SiteDataValue<?> val = AddSiteDataPanel.getValue(type, measType, valStr);
					vals.add(val);
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

}
