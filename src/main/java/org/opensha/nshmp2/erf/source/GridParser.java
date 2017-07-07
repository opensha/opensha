package org.opensha.nshmp2.erf.source;

import static com.google.common.base.Preconditions.*;
import static org.opensha.nshmp2.util.FaultCode.*;
import static org.opensha.nshmp2.util.FocalMech.*;
import static org.opensha.nshmp2.util.NSHMP_Utils.*;
import static org.opensha.nshmp2.util.RateType.*;
import static org.opensha.nshmp2.util.SourceRegion.*;
import static org.opensha.nshmp2.util.SourceType.*;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.geo.Direction;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.nshmp2.util.FaultCode;
import org.opensha.nshmp2.util.FocalMech;
import org.opensha.nshmp2.util.NSHMP_Utils;
import org.opensha.nshmp2.util.RateType;
import org.opensha.nshmp2.util.SourceIMR;
import org.opensha.nshmp2.util.SourceRegion;
import org.opensha.nshmp2.util.Utils;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;

/**
 * 2008 NSHMP grid source parser.
 */
public class GridParser {

	private Logger log;
	private static final String GRD_PATH = "../conf/";
	private static final String DAT_PATH = "/resources/data/nshmp/sources/";

	// parsed
	private String srcName;
	private SourceRegion srcRegion;
	private SourceIMR srcIMR;
	private double srcWt;
	private double minLat, maxLat, dLat;
	private double minLon, maxLon, dLon;
	private double[] depths;
	private Map<FocalMech, Double> mechWtMap;
	private double dR, rMax;
	private GR_Data grSrc;
	private FaultCode fltCode;
	private boolean bGrid, mMaxGrid, weightGrid;
	private double mTaper;
	private URL aGridURL, bGridURL, mMaxGridURL, weightGridURL;
	private double timeSpan;
	private RateType rateType;
	private double strike = Double.NaN;

	// generated
	private double[] aDat, bDat, mMinDat, mMaxDat, wgtDat;

	// build grids using broad region but reduce to src location and mfd lists
	// and a border (Region) used by custom calculator to skip grid entirely
	private LocationList srcLocs;
	private List<IncrementalMagFreqDist> mfdList;
	private Region border;
	
	// temp list of srcIndices used to create bounding region; list is also
	// referenced when applying craton/margin weighs to mfds
	private int[] srcIndices; // already sorted when built

	GridParser(Logger log) {
		this.log = log;
	}

	GridERF parse(SourceFile sf) {
		srcName = sf.getName();
		srcRegion = sf.getRegion();
		srcWt = sf.getWeight();
				
		List<String> dat = sf.readLines();
		Iterator<String> it = dat.iterator();

		// grid of sites (1-30) or station list (0)
		int numSta = readInt(it.next(), 0);
		// skip stations or lat-lon bounds
		Iterators.advance(it, (numSta > 0) ? numSta : 2);
		// skip site data (Vs30) and Campbell basin depth
		it.next();
		// read rupture top data (num, [z, wt M<=6.5, wt M>6.5], ...)
		readRuptureTop(it.next());
		// read focal mech weights (SS, REVERSE, NORMAL)
		readMechWeights(it.next());
		// read gm lookup array parameters; delta R and R max
		readLookupArrayDat(it.next());

		// read source region dimensions
		readSourceLatRange(it.next());
		readSourceLonRange(it.next());

		// mag data
		grSrc = new GR_Data(it.next(), GRIDDED);

		// iflt, ibmat, maxMat, Mtaper
		// iflt = 0 -> no finite faults
		// iflt = 1 -> apply finite fault corrections for M>6 assuming random
		// strike
		// iflt = 2 -> use finite line faults for M>6 and fix strike
		// iflt = 3 -> use finite faults with Johston mblg to Mw converter
		// iflt = 4 -> use finite faults with Boore and Atkinson mblg to Mw
		// converter
		// ibmax = 0 -> use specified b value
		// ibmax = 1 -> use b value matrix (provided in a file)
		// maxMat = 0 -> use specified maximum magnitude
		// maxMat = 1 -> use maximum magnitude matrix (provided in a file)
		// maxMat = -1 -> use as maximum magnitude the minimum between the
		// default and grid value
		String grdDatRaw = it.next();
		int[] grdDat = readInts(grdDatRaw, 3);
		fltCode = FaultCode.typeForID(grdDat[0]);
		bGrid = grdDat[1] > 0 ? true : false;
		mMaxGrid = grdDat[2] > 0 ? true : false;
		mTaper = readDouble(grdDatRaw, 3);
		weightGrid = mTaper > 0 ? true : false;

		if (bGrid) bGridURL = readSourceURL(it.next());
		if (mMaxGrid) mMaxGridURL = readSourceURL(it.next());
		if (weightGrid) weightGridURL = readSourceURL(it.next());
		aGridURL = readSourceURL(it.next());

		// read rate information if rateType is CUMULATIVE
		// it will require conversion to INCREMENTAL
		readRateInfo(it.next());

		// read strike or rjb array
		if (fltCode == FIXED) strike = readDouble(it.next(), 0);

		// done reading; skip atten rel config

		srcIMR = SourceIMR.imrForSource(GRIDDED, srcRegion, srcName, fltCode);
		
		GridERF erf = createGridSource();
		return erf;
	}

	private GridERF createGridSource() {
		initDataGrids();

		// TODO switch to static CEUS & WUS regions
		GriddedRegion region = new GriddedRegion(new Location(minLat, minLon),
			new Location(maxLat, maxLon), dLat, dLon, GriddedRegion.ANCHOR_0_0);
		generateMFDs(region);
		initSrcRegion(region);

		// KLUDGY: need to post process CEUS grids to handle craton and
		// extended margin weighting grids
		if (srcName.contains("2007all8")) {
			ceusScaleRates();
		}

		GridERF gs = new GridERF(srcName, generateInfo(), border,
			srcLocs, mfdList, depths, mechWtMap, fltCode, strike, srcRegion,
			srcIMR, srcWt, rMax, dR);
		return gs;
	}

	private void initSrcRegion(GriddedRegion region) {
		LocationList srcLocs = new LocationList();
		int currIdx = srcIndices[0];
		srcLocs.add(region.locationForIndex(currIdx));
		Direction startDir = Direction.WEST;
		Direction sweepDir = startDir.next();
		while (sweepDir != startDir) {
			int sweepIdx = region.move(currIdx, sweepDir);
			int nextIdx = Arrays.binarySearch(srcIndices, sweepIdx);
			if (nextIdx >= 0) {
				Location nextLoc = region.locationForIndex(srcIndices[nextIdx]);
				//System.out.println(aDat[srcIndices[nextIdx]] + " " + nextLoc);
				if (nextLoc.equals(srcLocs.get(0))) break;
				srcLocs.add(nextLoc);
				currIdx = srcIndices[nextIdx];
				startDir = sweepDir.opposite().next();
				sweepDir = startDir.next();
				continue;
			}
			sweepDir = sweepDir.next();
		}
		// KLUDGY san gorgonio hack; only 11 grid points whose outline (16 pts)
		// does not play nice with Region
		if (srcLocs.size() == 16) {
			for (int i=0; i < srcLocs.size(); i++) {
				if (i==0 || i==8) continue;
				double offset = (i > 8) ? 0.01 : -0.01;
				Location ol = srcLocs.get(i);
				Location nl = new Location(ol.getLatitude() + offset, ol.getLongitude());
				
				srcLocs.set(i, nl);
			}
		}
		border = new Region(srcLocs, null);
	}

	private void generateMFDs(GriddedRegion region) {
		mfdList = Lists.newArrayList();
		srcLocs = new LocationList();
		List<Integer> srcIndexList = Lists.newArrayList();

		for (int i = 0; i < aDat.length; i++) {
			if (aDat[i] == 0) {
				continue;
			}
			// use fixed value if mMax matrix value was 0
			double maxM = mMaxDat[i] <= 0 ? grSrc.mMax : mMaxDat[i];
			// a-value is stored as log10(a)
			GR_Data gr = new GR_Data(aDat[i], bDat[i], mMinDat[i], maxM,
				grSrc.dMag);
			GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(
				gr.mMin, gr.nMag, gr.dMag, 1.0, gr.bVal);
			mfd.scaleToIncrRate(gr.mMin, incrRate(gr.aVal, gr.bVal, gr.mMin));
			// apply weight
			if (weightGrid && mfd.getMaxX() >= mTaper) {
				int j = mfd.getXIndex(mTaper + grSrc.dMag / 2);
				for (; j < mfd.size(); j++)
					mfd.set(j, mfd.getY(j) * wgtDat[i]);
			}
			mfdList.add(mfd);
			srcLocs.add(region.locationForIndex(i));
//			if (LocationUtils.areSimilar(region.locationForIndex(i), 
//				NEHRP_TestCity.SEATTLE.location())) {
//				System.out.println("aVal: " + aDat[i]);
//			}
//			if (i==61222) {
//				System.out.println("aValMax: " + aDat[i]);
//				System.out.println(mfd);
//			}
			srcIndexList.add(i);
		}
		srcIndices = Ints.toArray(srcIndexList);
//		System.out.println("max aVal: " + Doubles.max(aDat));
//		System.out.println("max aVal: " + Math.pow(10, Doubles.max(aDat)));
	}

	private void initDataGrids() {
		int nRows = (int) Math.rint((maxLat - minLat) / dLat) + 1;
		int nCols = (int) Math.rint((maxLon - minLon) / dLon) + 1;
		// always have an a-grid file
		aDat = readGrid(aGridURL, nRows, nCols);
		// might have a b-grid file, but not likely
		bDat = bGrid ? readGrid(bGridURL, nRows, nCols) : makeGrid(
			aDat.length, grSrc.bVal);

		// KLUDGY numerous b-values are 0 but there is a hook in hazgridXnga5
		// (line 931) to override a grid based b=0 to the b-value set in the
		// config for a grid source.
		for (int i = 0; i < bDat.length; i++) {
			if (bDat[i] == 0.0) bDat[i] = grSrc.bVal;
		}

		// don't have variable mMin, but combined grids could
		mMinDat = makeGrid(aDat.length, grSrc.mMin);
		// variable mMax is common
		mMaxDat = mMaxGrid ? readGrid(mMaxGridURL, nRows, nCols) : makeGrid(
			aDat.length, grSrc.mMax);
		// weights; mostly for CA
		wgtDat = weightGrid ? readGrid(weightGridURL, nRows, nCols) : null;
	}

	private double[] makeGrid(int size, double value) {
		double[] dat = new double[size];
		Arrays.fill(dat, value);
		return dat;
	}

	/*
	 * This line is set up to configure a probability distribution of magnitude
	 * dependent rupture top depths. These are actually not used in favor of
	 * fixed values for M<6.5 and M>=6.5
	 */
	private void readRuptureTop(String line) {
		int numDepths = readInt(line, 0);
		double[] depthDat = readDoubles(line, 1 + 2 * numDepths);
		double loMagDepth, hiMagDepth;
		if (numDepths == 1) {
			loMagDepth = depthDat[1];
			hiMagDepth = depthDat[1];
		} else {
			loMagDepth = depthDat[4];
			hiMagDepth = depthDat[1];
		}
		depths = new double[] { loMagDepth, hiMagDepth };
	}

	private void readMechWeights(String line) {
		double[] weights = readDoubles(line, 3);
		mechWtMap = Maps.newEnumMap(FocalMech.class);
		mechWtMap.put(STRIKE_SLIP, weights[0]);
		mechWtMap.put(REVERSE, weights[1]);
		mechWtMap.put(NORMAL, weights[2]);
		// mechWts = weights;
	}

	private void readLookupArrayDat(String line) {
		double[] rDat = readDoubles(line, 2);
		dR = (int) rDat[0];
		rMax = (int) rDat[1];
	}

	private void readSourceLatRange(String line) {
		double[] latDat = readDoubles(line, 3);
		minLat = latDat[0];
		maxLat = latDat[1];
		dLat = latDat[2];
	}

	private void readSourceLonRange(String line) {
		double[] lonDat = readDoubles(line, 3);
		minLon = lonDat[0];
		maxLon = lonDat[1];
		dLon = lonDat[2];
	}

	private URL readSourceURL(String path) {
		checkArgument(path.startsWith(GRD_PATH), "Bad file path: " + path);
		return GridParser.class.getResource(DAT_PATH +
			path.substring(GRD_PATH.length()));
	}

	private void readRateInfo(String line) {
		timeSpan = readDouble(line, 0);
		int rateTypeVal = readInt(line, 1);
		rateType = (rateTypeVal == 0) ? INCREMENTAL : CUMULATIVE;
	}

	private String generateInfo() {
		// @formatter:off
		return new StringBuilder()
		.append(IOUtils.LINE_SEPARATOR)
		.append("=========== Grid Config ============")
		.append(IOUtils.LINE_SEPARATOR)
		.append("            Name: ").append(srcName)
		.append(IOUtils.LINE_SEPARATOR)
		.append("       Lat range: ").append(minLat).append(" ").append(maxLat)
		.append(IOUtils.LINE_SEPARATOR)
		.append("       Lon range: ").append(minLon).append(" ").append(maxLon)
		.append(IOUtils.LINE_SEPARATOR)
		.append("     [dLat dLon]: ").append(dLat).append(" ").append(dLon)
		.append(IOUtils.LINE_SEPARATOR)
		.append("    Source count: ").append(srcLocs.size())
		.append(IOUtils.LINE_SEPARATOR)
		.append("   Rup top M\u003C6.5: ").append(depths[0])
		.append(IOUtils.LINE_SEPARATOR)
		.append("   Rup top M\u22656.5: ").append(depths[1])
		.append(IOUtils.LINE_SEPARATOR)
		.append("    Mech weights: ")
		.append("SS=").append(mechWtMap.get(STRIKE_SLIP))
		.append(" R=").append(mechWtMap.get(REVERSE))
		.append(" N=").append(mechWtMap.get(NORMAL))
		.append(IOUtils.LINE_SEPARATOR)
		.append("   opt [dR rMax]: ").append(dR).append(" ").append(rMax)
		.append(IOUtils.LINE_SEPARATOR)
		.append(" GR [b M- M+ dM]: ").append(grSrc.bVal).append(" ").append(grSrc.mMin)
		.append(" ").append(grSrc.mMax).append(" ").append(grSrc.dMag)
		.append(IOUtils.LINE_SEPARATOR)
		.append("          a grid: ").append(StringUtils.substringAfter(aGridURL.toString(), "/"))
		.append(IOUtils.LINE_SEPARATOR)
		.append("          b grid: ").append(bGrid)
		.append(" ").append((bGridURL != null) ? StringUtils.substringAfter(bGridURL.toString(), "/") : "")
		.append(IOUtils.LINE_SEPARATOR)
		.append("       mMax grid: ").append(mMaxGrid)
		.append(" ").append((mMaxGridURL != null) ? StringUtils.substringAfter(mMaxGridURL.toString(), "/") : "")
		.append(IOUtils.LINE_SEPARATOR)
		.append("     weight grid: ").append(weightGrid)
		.append(" ").append((weightGridURL != null) ? StringUtils.substringAfter(weightGridURL.toString(), "/") : "")
		.append(IOUtils.LINE_SEPARATOR)
		.append("         M taper: ").append(mTaper)
		.append(IOUtils.LINE_SEPARATOR)
		.append("       Time span: ").append(timeSpan)
		.append(IOUtils.LINE_SEPARATOR)
		.append("            Rate: ").append(rateType)
		.append(IOUtils.LINE_SEPARATOR)
		.append("      Fault Code: ").append(fltCode)
		.append(IOUtils.LINE_SEPARATOR)
		.append("          Strike: ").append(strike)
		.append(IOUtils.LINE_SEPARATOR).toString();
		// @formatter:off
	}

	/////////////// CEUS Customizations ///////////////

	// wtmj_cra: full weight up to 6.55; Mmax=6.85 @ 0.2 wt
	// wtmj_ext: full weight up to 6.85; Mmax=7.15 @ 0.2 wt
	// wtmab_cra: full weight up to 6.75; Mmax=7.05 @ 0.2 wt
	// wtmab_ext: full weight up to 7.15; Mmax=7.35 @ 0.2 wt
	private static double[] wtmj_cra =  { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.2, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 };
	private static double[] wtmj_ext =  { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.7, 0.2, 0.0, 0.0, 0.0 };
	private static double[] wtmab_cra = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.9, 0.7, 0.2, 0.0, 0.0, 0.0, 0.0 };
	private static double[] wtmab_ext = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.2, 0.0 };
	private static boolean[] cratonFlags;
	private static boolean[] marginFlags;
	
	
	private void ceusScaleRates() {
		initMasks();
		
		// set weights by file name
		double[] craWt = wtmj_cra;
		double[] marWt = wtmj_ext;
		if (srcName.contains(".AB.")) {
			craWt = wtmab_cra;
			marWt = wtmab_ext;
		}
		double[] weights;
		
		// adjust mfds
		for (int i=0; i<srcIndices.length; i++) {
			IncrementalMagFreqDist mfd = mfdList.get(i);
			if (mfd == null) continue;
			int flagIdx = srcIndices[i];
			boolean craFlag = cratonFlags[flagIdx];
			boolean marFlag = marginFlags[flagIdx];
			if ((craFlag | marFlag) == false) continue;
			weights = craFlag ? craWt : marWt;
			applyWeight(mfd, weights);
		}
	}
	
	private void applyWeight(IncrementalMagFreqDist mfd, double[] weights) {
		for (int i=0; i<mfd.size(); i++) {
			double weight = weights[i];
			if (weight == 1.0) continue;
			mfd.set(i, mfd.getY(i) * weight);
		}
	}

	private void initMasks() {
		// this is only used for CEUS so we don't have to worry about having
		// the wrong dimensions set for these static fields
		if (cratonFlags == null) {
			URL craton = Utils.getResource("/imr/craton");
			URL margin = Utils.getResource("/imr/margin");
			int nRows = (int) Math.rint((maxLat - minLat) / dLat) + 1;
			int nCols = (int) Math.rint((maxLon - minLon) / dLon) + 1;
			cratonFlags = NSHMP_Utils.readBoolGrid(craton, nRows, nCols);
			marginFlags = NSHMP_Utils.readBoolGrid(margin, nRows, nCols);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Logger log = NSHMP_Utils.logger();
		Level level = Level.FINE;
		log.setLevel(level);
		for (Handler h : NSHMP_Utils.logger().getHandlers()) {
			h.setLevel(level);
		}

		List<SourceFile> sources = Lists.newArrayList();
//		sources.addAll(SourceFileMgr.get(null, GRIDDED));
//		sources.addAll(SourceMgr.get(CEUS, GRIDDED, "CEUS.2007all8.AB.in"));
//		sources.addAll(SourceFileMgr.get(CA, GRIDDED, "mojave.in"));
//		sources.addAll(SourceFileMgr.get(CA, GRIDDED, "sangorg.in"));
//		sources.addAll(SourceMgr.get(CEUS, GRIDDED, "CEUSchar.71.in"));
//		sources.addAll(SourceMgr.get(WUS, GRIDDED, "EXTmap.ch.in"));
//		sources.addAll(SourceMgr.get(WUS, GRIDDED, "portdeep.in"));
		sources.addAll(SourceMgr.get(CA, GRIDDED, "brawmap.in"));

		for (SourceFile sf : sources) {

			GridParser parser = new GridParser(log);
			System.out.println("Building: " + sf);
			GridERF erf = parser.parse(sf);
			log.fine(erf.toString());
			
			System.out.println(erf.getMFD(new Location(32.9, -116.0)));
			//GridUtils.gridToKML(erf, "Grid_" + sf.getName(), Utils.randomColor(), false);
//			RegionUtils.regionToKML(erf.getBorder(), "Grid_" + sf.getName(),
//				Utils.randomColor());
//			System.out.println(gs.getRegion().getNodeCount());
//			System.out.println(gs.getRegion().indexForLocation(loc));
//			System.out.println(gs.getMFD(loc));
//			System.out.println(erf.getMFD(new Location(35.6, -90.4)));
//			System.out.println(erf.getMFD(new Location(35.2, -90.1)));
		}
		
	}
}
