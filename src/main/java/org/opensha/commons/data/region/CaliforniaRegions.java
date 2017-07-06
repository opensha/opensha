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

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;

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
	
	
	/** 
	 * Gridded region used in the Regional Earthquake Likelihood 
	 * Models (RELM) project. Grid spacing is 0.1&deg;.
	 * 
	 * NOTE: This is an alternative version of the RELM_TESTING_GRIDDED region
	 * below.
	 */
	@Deprecated
	public static final class RELM_GRIDDED extends 
			GriddedRegion {
		/** New instance of region. */
		public RELM_GRIDDED() {
			super(readCoords("RELM.coords"), 
					BorderType.MERCATOR_LINEAR, 0.1, ANCHOR_0_0);
		}
	}

	/** 
	 * A simplified representation of the RELM gridded region. 
	 */
	public static final class RELM_TESTING extends Region {
		/** New instance of region. */
		public RELM_TESTING() {
			super(readCoords("RELM_testing.coords"), 
					BorderType.MERCATOR_LINEAR);
			this.setName("RELM_TESTING Region");
		}
	}
			
	/** 
	 * A simplified representation of the RELM gridded region.
	 * Grid spacing is 0.1&deg;.
	 */
	public static final class RELM_TESTING_GRIDDED extends 
			GriddedRegion {
		/** New instance of region. */
		public RELM_TESTING_GRIDDED() {
			this(0.1);
		}
		
		/** New instance of region. */
		public RELM_TESTING_GRIDDED(double spacing) {
			super(readCoords("RELM_testing.coords"), 
					BorderType.MERCATOR_LINEAR, spacing, ANCHOR_0_0);
			this.setName("RELM_TESTING Region");
		}
	}

	/** 
	 * Expanded RELM region used to capture large external events.
	 */
	public static final class RELM_COLLECTION extends Region {
		/** New instance of region. */
		public RELM_COLLECTION() {
			super(readCoords("RELM_collection.coords"), 
					BorderType.MERCATOR_LINEAR);
			this.setName("RELM_COLLECTION Region");
		}
	}

	/** 
	 * Expanded gridded RELM region used to capture large external events.
	 * Grid spacing is 0.1&deg;.
	 */
	public static final class RELM_COLLECTION_GRIDDED extends 
			GriddedRegion {
		/** New instance of region. */
		public RELM_COLLECTION_GRIDDED() {
			super(readCoords("RELM_collection.coords"), 
					BorderType.MERCATOR_LINEAR, 0.1, ANCHOR_0_0);
		}
	}

	/** 
	 * Northern half of the RELM region.
	 */
	public static final class RELM_NOCAL extends Region {
		/** New instance of region. */
		public RELM_NOCAL() {
			super(readCoords("RELM_NoCal.coords"), 
					BorderType.MERCATOR_LINEAR);
			this.setName("RELM_NOCAL Region");
		}
	}

	/** 
	 * Northern half of the gridded RELM region. Grid spacing is 0.1&deg;.
	 */
	public static final class RELM_NOCAL_GRIDDED extends 
			GriddedRegion {
		/** New instance of region. */
		public RELM_NOCAL_GRIDDED() {
			super(readCoords("RELM_NoCal.coords"), 
					BorderType.MERCATOR_LINEAR, 0.1, ANCHOR_0_0);
			this.setName("RELM_NOCAL Region");
		}
	}

	/** 
	 * Southern half of the RELM region.
	 */
	public static final class RELM_SOCAL extends Region {
		/** New instance of region. */
		public RELM_SOCAL() {
			super(readCoords("RELM_SoCal.coords"), 
					BorderType.MERCATOR_LINEAR);
			this.setName("RELM_SOCAL Region");
		}
	}

	/** 
	 * Southern half of the gridded RELM region. Grid spacing is 0.1&deg;.
	 */
	public static final class RELM_SOCAL_GRIDDED extends 
			GriddedRegion {
		/** New instance of region. */
		public RELM_SOCAL_GRIDDED() {
			super(readCoords("RELM_SoCal.coords"), 
					BorderType.MERCATOR_LINEAR, 0.1, ANCHOR_0_0);
			this.setName("RELM_SOCAL Region");
		}
	}

	/** 
	 * A gridded, box-shaped central California region used in the 2002  
	 * Working Group on California Earthquake Probabilities (WGCEP).
	 * Grid spacing is 0.1&deg;.
	 */
	public static final class SF_BOX_GRIDDED extends 
			GriddedRegion {
		/** New instance of region. */
		public SF_BOX_GRIDDED() {
			super(readCoords("WG02.coords"), 
					BorderType.MERCATOR_LINEAR, 0.1, ANCHOR_0_0);
		}
	}
	
	/** 
	 * WGCEP 2002's San Francisco Box.
	 */
	public static final class SF_BOX extends Region {
		/** New instance of region. */
		public SF_BOX() {
			super(readCoords("WG02.coords"), 
					BorderType.MERCATOR_LINEAR);
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
	public static final class LA_BOX_GRIDDED extends 
			GriddedRegion {
		/** New instance of region. */
		public LA_BOX_GRIDDED() {
			this(0.1);
		}
		
		public LA_BOX_GRIDDED(double spacing) {
			super(readCoords("WG07.coords"), 
					BorderType.MERCATOR_LINEAR, spacing,
					new Location(34,-118));
		}
	}
	
	/** 
	 * WGCEP 2007's Los Angeles Box.
	 */
	public static final class LA_BOX extends Region {
		/** New instance of region. */
		public LA_BOX() {
			super(readCoords("WG07.coords"), 
					BorderType.MERCATOR_LINEAR);
			this.setName("LA_BOX Region");
		}
	}

	/** 
	 * Northridge Box (used to demonstrate particularly characteristic MFDs in UCERF2).
	 */
	public static final class NORTHRIDGE_BOX extends Region {
		/** New instance of region. */
		public NORTHRIDGE_BOX() {
			super(readCoords("NorthridgeBox.coords"), 
					BorderType.MERCATOR_LINEAR);
			this.setName("NORTHRIDGE_BOX Region");
		}
	}

	/** 
	 * San Diago Box by request from Morgan Page
	 */
	public static final class SAN_DIEGO_BOX extends Region {
		/** New instance of region. */
		public SAN_DIEGO_BOX() {
			super(readCoords("SanDiego.coords"), 
					BorderType.MERCATOR_LINEAR);
			this.setName("SAN_DIEGO_BOX Region");
		}
	}


	/** 
	 * A box defining the region of the CyberShake 1.0 map region
	 */
	public static final class CYBERSHAKE_MAP_REGION extends Region {
		/** New instance of region. */
		public CYBERSHAKE_MAP_REGION() {
			super(readCoords("CyberShake_Map.coords"), 
					BorderType.MERCATOR_LINEAR);
			this.setName("CyberShake Map Region");
		}
	}
	
	/** 
	 * A box defining the region of the CyberShake 1.0 map region
	 */
	public static final class CYBERSHAKE_MAP_GRIDDED extends GriddedRegion {
		/** New instance of region. */
		public CYBERSHAKE_MAP_GRIDDED(double spacing) {
			super(new CYBERSHAKE_MAP_REGION(), spacing, null);
		}
	}
	
	/** 
	 * A box defining the region of the CyberShake 1.0 map region
	 */
	public static final class CYBERSHAKE_CCA_MAP_REGION extends Region {
		/** New instance of region. */
		public CYBERSHAKE_CCA_MAP_REGION() {
			super(readCoords("CyberShake_Map_CCA.coords"), 
					BorderType.MERCATOR_LINEAR);
			this.setName("CyberShake CCA Map Region");
		}
	}
	
	/** 
	 * A box defining the region of the CyberShake 1.0 map region
	 */
	public static final class CYBERSHAKE_CCA_MAP_GRIDDED extends GriddedRegion {
		/** New instance of region. */
		public CYBERSHAKE_CCA_MAP_GRIDDED(double spacing) {
			super(new CYBERSHAKE_CCA_MAP_REGION(), spacing, null);
		}
	}

	/*
	 * Reads coordinate pairs from a file. Each line of the file should have
	 * a comma-delimited lat-ln pair e.g. 41.23,-117.89
	 */
	private static LocationList readCoords(String filename) {
		BufferedReader br;
		try {
			InputStream is = CaliforniaRegions.class.getResourceAsStream("/resources/data/region/" + filename);
			br = new BufferedReader(new InputStreamReader(is));
			LocationList ll = new LocationList();
			String[] vals;
	        String s;
	        while ((s = br.readLine()) != null) {
	        	vals = s.trim().split(",");
	        	double lat = Double.valueOf(vals[0]);
	        	double lon = Double.valueOf(vals[1]);
	        	Location loc = new Location(lat, lon);
	        	ll.add(loc);
	        }
	        br.close();
	        return ll;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	public static void main(String[] args) {
		RegionUtils.regionToKML(new RELM_GRIDDED(), "Relm Gridded", Color.BLUE);
		RegionUtils.regionToKML(new RELM_TESTING_GRIDDED(), "Relm Testing", Color.RED);
		
		
		
	}

}
