/**
 * 
 */
package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.util.FocalMech;

import com.google.common.collect.Maps;




/**
 * Read NSHMP background seismicity files.  This trims the western edge of the RELM
 * region so there are no zero rate bins (i.e., the number of locations is the same
 * as the number of non-zero rate cells).  The number of locations in the result is
 * numLocs=7654.
 * 
 * I verified sumOfAllAvals against my hand sum using Igor.
 * 
 * @author Ned Field & Vipin Gupta
 *
 */
public class NSHMP_GridSourceGenerator implements Serializable {

	private CaliforniaRegions.RELM_GRIDDED region;

	private final WC1994_MagLengthRelationship magLenRel = new WC1994_MagLengthRelationship();

	private final static String PATH = "/data/erf/ucerf2/griddedSeis/";
	private final static String LAT_LON_FILENAME = PATH + "LonsLats.txt";

	private int[] aValIndexForLocIndex;
	private double[] sumOfAllAvals;
	private int numAvals;
	// a-val and mmax values
	protected double[] agrd_brawly_out, agrd_creeps_out, agrd_cstcal_out, agrd_deeps_out,
	agrd_mendos_out, agrd_wuscmp_out, agrd_wusext_out, agrd_impext_out, area1new_agrid, 
	area2new_agrid, area3new_agrid, area4new_agrid,mojave_agrid, sangreg_agrid,
	fltmmaxAll21ch_out6, fltmmaxAll21gr_out6, fltmmaxAll24ch_out6, fltmmaxAll24gr_out6;

	protected final static double B_VAL = 0.8;
	protected final static double B_VAL_CREEPING = 0.9;
	protected final static double DELTA_MAG = 0.1;

	protected final double C_ZONES_MAX_MAG = 7.6;
	protected final double DEFAULT_MAX_MAG=7.0;
	
	private double[] fracStrikeSlip,fracNormal,fracReverse;

	private double maxFromMaxMagFiles;
	
	private double magCutOff = Double.NaN;
	
	private WeightedList<PointSourceDistanceCorrection> distCorrs;

	public NSHMP_GridSourceGenerator(WeightedList<PointSourceDistanceCorrection> distCorrs) {
		region = new CaliforniaRegions.RELM_GRIDDED();
		this.distCorrs = distCorrs;
		//LocationList locList = getLocationList();

		// make polygon from the location list
		//createEvenlyGriddedGeographicRegion(locList, GRID_SPACING);

		setA_ValIndexForLocIndex();
		//System.out.println("numAvals="+numAvals+"; numLocs="+getNumGridLocs());
		//System.out.println("reading all files");
		readAllGridFiles();
		
		getFocalMechFractions();
		//System.out.println("done");

		/*  This is to output and check the sum 
		    Location loc;
		    System.out.println("test_lat\ttest_lon\ttest_rate");
		    for(int i=0;i<this.getNumGridLocs();i++) {
		    		loc = this.getGridLocation(i);
		    		System.out.println((float)loc.getLatitude()+"\t"+
		    				(float)loc.getLongitude()+"\t"+
		    				(float)sumOfAllAvals[i]);
		    }
		 */

	}

	public GriddedRegion getGriddedRegion() {
		return region;
	}
	
	/**
	 * Whether all sources should just be treated as point sources
	 * 
	 * @param areAllPointSources
	 */
	public void setAsPointSources(boolean areAllPointSources) {
		if(areAllPointSources) this.magCutOff = 10.0;
		else this.magCutOff = 6.0;
	}
	
	public void setDistanceCorrections(WeightedList<PointSourceDistanceCorrection> distCorrs) {
		this.distCorrs = distCorrs;
	}
	
	/**
	 * Get all fixed-strike gridded sources
	 * 
	 * @param includeC_Zones
	 * @param duration
	 * @param applyBulgeReduction
	 * @param applyMaxMagGrid
	 * @return
	 */ 
	public ArrayList<ProbEqkSource> getAllFixedStrikeSources(double duration) {
		ArrayList<ProbEqkSource> sources = new ArrayList<ProbEqkSource>();
		
		sources.addAll(getBrawleyFixedStrikeSources(duration));
		sources.addAll(getMendosFixedStrikeSources(duration));
		sources.addAll(getCreepsFixedStrikeSources(duration));
		sources.addAll(getArea1FixedStrikeSources(duration));
		sources.addAll(getArea2FixedStrikeSources(duration));
		sources.addAll(getArea3FixedStrikeSources(duration));
		sources.addAll(getArea4FixedStrikeSources(duration));
		sources.addAll(getMojaveFixedStrikeSources(duration));
		sources.addAll(getSangregFixedStrikeSources(duration));
		
		return sources;
	}
	
	
	public ArrayList<Point2Vert_FaultPoisSource> getBrawleyFixedStrikeSources(double duration) {
		ArrayList<Point2Vert_FaultPoisSource> sources = new ArrayList<Point2Vert_FaultPoisSource>();
		for(int locIndex=0;locIndex<region.getNodeCount();locIndex++) {
			if(agrd_brawly_out[locIndex] >0) {
				GutenbergRichterMagFreqDist mfd = getMFD(5.0, 6.5, agrd_brawly_out[locIndex], B_VAL, false);
				sources.add(new Point2Vert_FaultPoisSource(region.locationForIndex(locIndex), mfd, magLenRel, 157, duration, magCutOff, 0.5,0.5,0, distCorrs));
				sources.get(sources.size()-1).setName("Brawley Point2Vert_FaultPoisSource");
			}
		}
		return sources;
	}

	public ArrayList<Point2Vert_FaultPoisSource> getMendosFixedStrikeSources(double duration) {
		ArrayList<Point2Vert_FaultPoisSource> sources = new ArrayList<Point2Vert_FaultPoisSource>();
		for(int locIndex=0;locIndex<region.getNodeCount();locIndex++) {
			if(agrd_mendos_out[locIndex] >0) {
				GutenbergRichterMagFreqDist mfd = getMFD(5.0, 7.3, agrd_mendos_out[locIndex], B_VAL, false);
				sources.add(new Point2Vert_FaultPoisSource(region.locationForIndex(locIndex), mfd, magLenRel,90, duration, magCutOff, 0.5,0.0,0.5, distCorrs));
				sources.get(sources.size()-1).setName("Mendos Point2Vert_FaultPoisSource");
			}
		}
		return sources;
	}
	
	public ArrayList<Point2Vert_FaultPoisSource> getCreepsFixedStrikeSources(double duration) {
		ArrayList<Point2Vert_FaultPoisSource> sources = new ArrayList<Point2Vert_FaultPoisSource>();
		for(int locIndex=0;locIndex<region.getNodeCount();locIndex++) {
			if(agrd_creeps_out[locIndex] >0) {
				GutenbergRichterMagFreqDist mfd = getMFD(5.0, 6, agrd_creeps_out[locIndex], B_VAL_CREEPING, false);
				sources.add(new Point2Vert_FaultPoisSource(region.locationForIndex(locIndex), mfd, magLenRel,360-42.5, duration, magCutOff, 1.0,0.0,0.0, distCorrs));
				sources.get(sources.size()-1).setName("Creeps Point2Vert_FaultPoisSource");
			}
		}
		return sources;
	}

	public ArrayList<Point2Vert_FaultPoisSource> getArea1FixedStrikeSources(double duration) {
		ArrayList<Point2Vert_FaultPoisSource> sources = new ArrayList<Point2Vert_FaultPoisSource>();
		for(int locIndex=0;locIndex<region.getNodeCount();locIndex++) {
			if(area1new_agrid[locIndex] >0) {
				GutenbergRichterMagFreqDist mfd = getMFD(6.5, C_ZONES_MAX_MAG, area1new_agrid[locIndex], B_VAL, false);
				sources.add(new Point2Vert_FaultPoisSource(region.locationForIndex(locIndex), mfd, magLenRel,360-35, duration, magCutOff, 1.0,0.0,0.0, distCorrs));
				sources.get(sources.size()-1).setName("Area1 Point2Vert_FaultPoisSource");
			}
		}
		return sources;
	}

	public ArrayList<Point2Vert_FaultPoisSource> getArea2FixedStrikeSources(double duration) {
		ArrayList<Point2Vert_FaultPoisSource> sources = new ArrayList<Point2Vert_FaultPoisSource>();
		for(int locIndex=0;locIndex<region.getNodeCount();locIndex++) {
			if(area2new_agrid[locIndex] >0) {
				GutenbergRichterMagFreqDist mfd = getMFD(6.5, C_ZONES_MAX_MAG, area2new_agrid[locIndex], B_VAL, false);
				sources.add(new Point2Vert_FaultPoisSource(region.locationForIndex(locIndex), mfd, magLenRel,360-25, duration, magCutOff, 1.0,0.0,0.0, distCorrs));
				sources.get(sources.size()-1).setName("Area2 Point2Vert_FaultPoisSource");
			}
		}
		return sources;
	}
	
	public ArrayList<Point2Vert_FaultPoisSource> getArea3FixedStrikeSources(double duration) {
		ArrayList<Point2Vert_FaultPoisSource> sources = new ArrayList<Point2Vert_FaultPoisSource>();
		for(int locIndex=0;locIndex<region.getNodeCount();locIndex++) {
			if(area3new_agrid[locIndex] >0) {
				GutenbergRichterMagFreqDist mfd = getMFD(6.5, C_ZONES_MAX_MAG, area3new_agrid[locIndex], B_VAL, false);
				sources.add(new Point2Vert_FaultPoisSource(region.locationForIndex(locIndex), mfd, magLenRel, 360-45, duration, magCutOff, 1.0,0.0,0.0, distCorrs));
				sources.get(sources.size()-1).setName("Area3 Point2Vert_FaultPoisSource");
			}
		}
		return sources;
	}

	public ArrayList<Point2Vert_FaultPoisSource> getArea4FixedStrikeSources(double duration) {
		ArrayList<Point2Vert_FaultPoisSource> sources = new ArrayList<Point2Vert_FaultPoisSource>();
		for(int locIndex=0;locIndex<region.getNodeCount();locIndex++) {
			if(area4new_agrid[locIndex] >0) {
				GutenbergRichterMagFreqDist mfd = getMFD(6.5, C_ZONES_MAX_MAG, area4new_agrid[locIndex], B_VAL, false);
				sources.add(new Point2Vert_FaultPoisSource(region.locationForIndex(locIndex), mfd, magLenRel, 360-45, duration, magCutOff, 1.0,0.0,0.0, distCorrs));
				sources.get(sources.size()-1).setName("Area4 Point2Vert_FaultPoisSource");
			}
		}
		return sources;
	}
	
	public ArrayList<Point2Vert_FaultPoisSource> getMojaveFixedStrikeSources(double duration) {
		ArrayList<Point2Vert_FaultPoisSource> sources = new ArrayList<Point2Vert_FaultPoisSource>();
		for(int locIndex=0;locIndex<region.getNodeCount();locIndex++) {
			if(mojave_agrid[locIndex] >0) {
				GutenbergRichterMagFreqDist mfd = getMFD(6.5, C_ZONES_MAX_MAG, mojave_agrid[locIndex], B_VAL, false);
				sources.add(new Point2Vert_FaultPoisSource(region.locationForIndex(locIndex), mfd, magLenRel, 360-47, duration, magCutOff, 1.0,0.0,0.0, distCorrs));
				sources.get(sources.size()-1).setName("Mojave Point2Vert_FaultPoisSource");
			}
		}
		return sources;
	}

	public ArrayList<Point2Vert_FaultPoisSource> getSangregFixedStrikeSources(double duration) {
		ArrayList<Point2Vert_FaultPoisSource> sources = new ArrayList<Point2Vert_FaultPoisSource>();
		for(int locIndex=0;locIndex<region.getNodeCount();locIndex++) {
			if(sangreg_agrid[locIndex] >0) {
				GutenbergRichterMagFreqDist mfd = getMFD(6.5, C_ZONES_MAX_MAG, sangreg_agrid[locIndex], B_VAL, false);
				sources.add(new Point2Vert_FaultPoisSource(region.locationForIndex(locIndex), mfd, magLenRel,360-67, duration, magCutOff, 1.0,0.0,0.0, distCorrs));
				sources.get(sources.size()-1).setName("Sangreg Point2Vert_FaultPoisSource");
			}
		}
		return sources;
	}


	

	/**
	 * Get all random strike gridded sources
	 * 
	 * @param duration
	 * @return
	 */ 
	public ArrayList<ProbEqkSource> getAllRandomStrikeGriddedSources(double duration) {
		int numSources =  region.getNodeCount();
		ArrayList<ProbEqkSource> sources = new ArrayList<ProbEqkSource>();
		for(int i=0; i<numSources; ++i) {
			sources.add(getRandomStrikeGriddedSource(i, duration));
		}
		return sources;
	}


	/**
	 * Get all cross hair gridded sources
	 * 
	 * @param duration
	 * @return
	 */ 
	public ArrayList<ProbEqkSource> getAllCrosshairGriddedSources(double duration) {
		int numSources =  region.getNodeCount();
		ArrayList<ProbEqkSource> sources = new ArrayList<ProbEqkSource>();
		for(int i=0; i<numSources; ++i) {
			sources.add(this.getCrosshairGriddedSource(i, duration));
		}
		return sources;
	}
	
	/**
	 * Get all NSHMP 2013 styled grid sources.
	 * 
	 * @param duration
	 * @return
	 */ 
	public List<ProbEqkSource> getAllNSHMP13_GriddedSources(double duration) {
		int numSources =  region.getNodeCount();
		ArrayList<ProbEqkSource> sources = new ArrayList<ProbEqkSource>();
		for(int i=0; i<numSources; ++i) {
			sources.add(this.getNSHMP13_GriddedSource(i, duration));
		}
		return sources;
	}

	/**
	 * Get the random strike gridded source at a specified index (this ignores the fixed-strike contribution)
	 * 
	 * @param srcIndex
	 * @return
	 */
	public ProbEqkSource getRandomStrikeGriddedSource(int srcIndex, double duration) {
		SummedMagFreqDist mfdAtLoc = getTotMFD_atLoc(srcIndex,  false, true,  true, false, false);
		return new Point2Vert_FaultPoisSource(region.locationForIndex(srcIndex), mfdAtLoc, magLenRel, duration, magCutOff,
				fracStrikeSlip[srcIndex],fracNormal[srcIndex],fracReverse[srcIndex], false, distCorrs);
	}
	
	/**
	 * Get Crosshair gridded source at a specified index (this ignores the fixed-strike contribution)
	 * 
	 * @param srcIndex
	 * @return
	 */
	public ProbEqkSource getCrosshairGriddedSource(int srcIndex, double duration) {
		boolean includeDeeps = false;
		//boolean includeDeeps = true;
		SummedMagFreqDist mfdAtLoc = getTotMFD_atLoc(srcIndex,  false, true,  true, false, includeDeeps);
		return new Point2Vert_FaultPoisSource(region.locationForIndex(srcIndex), mfdAtLoc, magLenRel, duration, magCutOff,
				fracStrikeSlip[srcIndex],fracNormal[srcIndex],fracReverse[srcIndex], true, distCorrs);
	}
	
	/**
	 * Returns a NSHMP 2013 style gridded source that is compatible with all
	 * NGAW2 relationships.
	 * @param srcIndex
	 * @param duration
	 * @return the source
	 */
	public ProbEqkSource getNSHMP13_GriddedSource(int srcIndex, double duration) {
		boolean includeDeeps = false;
		//boolean includeDeeps = true;
		SummedMagFreqDist mfdAtLoc = getTotMFD_atLoc(srcIndex,  false, true,  true, false, includeDeeps);
		Map<FocalMech, Double> mechMap = Maps.newEnumMap(FocalMech.class);
		mechMap.put(FocalMech.STRIKE_SLIP, fracStrikeSlip[srcIndex]);
		mechMap.put(FocalMech.NORMAL, fracNormal[srcIndex]);
		mechMap.put(FocalMech.REVERSE, fracReverse[srcIndex]);
		return new PointSourceNshm(region.locationForIndex(srcIndex), mfdAtLoc,
			duration, mechMap, distCorrs);
	}
	



	/**
	 * Get the number of Random Strike gridded sources
	 *  
	 * @return
	 */
	public int getNumSources() {
		return region.getNodeCount();
	}


	/**
	 * This determins the index in each grid file that corresponds to the 
	 * ith location in the RELM regions, setting the value to -1 if there is
	 * no NSHMP grid cell for any of the RELM sites (the most western lons).
	 *
	 */
	private void setA_ValIndexForLocIndex() {
		aValIndexForLocIndex = new int[region.getNodeCount()];
		//initialize values to -1 (bogus index) because not all RELM locs have a corresponding line in the grid file
		for(int i=0;i<aValIndexForLocIndex.length;i++)
			aValIndexForLocIndex[i] = -1;
		numAvals = 0;

		try { 
			// Region filename
			InputStreamReader regionFileReader = new InputStreamReader(getClass().getResourceAsStream(LAT_LON_FILENAME));
			BufferedReader br = new BufferedReader(regionFileReader);
			String latlonLine = br.readLine(); // skip header line
			Location loc;
			double lat, lon;
			int fileIndex = 0;

			latlonLine = br.readLine();
			while(latlonLine!=null) { // iterate over all lines of the file
				StringTokenizer tokenizer = new StringTokenizer(latlonLine);
				lon = Double.parseDouble(tokenizer.nextToken());
				lat = Double.parseDouble(tokenizer.nextToken());
//				loc = new Location(lat, lon);
				loc = Location.backwardsCompatible(lat, lon, 0d);
				
				if(region.contains(loc)) {
					aValIndexForLocIndex[region.indexForLocation(loc)] = fileIndex;
				}
				latlonLine = br.readLine();
				fileIndex += 1;
				numAvals += 1;
			}
			br.close();
			regionFileReader.close();
		}catch(Exception e) {
			e.printStackTrace();
		}

		/* No longer needed
		// check for bogus indices
		for(int i=0;i<aValIndexForLocIndex.length;i++)
			if (aValIndexForLocIndex[i] == -1)
				System.out.println("Bogus index at "+i)
		 */

	}

	/**
	 * This reads all grid files into arrays
	 *
	 */
	private void readAllGridFiles() {

		// 
		sumOfAllAvals = new double[numAvals];

		agrd_cstcal_out = readGridFile(PATH+"agrd_cstcal.out.asc",true);
		agrd_brawly_out = readGridFile(PATH+"agrd_brawly.out.asc",true);
		agrd_creeps_out = readGridFile(PATH+"agrd_creeps.out.asc",true);
		agrd_deeps_out = readGridFile(PATH+"agrd_deeps.out.asc",true);
		agrd_mendos_out = readGridFile(PATH+"agrd_mendos.out.asc",true);
		agrd_wuscmp_out = readGridFile(PATH+"agrd_wuscmp.out.asc",true);
		agrd_wusext_out = readGridFile(PATH+"agrd_wusext.out.asc",true);
		agrd_impext_out = readGridFile(PATH+"agrd_impext.out.asc",true);
		area1new_agrid  = readGridFile(PATH+"area1new.agrid.asc",true);
		area2new_agrid = readGridFile(PATH+"area2new.agrid.asc",true);
		area3new_agrid = readGridFile(PATH+"area3new.agrid.asc",true);
		area4new_agrid = readGridFile(PATH+"area4new.agrid.asc",true);
		mojave_agrid = readGridFile(PATH+"mojave.agrid.asc",true);
		sangreg_agrid = readGridFile(PATH+"sangreg.agrid.asc",true);
		fltmmaxAll21ch_out6 = readGridFile(PATH+"fltmmaxALL21ch.out6.asc",false);
		fltmmaxAll21gr_out6 = readGridFile(PATH+"fltmmaxALL21gr.out6.asc",false);
		fltmmaxAll24ch_out6 = readGridFile(PATH+"fltmmaxALL24ch.out6.asc",false);
		fltmmaxAll24gr_out6 = readGridFile(PATH+"fltmmaxALL24gr.out6.asc",false);

		int numMags = fltmmaxAll21ch_out6.length;


		// find maximum magitude from max mag files
		maxFromMaxMagFiles = -1;
		for(int i=0; i<numMags; ++i) {
			if(fltmmaxAll21ch_out6[i] > maxFromMaxMagFiles) 
				maxFromMaxMagFiles = fltmmaxAll21ch_out6[i];
			if(fltmmaxAll21gr_out6[i] > maxFromMaxMagFiles) 
				maxFromMaxMagFiles = fltmmaxAll21gr_out6[i];
			if(fltmmaxAll24ch_out6[i] > maxFromMaxMagFiles) 
				maxFromMaxMagFiles = fltmmaxAll24ch_out6[i];
			if(fltmmaxAll24gr_out6[i] > maxFromMaxMagFiles) 
				maxFromMaxMagFiles = fltmmaxAll24gr_out6[i];
		}

		//System.out.println(maxFromMaxMagFiles);
		/* find indices that are zeros in all files 
		// NO LONGER NEEDED SINCE I TRIM THE RELM REGION
		System.out.println("Looking for zeros ...");
		for(int i=0; i<agrd_brawly_out.length;i++){
			boolean allZero = true;
			if(agrd_brawly_out[i] !=0) allZero = false;
			if(agrd_creeps_out[i] !=0) allZero = false;
			if(agrd_cstcal_out[i] !=0) allZero = false;
			if(agrd_deeps_out[i] !=0) allZero = false;
			if(agrd_mendos_out[i] !=0) allZero = false;
			if(agrd_wuscmp_out[i] !=0) allZero = false;
			if(agrd_wusext_out[i] !=0) allZero = false;
			if(area1new_agrid[i] !=0) allZero = false;
			if(area2new_agrid[i] !=0) allZero = false;
			if(area3new_agrid[i] !=0) allZero = false;
			if(area4new_agrid[i] !=0) allZero = false;
			if(mojave_agrid[i] !=0) allZero = false;
			if(sangreg_agrid[i] !=0) allZero = false;

			if(allZero) {
				Location loc = this.getGridLocation(i);
				System.out.println(i+" is all zero; aValIndexForLocIndex = "+
						aValIndexForLocIndex[i]+" Location: "+
						(float)loc.getLatitude()+"\t"+
	    				(float)loc.getLongitude()+"\t"+
	    				(float)sumOfAllAvals[i]);
			}
		}
		 */

	}

	/**
	 * this reads an NSHMP grid file.  The boolean specifies whether to add this to a running 
	 * total (sumOfAllAvals[i]).
	 * This could be modified to read binary files
	 * @param fileName
	 * @return
	 */
	public double[] readGridFile(String fileName, boolean addToSumOfAllAvals) {
		double[] allGridVals = new double[numAvals];
//		System.out.println("    Working on "+fileName);
		try { 
			InputStreamReader ratesFileReader = new InputStreamReader(getClass().getResourceAsStream(fileName));
			BufferedReader ratesFileBufferedReader = new BufferedReader(ratesFileReader);
			String ratesLine = ratesFileBufferedReader.readLine(); // skip header
			ratesLine = ratesFileBufferedReader.readLine();
			int index = 0;

			while(ratesLine!=null) { // iterate over all locations
				allGridVals[index] = Double.parseDouble(ratesLine);
				index += 1;
				ratesLine = ratesFileBufferedReader.readLine();
			}
			ratesFileBufferedReader.close();
			ratesFileReader.close();
		}catch(Exception e) {
			e.printStackTrace();
		}

		//now keep only the ones in the RELM region
		double[] gridVals = new double[region.getNodeCount()];
		for(int i=0;i<gridVals.length;i++) {
			int aValIndex = aValIndexForLocIndex[i];
			if(aValIndex != -1) {  // ignore the RELM locs outside the NSHMP region
				gridVals[i] = allGridVals[aValIndex];
				if(addToSumOfAllAvals) sumOfAllAvals[i]+=gridVals[i];
			}
			else
				throw new RuntimeException("Problem with indices!");
		}
		return gridVals;
	}

	/**
	 * This creates an NSHMP mag-freq distribution from their a-value, 
	 * with an option to reduce rates at & above M 6.5 by a factor of three.
	 * @param minMag
	 * @param maxMag
	 * @param aValue
	 * @param bValue
	 * @param applyBulgeReduction
	 * @return
	 */		
	public GutenbergRichterMagFreqDist getMFD(double minMag, double maxMag, double aValue, 
			double bValue, boolean applyBulgeReduction) {

		minMag += DELTA_MAG/2;
		maxMag -= DELTA_MAG/2;
		int numMag = Math.round((float)((maxMag-minMag)/DELTA_MAG+1));
		GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(minMag, numMag, DELTA_MAG, 1.0, bValue);
//		double moRate = Frankel02_AdjustableEqkRupForecast.getMomentRate(minMag, numMag, DELTA_MAG, aValue, bValue);
//		mfd.scaleToTotalMomentRate(moRate);
		mfd.scaleToIncrRate(minMag, aValue*Math.pow(10,-bValue*minMag));

//		apply bulge reduction at & above mag 6.5 if desired
		if(applyBulgeReduction && mfd.getMaxX()>=6.5) {	
			for(int i=mfd.getXIndex(6.5+DELTA_MAG/2); i<mfd.size(); ++i)
				mfd.set(i, mfd.getY(i)/3);
		}
		return mfd;
	}


	/**
	 * This gets the total NSHMP mag-freq dist for the given array of a-values
	 * @param minMag - NOT YET BIN CENTERED!
	 * @param maxMag - NOT YET BIN CENTERED!
	 * @param aValueArray
	 * @param bValue
	 * @param applyBulgeReduction
	 * @return
	 */
	private GutenbergRichterMagFreqDist getTotalMFD(double minMag, double maxMag, double[] aValueArray, 
			double bValue, boolean applyBulgeReduction) {

		double tot_aValue = 0;
		for(int i=0; i<aValueArray.length; i++) tot_aValue += aValueArray[i];
		return getMFD(minMag, maxMag, tot_aValue, bValue, applyBulgeReduction);
	}


	public IncrementalMagFreqDist getTotalC_ZoneMFD() {
		return getTotalC_ZoneMFD_InRegion(null);
	}


	public IncrementalMagFreqDist getTotalC_ZoneMFD_InRegion(Region region) {

		// find max mag among all contributions
		double maxMagAtLoc = C_ZONES_MAX_MAG-UCERF2.DELTA_MAG/2;
		// create summed MFD
		int numMags = (int)Math.round((maxMagAtLoc-UCERF2.MIN_MAG)/DELTA_MAG) + 1;
		SummedMagFreqDist mfdAtLoc = new SummedMagFreqDist(UCERF2.MIN_MAG, maxMagAtLoc, numMags);
		int numLocs = this.region.getNodeCount();
		for(int i=0; i<numLocs; i++)
			if(region==null || region.contains(this.region.locationForIndex(i))) {
				mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area1new_agrid[i], B_VAL, false), true);
				mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area2new_agrid[i], B_VAL, false), true);
				mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area3new_agrid[i], B_VAL, false), true);
				mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area4new_agrid[i], B_VAL, false), true);
				mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, mojave_agrid[i], B_VAL, false), true);
				mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, sangreg_agrid[i], B_VAL, false), true);	

			}
		return mfdAtLoc;
	}


	/**
	 * Note that applyBulgeReduction only applies to agrd_cstcal_out
	 * Get Total MFD for Region. Set region to null if you want the entire region.
	 * Note that this includes the agrd_deeps_out contribution (which are excluded from the sources)
	 * 
	 * @param region 
	 * @param includeC_zones
	 * @param applyBulgeReduction
	 * @param applyMaxMagGrid
	 * @return
	 */
	public SummedMagFreqDist getTotMFDForRegion(Region region, boolean includeC_zones, 
			boolean applyBulgeReduction, boolean applyMaxMagGrid, boolean includeFixedRakeSources) {

		// create summed MFD
		SummedMagFreqDist totMFD = new SummedMagFreqDist(UCERF2.MIN_MAG, UCERF2.MAX_MAG, UCERF2.NUM_MAG);
		int numLocs = this.region.getNodeCount();
		for(int locIndex=0; locIndex<numLocs; ++locIndex)
			if(region==null || region.contains(this.region.locationForIndex(locIndex)))
				totMFD.addResampledMagFreqDist(getTotMFD_atLoc( locIndex,  includeC_zones, 
						applyBulgeReduction,  applyMaxMagGrid, includeFixedRakeSources, true), true);
		return totMFD;
	}

	/**
	 * Note that applyBulgeReduction only applies to agrd_cstcal_out
	 * @param locIndex
	 * @param includeC_zones
	 * @param applyBulgeReduction
	 * @param applyMmaxGrid
	 * @param includeFixedRakeSources
	 * @param include_agrd_deeps_out
	 * @return
	 */
	public SummedMagFreqDist getTotMFD_atLoc(int locIndex, boolean includeC_zones, 
			boolean applyBulgeReduction, boolean applyMaxMagGrid, boolean includeFixedRakeSources, 
			boolean include_agrd_deeps_out) {


		// find max mag among all contributions
		double maxMagAtLoc = C_ZONES_MAX_MAG-UCERF2.DELTA_MAG/2;

		// create summed MFD
		int numMags = (int)Math.round((maxMagAtLoc-UCERF2.MIN_MAG)/DELTA_MAG) + 1;
		SummedMagFreqDist mfdAtLoc = new SummedMagFreqDist(UCERF2.MIN_MAG, maxMagAtLoc, numMags);

		// create and add each contributing MFD
		if(includeFixedRakeSources) {
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, 6.5, agrd_brawly_out[locIndex], B_VAL, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, 7.3, agrd_mendos_out[locIndex], B_VAL, false), true);	
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, 6.0, agrd_creeps_out[locIndex], B_VAL_CREEPING, false), true);			
		}
		
		if(include_agrd_deeps_out)
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, 7.2, agrd_deeps_out[locIndex], B_VAL, false), true);
		
		mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, fltmmaxAll21ch_out6[locIndex], 0.667*agrd_impext_out[locIndex], B_VAL, applyBulgeReduction), true);
		mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, fltmmaxAll21gr_out6[locIndex], 0.333*agrd_impext_out[locIndex], B_VAL, applyBulgeReduction), true);
		if(applyMaxMagGrid) {	 // Apply Max Mag from files

			// 50% weight on the two different Mmax files:
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, fltmmaxAll21ch_out6[locIndex], 0.5*0.667*agrd_cstcal_out[locIndex], B_VAL, applyBulgeReduction), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, fltmmaxAll21gr_out6[locIndex], 0.5*0.333*agrd_cstcal_out[locIndex], B_VAL, applyBulgeReduction), true);

			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, fltmmaxAll24ch_out6[locIndex], 0.5*0.667*agrd_cstcal_out[locIndex], B_VAL, applyBulgeReduction), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, fltmmaxAll24gr_out6[locIndex], 0.5*0.333*agrd_cstcal_out[locIndex], B_VAL, applyBulgeReduction), true);

			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, fltmmaxAll21ch_out6[locIndex], 0.667*agrd_wuscmp_out[locIndex], B_VAL, applyBulgeReduction), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, fltmmaxAll21gr_out6[locIndex], 0.333*agrd_wuscmp_out[locIndex], B_VAL, applyBulgeReduction), true);

			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, fltmmaxAll21ch_out6[locIndex], 0.667*agrd_wusext_out[locIndex], B_VAL, applyBulgeReduction), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, fltmmaxAll21gr_out6[locIndex], 0.333*agrd_wusext_out[locIndex], B_VAL, applyBulgeReduction), true);
		} else { // Apply default Mag Max
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, DEFAULT_MAX_MAG, agrd_cstcal_out[locIndex], B_VAL, applyBulgeReduction), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, DEFAULT_MAX_MAG, agrd_wuscmp_out[locIndex], B_VAL, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(5.0, DEFAULT_MAX_MAG, agrd_wusext_out[locIndex], B_VAL, applyBulgeReduction), true);
		}
		if(includeC_zones && includeFixedRakeSources) { // Include C-Zones
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area1new_agrid[locIndex], B_VAL, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area2new_agrid[locIndex], B_VAL, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area3new_agrid[locIndex], B_VAL, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area4new_agrid[locIndex], B_VAL, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, mojave_agrid[locIndex], B_VAL, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, sangreg_agrid[locIndex], B_VAL, false), true);	
		}	

		return mfdAtLoc;
	}
	
	public void writeNumSources() {
		int numLocs = region.getNodeCount();
		int num;
		System.out.println("Total Num Locs = "+numLocs);
		num=0;
		for(int i=0; i<numLocs; i++) if(agrd_brawly_out[i]>0) num+=1;
		System.out.println("agrd_brawly_out: "+num);
		num=0;
		for(int i=0; i<numLocs; i++) if(agrd_creeps_out[i]>0) num+=1;
		System.out.println("agrd_creeps_out: "+num);
		//num=0;
		//for(int i=0; i<numLocs; i++) if(agrd_deeps_out[i]>0) num+=1;
		//System.out.println("agrd_deeps_out: "+num);
		num=0;
		for(int i=0; i<numLocs; i++) if(agrd_mendos_out[i]>0) num+=1;
		System.out.println("agrd_mendos_out: "+num);
		num=0;
		for(int i=0; i<numLocs; i++) if(agrd_impext_out[i]>0) num+=1;
		System.out.println("agrd_impext_out: "+num);
		num=0;
		for(int i=0; i<numLocs; i++) if(area1new_agrid[i]>0) num+=1;
		System.out.println("area1new_agrid: "+num);
		num=0;
		for(int i=0; i<numLocs; i++) if(area2new_agrid[i]>0) num+=1;
		System.out.println("area2new_agrid: "+num);
		num=0;
		for(int i=0; i<numLocs; i++) if(area3new_agrid[i]>0) num+=1;
		System.out.println("area3new_agrid: "+num);
		num=0;
		for(int i=0; i<numLocs; i++) if(area4new_agrid[i]>0) num+=1;
		System.out.println("area4new_agrid: "+num);
		num=0;
		for(int i=0; i<numLocs; i++) if(mojave_agrid[i]>0) num+=1;
		System.out.println("mojave_agrid: "+num);
		num=0;
		for(int i=0; i<numLocs; i++) if(sangreg_agrid[i]>0) num+=1;
		System.out.println("sangreg_agrid: "+num);

	}
	
	/**
	 * The computes the fraction of each focal mechanism type at each location for the randomly oriented seismicity.
	 * All fixed-strike sources are ignored.  The fractions for deeps above M ~7.0 are not exactly correct.
	 * 
	 */
	private void getFocalMechFractions() {
		fracStrikeSlip = new double[region.getNodeCount()];
		fracNormal = new double[region.getNodeCount()];
		fracReverse = new double[region.getNodeCount()];
		
		double ss_rate, n_rate, rv_rate;
		for(int loc=0; loc<region.getNodeCount();loc++) {
			
			ss_rate = 0;
			n_rate = 0;
			rv_rate = 0;
			
			ss_rate += 0.5*agrd_cstcal_out[loc];
			rv_rate += 0.5*agrd_cstcal_out[loc];
			
			//ss_rate += agrd_deeps_out[loc];
			
			ss_rate += 0.5*agrd_impext_out[loc];
			n_rate  += 0.5*agrd_impext_out[loc];
			
			ss_rate += 0.5*agrd_wuscmp_out[loc];
			rv_rate += 0.5*agrd_wuscmp_out[loc];

			ss_rate += 0.5*agrd_wusext_out[loc];
			n_rate  += 0.5*agrd_wusext_out[loc];

			// zero out any very low relative rates
			double total = ss_rate+n_rate+rv_rate;
			if((ss_rate/total) < 1e-3) ss_rate = 0;
			if((n_rate/total) < 1e-3) n_rate = 0;
			if((rv_rate/total) < 1e-3) rv_rate = 0;
			
			total = ss_rate+n_rate+rv_rate;			
			fracStrikeSlip[loc] = ss_rate/total;
			fracNormal[loc] = n_rate/total;
			fracReverse[loc]= rv_rate/total;
			//System.out.println(loc+"\t"+fracStrikeSlip[loc]+"\t"+fracNormal[loc]+"\t"+fracReverse[loc]);
		}
	}
	
	
	public void writeMomentRateOfFixedStrikeSources() {
		
		double totMoRate = 0;
		for(ProbEqkSource source : getBrawleyFixedStrikeSources(1.0)) {
			totMoRate += source.computeEquivTotalMomentRate(1.0);
		}
		System.out.println("BrawleyFixedStrikeSources moRate="+(float)totMoRate);

		totMoRate = 0;
		for(ProbEqkSource source : getMendosFixedStrikeSources(1.0)) {
			totMoRate += source.computeEquivTotalMomentRate(1.0);
		}
		System.out.println("MendosFixedStrikeSources moRate="+(float)totMoRate);

		totMoRate = 0;
		for(ProbEqkSource source : getCreepsFixedStrikeSources(1.0)) {
			totMoRate += source.computeEquivTotalMomentRate(1.0);
		}
		System.out.println("CreepsFixedStrikeSources moRate="+(float)totMoRate);

		//others
//		sources.addAll(getArea1FixedStrikeSources(duration));
//		sources.addAll(getArea2FixedStrikeSources(duration));
//		sources.addAll(getArea3FixedStrikeSources(duration));
//		sources.addAll(getArea4FixedStrikeSources(duration));
//		sources.addAll(getMojaveFixedStrikeSources(duration));
//		sources.addAll(getSangregFixedStrikeSources(duration));

	}
	

	public static void main(String args[]) {
		
		/**
		 * This code gets MFD from all ruptures.
		 * IMPORTANT: MFD from this piece of code will be differenct from
		 * the MFD returned by getTotMFDForRegion() method because we do not
		 * include "agrd_deeps" for making sources. 
		 * 
		 */
		NSHMP_GridSourceGenerator srcGen = new NSHMP_GridSourceGenerator(null);
		
		srcGen.writeMomentRateOfFixedStrikeSources();
		
//		double duration = 30;
//		srcGen.setAsPointSources(false);
//		ArrayList<ProbEqkSource> allSources = new ArrayList<ProbEqkSource>();
//		allSources.addAll(srcGen.getAllCrosshairGriddedSources(duration));
//		allSources.addAll(srcGen.getAllFixedStrikeSources(duration));
//
//		// Now calculate the total MFD
//		SummedMagFreqDist mfd= new SummedMagFreqDist(UCERF2.MIN_MAG, UCERF2.MAX_MAG, UCERF2.NUM_MAG);
//
//		double mag, rate;
//		for(int srcIndex=0; srcIndex<allSources.size(); ++srcIndex) {
//			ProbEqkSource source = allSources.get(srcIndex);
//			//System.out.println(source.getName());
//			int numRups = source.getNumRuptures();
//			for(int rupIndex=0; rupIndex<numRups; ++rupIndex) {
//				ProbEqkRupture rup = source.getRupture(rupIndex);
//				mag = rup.getMag();
//				rate = rup.getMeanAnnualRate(duration);
//				mfd.add(mag, rate);
//			}
//		}
//		
//		System.out.println(mfd.getCumRateDistWithOffset());

		//System.out.println(srcGen.getTotalC_ZoneMFD().getCumRateDist());
		//System.out.println(srcGen.getTotMFDForRegion(false, true, true));
		//double[] area1new_agrid  = srcGen.readGridFile(PATH+"area1new.agrid.asc",false);
		//for(int i=0; i<area1new_agrid.length; i++) System.out.println(area1new_agrid[i]);
		
		// srcGen.writeNumSources();
		
		// test memory usage when creating all sources
//		System.out.println("getting sources");
//		ArrayList<ProbEqkSource> sources = srcGen.getAllGriddedSources(true, 1, true, true);

		/*
		// This makes the file for Erdem
		DecimalFormat latLonFormat = new DecimalFormat("0.000");
		try {
			FileWriter fw = new FileWriter("BckFileForErdem.txt");
			for(int locIndex=0; locIndex<numLocs; ++locIndex) {
				Location loc = srcGen.getGridLocation(locIndex);
				SummedMagFreqDist mfdAtLoc = srcGen.getTotMFD_atLoc(locIndex, true, true, true);
				fw.write(latLonFormat.format(loc.getLatitude())+"\t"+
						latLonFormat.format(loc.getLongitude())+"\t"+
						(float)mfdAtLoc.getCumRate(UCERF2.MIN_MAG)+"\n");
			}
			fw.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
		*/
	}
}
