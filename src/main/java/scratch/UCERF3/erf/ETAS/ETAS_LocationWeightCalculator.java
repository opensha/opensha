package scratch.UCERF3.erf.ETAS;

import java.awt.Color;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;

/**
 * TODO Add depth dependent wts and make separate pointWt for each possible depth
 * 
 * 
 * This class gives the probability of sampling a primary aftershock at a given latitude, longitude, and depth
 * (each given relative to the source location).  This is achieved by creating a discretized/cubed quarter disk, where the
 * the height of the disk is related to  seismogenic depth.  A quarter disk is can be used because of 
 * symmetry.  This class honors the target distance day precisely, accounting for the transition between between 
 * sub-seimogenic distances and supra-seismogenic distance (where the triggering volume value goes from a spherical shell
 * close in to a cylindrical shell farther out); the sampling is consequently hypocenter dependent.
 * 
 * This utilizes a discretized volume with adaptive grid spacing.  For example, if lat/lon grid spacing is 0.02 (about 2 km), then the
 * first cell (between latitude 0 and 0.02) is subdivided into 100 sub cells (0.02/100, or a spacing of ~20 meters). The next
 * set of cells is discretized at ~0.1 km; etc.
 * 
 * 
 * This uses a faster, more approximate distance calculation 
 * (see getDistance(double relLat, double relLon, double relDep) here)
 * 
 * 
 * NOTES:
 * 
 * @author field
 *
 */
public class ETAS_LocationWeightCalculator {
	
	final static boolean D = false;
	
	int numLatLon, numDepth, numParDepth;
	double maxLatLonDeg, maxDepthKm, latLonDiscrDeg, depthDiscrKm, midLat, maxDistKm;
	
	double etasDistDecay, etasMinDist;
	
	double cosMidLat;
		
//	double[][][] pointWt;
	ArrayList<double[][][]> pointWtList;
	
	double histLogMinDistKm=Double.NaN;	// log10 distance; old=-2.0
	double histLogMaxDistKm = 4.0;	// log10 distance; 10,000 km
	double histLogDeltaDistKm = 0.1 ;	// old=0.2
	int histNum;				// old=31
	
	LocationList[][][] subLocsArray;
	IntegerPDF_FunctionSampler[][][] subLocSamplerArray;

	int[] numSubDistances = {100,20,10,5,2,2};
	
	SeisDepthDistribution seisDepthDistribution;
	
	EvenlyDiscretizedFunc targetLogDistDecay;
	ArrayList<HistogramFunction> logDistWeightHistList, logDistNumCellHistList, finalLogDistDecayList;
	
	ETAS_Utils etas_utils;
	
	Location[] locationArray;
	IntegerPDF_FunctionSampler[] randLocSampler;

	
	/**
	 * 
	 * @param maxDistKm - the maximum distance for sampling in km
	 * @param maxDepthKm - the max seismogenic thickness
	 * @param latLonDiscrDeg - the lat and lon discretization (cube spacing) in degrees (0.02 is recommended)
	 * @param depthDiscrKm - the depth discretization in km (2.0 is recommended)
	 * @param midLat - the mid latitude used to compute bin widths (since widths decrease with latitude)
	 * @param etasDistDecay - the ETAS distance decay parameter
	 * @param etasMinDist - the ETAS min distance
	 * @param etas_utils - used for generating random samples so that reproducibility can be maintained.
	 */
	public ETAS_LocationWeightCalculator(double maxDistKm, double maxDepthKm, double latLonDiscrDeg, double depthDiscrKm, 
			double midLat, double etasDistDecay, double etasMinDist, ETAS_Utils etas_utils) {
		
		cosMidLat = Math.cos(midLat*Math.PI/180);
		double aveLatLonDiscrKm = (latLonDiscrDeg+cosMidLat*latLonDiscrDeg)*111/2.0;
		this.maxDistKm = maxDistKm;
		this.maxLatLonDeg = maxDistKm/(111*cosMidLat);	// degrees
		
		this.maxDepthKm = maxDepthKm;
		this.latLonDiscrDeg = latLonDiscrDeg;
		this.depthDiscrKm = depthDiscrKm;
		this.midLat = midLat;
		this.etasDistDecay=etasDistDecay;
		this.etasMinDist=etasMinDist;
		
		this.etas_utils = etas_utils;
						
		// the number of points in each direction
		numLatLon = (int)Math.round(maxLatLonDeg/latLonDiscrDeg);
		numDepth = (int)Math.round(maxDepthKm/depthDiscrKm);
		numParDepth = numDepth+1;
		
		double aveCellVolume = (111.11*latLonDiscrDeg)*(111.11*cosMidLat*latLonDiscrDeg)*depthDiscrKm;
	
		if (D) System.out.println("aveLatLonDiscrKm="+aveLatLonDiscrKm+
				"\nmaxLatLonDeg="+maxLatLonDeg+
				"\ncosMidLat="+cosMidLat+
				"\nnumLatLon="+numLatLon+
				"\nnumDepth="+numDepth+
				"\naveCellVolume="+aveCellVolume);
		
		// the following is info for the close points that are subdivided
		int maxNumPtsWithSubLocs = numSubDistances.length;
		subLocsArray = new LocationList[maxNumPtsWithSubLocs][maxNumPtsWithSubLocs][maxNumPtsWithSubLocs];
		subLocSamplerArray = new IntegerPDF_FunctionSampler[maxNumPtsWithSubLocs][maxNumPtsWithSubLocs][maxNumPtsWithSubLocs];

				
		double[] distances=null;
		
		seisDepthDistribution = new SeisDepthDistribution(etas_utils);

		// find minimum distance that will be sampled, and then find appropriate first bin
//		double minDistSampled = Double.POSITIVE_INFINITY;
//		for(double val : getSubDistances(0, 0, 0, numSubDistances[0]))
//			if(val<minDistSampled) minDistSampled=val;
//		histLogMinDistKm = Math.ceil(Math.log10(minDistSampled)/histLogDeltaDistKm)*histLogDeltaDistKm; // make sure there is one in the first bin
//		if(D) System.out.println("minDistSampled="+minDistSampled);

		histLogMinDistKm = -1.5;
		histNum = Math.round((float)((histLogMaxDistKm-histLogMinDistKm)/histLogDeltaDistKm)) +1;
		if(D) System.out.println("histNum="+histNum);
		
		pointWtList = new ArrayList<double[][][]>();
		logDistWeightHistList = new ArrayList<HistogramFunction> ();
		logDistNumCellHistList = new ArrayList<HistogramFunction> ();
		finalLogDistDecayList = new ArrayList<HistogramFunction> ();
		for(int i=0;i<numParDepth;i++) {
			pointWtList.add(new double[numLatLon][numLatLon][numDepth]);
			logDistWeightHistList.add(new HistogramFunction(histLogMinDistKm,histLogMaxDistKm,histNum));
			logDistNumCellHistList.add(new HistogramFunction(histLogMinDistKm,histLogMaxDistKm,histNum));
			finalLogDistDecayList.add(new HistogramFunction(histLogMinDistKm,histLogMaxDistKm,histNum));
		}

		
		targetLogDistDecay = ETAS_Utils.getTargetDistDecayFunc(histLogMinDistKm, histLogMaxDistKm, histNum, etasDistDecay, etasMinDist);
		
		for(int iParDep = 0; iParDep<this.numParDepth; iParDep++) {
			System.out.println("iParDep="+iParDep+"; ParDepth="+getParDepth(iParDep));
			HistogramFunction logDistWeightHist = logDistWeightHistList.get(iParDep);
			HistogramFunction logDistNumCellHist = logDistNumCellHistList.get(iParDep);
			HistogramFunction finalLogDistDecay = finalLogDistDecayList.get(iParDep);
			double[][][] pointWt = pointWtList.get(iParDep);
			for(int iLat=0;iLat<numLatLon; iLat++) {
				for(int iLon=0;iLon<numLatLon; iLon++) {
					for(int iDep=0; iDep<numDepth;iDep++) {
						// find the largest index) proxy for farthest distance
						double depth = getDepth(iDep);
						double wtAtDepth = seisDepthDistribution.getProbBetweenDepths(depth-depthDiscrKm/2d,depth+depthDiscrKm/2d)*numDepth;

						int iDepDiff = iDep-iParDep;
						if(iDepDiff<0) iDepDiff = -iDepDiff-1;
						int maxIndex = Math.max(iDepDiff, Math.max(iLat, iLon));
						if(maxIndex<numSubDistances.length) {
							distances = getSubDistances(iLat, iLon, iDepDiff, numSubDistances[maxIndex]);
							//						if(D) System.out.println(maxIndex+"\t"+numSubDistances[maxIndex]+"\t"+distances.length);
							double volume = aveCellVolume/distances.length;	// latter represents the number of sub-cells
							for(int i=0;i<distances.length;i++) {
								double dist = distances[i];
								// the following is to get the weight in the right ballpark; only correct if parent is half way down seismo thickness and there is no depth distribution
								double wt = 4d*ETAS_Utils.getHardebeckDensity(dist, etasDistDecay, etasMinDist, maxDepthKm)*volume*wtAtDepth;
								double logDist = Math.log10(dist);
								if(logDist<logDistWeightHist.getX(0)) {	// in case it's below the first bin
									logDistWeightHist.add(0, wt);
									logDistNumCellHist.add(0, 1);
								}
								else if (dist<maxDistKm) {
									logDistWeightHist.add(logDist,wt);
									logDistNumCellHist.add(logDist,1);
								}
							}
						}
						else {
							double dist = getDistance(iLat, iLon, iDepDiff);
							if(dist<maxDistKm) {
								double wt = 4d*ETAS_Utils.getHardebeckDensity(dist, etasDistDecay, etasMinDist, maxDepthKm)*aveCellVolume*wtAtDepth;
								//							pointWt[iLat][iLon][iDep] = wt;
								logDistWeightHist.add(Math.log10(dist),wt);							
								logDistNumCellHist.add(Math.log10(dist),1);							
							}
						}
					}
				}
			}

			// plot to check for any zero bins
			if (D) {
				GraphWindow graph3 = new GraphWindow(logDistNumCellHist, "Num Cells in Each Bin for iParDep="+iParDep); 
			}


			if (D) System.out.println("logWtHistogram.calcSumOfY_Vals()="+targetLogDistDecay.calcSumOfY_Vals());

			// now fill in weights for each point
			for(int iLat=0;iLat<numLatLon; iLat++) {
				for(int iLon=0;iLon<numLatLon; iLon++) {
					for(int iDep=0; iDep<numDepth;iDep++) {
						// find the largest index) proxy for farthest distance
						double depth = getDepth(iDep);
						double wtAtDepth = seisDepthDistribution.getProbBetweenDepths(depth-depthDiscrKm/2d,depth+depthDiscrKm/2d)*numDepth;
						int iDepDiff = iDep-iParDep;
						if(iDepDiff<0) iDepDiff = -iDepDiff-1;
						int maxIndex = Math.max(iDepDiff, Math.max(iLat, iLon));
						if(maxIndex<numSubDistances.length) {
							distances = getSubDistances(iLat, iLon, iDepDiff, numSubDistances[maxIndex]);
							double volume = aveCellVolume/distances.length;
							for(int i=0;i<distances.length;i++) {
								double dist = distances[i];
								double wt = 4d*ETAS_Utils.getHardebeckDensity(dist, etasDistDecay, etasMinDist, maxDepthKm)*volume*wtAtDepth;
								double logDist = Math.log10(dist);
								if(logDist<logDistWeightHist.getX(0)) {	// in case it's below the first bin
									double modWt=wt*targetLogDistDecay.getY(0)/logDistWeightHist.getY(0);
									pointWt[iLat][iLon][iDep] += modWt;
									finalLogDistDecay.add(0, modWt);
								}
								else if (dist<maxDistKm) {
									double modWt=wt*targetLogDistDecay.getY(logDist)/logDistWeightHist.getY(logDist);
									pointWt[iLat][iLon][iDep] += modWt;
									finalLogDistDecay.add(logDist,modWt);

								}
							}
						}
						else {
							double dist = getDistance(iLat, iLon, iDepDiff);
							if(dist<maxDistKm) {
								double wt = 4d*ETAS_Utils.getHardebeckDensity(dist, etasDistDecay, etasMinDist, maxDepthKm)*aveCellVolume*wtAtDepth;
								double logDist = Math.log10(dist);
								double modWt=wt*targetLogDistDecay.getY(logDist)/logDistWeightHist.getY(logDist);
								pointWt[iLat][iLon][iDep] = modWt;	
								finalLogDistDecay.add(Math.log10(dist),modWt);							

							}
						}
					}
				}
			}

			if (D) {
				logDistWeightHist.setName("logDistWeightHist");
				targetLogDistDecay.setName("targetLogDistDecay");
				finalLogDistDecay.setName("finalLogDistDecay");
				ArrayList<EvenlyDiscretizedFunc> funcs1 = new ArrayList<EvenlyDiscretizedFunc>();
				funcs1.add(logDistWeightHist);
				funcs1.add(targetLogDistDecay);
				funcs1.add(finalLogDistDecay);
				GraphWindow graph = new GraphWindow(funcs1, "logDistWeightHist for iParDep="+iParDep); 
			}

			// test total weight
			double totWtTest=0;
			for(int iDep=0;iDep<numDepth; iDep++) {
				for(int iLat=0;iLat<numLatLon; iLat++) {
					for(int iLon=0;iLon<numLatLon; iLon++) {
						totWtTest += pointWt[iLat][iLon][iDep];
					}
				}
			}
			if (D) System.out.println("totWtTest = "+ totWtTest+" for iParDep="+iParDep);
		}

	}
	
	
	/**
	 * This returns a random location within the cube containing the given location, and
	 * for the specified depth.  The returned location is relative to the center of the cube.
	 * (delta lat, lon, and depth). For short distances from the parent, this accounts for the 
	 * distance decay within the cube by sampling among a number of discrete points inside that 
	 * cube. Some additional randomness is finally added (to prevent aftershock from stacking onto
	 * the exact same location).
	 * @param relLat - target latitude relative to the source latitude
	 * @param relLon - as above for longitude
	 * @param dep - absolute depth (km)
	 * @param parDep - absolute depth of parent/source (km)
	 * 
	 * @return
	 */
	public Location getRandomDeltaLoc(double relLat, double relLon, double depth, double parDep) {
		int iLat = getLatIndex(relLat);
		int iLon = getLonIndex(relLon);
		int iDep = getDepthIndex(depth);
		int iParDep = getParDepthIndex(parDep);
		HistogramFunction logDistWeightHist = logDistWeightHistList.get(iParDep);
		Location loc;	// the location before some added randomness
		double deltaSubLatLon;
		double deltaDepth;
		
		int iDepDiff = iDep-iParDep;
		if(iDepDiff<0) iDepDiff = -iDepDiff-1;
		int maxIndex = Math.max(iDepDiff, Math.max(iLat, iLon));
		if(maxIndex<numSubDistances.length) {
			int numSubLoc = numSubDistances[maxIndex];
			deltaSubLatLon = latLonDiscrDeg/numSubLoc;
			deltaDepth = depthDiscrKm/numSubLoc;
			if(subLocsArray[iLat][iLon][iDepDiff] == null) {
				double midLat = getLat(iLat);
				double midLon = getLon(iLon);
				double midDepth = getDepth(iDepDiff);
				
				LocationList locList = new LocationList();
				IntegerPDF_FunctionSampler newSampler = new IntegerPDF_FunctionSampler(numSubLoc*numSubLoc*numSubLoc);
				int index = 0;
				for(int iSubLat = 0; iSubLat < numSubLoc; iSubLat++) {
					double lat = (midLat-latLonDiscrDeg/2) + iSubLat*deltaSubLatLon + deltaSubLatLon/2;
					for(int iSubLon = 0; iSubLon < numSubLoc; iSubLon++) {
						double lon = (midLon-latLonDiscrDeg/2) + iSubLon*deltaSubLatLon + deltaSubLatLon/2;
						for(int iSubDep = 0; iSubDep < numSubLoc; iSubDep++) {
							double dep = (midDepth-depthDiscrKm/2) + iSubDep*deltaDepth + deltaDepth/2;
							locList.add(new Location(lat-midLat,lon-midLon,dep-midDepth));	// add the deltaLoc to list
							double dist = getDistance(lat, lon, dep);
							double logDist = Math.log10(dist);
							double wt = 4d*ETAS_Utils.getHardebeckDensity(dist, etasDistDecay, etasMinDist, maxDepthKm);	// depth and cell volume not important here
							double normWt;
							if(logDist<logDistWeightHist.getX(0))
								normWt = targetLogDistDecay.getY(0)/logDistWeightHist.getY(0);
							else
								normWt = targetLogDistDecay.getY(logDist)/logDistWeightHist.getY(logDist);
							newSampler.add(index, wt*normWt);		// add the sampler
							index ++;
						}
					}
				}
				subLocsArray[iLat][iLon][iDepDiff] = locList;
				subLocSamplerArray[iLat][iLon][iDepDiff] = newSampler;
			}
			
			int randLocIndex = subLocSamplerArray[iLat][iLon][iDepDiff].getRandomInt(etas_utils.getRandomDouble());
			loc = subLocsArray[iLat][iLon][iDepDiff].get(randLocIndex);		
			if(iDep-iParDep<0) { // need to flip the sign of the depth if parent/source is above
				loc = new Location(loc.getLatitude(), loc.getLongitude(), -loc.getDepth());
			}

		}
		else {
			deltaSubLatLon = latLonDiscrDeg;
			deltaDepth = depthDiscrKm;
			loc = new Location(0, 0, 0);	// no delta
		}
		// Add an additional random element
		return new Location(loc.getLatitude()+deltaSubLatLon*(etas_utils.getRandomDouble()-0.5)*0.999,
					loc.getLongitude()+deltaSubLatLon*(etas_utils.getRandomDouble()-0.5)*0.999,
					loc.getDepth()+deltaDepth*(etas_utils.getRandomDouble()-0.5)*0.999);
//		return loc;
		
	}
	
	/**
	 * Get the distance (km) to the given point
	 * @param iLat
	 * @param iLon
	 * @param iDep
	 * @return
	 */
	private double getDistance(int iLat, int iLon, int iDep) {
		return getDistance(getLat(iLat), getLon(iLon), getDepth(iDep));
	}
	
	/**
	 * Get the distance (km) to the given location (approx distance calculation is applied)
	 * @param relLat
	 * @param relLon
	 * @param relDep
	 * @return
	 */
	private double getDistance(double relLat, double relLon, double relDep) {
		double latDistKm = relLat*111;
		double lonDistKm = relLon*111*cosMidLat;
		return Math.sqrt(latDistKm*latDistKm+lonDistKm*lonDistKm+relDep*relDep);
	}

	
	/**
	 * This gives the probability of sampling an event in the cube containing the 
	 * specified location, which also depends on the given depth of the main shock
	 * @param relLat - latitude relative to the source location (targetLat-sourceLat)
	 * @param relLon - as above, but for longitude
	 * @param dep - absolute depth
	 * @param parDep - absolute depth of parent source
	 * @return
	 */
	public double getProbAtPoint(double relLat, double relLon, double dep, double parDep) {
		// are there two layers for this relative depth?
		int relDepIndex = getDepthIndex(dep);
		int relLatIndex = getLatIndex(relLat);
		int relLonIndex = getLonIndex(relLon);
		if(relLatIndex>= numLatLon || relLonIndex>=numLatLon) {
			return 0.0;
		}
		return this.pointWtList.get(getParDepthIndex(parDep))[relLatIndex][relLonIndex][relDepIndex];
	}
	
	private double getLat(int iLat) {
		return iLat*latLonDiscrDeg+latLonDiscrDeg/2.0;
	}
	
	private int getLatIndex(double  relLat) {
//		return (int) Math.round((relLat-latLonDiscrDeg/2.0)/latLonDiscrDeg);
		return (int) (relLat/latLonDiscrDeg);

	}

	
	private double getLon(int iLon) {
		return iLon*latLonDiscrDeg+latLonDiscrDeg/2.0;
	}
	
	private int getLonIndex(double  relLon) {
//		return (int) Math.round((relLon-latLonDiscrDeg/2.0)/latLonDiscrDeg);
		return (int) (relLon/latLonDiscrDeg);
	}

	private double getDepth(int iDep) {
		return iDep*depthDiscrKm+depthDiscrKm/2.0;
	}

	private int getDepthIndex(double depth) {
//		return (int)Math.round((depth-depthDiscrKm/2.0)/depthDiscrKm);
		return (int)(depth/depthDiscrKm);
	}

	private double getParDepth(int iParDep) {
		return iParDep*depthDiscrKm;
	}
	
	private int getParDepthIndex(double parDep) {
		return (int)Math.round(parDep/depthDiscrKm);
	}
	

	/**
	 * This returns a random location (cube centered), where the lat and lon are relative to 
	 * the parent event epicenter (and always positive because of the quarter-space used here), 
	 * and depth is absolute
	 * @param parDepth
	 * @return
	 */
	public Location getRandomLoc(double parDepth) {
		if(locationArray == null)
			initRandomLocData();
		int randInt = randLocSampler[getParDepthIndex(parDepth)].getRandomInt(etas_utils.getRandomDouble());
		return locationArray[randInt];
	}
		
	private void initRandomLocData() {
		long st;
		if(D) {
			System.out.println("Starting initRandomLocData()");
			st = System.currentTimeMillis();
		}
		int totNumPts = numLatLon*numLatLon*numDepth;
		locationArray = new Location[totNumPts];
		randLocSampler = new IntegerPDF_FunctionSampler[numParDepth];
		for(int iParDep=0;iParDep<numParDepth;iParDep++) {
			randLocSampler[iParDep] = new IntegerPDF_FunctionSampler(totNumPts);
		}
		int index=0;
		for(int iDep=0;iDep<numDepth; iDep++) {
			for(int iLat=0;iLat<numLatLon; iLat++) {
				for(int iLon=0;iLon<numLatLon; iLon++) {
					for(int iParDep=0;iParDep<numParDepth; iParDep++) {
						double wt = getProbAtPoint(getLat(iLat), getLon(iLon), getDepth(iDep), getParDepth(iParDep));
						randLocSampler[iParDep].set(index,wt);
					}
					locationArray[index] = new Location(getLat(iLat), getLon(iLon), getDepth(iDep));
					index +=1;
				}
			}
		}
		if(D) {
			double timeSec = ((double)(System.currentTimeMillis()-st))/1000d;
			System.out.println("Done with initRandomLocData(); that took "+timeSec+" seconds");
		}

	}


	public void testRandomSamples(int numSamples, double parDepth) {
		
		//test
//		getRandomDeltaLoc(this.getLat(0), this.getLon(1), this.getDepth(2));
//		System.exit(0);
		
		IntegerPDF_FunctionSampler sampler;
		int totNumPts = numLatLon*numLatLon*numDepth;
		sampler = new IntegerPDF_FunctionSampler(totNumPts);
		int[] iLatArray = new int[totNumPts];
		int[] iLonArray = new int[totNumPts];
		int[] iDepArray = new int[totNumPts];
		int index=0;
		for(int iDep=0;iDep<numDepth; iDep++) {
			for(int iLat=0;iLat<numLatLon; iLat++) {
				for(int iLon=0;iLon<numLatLon; iLon++) {
					double wt = getProbAtPoint(getLat(iLat), getLon(iLon), getDepth(iDep),parDepth);
					sampler.set(index,wt);
					iLatArray[index]=iLat;
					iLonArray[index]=iLon;
					iDepArray[index]=iDep;
					index +=1;
				}
			}
		}
		
		double histLogMinDistKm = -1.3;	// this avoids showing numerical issues at distances closer than ~50 km
		double histLogMaxDistKm = this.histLogMaxDistKm;
		int histNum = Math.round((float)((histLogMaxDistKm-histLogMinDistKm)/histLogDeltaDistKm)) +1;
		
		// create histograms
		HistogramFunction testLogHistogram = new HistogramFunction(histLogMinDistKm,histLogMaxDistKm,histNum);
//		HistogramFunction testHistogram = new HistogramFunction(0.5 , 1009.5, 1010);
		HistogramFunction depthDistHistogram = new HistogramFunction(1d, 12, 2d);
		
		
		DefaultXY_DataSet epicenterLocs = new DefaultXY_DataSet();
		double[] distArray = new double[numSamples];


		for(int i=0;i<numSamples;i++) {
			int sampIndex = sampler.getRandomInt(etas_utils.getRandomDouble());
			double relLat = getLat(iLatArray[sampIndex]);
			double relLon = getLon(iLonArray[sampIndex]);
			double depth = getDepth(iDepArray[sampIndex]);
			Location deltaLoc=getRandomDeltaLoc(relLat, relLon, depth, parDepth);
//			deltaLoc = new Location(0d,0d,0d);
			double dist = getDistance(relLat+deltaLoc.getLatitude(), relLon+deltaLoc.getLongitude(), depth+deltaLoc.getDepth()-parDepth);
			distArray[i]=dist;
			epicenterLocs.set((relLat+deltaLoc.getLatitude())*111., (relLon+deltaLoc.getLongitude())*111.*cosMidLat);
			depthDistHistogram.add(depth+deltaLoc.getDepth(),1.0/numSamples);
			if(dist<this.maxDistKm) {
//				testHistogram.add(dist, 1.0/numSamples);
				double logDist = Math.log10(dist);
				if(logDist<testLogHistogram.getX(0))
					testLogHistogram.add(0, 1.0/numSamples);
				else if (logDist<histLogMaxDistKm)
					testLogHistogram.add(logDist,1.0/numSamples);
			}
		}
		
		// convert to density function
		double logBinHalfWidth = testLogHistogram.getDelta()/2;
		for(int i=0;i<testLogHistogram.size();i++) {
			double lowerBinEdge=0;
			if(i!=0)
				lowerBinEdge = Math.pow(10,testLogHistogram.getX(i)-logBinHalfWidth);
			double upperBinEdge = Math.pow(10,testLogHistogram.getX(i)+logBinHalfWidth);
			testLogHistogram.set(i,testLogHistogram.getY(i)/(upperBinEdge-lowerBinEdge));
		}
		
		// make nearest-neighbor data
		Arrays.sort(distArray);
		DefaultXY_DataSet nearestNeighborPrimaryData = new DefaultXY_DataSet();
		for(int i=0;i<numSamples-1;i++) {
			double xVal=Math.log10((distArray[i+1]+distArray[i])/2.0);
			double yVal=1.0/(distArray[i+1]-distArray[i]);
			nearestNeighborPrimaryData.set(xVal,yVal/distArray.length);
		}
		nearestNeighborPrimaryData.setName("Nearest neighbor distance data");
		nearestNeighborPrimaryData.setInfo("");


		
		EvenlyDiscretizedFunc targetLogDistDecayFunc = ETAS_Utils.getTargetDistDecayDensityFunc(histLogMinDistKm, histLogMaxDistKm, histNum, etasDistDecay, etasMinDist);

		ArrayList<XY_DataSet> funcs1 = new ArrayList<XY_DataSet>();
		funcs1.add(nearestNeighborPrimaryData);
		funcs1.add(targetLogDistDecayFunc);
		funcs1.add(testLogHistogram);

		ArrayList<PlotCurveCharacterstics> chars = new ArrayList<PlotCurveCharacterstics>();
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 2, Color.GREEN));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, Color.RED));
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 6, Color.BLUE));

		GraphWindow graph = new GraphWindow(funcs1, "Distance Decay Test for Parent Depth of "+(float)parDepth+" km",chars); 
		graph.setAxisRange(-1.3, 3, 1e-6, 1e4);
		graph.setYLog(true);
		graph.setX_AxisLabel("Log10 Distance (km)");
		graph.setY_AxisLabel("Density");
		graph.setTickLabelFontSize(18);
		graph.setAxisLabelFontSize(20);
		graph.setTickLabelFontSize(22);

		
//		// make target histogram
//		EvenlyDiscretizedFunc targetHist = new EvenlyDiscretizedFunc(0.5 , 999.5, 1000);
//		double halfDelta=targetHist.getDelta()/2;
//		for(int i=0;i<targetHist.size();i++) {
//			double upper = ETAS_Utils.getDecayFractionInsideDistance(etasDistDecay, etasMinDist, targetHist.getX(i)+halfDelta);
//			double lower = ETAS_Utils.getDecayFractionInsideDistance(etasDistDecay, etasMinDist, targetHist.getX(i)-halfDelta);
//			targetHist.set(i,upper-lower);
//		}

		
//		ArrayList funcs2 = new ArrayList();
//		funcs2.add(testHistogram);
//		funcs2.add(targetHist);
//		GraphWindow graph2 = new GraphWindow(funcs2, "testHistogram for ParDepth="+(float)parDepth); 
//		graph2.setAxisRange(0.1, 1000, 1e-6, 1);
//		graph2.setYLog(true);
//		graph2.setXLog(true);
//		graph2.setX_AxisLabel("Distance");
//		graph2.setY_AxisLabel("Density");

		
		PlotCurveCharacterstics plotSym = new PlotCurveCharacterstics(PlotSymbol.CROSS, 1f, Color.BLACK);
		GraphWindow graph3 = new GraphWindow(epicenterLocs, "epicenterLocs for ParDepth="+(float)parDepth, plotSym); 

		PlotCurveCharacterstics plotChar = new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK);
		GraphWindow graph4 = new GraphWindow(depthDistHistogram, "Depth Distibution for ParDepth="+(float)parDepth, plotChar); 
		graph4.setX_AxisLabel("Depth");
		graph4.setY_AxisLabel("Sampled Density");
		graph4.setGriddedFuncAxesTicks(true);
		
// TES OUT FILE
//		try{
//			FileWriter fw1 = new FileWriter("test456.txt");
//			fw1.write("iLat\tiLon\tiDep\trelLat\trelLon\trelDep\twt\n");
//			for(int i=0; i<sampler.getNum(); i++) {
//				int iLat = iLatArray[i];
//				int iLon = iLonArray[i];
//				int iDep = iDepArray[i];
//				double relLat = this.getLat(iLat);
//				double relLon = this.getLon(iLon);
//				double relDep = this.getDepth(iDep);
//				if(relLat<0.25 && relLon<0.25)
//					fw1.write(iLat+"\t"+iLon+"\t"+iDep+"\t"+(float)relLat+"\t"+(float)relLon+"\t"+(float)relDep+"\t"+(float)sampler.getY(i)+"\n");
//			}
//			fw1.close();
//		}catch(Exception e) {
//			e.printStackTrace();
//		}


	}
	
	
	
	private double[] getSubDistances(int iLat, int iLon, int iDep, int numDiscr) {
		double[] distances = new double[numDiscr*numDiscr*numDiscr];
		double midLat = getLat(iLat);
		double midLon = getLon(iLon);
		double midDepth = getDepth(iDep);
		double deltaSubLatLon = latLonDiscrDeg/numDiscr;
		double deltaDepth = depthDiscrKm/numDiscr;
		int index=0;
// System.out.println("midLat="+midLat+"\tmidLon="+midLon+"\tmidDepth="+midDepth);
//System.out.println("relLat\trelLon\trelDepth\tdist");

		for(int latIndex = 0; latIndex < numDiscr; latIndex++) {
			double relLat = (midLat-latLonDiscrDeg/2) + latIndex*deltaSubLatLon + deltaSubLatLon/2;
			for(int lonIndex = 0; lonIndex < numDiscr; lonIndex++) {
				double relLon = (midLon-latLonDiscrDeg/2) + lonIndex*deltaSubLatLon + deltaSubLatLon/2;
				for(int depIndex = 0; depIndex < numDiscr; depIndex++) {
					double relDep = (midDepth-depthDiscrKm/2) + depIndex*deltaDepth + deltaDepth/2;
					distances[index] = getDistance(relLat, relLon, relDep);
//System.out.println((float)relLat+"\t"+(float)relLon+"\t"+(float)relDep+"\t"+(float)distances[index]);

					index+=1;
				}
			}
		}
		return distances;
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
//		ETAS_LocationWeightCalculator calc = new ETAS_LocationWeightCalculator(1000.0, 24.0, 0.01, 1.0, 38.0, 2, 0.3);
		
		double maxDistKm=1000.0;
		double maxDepthKm=24;
//		double latLonDiscrDeg=0.005;
//		double depthDiscrKm=0.5;
//		double latLonDiscrDeg=0.05;
		double latLonDiscrDeg=0.02;
		double depthDiscrKm=2.0;
		double midLat=37.25;
		double etasDistDecay=ETAS_Utils.distDecay_DEFAULT;
		double etasMinDist=ETAS_Utils.minDist_DEFAULT;
		
		ETAS_SimAnalysisTools.writeMemoryUse("before");
		ETAS_LocationWeightCalculator calc = new ETAS_LocationWeightCalculator(maxDistKm, maxDepthKm, 
					latLonDiscrDeg, depthDiscrKm, midLat, etasDistDecay, etasMinDist, new ETAS_Utils());
		ETAS_SimAnalysisTools.writeMemoryUse("after");

//		for(int i=0; i<=12;i++)
//			calc.testRandomSamples(100000, i*2);
		
		calc.testRandomSamples(100000, 16d);

	}

}
