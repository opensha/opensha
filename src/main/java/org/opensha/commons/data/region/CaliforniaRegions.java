/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.data.region;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;

import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * This wrapper class contains a number of California regions commonly and 
 * historically used in seismic hazard analysis.
 * 
 * @author Peter Powers
 * @version $Id: CaliforniaRegions.java 11404 2016-09-19 22:10:22Z kmilner $
 * @see Region
 * @see GriddedRegion
 */
public class CaliforniaRegions {
	
	// TODO each should probably implement 'named' interface
	// TODO RELM_NOCAL/SOCAL minimally used; revisit; clean
	
//	public static void main(String[] args) {
//		GriddedRegion rr = new RELM_GRIDDED();
//		System.out.println(rr.getNumGridLocs());
//	}
	
	private CaliforniaRegions() {};
	
	private static class CA_Region extends Region {
		
		protected String prefix;

		public CA_Region(String prefix) {
			super(loadRegion(prefix));
			this.prefix = prefix;
		}
	}
	
	private static class CA_GriddedRegion extends GriddedRegion {
		
		protected String prefix;

		public CA_GriddedRegion(String prefix, double spacing) {
			super(loadGridRegion(prefix, spacing));
			this.prefix = prefix;
		}
	}
	
	/** 
	 * Gridded region used in the Regional Earthquake Likelihood 
	 * Models (RELM) project. Grid spacing is 0.1&deg;.
	 * 
	 * NOTE: This is an alternative version of the RELM_TESTING_GRIDDED region
	 * below.
	 */
	@Deprecated
	public static final class RELM_GRIDDED extends CA_GriddedRegion {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public RELM_GRIDDED() {
			super("RELM", 0.1);
		}
	}

	/** 
	 * A simplified representation of the RELM gridded region. 
	 */
	public static final class RELM_TESTING extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public RELM_TESTING() {
			super("RELM_testing");
			this.setName("RELM_TESTING Region");
		}
	}
			
	/** 
	 * A simplified representation of the RELM gridded region.
	 * Grid spacing is 0.1&deg;.
	 */
	public static final class RELM_TESTING_GRIDDED extends CA_GriddedRegion {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public RELM_TESTING_GRIDDED() {
			this(0.1);
		}
		
		/** New instance of region. */
		public RELM_TESTING_GRIDDED(double spacing) {
			super("RELM_testing", spacing);
			this.setName("RELM_TESTING Region");
		}
	}

	/** 
	 * Expanded RELM region used to capture large external events.
	 */
	public static final class RELM_COLLECTION extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public RELM_COLLECTION() {
			super("RELM_collection");
			this.setName("RELM_COLLECTION Region");
		}
	}

	/** 
	 * Expanded gridded RELM region used to capture large external events.
	 * Grid spacing is 0.1&deg;.
	 */
	public static final class RELM_COLLECTION_GRIDDED extends CA_GriddedRegion {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public RELM_COLLECTION_GRIDDED() {
			super("RELM_collection", 0.1);
		}
	}

	/** 
	 * Northern half of the RELM region.
	 */
	public static final class RELM_NOCAL extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public RELM_NOCAL() {
			super("RELM_NoCal");
			this.setName("RELM_NOCAL Region");
		}
	}

	/** 
	 * Northern half of the gridded RELM region. Grid spacing is 0.1&deg;.
	 */
	public static final class RELM_NOCAL_GRIDDED extends CA_GriddedRegion {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public RELM_NOCAL_GRIDDED() {
			super("RELM_NoCal", 0.1);
			this.setName("RELM_NOCAL Region");
		}
	}

	/** 
	 * Southern half of the RELM region.
	 */
	public static final class RELM_SOCAL extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public RELM_SOCAL() {
			super("RELM_SoCal");
			this.setName("RELM_SOCAL Region");
		}
	}

	/** 
	 * Southern half of the gridded RELM region. Grid spacing is 0.1&deg;.
	 */
	public static final class RELM_SOCAL_GRIDDED extends CA_GriddedRegion {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public RELM_SOCAL_GRIDDED() {
			super("RELM_SoCal", 0.1);
			this.setName("RELM_SOCAL Region");
		}
	}

	/** 
	 * A gridded, box-shaped central California region used in the 2002  
	 * Working Group on California Earthquake Probabilities (WGCEP).
	 * Grid spacing is 0.1&deg;.
	 */
	public static final class SF_BOX_GRIDDED extends CA_GriddedRegion {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public SF_BOX_GRIDDED() {
			super("WG02", 0.1);
		}
	}
	
	/** 
	 * WGCEP 2002's San Francisco Box.
	 */
	public static final class SF_BOX extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public SF_BOX() {
			super("WG02");
			this.setName("SF_BOX Region");
		}
	}

				
	/** 
	 * A gridded, box-shaped region centered on Los Angeles (with the same 
	 * dimensions as that for the 2002 Working Group) used by the 2007 Working 
	 * Group on California Earthquake Probabilities (WGCEP). Grid spacing
	 * is 0.1&deg;.
	 * 
	 * TODO this may not be necessary; no references
	 */
	public static final class LA_BOX_GRIDDED extends CA_GriddedRegion {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public LA_BOX_GRIDDED() {
			this(0.1);
		}
		
		public LA_BOX_GRIDDED(double spacing) {
			super("WG07", spacing);
		}
	}
	
	/** 
	 * WGCEP 2007's Los Angeles Box.
	 */
	public static final class LA_BOX extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public LA_BOX() {
			super("WG07");
			this.setName("LA_BOX Region");
		}
	}

	/** 
	 * Northridge Box (used to demonstrate particularly characteristic MFDs in UCERF2).
	 */
	public static final class NORTHRIDGE_BOX extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public NORTHRIDGE_BOX() {
			super("NorthridgeBox");
			this.setName("NORTHRIDGE_BOX Region");
		}
	}

	/** 
	 * San Diago Box by request from Morgan Page
	 */
	public static final class SAN_DIEGO_BOX extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public SAN_DIEGO_BOX() {
			super("SanDiego");
			this.setName("SAN_DIEGO_BOX Region");
		}
	}


	/** 
	 * A box defining the region of the CyberShake 1.0 map region
	 */
	public static final class CYBERSHAKE_MAP_REGION extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public CYBERSHAKE_MAP_REGION() {
			super("CyberShake_Map");
			this.setName("CyberShake Map Region");
		}
	}
	
	/** 
	 * A box defining the region of the CyberShake 1.0 map region
	 */
	public static final class CYBERSHAKE_MAP_GRIDDED extends CA_GriddedRegion {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public CYBERSHAKE_MAP_GRIDDED(double spacing) {
			super("CyberShake_Map", spacing);
		}
	}
	
	/** 
	 * A box defining the region of the CyberShake CCA map region
	 */
	public static final class CYBERSHAKE_CCA_MAP_REGION extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public CYBERSHAKE_CCA_MAP_REGION() {
			super("CyberShake_Map_CCA");
			this.setName("CyberShake CCA Map Region");
		}
	}
	
	/** 
	 * A box defining the region of the CyberShake CCA map region
	 */
	public static final class CYBERSHAKE_CCA_MAP_GRIDDED extends CA_GriddedRegion {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public CYBERSHAKE_CCA_MAP_GRIDDED(double spacing) {
			super("CyberShake_Map_CCA", spacing);
		}
	}
	
	/** 
	 * A box defining the region of the CyberShake Bay Area map region
	 */
	public static final class CYBERSHAKE_BAY_AREA_MAP_REGION extends CA_Region {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		/** New instance of region. */
		public CYBERSHAKE_BAY_AREA_MAP_REGION() {
			super("CyberShake_Map_BayArea");
			this.setName("CyberShake Bay Area Map Region");
		}
	}
	
	private static Gson gson;
	private synchronized static Gson getGson() {
		if (gson == null) {
			GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
			builder.registerTypeHierarchyAdapter(Region.class, new Region.Adapter());
			builder.registerTypeHierarchyAdapter(GriddedRegion.class, new GriddedRegion.Adapter());
			gson = builder.create();
		}
		return gson;
	}
	
	private static Region loadRegion(String prefix) {
		// first try geojson
		BufferedReader reader = getReader(prefix+".geojson");
		if (reader != null)
			return getGson().fromJson(reader, Region.class);
		// fallback to coordinates
		reader = getReader(prefix+".coords");
		return new Region(readCoords(reader), BorderType.MERCATOR_LINEAR);
	}
	
	private static GriddedRegion loadGridRegion(String prefix, double spacing) {
		// first try geojson
		BufferedReader reader = getReader(prefix+"_"+(float)spacing+".geojson");
		if (reader != null)
			return getGson().fromJson(reader, GriddedRegion.class);
		// fallback to coordinates
		reader = getReader(prefix+".coords");
		return new GriddedRegion(readCoords(reader), BorderType.MERCATOR_LINEAR, spacing, GriddedRegion.ANCHOR_0_0);
	}
	
	private static BufferedReader getReader(String filename) {
		InputStream is = CaliforniaRegions.class.getResourceAsStream("/data/region/" + filename);
		if (is == null)
			return null;
		return new BufferedReader(new InputStreamReader(is));
	}

	/*
	 * Reads coordinate pairs from a file. Each line of the file should have
	 * a comma-delimited lat-ln pair e.g. 41.23,-117.89
	 */
	private static LocationList readCoords(BufferedReader br) {
		try {
			LocationList ll = new LocationList();
			String[] vals;
	        String s;
	        while ((s = br.readLine()) != null) {
	        	vals = s.trim().split(",");
	        	double lat = Double.valueOf(vals[0]);
	        	double lon = Double.valueOf(vals[1]);
	        	// keep backwards compatibility
	        	Location loc = Location.backwardsCompatible(lat, lon, 0d);
	        	ll.add(loc);
	        }
	        br.close();
	        return ll;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	public static void main(String[] args) throws IOException {
//		RegionUtils.regionToKML(new RELM_GRIDDED(), "Relm Gridded", Color.BLUE);
//		RegionUtils.regionToKML(new RELM_TESTING_GRIDDED(), "Relm Testing", Color.RED);
		
		File outputDir = new File("src/main/resources/data/region");
		Preconditions.checkState(outputDir.exists());
		
		Region[] regions = {
				new RELM_GRIDDED(),
				new RELM_TESTING(),
				new RELM_TESTING_GRIDDED(),
				new RELM_COLLECTION(),
				new RELM_COLLECTION_GRIDDED(),
				new RELM_NOCAL(),
				new RELM_NOCAL_GRIDDED(),
				new RELM_SOCAL(),
				new RELM_SOCAL_GRIDDED(),
				new SF_BOX_GRIDDED(),
				new SF_BOX(),
				new LA_BOX_GRIDDED(),
				new LA_BOX(),
				new NORTHRIDGE_BOX(),
				new SAN_DIEGO_BOX(),
				new CYBERSHAKE_MAP_REGION(),
				new CYBERSHAKE_CCA_MAP_REGION(),
				new CYBERSHAKE_BAY_AREA_MAP_REGION(),
		};
		
		for (Region region : regions) {
			String prefix;
			Type type;
			if (region instanceof CA_Region) {
				prefix = ((CA_Region)region).prefix;
				type = Region.class;
			} else {
				Preconditions.checkState(region instanceof CA_GriddedRegion);
				CA_GriddedRegion caGrid = (CA_GriddedRegion)region;
				prefix = caGrid.prefix+"_"+(float)caGrid.getSpacing();
				type = GriddedRegion.class;
			}
			System.out.println(region.getName()+" has prefix "+prefix);
			if (region instanceof GriddedRegion)
				System.out.println("\t"+((GriddedRegion)region).getNodeCount()+" grid nodes");
			File jsonFile = new File(outputDir, prefix+".geojson");
			if (jsonFile.exists()) {
				// try reading it
				BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
				System.out.println("Deserializing from: "+jsonFile.getAbsolutePath());
				Region serialized = getGson().fromJson(reader, type);
				System.out.println("\tdeserialized name: "+serialized.getName());
				System.out.println("\tequals? "+region.equals(serialized));
				System.out.println("\tequalsRegion? "+region.equalsRegion(serialized));
			} else {
				// write it
				BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile));
				System.out.println("Serializing to: "+jsonFile.getAbsolutePath());
				getGson().toJson(region, type, writer);
				writer.close();
			}
		}
	}

}
