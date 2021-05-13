package org.opensha.sha.calc.hazus.parallel;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.siteData.AbstractSiteData;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.data.siteData.SiteDataValueList;
import org.opensha.commons.data.siteData.impl.CVM4BasinDepth;
import org.opensha.commons.data.siteData.impl.WaldAllenGlobalVs30;
import org.opensha.commons.data.siteData.impl.WillsMap2006;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.mpj.FastMPJShellScriptWriter;
import org.opensha.commons.hpc.mpj.MPJExpressShellScriptWriter;
import org.opensha.commons.hpc.pbs.USC_HPCC_ScriptWriter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.calc.hazardMap.components.AsciiFileCurveArchiver;
import org.opensha.sha.calc.hazardMap.components.CalculationInputsXMLFile;
import org.opensha.sha.calc.hazardMap.components.CalculationSettings;
import org.opensha.sha.calc.hazardMap.components.CurveResultsArchiver;
import org.opensha.sha.calc.hazardMap.mpj.MPJHazardCurveDriver;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.MultiIMR_Averaged_AttenRel;
import org.opensha.sha.imr.attenRelImpl.NGA_2008_Averaged_AttenRel;
import org.opensha.sha.imr.attenRelImpl.NGA_2008_Averaged_AttenRel_NoAS;
import org.opensha.sha.imr.attenRelImpl.NSHMP_2008_CA;
import org.opensha.sha.imr.attenRelImpl.USGS_Combined_2004_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.util.SiteDataTypeParameterNameMap;
import org.opensha.sha.util.SiteTranslator;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;

public class HazusJobWriter {
	
	private static SimpleDateFormat df = new SimpleDateFormat("yyyy_MM_dd-HH_mm");
	private static final boolean constrainBasinMin = false;
	
	static MeanUCERF2 getUCERF2(int years, int startYear, boolean includeBackSeis) {
		return getUCERF2(years, startYear, includeBackSeis, UCERF2.BACK_SEIS_RUP_CROSSHAIR);
	}
	
	static MeanUCERF2 getUCERF2(int years, int startYear, boolean includeBackSeis, String backSeisType) {
		MeanUCERF2 ucerf = new MeanUCERF2();
		
		if (startYear > 0) {
			ucerf.getAdjustableParameterList().getParameter(String.class, UCERF2.PROB_MODEL_PARAM_NAME)
					.setValue(MeanUCERF2.PROB_MODEL_WGCEP_PREF_BLEND);
			ucerf.getTimeSpan().setDuration(years);
			ucerf.getTimeSpan().setStartTime(startYear);
		} else {
			ucerf.getAdjustableParameterList().getParameter(String.class, UCERF2.PROB_MODEL_PARAM_NAME)
					.setValue(UCERF2.PROB_MODEL_POISSON);
			ucerf.getTimeSpan().setDuration(years);
		}
		
		ucerf.setParameter(UCERF2.BACK_SEIS_RUP_NAME, backSeisType);
		StringParameter backSeisParam = (StringParameter) ucerf.getParameter(UCERF2.BACK_SEIS_NAME);
		if (includeBackSeis)
			backSeisParam.setValue(UCERF2.BACK_SEIS_INCLUDE);
		else
			backSeisParam.setValue(UCERF2.BACK_SEIS_EXCLUDE);
		
		ucerf.updateForecast();
		System.out.println("UCERF Params:");
		System.out.println(ucerf.getAdjustableParameterList().getParameterListMetadataString());
		
		return ucerf;
	}

	static AbstractERF getERF(int years, int startYear, boolean includeBackSeis) {
		return getUCERF2(years, startYear, includeBackSeis);
	}
	
	private static ScalarIMR getUSGSCombined2004IMR() {
		ScalarIMR attenRel = new USGS_Combined_2004_AttenRel(null);
		attenRel.setParamDefaults();
		attenRel.getParameter(ComponentParam.NAME).
				setValue(Component.AVE_HORZ);
		return attenRel;
	}
	
	private static ScalarIMR getUSGSCombined2008IMR() {
		ScalarIMR attenRel = new NSHMP_2008_CA(null);
		attenRel.setParamDefaults();
		return attenRel;
	}
	
	private static ScalarIMR getCB_2008IMR() {
		ScalarIMR imr = new CB_2008_AttenRel(null);
		imr.setParamDefaults();
		return imr;
	}
	
	private static ScalarIMR getNGA_2008IMR(
			boolean propEffectSpeedup, boolean includeAS) {
		ScalarIMR imr;
		if (includeAS)
			imr = new NGA_2008_Averaged_AttenRel(null);
		else
			imr = new NGA_2008_Averaged_AttenRel_NoAS(null);
		imr.setParamDefaults();
		return imr;
	}

	static ScalarIMR getIMR(String name, double sigmaTrunc, boolean propEffectSpeedup){
		ScalarIMR attenRel = null;
		if (name.equals(NSHMP_08_NAME))
			attenRel = getUSGSCombined2008IMR();
		else if (name.equals(MultiIMR_NAME))
			attenRel = getNGA_2008IMR(propEffectSpeedup, true);
		else if (name.equals(MultiIMR_NO_AS_NAME))
			attenRel = getNGA_2008IMR(propEffectSpeedup, false);
		else {
			for (AttenRelRef ref : AttenRelRef.values()) {
				if (ref.name().equals(name)) {
					attenRel = ref.instance(null);
					break;
				}
			}
			if (attenRel == null)
				throw new IllegalArgumentException("Not valid IMR: "+name);
		}
//		ScalarIntensityMeasureRelationshipAPI attenRel = getUSGSCombined2008IMR();
//		ScalarIntensityMeasureRelationshipAPI attenRel = getCB_2008IMR();
		attenRel.getParameter(Vs30_Param.NAME).setValue(new Double(760));
		if (sigmaTrunc > 0) {
			attenRel.getParameter(SigmaTruncTypeParam.NAME).
				setValue(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);
			attenRel.getParameter(SigmaTruncLevelParam.NAME).
				setValue(new Double(sigmaTrunc));
		} else {
			attenRel.getParameter(SigmaTruncTypeParam.NAME).
			setValue(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_NONE);
		}
		
		return attenRel;
	}
	
	public static LocationList loadCSV(File file) throws IOException {
		LocationList locs = new LocationList();
		
		CSVFile<String> csv = CSVFile.readFile(file, true);
		
		for (int i=0; i<csv.getNumRows(); i++) {
			List<String> line = csv.getLine(i);
			if (line.get(0).equals("GRID_ID"))
				// header
				continue;
			int start;
			if (line.size() == 3)
				// has an ID field
				start = 1;
			else
				start = 0;
			double lat = Double.parseDouble(line.get(start));
			double lon = Double.parseDouble(line.get(start+1));
			locs.add(new Location(lat, lon));
		}
		
		return locs;
	}
	
	public static ArrayList<Site> loadSites(List<SiteData<?>> provs, LocationList locs, List<ScalarIMR> imrs,
			boolean nullBasin, boolean constrainBasinMin, SiteDataValue<?> hardcodedVal) throws IOException {
		if (provs == null)
			provs = new ArrayList<SiteData<?>>();
		
		if (constrainBasinMin) {
			// constrain basin depth to default minimums
			for (SiteData<?> prov : provs) {
				if (prov.getDataType().equals(SiteData.TYPE_DEPTH_TO_2_5)
						|| prov.getDataType().equals(SiteData.TYPE_DEPTH_TO_1_0)) {
					Parameter<Double> minBasinParam = null;
					try {
						minBasinParam = prov.getAdjustableParameterList()
							.getParameter(Double.class, AbstractSiteData.PARAM_MIN_BASIN_DEPTH_DOUBLE_NAME);
					} catch (ParameterException e) {}
					if (minBasinParam != null) {
//						while (siteParamsIt.hasNext()) {
						for (ScalarIMR imr : imrs) {
							for (Parameter<?> param : imr.getSiteParams()) {
								if (param.getName().equals(DepthTo2pt5kmPerSecParam.NAME)
										&& prov.getDataType().equals(SiteData.TYPE_DEPTH_TO_2_5)) {
									minBasinParam.setValue((Double)param.getValue());
								} else if (param.getName().equals(DepthTo1pt0kmPerSecParam.NAME)
										&& prov.getDataType().equals(SiteData.TYPE_DEPTH_TO_1_0)) {
									Double minVal = (Double)param.getValue();
									// convert from KM to M
									minVal *= 1000;
									minBasinParam.setValue(minVal);
								}
							}
						}
					}
				}
			}
		}
		
		ArrayList<SiteDataValue<?>>[] siteData = new ArrayList[locs.size()];
		if (hardcodedVal != null) {
			for (int i=0; i<siteData.length; i++) {
				siteData[i] = new ArrayList<SiteDataValue<?>>();
				siteData[i].add(hardcodedVal);
			}
		}
		
		for (SiteData<?> prov : provs) {
			SiteDataValueList<?> vals = prov.getAnnotatedValues(locs);
			for (int i=0; i<siteData.length; i++) {
				if (siteData[i] == null)
					siteData[i] = new ArrayList<SiteDataValue<?>>();
				SiteDataValue<?> val = vals.getValue(i);
				siteData[i].add(val);
			}
		}
		
		SiteTranslator trans = new SiteTranslator();
		
		ArrayList<Site> sites = new ArrayList<Site>();
		for (int i=0; i<locs.size(); i++) {
			Location loc = locs.get(i);
			Site site = new Site(loc);
			ArrayList<SiteDataValue<?>> datas = siteData[i];
			for (ScalarIMR imr : imrs) {
				for (Parameter<?> siteParam : imr.getSiteParams()) {
					if (site.containsParameter(siteParam))
						continue;
					Parameter<?> clonedParam = (Parameter<?>) siteParam.clone();
					String paramName = siteParam.getName();
					if (nullBasin &&
							(paramName.equals(DepthTo2pt5kmPerSecParam.NAME)
									|| paramName.equals(DepthTo1pt0kmPerSecParam.NAME))) {
						clonedParam.setValue(null);
					} else {
						trans.setParameterValue(clonedParam, datas);
					}
					site.addParameter(clonedParam);
				}
			}
			sites.add(site);
		}
		
		return sites;
	}
	
	static final String NSHMP_08_NAME = "NSHMP08";
	static final String MultiIMR_NAME = "MultiIMR";
	static final String MultiIMR_NO_AS_NAME = "MultiIMRnoAS";

	public static void main(String args[]) throws IOException, InvocationTargetException {
		if (args.length < 7 || args.length > 11) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(HazusJobWriter.class)+
					" <T/F: time dependent> <"+NSHMP_08_NAME+"/"+MultiIMR_NAME+"/"+MultiIMR_NO_AS_NAME+">"+
					" <T/F: prop effect speedup> <T/F: back seis>"+
					" <HardCoded Vs30 (or 'null'/'wald'/'nobasin' for site data providers)>"+
					" <spacing> <dir name> [<sites per job>] (OR) [<minutes> <nodes> [<ppn> [<queue>]]]");
			System.exit(2);
		}
		boolean MPJ = args.length > 8;
		
		boolean timeDep = Boolean.parseBoolean(args[0]);
		String imrStr = args[1];
		boolean propEffectSpeedup = Boolean.parseBoolean(args[2]);
		boolean includeBackSeis = Boolean.parseBoolean(args[3]);
		String vs30Str = args[4];
		SiteDataValue<?> hardcodedVal;
		boolean noBasin = false;
		boolean useWald = false;
		if (vs30Str.equals("null")) {
			hardcodedVal = null;
		} else if (vs30Str.equalsIgnoreCase("wald")) {
			useWald = true;
			hardcodedVal = null;
		} else if (vs30Str.equalsIgnoreCase("nobasin")) {
			hardcodedVal = null;
			noBasin = true;
		} else
			hardcodedVal = new SiteDataValue<Double>(SiteData.TYPE_VS30, SiteData.TYPE_FLAG_INFERRED,
					Double.parseDouble(vs30Str));
		String dirName = args[6];
		
		int years = 50;
		int startYear;
		if (timeDep)
			startYear = 2012;
		else
			startYear = -1;
		AbstractERF erf = getERF(years, startYear, includeBackSeis);
		
//		SiteDataValue<?> hardcodedVal =
//			new SiteDataValue<String>(SiteDataAPI.TYPE_WILLS_CLASS, SiteDataAPI.TYPE_FLAG_INFERRED, "B");
		boolean nullBasin = true;
//		SiteDataValue<?> hardcodedVal =
//			new SiteDataValue<Double>(SiteDataAPI.TYPE_VS30, SiteDataAPI.TYPE_FLAG_INFERRED, 760.0);
//		SiteDataValue<?> hardcodedVal = null;
		
		double sigmaTrunc = 3;
		ScalarIMR imr = getIMR(imrStr, sigmaTrunc, propEffectSpeedup);
		
//		double spacing = 0.1;
		double spacing = Double.parseDouble(args[5]);
//		double spacing = 0.05;
		
		int mins = Integer.parseInt(args[7]);
		int nodes = Integer.parseInt(args[8]);
		int ppn;
		if (args.length > 9)
			ppn = Integer.parseInt(args[9]);
		else
			ppn = 0;
		String queue;
		if (args.length > 10)
			queue = args[10];
		else
			queue = null;
		
		File hazMapsDir = new File("/home/scec-02/kmilner/hazMaps");
		
		prepareJob(erf, imr, spacing, years, nullBasin, hardcodedVal, noBasin,
				useWald, dirName, mins, nodes, ppn, queue, hazMapsDir);
	}
	
	public static void prepareJob(AbstractERF erf, ScalarIMR imr, double spacing, int years,
			boolean nullBasin, SiteDataValue<?> hardcodedVal, boolean noBasin, boolean useWald, String dirName,
			int mins, int nodes, int ppn, String queue, File hazMapsDir) throws IOException, InvocationTargetException {
		
		Map<TectonicRegionType, ScalarIMR> imrMap =
			TRTUtils.wrapInHashMap(imr);
		List<Map<TectonicRegionType, ScalarIMR>> imrMaps = 
			new ArrayList<Map<TectonicRegionType,ScalarIMR>>();
		imrMaps.add(imrMap);
		
		Iterator<Parameter<?>> imrSiteParamsIt = imr.getSiteParamsIterator();
		while (imrSiteParamsIt.hasNext()) {
			Parameter<?> param =imrSiteParamsIt.next();
			String paramName = param.getName();
			if (nullBasin && (paramName.equals(DepthTo2pt5kmPerSecParam.NAME)
								|| paramName.equals(DepthTo1pt0kmPerSecParam.NAME))) {
				param.setValue(null);
			}
		}
		
		String spacingCode = ""+(int)(spacing * 100d);
		if (spacingCode.length() < 2)
			spacingCode = "0"+spacingCode;
//		Location topLeft = new Location(42.1, -125.5);
//		Location bottomRight = new Location(32.4, -114.1);
//		GriddedRegion region = new GriddedRegion(topLeft, bottomRight, spacing, topLeft);
//		GriddedRegion region = new CaliforniaRegions.RELM_TESTING_GRIDDED(spacing);
		File spacingFile = new File(hazMapsDir, spacingCode+"grid.csv");
		LocationList locs = loadCSV(spacingFile);
		
		ArrayList<SiteData<?>> provs = null;
		if (hardcodedVal == null) {
			provs = new ArrayList<SiteData<?>>();
			SiteDataTypeParameterNameMap siteDataMap = SiteTranslator.DATA_TYPE_PARAM_NAME_MAP;
			if (useWald) {
				System.out.println("Using WALD/ALLEN!");
				if (siteDataMap.isTypeApplicable(SiteData.TYPE_VS30, imr))
					provs.add(new WaldAllenGlobalVs30());
			} else {
				if (siteDataMap.isTypeApplicable(SiteData.TYPE_VS30, imr))
					provs.add(new WillsMap2006());
			}
			if (!noBasin) {
				if (siteDataMap.isTypeApplicable(SiteData.TYPE_DEPTH_TO_2_5, imr))
					provs.add(new CVM4BasinDepth(SiteData.TYPE_DEPTH_TO_2_5));
				if (siteDataMap.isTypeApplicable(SiteData.TYPE_DEPTH_TO_1_0, imr))
					provs.add(new CVM4BasinDepth(SiteData.TYPE_DEPTH_TO_1_0));
			}
		}
		
		if (constrainBasinMin) {
			// constrain basin depth to default minimums
			for (SiteData<?> prov : provs) {
				if (prov.getDataType().equals(SiteData.TYPE_DEPTH_TO_2_5)
						|| prov.getDataType().equals(SiteData.TYPE_DEPTH_TO_1_0)) {
					Parameter<Double> minBasinParam = null;
					try {
						minBasinParam = prov.getAdjustableParameterList()
							.getParameter(AbstractSiteData.PARAM_MIN_BASIN_DEPTH_DOUBLE_NAME);
					} catch (ParameterException e) {}
					if (minBasinParam != null) {
						ListIterator<Parameter<?>> siteParamsIt = imr.getSiteParamsIterator();
						while (siteParamsIt.hasNext()) {
							Parameter<?> param = siteParamsIt.next();
							if (param.getName().equals(DepthTo2pt5kmPerSecParam.NAME)
									&& prov.getDataType().equals(SiteData.TYPE_DEPTH_TO_2_5)) {
								minBasinParam.setValue((Double)param.getValue());
							} else if (param.getName().equals(DepthTo1pt0kmPerSecParam.NAME)
									&& prov.getDataType().equals(SiteData.TYPE_DEPTH_TO_1_0)) {
								Double minVal = (Double)param.getValue();
								// convert from KM to M
								minVal *= 1000;
								minBasinParam.setValue(minVal);
							}
						}
					}
				}
			}
		}
		
		ArrayList<SiteDataValue<?>>[] siteData = new ArrayList[locs.size()];
		if (hardcodedVal == null) {
			for (SiteData<?> prov : provs) {
				SiteDataValueList<?> vals = prov.getAnnotatedValues(locs);
				for (int i=0; i<siteData.length; i++) {
					if (siteData[i] == null)
						siteData[i] = new ArrayList<SiteDataValue<?>>();
					SiteDataValue<?> val = vals.getValue(i);
//					if ((val.getDataType().equals(SiteDataAPI.TYPE_DEPTH_TO_2_5)
//							&& (Double)val.getValue() > DepthTo2pt5kmPerSecParam.MAX)
//							|| (val.getDataType().equals(SiteDataAPI.TYPE_DEPTH_TO_1_0)
//							&& (Double)val.getValue() > DepthTo1pt0kmPerSecParam.MAX)) {
//						System.out.println("Got a super high: " + val);
//						val = new SiteDataValue<Double>(val.getDataType(),
//								val.getDataMeasurementType(), Double.NaN);
//					}
					siteData[i].add(val);
				}
			}
		} else {
			for (int i=0; i<siteData.length; i++) {
				siteData[i] = new ArrayList<SiteDataValue<?>>();
				siteData[i].add(hardcodedVal);
			}
		}
		
		SiteTranslator trans = new SiteTranslator();
		
		ArrayList<Site> sites = new ArrayList<Site>();
		for (int i=0; i<locs.size(); i++) {
			Location loc = locs.get(i);
			Site site = new Site(loc);
			ListIterator<Parameter<?>> it = imr.getSiteParamsIterator();
			ArrayList<SiteDataValue<?>> datas = siteData[i];
			while (it.hasNext()) {
				Parameter<?> siteParam = it.next();
				Parameter clonedParam = (Parameter) siteParam.clone();
				String paramName = siteParam.getName();
//				if (nullBasin &&
//						(paramName.equals(DepthTo2pt5kmPerSecParam.NAME)
//								|| paramName.equals(DepthTo1pt0kmPerSecParam.NAME))) {
//					clonedParam.setValue(null);
//				} else {
					trans.setParameterValue(clonedParam, datas);
//				}
				site.addParameter(clonedParam);
			}
			sites.add(site);
		}
		IMT_Info imtInfo = new IMT_Info();
		HashMap<String, DiscretizedFunc> imtXValMap = new HashMap<String, DiscretizedFunc>();
		imtXValMap.put(PGA_Param.NAME, imtInfo.getDefaultHazardCurve(PGA_Param.NAME));
		if (imr.isIntensityMeasureSupported(PGV_Param.NAME))
			imtXValMap.put(PGV_Param.NAME, imtInfo.getDefaultHazardCurve(PGV_Param.NAME));
		imtXValMap.put(SA_Param.NAME, imtInfo.getDefaultHazardCurve(SA_Param.NAME));
		CalculationSettings calcSet = new CalculationSettings(imtXValMap, 200.0);
		
//		String jobDir = "/home/scec-00/kmilner/hazMaps/hazus_test-" + df.format(new Date()) + "/";
//		String jobDir = "/home/scec-02/kmilner/hazMaps/"+dirName+"/";
		File jobDir = new File(hazMapsDir, dirName);
		String curveDir = new File(jobDir, "curves").getAbsolutePath()+File.separator;
		CurveResultsArchiver archiver = new AsciiFileCurveArchiver(curveDir, true, false);
		
		File javaBin = USC_HPCC_ScriptWriter.JAVA_BIN;
		File svnDir = new File(hazMapsDir, "svn");
		File distDir = new File(svnDir, "dist");
		File libDir = new File(svnDir, "lib");
		File jarFile = new File(distDir, "OpenSHA_complete.jar");
		
		
		
		HazusDataSetDAGCreator dag = new HazusDataSetDAGCreator(erf, imrMaps, sites,
				calcSet, archiver, javaBin.getAbsolutePath(), jarFile.getAbsolutePath(), years, spacing);
		
		ArrayList<File> classpath = new ArrayList<File>();
		classpath.add(jarFile);
		classpath.add(new File(libDir, "commons-cli-1.2.jar"));
		classpath.add(new File(libDir, "parallelcolt-0.9.4.jar"));
		classpath.add(new File(libDir, "csparsej.jar"));

//		MPJExpressShellScriptWriter mpj = new MPJExpressShellScriptWriter(javaBin, 2000, classpath,
//				USC_HPCC_ScriptWriter.MPJ_HOME, false);
		FastMPJShellScriptWriter mpj = new FastMPJShellScriptWriter(javaBin, 8000, classpath,
				USC_HPCC_ScriptWriter.FMPJ_HOME);


		ArrayList<Parameter<Double>> imts = HazusDataSetDAGCreator.getIMTList(imrMaps);

		CalculationInputsXMLFile inputs = new CalculationInputsXMLFile(erf,
				HazusDataSetDAGCreator.getHAZUSMaps(imrMaps), imts,
				sites, calcSet, archiver);

		jobDir.mkdir();

		File inputsFile = new File(jobDir, "inputs.xml");
		XMLUtils.writeObjectToXMLAsRoot(inputs, inputsFile);

		String cliArgs = inputsFile.getAbsolutePath();

		List<String> script = mpj.buildScript(MPJHazardCurveDriver.class.getName(), cliArgs);
		USC_HPCC_ScriptWriter writer = new USC_HPCC_ScriptWriter();

		script = writer.buildScript(script, mins, nodes, ppn, queue);

		JavaShellScriptWriter assembleWriter = new JavaShellScriptWriter(javaBin, 2048, classpath);
		String metadataFile = dag.writeMetadataFile(jobDir.getAbsolutePath());
		String assembleArgs = archiver.getStoreDir().getPath() + " " + years + " " + metadataFile;
		String assembleCommand = assembleWriter.buildCommand(HazusDataSetAssmbler.class.getName(), assembleArgs);
		String exitLine = script.remove(script.size()-1);
		script.addAll(assembleWriter.getJVMSetupLines());
		script.add(assembleCommand);
		script.add("");
		script.add(exitLine);

		if (dirName.startsWith("20") && dirName.contains("-"))
			dirName = dirName.substring(dirName.indexOf("-")+1);
		File pbsFile = new File(jobDir, dirName+".pbs");
		JavaShellScriptWriter.writeScript(pbsFile, script);
	}

}
