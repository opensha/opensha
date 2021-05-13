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

package org.opensha.commons.util.binFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

public class GeolocatedRectangularBinaryMesh2DCalculator extends
		BinaryMesh2DCalculator {
	
	public static final boolean D = false;
	
	private double minLat;
	private double maxLat;
	private double minLon;
	private double maxLon;
	private double gridSpacingX;
	private double gridSpacingY;
	
	private boolean startBottom;
	private boolean startLeft;
	
	private boolean wrapX = false;
	private boolean wrapY= false;
	
	private boolean allLonPos = false;

	/**
	 * Creates a new GeolocatedRectangularBinaryMesh2DCalculator assuming that the data starts at the bottom left
	 * corner of the region (at minLat, minLon) and is ordered fast-X-Y.
	 * 
	 * @param numType
	 * @param nx
	 * @param ny
	 * @param minLat
	 * @param minLon
	 * @param gridSpacing
	 */
	public GeolocatedRectangularBinaryMesh2DCalculator(DataType numType, int nx, int ny,
			double minLat, double minLon, double gridSpacing) {
		this(numType, nx, ny, minLat, minLon, true, true, gridSpacing);
	}

	/**
	 * Creates a new GeolocatedRectangularBinaryMesh2DCalculator assuming that the data starts at the bottom left
	 * corner of the region (at minLat, minLon) and is ordered fast-X-Y.
	 * 
	 * TODO update documentation
	 * 
	 * @param numType
	 * @param nx
	 * @param ny
	 * @param minLat
	 * @param minLon
	 * @param gridSpacing
	 */
	public GeolocatedRectangularBinaryMesh2DCalculator(DataType numType, int nx, int ny,
			double startLat, double startLon, boolean startBottom, boolean startLeft, double gridSpacing) {
		this(numType, nx, ny, startLat, startLon, startBottom, startLeft, gridSpacing, gridSpacing);
	}

	/**
	 * Creates a new GeolocatedRectangularBinaryMesh2DCalculator assuming that the data starts at the bottom left
	 * corner of the region (at minLat, minLon) and is ordered fast-X-Y.
	 * 
	 * TODO update documentation
	 * 
	 * @param numType
	 * @param nx
	 * @param ny
	 * @param minLat
	 * @param minLon
	 * @param gridSpacingX
	 * @param gridSpacingY
	 */
	public GeolocatedRectangularBinaryMesh2DCalculator(DataType numType, int nx, int ny,
			double startLat, double startLon, boolean startBottom, boolean startLeft,
			double gridSpacingX, double gridSpacingY) {
		super(numType, nx, ny);
		
		if (startBottom) {
			minLat = startLat;
			maxLat = startLat + gridSpacingY * (ny-1);
		} else {
			maxLat = startLat;
			minLat = startLat - gridSpacingY * (ny-1);
		}
		
		if (startLeft) {
			minLon = startLon;
			maxLon = startLon + gridSpacingX * (nx-1);
		} else {
			maxLon = startLon;
			minLon = startLon - gridSpacingX * (nx-1);
		}
		
		this.startBottom = startBottom;
		this.startLeft = startLeft;
		this.gridSpacingX = gridSpacingX;
		this.gridSpacingY = gridSpacingY;
		
		if (minLon >= 0)
			allLonPos = true;
		
		if (D) {
			System.out.println("minLat: " + minLat + ", maxLat: " + maxLat);
			System.out.println("minLon: " + minLon + ", maxLon: " + maxLon);
		}
		
		if ((minLat + 180) == (maxLat + gridSpacingY)) {
			if (D) System.out.println("Wrapping Y!");
			wrapY = true;
		}
		if ((minLon + 360) == (maxLon + gridSpacingX)) {
			if (D) System.out.println("Wrapping X!");
			wrapX = true;
		}
	}
	
	public static GeolocatedRectangularBinaryMesh2DCalculator readHDR(File hdrFile) throws IOException {
		/*
		 * EXAMPLE:
		 * BYTEORDER      I
		 * LAYOUT         BIL
		 * NROWS          916
		 * NCOLS          1266
		 * NBANDS         1
		 * NBITS          32
		 * BANDROWBYTES   5064
		 * TOTALROWBYTES  5064
		 * PIXELTYPE      FLOAT
		 * ULXMAP         -119.38000000000001
		 * ULYMAP         35.08
		 * XDIM           0.002
		 * YDIM           0.002
		 */
		DataType dataType = null;
		Integer nx = null, ny = null;
		MeshOrder meshOrder = MeshOrder.FAST_XY;
		Double gridSpacing = null;
		Double startLat = null, startLon = null;
		for (String line : Files.readLines(hdrFile, Charset.defaultCharset())) {
			line = line.trim();
			String[] split = line.split("\\s+");
			Preconditions.checkState(split.length == 2, "Bad split on line: "+line);
			switch (split[0]) {
			case "PIXELTYPE":
				dataType = DataType.valueOf(split[1]);
				Preconditions.checkNotNull(dataType, "Unkown data type: %s", split[1]);
				break;
			case "NROWS":
				ny = Integer.parseInt(split[1]);
				break;
			case "NCOLS":
				nx = Integer.parseInt(split[1]);
				break;
			case "LAYOUT":
				Preconditions.checkState(split[1].equals("BIL"), "Only BIL layout currently supported");
				break;
			case "XDIM":
				if (gridSpacing == null)
					gridSpacing = Double.parseDouble(split[1]);
				else
					Preconditions.checkState(gridSpacing == Double.parseDouble(split[1]), "XDIM must equal YDIM");
				break;
			case "YDIM":
				if (gridSpacing == null)
					gridSpacing = Double.parseDouble(split[1]);
				else
					Preconditions.checkState(gridSpacing == Double.parseDouble(split[1]), "XDIM must equal YDIM");
				break;
			case "ULXMAP":
				startLon = Double.parseDouble(split[1]);
				break;
			case "ULYMAP":
				startLat = Double.parseDouble(split[1]);
				break;
				
			default:
				break;
			}
		}
		Preconditions.checkNotNull(dataType, "data type not specified in input file (no PIXELTYPE line)");
		Preconditions.checkNotNull(nx, "nx not specified in input file (no NCOLS line)");
		Preconditions.checkNotNull(ny, "ny not specified in input file (no NROWS line)");
		Preconditions.checkNotNull(gridSpacing, "gridSpacing not specified in input file (no XDIM/YDIM lines)");
		Preconditions.checkNotNull(startLat, "startLat not specified in input file (no ULYMAP line)");
		Preconditions.checkNotNull(startLon, "startLon not specified in input file (no ULXMAP line)");
		GeolocatedRectangularBinaryMesh2DCalculator calc =new GeolocatedRectangularBinaryMesh2DCalculator(
				dataType, nx, ny, startLat, startLon, false, true, gridSpacing);
		calc.setMeshOrder(meshOrder);
		return calc;
	}
	
	public long[] calcClosestLocationIndices(Location loc) {
		return calcClosestLocationIndices(loc.getLatitude(), loc.getLongitude());
	}
	
	public long[] calcClosestLocationIndices(double lat, double lon) {
		long x = calcX(lon);
		long y = calcY(lat);
		
		if (x < 0 || y < 0) {
			return null;
		}
		
		if (x >= nx) {
			if (wrapX)
				x = x % nx;
			else
				return null;
		}
		
		if (y >= ny) {
			if (wrapY)
				y = y % ny;
			else
				return null;
		}
		
		long pt[] = { x, y };
		
		return pt;
	}
	
	public long calcClosestLocationIndex(Location loc) {
		return calcClosestLocationIndex(loc.getLatitude(), loc.getLongitude());
	}
	
	public long calcClosestLocationIndex(double lat, double lon) {
		long pt[] = calcClosestLocationIndices(lat, lon);
		
		// if pt is null, return -1, else return mesh index
		if (pt == null)
			return -1;
		else
			return this.calcMeshIndex(pt[0], pt[1]);
	}
	
	public long calcClosestLocationFileIndex(Location loc) {
		return calcClosestLocationFileIndex(loc.getLatitude(), loc.getLongitude());
	}
	
	public long calcClosestLocationFileIndex(double lat, double lon) {
		long pt[] = calcClosestLocationIndices(lat, lon);
		
		// if pt is null, return -1, else return file index
		if (pt == null)
			return -1;
		else
			return this.calcFileIndex(pt[0], pt[1]);
	}
	
	public Location getLocationForPoint(long x, long y) {
		double lat;
		if (startBottom)
			lat = minLat + y * gridSpacingY;
		else
			lat = maxLat - y * gridSpacingY;
		double lon;
		if (startLeft)
			lon = minLon + x * gridSpacingX;
		else
			lon = maxLon - x * gridSpacingX;
		
		return new Location(lat, lon);
	}
	
	public Location calcClosestLocation(Location loc) {
		return calcClosestLocation(loc.getLatitude(), loc.getLongitude());
	}
	
	public Location calcClosestLocation(double lat, double lon) {
		long pt[] = calcClosestLocationIndices(lat, lon);
		
		// if pt is null, return null, else return location
		if (pt == null)
			return null;
		else
			return getLocationForPoint(pt[0], pt[1]);
	}
	
	private long calcX(double lon) {
		if (allLonPos && lon < 0)
			lon += 360;
		if (startLeft)
			return ((long)((lon - minLon) / gridSpacingX + 0.5));
		else
			return (long)((maxLon - lon) / gridSpacingX + 0.5);
	}
	
	private long calcY(double lat) {
		if (startBottom)
			return ((long)((lat - minLat) / gridSpacingY + 0.5));
		else
			return ((long)((maxLat - lat) / gridSpacingY + 0.5));
	}

	public double getMinLat() {
		return minLat;
	}

	public double getMaxLat() {
		return maxLat;
	}

	public double getMinLon() {
		return minLon;
	}

	public double getMaxLon() {
		return maxLon;
	}

	public double getGridSpacingX() {
		return gridSpacingX;
	}

	public double getGridSpacingY() {
		return gridSpacingY;
	}

	public boolean isStartBottom() {
		return startBottom;
	}

	public void setStartBottom(boolean startBottom) {
		this.startBottom = startBottom;
	}

	public boolean isStartLeft() {
		return startLeft;
	}

	public void setStartLeft(boolean startLeft) {
		this.startLeft = startLeft;
	}
	
	public Region getApplicableRegion() {
//		try {
			return new Region(
					new Location(minLat,minLon),
					new Location(maxLat,maxLon));
//		} catch (RegionConstraintException e) {
//			e.printStackTrace();
//			return null;
//		}
	}
	
	public boolean isWrapLat() {
		return wrapY;
	}
	
	public boolean isWrapLon() {
		return wrapX;
	}

}
