package org.opensha.commons.data.siteData.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.opensha.commons.data.siteData.AbstractSiteData;
import org.opensha.commons.data.siteData.servlet.SiteDataServletAccessor;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.RunScript;
import org.opensha.commons.util.ServerPrefUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class GarciaRegionPerlWrapper extends AbstractSiteData<String> {
	
	private static final boolean D = false;
	
	private File perlScript;
	private SiteDataServletAccessor<String> accessor;
	
	private Region region = Region.getGlobalRegion(); 
	
	public static final String SERVLET_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL()
			+"SiteData/GarciaRegionPerlWrapper";
	
	public GarciaRegionPerlWrapper(File perlScript) {
		Preconditions.checkState(perlScript.exists(),
				"Perl script doesn't exist: %s", perlScript.getAbsolutePath());
		this.perlScript = perlScript;
	}
	
	public GarciaRegionPerlWrapper() {
		accessor = new SiteDataServletAccessor<String>(this, SERVLET_URL);
	}

	@Override
	public Region getApplicableRegion() {
		return region;
	}

	@Override
	public double getResolution() {
		return 0;
	}

	@Override
	public String getName() {
		return "Garcia Reigon Perl Wrapper";
	}

	@Override
	public String getShortName() {
		return "GarciaPerlWrap";
	}

	@Override
	public String getDataType() {
		return TYPE_TECTONIC_REGIME;
	}

	@Override
	public String getDataMeasurementType() {
		return TYPE_FLAG_INFERRED;
	}

	@Override
	public Location getClosestDataLocation(Location loc) throws IOException {
		return loc;
	}

	@Override
	public ArrayList<String> getValues(LocationList locs) throws IOException {
		if (accessor != null)
			return accessor.getValues(locs);
		File tempDir = null;
		ArrayList<String> ret = null;
		try {
			tempDir = Files.createTempDir();
			
			if (D) System.out.println("Temp dir: "+tempDir.getAbsolutePath());
			
			File inputFile = new File(tempDir, "fake_input_cat.txt");
			File outputFile = new File(tempDir, "output.txt");
			FileWriter fw = new FileWriter(inputFile);
			for (Location loc : locs)
				fw.write("col1 col2 col3 col4 col5 "+loc.getLongitude()+" "+loc.getLatitude()+" "+loc.getDepth()+
						" col9 col10\n");
			fw.close();
			
			File runScript = new File(tempDir, "runFE.sh");
			
			fw = new FileWriter(runScript);
			
			fw.write("#!/bin/bash\n");
			fw.write("\n");
			fw.write("cd "+perlScript.getParentFile().getAbsolutePath()+"\n");
			fw.write("\n");
			fw.write("/bin/cat "+inputFile.getAbsolutePath()+" | ./"+perlScript.getName()
					+" > "+outputFile.getAbsolutePath()+"\n");
			
			fw.close();
			
			String[] command ={"sh","-c","sh "+runScript.getAbsolutePath()};
			
			if (D) System.out.println("Running script: "+runScript.getAbsolutePath());
			RunScript.runScript(command);
			
			Preconditions.checkState(outputFile.exists());
			
			ret = Lists.newArrayList();
			
			for (String line : Files.readLines(outputFile, Charset.defaultCharset())) {
				line = line.trim();
				if (line.isEmpty())
					continue;
				String[] split = line.split(" ");
				Preconditions.checkState(split.length == 12, "Expected 12 items, got %s", split.length);
				String reg = split[11];
				Preconditions.checkState(isValueValid(reg), "Bad value: %s", reg);
				ret.add(reg);
			}
			
			Preconditions.checkState(ret.size() == locs.size(), "Bad output size");
		} finally {
			if (tempDir != null) {
				if (D) System.out.println("Deleting "+tempDir.getAbsolutePath());
				FileUtils.deleteDirectory(tempDir);
			}
		}
		return ret;
	}

	@Override
	public String getValue(Location loc) throws IOException {
		LocationList locs = new LocationList();
		
		locs.add(loc);
		
		return getValues(locs).get(0);
	}

	@Override
	public boolean isValueValid(String el) {
		return el != null && el.length() > 0;
	}

	@Override
	public String getMetadata() {
		return "Java wrapper to F-E region Perl Script, updated for Garcia subregions";
	}
	
	public static void main(String[] args) {
//		GarciaRegionPerlWrapper garcia = new GarciaRegionPerlWrapper(
//				new File("/home/kevin/OpenSHA/oaf/flinn_engdahl_regions/feregion_ajm.pl"));
		GarciaRegionPerlWrapper garcia = new GarciaRegionPerlWrapper();
		
		LocationList locs = new LocationList();
		
		locs.add(new Location(28.2305, 84.7314, 8.22));
		locs.add(new Location(35, -118, 7d));
		locs.add(new Location(35, -50, 7d));
		for (int i=0; i<10; i++) {
			double lat = 180d*Math.random()-90d;
			double lon = 360d*Math.random()-180d;
			double depth = 20d*Math.random();
			locs.add(new Location(lat, lon, depth));
		}
		
		try {
			List<String> vals = garcia.getValues(locs);
			for (int i=0; i<locs.size(); i++)
				System.out.println(locs.get(i)+": "+vals.get(i));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
