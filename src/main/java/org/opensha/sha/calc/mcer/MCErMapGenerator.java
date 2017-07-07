package org.opensha.sha.calc.mcer;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSetMath;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbol;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbol.Symbol;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbolSet;
import org.opensha.commons.mapping.gmt.elements.TopographicSlopeFile;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.sha.cybershake.maps.CyberShake_GMT_MapGenerator;
import org.opensha.sha.cybershake.maps.GMT_InterpolationSettings;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

import scratch.UCERF3.analysis.FaultBasedMapGen;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

public class MCErMapGenerator {
	
	public static GMT_InterpolationSettings  interpSettings =
			GMT_InterpolationSettings.getDefaultSettings();
	
//	public static void calculateMaps(int datasetID, int imTypeID, double period, ERF erf,
//			List<AttenuationRelationship> gmpes, SiteData<Double> vs30Prov, File outputDir)
//					throws IOException, GMT_MapException {
//		if (gmpes != null && gmpes.isEmpty())
//			gmpes = null;
//		ArbDiscrGeoDataSet probData = new ArbDiscrGeoDataSet(true);
//		ArbDiscrGeoDataSet detData = new ArbDiscrGeoDataSet(true);
//		ArbDiscrGeoDataSet detLowerLimit = new ArbDiscrGeoDataSet(true);
//		
//		DBAccess db = Cybershake_OpenSHA_DBApplication.getDB();
//		
//		HazardCurveFetcher fetcher = new HazardCurveFetcher(db, datasetID, imTypeID);
//		CachedPeakAmplitudesFromDB amps2db = new CachedPeakAmplitudesFromDB(db, MCERDataProductsCalc.cacheDir, erf);
//		Runs2DB runs2db = new Runs2DB(db);
//		
//		CyberShakeMCErDeterministicCalc detCalc = new CyberShakeMCErDeterministicCalc(amps2db, erf);
//		
//		List<CybershakeSite> sites = fetcher.getCurveSites();
//		CybershakeIM im = fetcher.getIM();
//		List<Integer> runIDs = fetcher.getRunIDs();
//		List<Double> detVals = fetcher.calcDeterministic(detCalc);
//		
//		ArbitrarilyDiscretizedFunc xValsFunc = new ArbitrarilyDiscretizedFunc();
//		xValsFunc.set(period, 0d);
//		
//		List<AttenuationRelationship> probGMPEs = null;
//		ArbDiscrGeoDataSet gmpeProbData = null;
//		ArbDiscrGeoDataSet gmpeDetData = null;
//		File gmpeProbCache = null;
//		File gmpeDetCache = null;
//		if (gmpes != null) {
//			if (gmpes.size() == 1) {
//				probGMPEs = gmpes;
//			} else {
//				probGMPEs = Lists.newArrayList();
//				probGMPEs.add(new MultiIMR_Averaged_AttenRel(gmpes));
//			}
//			
//			gmpeProbCache = getGMPECacheFile(true, gmpes, datasetID, imTypeID, outputDir);
//			gmpeDetCache = getGMPECacheFile(false, gmpes, datasetID, imTypeID, outputDir);
//			// see if cached
//			if (gmpeProbCache.exists())
//				gmpeProbData = ArbDiscrGeoDataSet.loadXYZFile(gmpeProbCache.getAbsolutePath(), true);
//			else
//				gmpeProbData = new ArbDiscrGeoDataSet(true);
//			if (gmpeDetCache.exists())
//				gmpeDetData = ArbDiscrGeoDataSet.loadXYZFile(gmpeDetCache.getAbsolutePath(), true);
//			else
//				gmpeDetData = new ArbDiscrGeoDataSet(true);
//		}
//		
//		CyberShakeComponent comp = im.getComponent();
//		Preconditions.checkNotNull(comp);
//		
//		for (int i=0; i<sites.size(); i++) {
//			CybershakeSite site = sites.get(i);
//			if (site.type_id == CybershakeSite.TYPE_TEST_SITE)
//				continue;
//			Location loc = site.createLocation();
//			if (probData.contains(loc))
//				continue;
//			
//			int runID = runIDs.get(i);
//			RTGMCalc probCalc = new RTGMCalc(runID, comp, null, db);
//			probCalc.setUse2PercentIn50(twoPercentIn50);
//			probCalc.setForceSingleIMTypeID(imTypeID);
//			if (probGMPEs != null && !gmpeProbData.contains(loc))
//				probCalc.setGMPEs(erf, probGMPEs);
//			Preconditions.checkState(probCalc.calc());
//			double probVal = probCalc.getCSSpectrumMap().get(comp).getY(im.getVal());
//			
//			probData.set(loc, probVal);
//			detData.set(loc, detVals.get(i));
//			// det lower limit
//			double vs30 = vs30Prov.getValue(loc);
//			detLowerLimit.set(loc, MCERDataProductsCalc.calc(xValsFunc, vs30, loc).getY(period));
//			
//			if (probGMPEs != null) {
//				if (!gmpeProbData.contains(loc)) {
//					double gmpeProbVal = probCalc.getGMPESpectrumMap().get(comp).get(0).getY(im.getVal());
//					gmpeProbData.set(loc, gmpeProbVal);
//				}
//				
//				if (!gmpeDetData.contains(loc)) {
//					// calculate deterministic
//					GMPEDeterministicComparisonCalc gmpeDetCalc = new GMPEDeterministicComparisonCalc(runs2db.getRun(runID),
//							site, comp, Lists.newArrayList(im.getVal()), CyberShakeMCErDeterministicCalc.percentile, erf, gmpes, null);
//					gmpeDetCalc.calc();
//					double maxGMPEDet = 0;
//					for (DeterministicResult result : gmpeDetCalc.getResults().rowMap().get(im.getVal()).values())
//						maxGMPEDet = Math.max(maxGMPEDet, result.getVal());
//					
//					gmpeDetData.set(loc, maxGMPEDet);
//				}
//			}
//			System.out.println("Completed site "+i+"/"+sites.size());
//		}
//		
//		if (gmpes != null) {
//			// write out gmpe caches
//			ArbDiscrGeoDataSet.writeXYZFile(gmpeProbData, gmpeProbCache);
//			ArbDiscrGeoDataSet.writeXYZFile(gmpeDetData, gmpeDetCache);
//		}
//		
//		File psvDir = new File(outputDir, "psv");
//		Preconditions.checkState(psvDir.exists() || psvDir.mkdir());
//		generateMaps(probData, detData, detLowerLimit, gmpeProbData, gmpeDetData, psvDir, period, true);
//		File saDir = new File(outputDir, "sa");
//		Preconditions.checkState(saDir.exists() || saDir.mkdir());
//		generateMaps(probData, detData, detLowerLimit, gmpeProbData, gmpeDetData, saDir, period, false);
//	}
//	
//	private static File getGMPECacheFile(boolean prob, List<AttenuationRelationship> gmpes, int datasetID,
//			int imTypeID, File outputDir) {
//		String name;
//		if (prob)
//			name = ".gmpe_prob";
//		else
//			name = ".gmpe_det";
//		name += "_dataset"+datasetID;
//		name += "_im"+imTypeID;
//		for (AttenuationRelationship gmpe : gmpes)
//			name += "_"+gmpe.getShortName();
//		name += ".txt";
//		return new File(outputDir, name);
//	}
	
	public static void calculateMaps(AbstractMCErProbabilisticCalc probCalc, AbstractMCErDeterministicCalc detCalc,
			Region region, Collection<Site> sites, double period, File outputDir) throws IOException, GMT_MapException {
		calculateMaps(null, null, probCalc, detCalc, null, null, null, region, sites, period, outputDir);
	}
	
	public static boolean doPSV = true;
	public static boolean doSA = true;
	
	public static void calculateMaps(String name, String prefix, AbstractMCErProbabilisticCalc probCalc, AbstractMCErDeterministicCalc detCalc,
			String compareName, AbstractMCErProbabilisticCalc compareProbCalc, AbstractMCErDeterministicCalc compareDetCalc,
			Region region, Collection<Site> sites, double period, File outputDir) throws IOException, GMT_MapException {
		Preconditions.checkNotNull(region);
		Preconditions.checkState(!sites.isEmpty(), "No sites given!");
		
		ArbDiscrGeoDataSet probData = null;
		if (probCalc != null)
			probData = new ArbDiscrGeoDataSet(true);
		ArbDiscrGeoDataSet detData = null;
		if (detCalc != null)
			detData = new ArbDiscrGeoDataSet(true);
		
		ArbDiscrGeoDataSet compareProbData = null;
		if (compareProbCalc != null)
			compareProbData = new ArbDiscrGeoDataSet(true);
		ArbDiscrGeoDataSet compareDetData = null;
		if (compareDetCalc != null)
			compareDetData = new ArbDiscrGeoDataSet(true);
		
		if (probData != null) {
			Stopwatch watch = Stopwatch.createStarted();
			System.out.println("Calculating main probabilistic for "+sites.size()+" sites.");
			for (Site site : sites) {
				if (probData != null)
					probData.set(site.getLocation(), probCalc.calc(site, period));
			}
			watch.stop();
			System.out.println("Took "+smartElapsed(watch));
			if (probCalc instanceof CachedMCErProbabilisticCalc)
				((CachedMCErProbabilisticCalc)probCalc).flushCache();
		}
		if (detData != null) {
			Stopwatch watch = Stopwatch.createStarted();
			System.out.println("Calculating main deterministic for "+sites.size()+" sites.");
			for (Site site : sites) {
				if (detData != null)
					detData.set(site.getLocation(), detCalc.calc(site, period).getVal());
			}
			watch.stop();
			System.out.println("Took "+smartElapsed(watch));
			if (detCalc instanceof CachedMCErDeterministicCalc)
				((CachedMCErDeterministicCalc)detCalc).flushCache();
		}
		
		if (compareProbData != null) {
			Stopwatch watch = Stopwatch.createStarted();
			System.out.println("Calculating comparison probabilistic for "+sites.size()+" sites.");
			for (Site site : sites) {
				if (compareProbData != null)
					compareProbData.set(site.getLocation(), compareProbCalc.calc(site, period));
			}
			watch.stop();
			System.out.println("Took "+smartElapsed(watch));
			if (compareProbCalc instanceof CachedMCErProbabilisticCalc)
				((CachedMCErProbabilisticCalc)compareProbCalc).flushCache();
		}
		if (compareDetData != null) {
			Stopwatch watch = Stopwatch.createStarted();
			System.out.println("Calculating comparison deterministic for "+sites.size()+" sites.");
			for (Site site : sites) {
				if (compareDetData != null)
					compareDetData.set(site.getLocation(), compareDetCalc.calc(site, period).getVal());
			}
			watch.stop();
			System.out.println("Took "+smartElapsed(watch));
			if (compareDetCalc instanceof CachedMCErDeterministicCalc)
				((CachedMCErDeterministicCalc)compareDetCalc).flushCache();
		}
		
		ArbDiscrGeoDataSet detLowerLimit = new ArbDiscrGeoDataSet(true);
		for (Site site : sites) {
			double vs30;
			try {
				vs30 = site.getParameter(Double.class, Vs30_Param.NAME).getValue();
			} catch (ParameterException e) {
				// no Vs30
				System.out.println("Skipping Det Lower Limit, site doesn't have Vs30");
				detLowerLimit = null;
				break;
			}
			try {
				detLowerLimit.set(site.getLocation(), ASCEDetLowerLimitCalc.calc(period, vs30, site.getLocation()));
			} catch (IllegalStateException e) {
				// no TL data found
				System.out.println("Skipping Det Lower Limit, site doesn't have TL data available");
				detLowerLimit = null;
				break;
			}
		}
		
		if (doPSV) {
			File psvDir = new File(outputDir, "psv");
			Preconditions.checkState(psvDir.exists() || psvDir.mkdir());
			generateMaps(region, name, prefix, probData, detData, detLowerLimit,
					compareName, compareProbData, compareDetData, psvDir, period, true);
		}
		if (doSA) {
			File saDir = new File(outputDir, "sa");
			Preconditions.checkState(saDir.exists() || saDir.mkdir());
			generateMaps(region, name, prefix, probData, detData, detLowerLimit,
					compareName, compareProbData, compareDetData, saDir, period, false);
		}
	}
	
	private static String smartElapsed(Stopwatch watch) {
		long sec = watch.elapsed(TimeUnit.SECONDS);
		if (sec == 0)
			return watch.elapsed(TimeUnit.MILLISECONDS)+" ms";
		if (sec > 60)
			return (float)((double)sec/60d)+" min";
		return sec+" s";
	}
	
	public static void generateMaps(Region region, GeoDataSet probData, GeoDataSet detData,
			GeoDataSet detLowerLimit, File outputDir, double period, boolean psv)
					throws IOException, GMT_MapException {
		generateMaps(region, null, null, probData, detData, detLowerLimit, null, null, null, outputDir, period, psv);
	}
	
	public static boolean plot_log = true;
	
	public static void generateMaps(Region region, String name, String prefix, GeoDataSet probData, GeoDataSet detData,
			GeoDataSet detLowerLimit, String compareName, GeoDataSet compareProbData, GeoDataSet compareDetData,
			File outputDir, double period, boolean psv) throws IOException, GMT_MapException {
		Preconditions.checkArgument(probData != null || detData != null || detLowerLimit != null);
		boolean generateMCER = probData != null && detData != null;
		if (generateMCER && detLowerLimit == null)
			System.err.println("WARNING: Calculating MCER without deterministic lower limit");
		
		CPT cpt = buildCPT(period, psv);
		
		String units = (float)period+"s";
		String prefixAdd;
		if (psv) {
			units += " PSV (cm/sec)";
			prefixAdd = "_psv";
		} else {
			units += " Sa (g)";
			prefixAdd = "_sa";
		}
		
		if (prefix != null && !prefix.isEmpty())
			prefixAdd = "_"+prefix+prefixAdd;
		
		if (name == null)
			name = "";
		else
			name = name+" ";
		
		if (compareProbData != null || compareDetData != null)
			Preconditions.checkState(compareName != null && !compareName.isEmpty(),
					"Must supply name for comparison data");
		
		if (probData != null) {
			generateMaps(region, probData, outputDir, period, psv, "prob_mcer"+prefixAdd, name+"Prob. MCE@-R@-, "+units, cpt, plot_log);
			if (compareProbData != null)
				generateMaps(region, compareProbData, outputDir, period, psv, compareName.toLowerCase()+"_prob_mcer"+prefixAdd,
						compareName+" Prob. MCE@-R@-, "+units, cpt, plot_log);
		}
		
		if (detData != null) {
			generateMaps(region, detData, outputDir, period, psv, "det_mcer"+prefixAdd, name+"Det. MCE@-R@-, "+units, cpt, plot_log);
			if (compareDetData != null)
				generateMaps(region, compareDetData, outputDir, period, psv, compareName.toLowerCase()+"_det_mcer"+prefixAdd,
						compareName+" Det. MCE@-R@-, "+units, cpt, plot_log);
		}
		
		if (detLowerLimit != null) {
			generateMaps(region, detLowerLimit, outputDir, period, psv, "det_lower_limit"+prefixAdd,
					"Det. Lower Limit, "+units, cpt, plot_log);
		}
		
		if (probData != null && detData != null && detLowerLimit != null) {
			ArbDiscrGeoDataSet combinedData = new ArbDiscrGeoDataSet(probData.isLatitudeX());
			for (Location loc : probData.getLocationList()) {
				double pVal = probData.get(loc);
				double dVal = detData.get(loc);
				double dLowVal = detLowerLimit.get(loc);
				
				double combinedVal = MCErCalcUtils.calcMCER(dVal, pVal, dLowVal);
				combinedData.set(loc, combinedVal);
			}
			generateMaps(region, combinedData, outputDir, period, psv, "combined_mcer"+prefixAdd,
					"Combined MCE@-R@-, "+units, cpt, plot_log);
			if (compareProbData != null && compareDetData != null) {
				ArbDiscrGeoDataSet gmpeCombinedData = new ArbDiscrGeoDataSet(probData.isLatitudeX());
				for (Location loc : probData.getLocationList()) {
					double pVal = compareProbData.get(loc);
					double dVal = compareDetData.get(loc);
					double dLowVal = detLowerLimit.get(loc);
					
					double combinedVal = MCErCalcUtils.calcMCER(dVal, pVal, dLowVal);
					gmpeCombinedData.set(loc, combinedVal);
				}
				generateMaps(region, gmpeCombinedData, outputDir, period, psv, compareName.toLowerCase()+"_combined_mcer"+prefixAdd,
						"GMPE Combined MCE@-R@-, "+units, cpt, plot_log);
				
				// now ratio
				GeoDataSet ratioData = GeoDataSetMath.divide(combinedData, gmpeCombinedData);
				CPT ratioCPT = CyberShake_GMT_MapGenerator.getRatioCPT();
				generateMaps(region, ratioData, outputDir, period, false, compareName.toLowerCase()+"_combined_ratio",
						name.trim()+"/"+compareName+" MCE@-R@- Ratio, "+units, ratioCPT, false);
			}
			
			// now governing scatter
			GMT_Map govMap = buildGoverningScatterMap(region, probData, detData, detLowerLimit, name+"Governing Scatter");
			FaultBasedMapGen.plotMap(outputDir, "governing_scatter"+prefixAdd, false, govMap);
			if (compareProbData != null && compareDetData != null) {
				govMap = buildGoverningScatterMap(region, compareProbData, compareDetData,
						detLowerLimit, compareName+" Governing Scatter");
				FaultBasedMapGen.plotMap(outputDir, "gmpe_governing_scatter"+prefixAdd, false, govMap);
			}
		}
	}
	
//	public static void generateGMPEOnlyMaps(List<ERF> erfs, List<String> erfNames, List<AttenuationRelationship> gmpes,
//			int datasetID, int imTypeID, double period, SiteData<Double> vs30Prov, File outputDir)
//					throws GMT_MapException, IOException {
//		DBAccess db = Cybershake_OpenSHA_DBApplication.getDB();
//		HazardCurveFetcher fetcher = new HazardCurveFetcher(db, datasetID, imTypeID);
//		
//		List<CybershakeSite> sites = fetcher.getCurveSites();
//		List<Integer> runIDs = fetcher.getRunIDs();
//		Preconditions.checkState(sites.size() == runIDs.size());
//		CybershakeIM im = fetcher.getIM();
//		CyberShakeComponent comp = im.getComponent();
//		
//		List<GeoDataSet> probDatas = Lists.newArrayList();
//		List<File> cacheFiles = Lists.newArrayList();
//		for (int i=0; i<erfs.size(); i++) {
//			String name = erfNames.get(i);
//			File cacheFile = getGMPECacheFile(true, gmpes, datasetID, imTypeID, outputDir);
//			cacheFile = new File(cacheFile.getParentFile(), cacheFile.getName().replaceAll(".txt", "")+"_"+name+".txt");
//			cacheFiles.add(cacheFile);
//			GeoDataSet probData;
//			if (cacheFile.exists())
//				probData = ArbDiscrGeoDataSet.loadXYZFile(cacheFile.getAbsolutePath(), true);
//			else
//				probData = new ArbDiscrGeoDataSet(true);
//			probDatas.add(probData);
//		}
//		
//		List<AttenuationRelationship> probGMPEs = null;
//		if (gmpes != null) {
//			if (gmpes.size() == 1) {
//				probGMPEs = gmpes;
//			} else {
//				probGMPEs = Lists.newArrayList();
//				probGMPEs.add(new MultiIMR_Averaged_AttenRel(gmpes));
//			}
//		}
//		
//		for (int i=0; i<sites.size(); i++) {
//			CybershakeSite site = sites.get(i);
//			if (site.type_id == CybershakeSite.TYPE_TEST_SITE)
//				continue;
//			Location loc = site.createLocation();
//			
//			for (int j=0; j<erfs.size(); j++) {
//				GeoDataSet probData = probDatas.get(j);
//				if (probData.contains(loc))
//					continue;
//				ERF erf = erfs.get(j);
//				RTGMCalc probCalc = new RTGMCalc(runIDs.get(i), comp, null, db);
//				probCalc.setUse2PercentIn50(twoPercentIn50);
//				probCalc.setForceSingleIMTypeID(imTypeID);
//				probCalc.setGMPEs(erf, probGMPEs);
//				try {
//					Preconditions.checkState(probCalc.calc());
//				} catch (IOException e) {
//					ExceptionUtils.throwAsRuntimeException(e);
//				}
//				double gmpeProbVal = probCalc.getGMPESpectrumMap().get(comp).get(0).getY(im.getVal());
//				probData.set(loc, gmpeProbVal);
//			}
//		}
//		
//		CPT ratioCPT = CyberShake_GMT_MapGenerator.getRatioCPT();
//		
//		String units = (float)period+"s";
//		
//		String dataType;
//		if (twoPercentIn50)
//			dataType = "2% in 50y";
//		else
//			dataType = "MCE@-R@-";
//		
//		for (boolean psv : new boolean[] { true, false }) {
//			String prefixAdd;
//			if (psv) {
//				units += " PSV (cm/sec)";
//				prefixAdd = "_psv";
//			} else {
//				units += " Sa (g)";
//				prefixAdd = "_sa";
//			}
//			CPT cpt = buildCPT(period, psv);
//			for (int i=0; i<erfs.size(); i++) {
//				String name1 = erfNames.get(i);
//				GeoDataSet data1 = probDatas.get(i);
//				
//				generateMaps(data1, outputDir, period, psv, name1+"_prob"+prefixAdd, name1+" GMPE Prob Map", cpt, true);
//				for (int j=i+1; j<erfs.size(); j++) {
//					String name2 = erfNames.get(j);
//					GeoDataSet data2 = probDatas.get(j);
//					
//					GeoDataSet ratioData = GeoDataSetMath.divide(data1, data2);
//					// find min/max
//					double maxRatio = 0d;
//					Location maxRatioLoc = null;
//					double minRatio = Double.POSITIVE_INFINITY;
//					Location minRatioLoc = null;
//					
//					for (int ind=0; ind<ratioData.size(); ind++) {
//						double r = ratioData.get(ind);
//						if (r > maxRatio) {
//							maxRatio = r;
//							maxRatioLoc = ratioData.getLocation(ind);
//						}
//						if (r < minRatio) {
//							minRatio = r;
//							minRatioLoc = ratioData.getLocation(ind);
//						}
//					}
//					System.out.println("Max ratio: "+maxRatio+" at "
//							+findClosestSite(sites, maxRatioLoc).short_name+", "+maxRatioLoc);
//					System.out.println("Min ratio: "+minRatio+" at "
//							+findClosestSite(sites, minRatioLoc).short_name+", "+minRatioLoc);
//					generateMaps(ratioData, outputDir, period, false, name1+"_vs_"+name2+"_prob_ratio"+prefixAdd,
//							"GMPE "+name1+"/"+name2+" Prob. "+dataType+" Ratio, "+units, ratioCPT, false);
//				}
//			}
//		}
//		
//		// write out caches
//		for (int i=0; i<cacheFiles.size(); i++)
//			ArbDiscrGeoDataSet.writeXYZFile(probDatas.get(i), cacheFiles.get(i));
//	}
//	
//	private static CybershakeSite findClosestSite(List<CybershakeSite> sites, Location loc) {
//		CybershakeSite closest = null;
//		double minDist = Double.POSITIVE_INFINITY;
//		
//		for (CybershakeSite site : sites) {
//			double dist = LocationUtils.horzDistanceFast(loc, site.createLocation());
//			if (dist < minDist) {
//				minDist= dist;
//				closest = site;
//			}
//		}
//		
//		return closest;
//	}
	
	private static void generateMaps(Region region, GeoDataSet data, File outputDir, double period, boolean psv,
			String prefix, String title, CPT cpt, boolean log) throws GMT_MapException, IOException {
		GMT_Map map = buildScatterMap(region, data, psv, period, title, cpt, log);
		// I hate this hack...but don't want to add more variables
		if (!log && title.toLowerCase().contains("ratio"))
			map.setCPTEqualSpacing(true);
		map.getInterpSettings().setSaveInterpSurface(true);
		String addr = FaultBasedMapGen.plotMap(outputDir, prefix+"_marks", false, map);
		// download interpolated
		FileUtils.downloadURL(addr+GMT_InterpolationSettings.INTERP_XYZ_FILE_NAME,
				new File(outputDir, prefix+"_"+GMT_InterpolationSettings.INTERP_XYZ_FILE_NAME));
		map.getInterpSettings().setSaveInterpSurface(false);
		// write scatter
		ArbDiscrGeoDataSet.writeXYZFile(data, new File(outputDir, prefix+"_map_data_scatter.txt").getAbsolutePath());
		map.setSymbolSet(null);
		FaultBasedMapGen.plotMap(outputDir, prefix, false, map);
		map.setContourIncrement(0.1);
		FaultBasedMapGen.plotMap(outputDir, prefix+"_contours", false, map);
		map.setContourOnly(true);
		FaultBasedMapGen.plotMap(outputDir, prefix+"_contours_only", false, map);
	}
	
	public static void applyGMTSettings(GMT_Map map, CPT cpt, String label) {
		map.setInterpSettings(interpSettings);
		map.setLogPlot(false); // already did manually
		map.setMaskIfNotRectangular(true);
		map.setTopoResolution(TopographicSlopeFile.CA_THREE);
//		map.setTopoResolution(null);
		map.setBlackBackground(false);
		map.setCustomScaleMin((double)cpt.getMinValue());
		map.setCustomScaleMax((double)cpt.getMaxValue());
		map.setCustomLabel(label);
		map.setRescaleCPT(false);
//		map.setDpi(150);
	}
	
	private static GMT_Map buildScatterMap(Region region, GeoDataSet data, boolean psv, double period,
			String label, CPT cpt, boolean log) {
		data = data.copy();
		if (psv)
			for (int index=0; index<data.size(); index++)
				data.set(index, MCErCalcUtils.saToPsuedoVel(data.get(index), period));
		if (log) {
			data.log10();
			label = "Log@-10@-("+label+")";
		}
		
		GMT_Map map = new GMT_Map(region, data, interpSettings.getInterpSpacing(), cpt);
		applyGMTSettings(map, cpt, label);
		
		// now add scatter
		PSXYSymbolSet xySet = new PSXYSymbolSet();
		CPT xyCPT = new CPT(0d, 1d, Color.WHITE, Color.WHITE);
		xySet.setCpt(xyCPT);
		for (Location loc : data.getLocationList()) {
			PSXYSymbol sym = new PSXYSymbol(new Point2D.Double(loc.getLongitude(), loc.getLatitude()),
					Symbol.INVERTED_TRIANGLE, 0.08f, 0f, null, Color.WHITE);
			xySet.addSymbol(sym, 0d);
//			symbols.add(sym);
		}
//		map.setSymbols(symbols);
		map.setSymbolSet(xySet);
		
		return map;
	}
	
	private static GMT_Map buildGoverningScatterMap(Region region, GeoDataSet probData, GeoDataSet detData,
			GeoDataSet detLowerLimit, String label) {
		GeoDataSet data = new ArbDiscrGeoDataSet(probData.isLatitudeX());
		
		// 0: prob, BLUE
		// 1: det, RED
		// 2: det lower, GRAY
		CPT xyCPT = new CPT();
		xyCPT.setBelowMinColor(Color.BLUE);
		xyCPT.add(new CPTVal(0f, Color.BLUE, 0.5f, Color.BLUE));
		xyCPT.add(new CPTVal(0.5f, Color.RED, 1.5f, Color.RED));
		xyCPT.add(new CPTVal(1.5f, Color.GRAY, 2f, Color.GRAY));
		xyCPT.setAboveMaxColor(Color.GRAY);
		
		for (Location loc : probData.getLocationList()) {
			double pVal = probData.get(loc);
			double dVal = detData.get(loc);
			double dLowVal = detLowerLimit.get(loc);
			
			double combinedVal = MCErCalcUtils.calcMCER(dVal, pVal, dLowVal);
			if (combinedVal == pVal)
				data.set(loc, 0d);
			else if (combinedVal == dVal)
				data.set(loc, 1d);
			else if (combinedVal == dLowVal)
				data.set(loc, 2d);
			else
				throw new IllegalStateException("Combined val not any of the inputs??");
		}
		
		// dummy CPT for plotting
		CPT cpt = new CPT(0, 1, Color.WHITE, Color.WHITE);
		
		GMT_Map map = new GMT_Map(region, null, interpSettings.getInterpSpacing(), cpt);
		applyGMTSettings(map, cpt, label);
		
		// now add scatter
		PSXYSymbolSet xySet = new PSXYSymbolSet();
		xySet.setCpt(xyCPT);
		for (Location loc : data.getLocationList()) {
			PSXYSymbol sym = new PSXYSymbol(new Point2D.Double(loc.getLongitude(), loc.getLatitude()),
					Symbol.INVERTED_TRIANGLE, 0.08f, 0f, null, Color.WHITE);
			xySet.addSymbol(sym, data.get(loc));
//			symbols.add(sym);
		}
//		map.setSymbols(symbols);
		map.setSymbolSet(xySet);
		
		return map;
	}
	
	private static CPT buildCPT(double period, boolean psv) throws IOException {
		CPT cpt = CyberShake_GMT_MapGenerator.getHazardCPT();
		
		if (psv)
			cpt = cpt.rescale(Math.log10(2e1), Math.log10(2e3));
		else
			cpt = cpt.rescale(-1, 1);
		
		return cpt;
	}

//	public static void main(String[] args) throws IOException, GMT_MapException, DocumentException {
////		// -1 here means RTGM
////		GeoDataSet probData = HardCodedInterpDiffMapCreator.getMainScatter(true, -1, Lists.newArrayList(35), 21);
////		
////		generateMaps(probData, null, null, new File("/tmp/mcer_test"), 3d, false);
////		generateMaps(probData, null, null, new File("/tmp/mcer_test"), 3d, true);
//		int datasetID = 57;
//		// geom mean 3s
////		int imTypeID = 21;
////		double period = 3d;
////		String outputName = "test1_geom";
//		
//		// geom mean 5s
////		int imTypeID = 11;
////		double period = 5d;
////		String outputName = "test1_geom";
//		
//		// geom mean 10s
////		int imTypeID = 1;
////		double period = 10d;
////		String outputName = "test1_geom";
//		
//		// RotD100
//		int imTypeID = 151;
//		double period = 2d;
//		
////		int imTypeID = 146;
////		double period = 3d;
//		
////		int imTypeID = 142;
////		double period = 5d;
//		
////		int imTypeID = 136;
////		double period = 10d;
//		
//		String outputName = "study_15_4_rotd100_old_det";
//		
//		File outputDir = new File("/home/kevin/CyberShake/MCER/maps/"+outputName+"/"+(int)period+"s");
//		Preconditions.checkState(outputDir.exists() || outputDir.mkdirs());
//		
//		ERF erf = MeanUCERF2_ToDB.createUCERF2ERF();
//		List<AttenuationRelationship> gmpes = Lists.newArrayList();
//		gmpes.add(AttenRelRef.ASK_2014.instance(null));
//		gmpes.add(AttenRelRef.BSSA_2014.instance(null));
//		gmpes.add(AttenRelRef.CB_2014.instance(null));
//		gmpes.add(AttenRelRef.CY_2014.instance(null));
//		for (AttenuationRelationship gmpe : gmpes)
//			gmpe.setParamDefaults();
//		
//		calculateMaps(datasetID, imTypeID, period, erf, gmpes, new WillsMap2006(), outputDir);
//		
////		// UCERF3/UCERF2 comparisons
////		twoPercentIn50 = true;
////		
////		String twoP_add = "";
////		if (twoPercentIn50 == true)
////			twoP_add = "_2pin50";
////		File outputDir = new File("/home/kevin/CyberShake/MCER/maps/ucerf3_ucerf2_gmpe_rotd100/"+(int)period+"s"+twoP_add);
////		Preconditions.checkState(outputDir.exists() || outputDir.mkdirs());
////		
////		ERF ucerf2 = MeanUCERF2_ToDB.createUCERF2ERF();
////		ERF ucerf3 = new FaultSystemSolutionERF(FaultSystemIO.loadSol(new File(
////				"/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/"
////				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip")));
////		ucerf3.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
////		ucerf3.getTimeSpan().setDuration(1d);
////		ucerf3.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
////		ucerf3.updateForecast();
////		
////		List<ERF> erfs = Lists.newArrayList(ucerf3, ucerf2);
////		List<String> names = Lists.newArrayList("UCERF3", "UCERF2");
////		
////		List<AttenuationRelationship> gmpes = Lists.newArrayList();
////		gmpes.add(AttenRelRef.ASK_2014.instance(null));
////		gmpes.add(AttenRelRef.BSSA_2014.instance(null));
////		gmpes.add(AttenRelRef.CB_2014.instance(null));
////		gmpes.add(AttenRelRef.CY_2014.instance(null));
////		for (AttenuationRelationship gmpe : gmpes)
////			gmpe.setParamDefaults();
////		
////		generateGMPEOnlyMaps(erfs, names, gmpes, datasetID, imTypeID, period, new WillsMap2006(), outputDir);
//		
//		System.exit(0);
//	}

}
