package scratch.UCERF3.erf;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math3.stat.StatUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BPT_AperiodicityParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.FaultGridSpacingParam;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.earthquake.rupForecastImpl.PointSource13b;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis.Point2Vert_FaultPoisSource;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.QuadSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.GaussianMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.SectionCluster;
import scratch.UCERF3.inversion.SectionClusterList;
import scratch.UCERF3.inversion.SectionConnectionStrategy;
import scratch.UCERF3.inversion.UCERF3SectionConnectionStrategy;
import scratch.UCERF3.inversion.laughTest.UCERF3PlausibilityConfig;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSystemIO;

public class FSS_ERF_ParamTest {
	
	
	/*
	 * All solutions are FM2.1
	 * *_1 solutions are generated on the fly
	 * *_2 solutions are the same as _1, but with different rates
	 */
	private static InversionFaultSystemSolution ivfss_1;
	private static InversionFaultSystemSolution ivfss_2;
	private static FaultSystemSolution fss_1_with_mfds;
	private static FaultSystemSolution fss_2_with_mfds;
	private static File ivfss_1_file;
	
	private static Map<String, List<?>> paramsOptionsMap;
	
	@BeforeClass
	public static void setUpBeforeClass() throws IOException {
		InversionFaultSystemRupSet rupSet1 = buildSmallTestRupSet();
		InversionFaultSystemRupSet rupSet2 = new InversionFaultSystemRupSet(
				rupSet1, rupSet1.getLogicTreeBranch(), null,
				rupSet1.getAveSlipForAllRups(),rupSet1.getCloseSectionsListList(),
				rupSet1.getRupturesForClusters(), rupSet1.getSectionsForClusters());
		
		double[] rates1 = new double[rupSet1.getNumRuptures()];
		for (int r=0; r<rupSet1.getNumRuptures(); r++) {
			rates1[r] = Math.random()*1e-4;
			if (Math.random() < 0.05)
				rates1[r] = 0;
		}
		double[] rates2 = new double[rupSet2.getNumRuptures()];
		for (int r=0; r<rupSet2.getNumRuptures(); r++)
			rates2[r] = Math.random()*1e-4;
		
		ivfss_1 = new InversionFaultSystemSolution(rupSet1, rates1);
		ivfss_1.getGridSourceProvider();
		ivfss_2 = new InversionFaultSystemSolution(rupSet2, rates2);
		
		fss_1_with_mfds = new FaultSystemSolution(rupSet1, rates1);
		populateMFDS(fss_1_with_mfds);
		fss_2_with_mfds = new FaultSystemSolution(rupSet2, rates2);
		populateMFDS(fss_2_with_mfds);
		
		// now save ivfss_1 to a file
		ivfss_1_file = File.createTempFile("openSHA", "test_sol.zip");
		FaultSystemIO.writeSol(ivfss_1, ivfss_1_file);
		
		// now initialze parameter options
		paramsOptionsMap = Maps.newHashMap();
		
		paramsOptionsMap.put(FaultGridSpacingParam.NAME, Lists.newArrayList(0.5d, 1d, 2d));
		paramsOptionsMap.put(AleatoryMagAreaStdDevParam.NAME, Lists.newArrayList(0d, 0.1d, 0.5d, 1d));
		paramsOptionsMap.put(ApplyGardnerKnopoffAftershockFilterParam.NAME,
				Lists.newArrayList(true, false));
		paramsOptionsMap.put(IncludeBackgroundParam.NAME, 
				Lists.newArrayList(EnumSet.allOf(IncludeBackgroundOption.class)));
		paramsOptionsMap.put(BackgroundRupParam.NAME,
				Lists.newArrayList(EnumSet.allOf(BackgroundRupType.class)));
		paramsOptionsMap.put(ProbabilityModelParam.NAME,
				Lists.newArrayList(EnumSet.allOf(ProbabilityModelOptions.class)));
		paramsOptionsMap.put(MagDependentAperiodicityParam.NAME,
				Lists.newArrayList(EnumSet.allOf(MagDependentAperiodicityOptions.class)));
		paramsOptionsMap.put(HistoricOpenIntervalParam.NAME, Lists.newArrayList(0d, 150d, 200d));
	}
	
	public static InversionFaultSystemRupSet buildSmallTestRupSet() {
		LogicTreeBranch branch = LogicTreeBranch.UCERF2;
		// this list will store our subsections
		List<FaultSection> subSections = Lists.newArrayList();
		
		FaultModels fm = branch.getValue(FaultModels.class);
		List<? extends FaultSection> fsd = fm.fetchFaultSections();
		double maxSubSectionLength = 0.5;
		double maxDistance = 5d;
		
		// build the subsections
		int sectIndex = 0;
		for (FaultSection parentSect : fsd) {
			if (parentSect.getSectionId() != 301 && parentSect.getSectionId() != 32)
				// only Mojave S and Parkfield
				continue;
			
			parentSect.setSlipRateStdDev(3d*Math.random());
			
			double ddw = parentSect.getOrigDownDipWidth();
			double maxSectLength = ddw*maxSubSectionLength;
			// the "2" here sets a minimum number of sub sections
			List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength, sectIndex, 2);
			// set random time of last event
			double yearsAgo = 200d*Math.random();
			long epochTime = System.currentTimeMillis() - (long)(yearsAgo*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
			for (FaultSection sect : newSubSects)
				sect.setDateOfLastEvent(epochTime);
			subSections.addAll(newSubSects);
			sectIndex += newSubSects.size();

		}
				
		UCERF3PlausibilityConfig laughTest = UCERF3PlausibilityConfig.getDefault();
		laughTest.setCoulombFilter(null);
		
		// calculate distances between each subsection
		Map<IDPairing, Double> subSectionDistances = DeformationModelFetcher.calculateDistances(maxDistance, subSections);
		Map<IDPairing, Double> reversed = Maps.newHashMap();
		// now add the reverse distance
		for (IDPairing pair : subSectionDistances.keySet()) {
			IDPairing reverse = pair.getReversed();
			reversed.put(reverse, subSectionDistances.get(pair));
		}
		subSectionDistances.putAll(reversed);
		Map<IDPairing, Double> subSectionAzimuths = DeformationModelFetcher.getSubSectionAzimuthMap(
				subSectionDistances.keySet(), subSections);
		
		SectionConnectionStrategy connectionStrategy = new UCERF3SectionConnectionStrategy(
				laughTest.getMaxJumpDist(), null);
		
		SectionClusterList clusters = new SectionClusterList(connectionStrategy,
				laughTest, subSections, subSectionDistances, subSectionAzimuths);
		
		List<List<Integer>> ruptures = Lists.newArrayList();
		for (SectionCluster cluster : clusters) {
			ruptures.addAll(cluster.getSectionIndicesForRuptures());
		}
		
		System.out.println("Created "+ruptures.size()+" ruptures");
		
		return new InversionFaultSystemRupSet(branch, clusters, subSections);
	}
//	private static InversionFaultSystemRupSet getSubset(InversionFaultSystemRupSet rupSet, int... parentSectIDs) {
//		int rupIndex = 0;
//		HashSet<Integer> parents = new HashSet<Integer>();
//		for (int parentID : parentSectIDs)
//			parents.add(parentID);
//		
//		List<FaultSectionPrefData> origFaultSectionData = rupSet.getFaultSectionDataList();
//		List<FaultSectionPrefData> newFaultSectionData = Lists.newArrayList();
//		
//		FaultSystemRupSet subRupSet = new FaultSystemRupSet(
//				faultSectionData, sectSlipRates, sectSlipRateStdDevs, sectAreas,
//				sectionForRups, mags, rakes, rupAreas, rupLengths, info);
//		
//		List<Integer> rupsToKeep = Lists.newArrayList();
//		List<List<FaultSectionPrefData>> sectionForRups = Lists.newArrayList();
//		for (int r=0; r<rupSet.getNumRuptures(); r++) {
//			List<Integer> rupSects = rupSet.getSectionsIndicesForRup(r);
//			for (int index : rupSects) {
//				if (!origFaultSectionData.get)
//			}
//		}
//	}
	
	@AfterClass
	public static void cleanUpAfterClass() {
		if (ivfss_1_file.exists()) {
			System.out.println("Deleting temp file");
			ivfss_1_file.delete();
		}
	}
	
	private static void populateMFDS(FaultSystemSolution sol) {
		Random rand = new Random();
		FaultSystemRupSet rupSet = sol.getRupSet();
		DiscretizedFunc[] rupMFDs = new DiscretizedFunc[rupSet.getNumRuptures()];
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			double mag = rupSet.getMagForRup(r);
			double rate = sol.getRateForRup(r);
			int numPts = rand.nextInt(10);
			double[] relativeWts = new double[numPts];
			for (int i=0; i<numPts; i++)
				relativeWts[i] = rand.nextDouble();
			double sumWts = StatUtils.sum(relativeWts);
			double[] mags = new double[numPts];
			for (int i=0; i<numPts; i++) {
				mags[i] = mag + (rand.nextDouble()-0.5)*0.1;
				relativeWts[i] = rate * relativeWts[i]/sumWts;
			}
			rupMFDs[r] = new LightFixedXFunc(mags, relativeWts);
		}
		sol.setRupMagDists(rupMFDs);
	}

	@Test
	public void testSetSolution() {
		// first set directly, then file, then directly
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF();
		erf.setSolution(ivfss_2);
		validateSol(erf, ivfss_2, true);
		erf.setParameter(FaultSystemSolutionERF.FILE_PARAM_NAME, ivfss_1_file);
		erf.updateForecast();
		validateSol(erf, ivfss_1, false);
		erf.setSolution(ivfss_1);
		validateSol(erf, ivfss_1, true);
		
		// now check file, directly, file
		erf = new FaultSystemSolutionERF();
		erf.setParameter(FaultSystemSolutionERF.FILE_PARAM_NAME, ivfss_1_file);
		erf.updateForecast();
		validateSol(erf, ivfss_1, false);
		erf.setSolution(ivfss_2);
		validateSol(erf, ivfss_2, true);
		erf.setParameter(FaultSystemSolutionERF.FILE_PARAM_NAME, ivfss_1_file);
		erf.updateForecast();
		validateSol(erf, ivfss_1, false);
	}
	
	private static void validateSol(FaultSystemSolutionERF erf, FaultSystemSolution sol,
			boolean checkSameInstance) {
		FaultSystemSolution erfSol = erf.getSolution();
		if (checkSameInstance)
			assertTrue("erf.getSolution() failed instance test", sol == erfSol);
		else
			assertFalse("erf.getSolution() shouldn't be same instance!", sol == erfSol);
		FaultSystemRupSet erfRupSet = erfSol.getRupSet();
		FaultSystemRupSet solRupSet = sol.getRupSet();
		assertEquals(erfRupSet.getNumRuptures(), solRupSet.getNumRuptures());
		// check certain rups
		Random rand = new Random();
		for (int i=0; i<10; i++) {
			int r = rand.nextInt(sol.getRupSet().getNumRuptures());
			assertEquals(sol.getRateForRup(r), erfSol.getRateForRup(r), 1e-14);
		}
	}
	
	private static int getInvIndex(ProbEqkSource source) {
		String srcName = source.getName();
		return Integer.parseInt(srcName.substring(srcName.indexOf("#")+1, srcName.indexOf(";")));
	}
	
	@Test
	public void testInitialParamSettings() {
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF();
		erf.setSolution(ivfss_1);
		erf.updateForecast();
		checkAllParams(getCurrentParamsMap(erf), erf);
	}
	
	@Test
	public void testBaseERFParamSetting() {
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF();
		erf.setSolution(ivfss_1);
		int numTests = 100;
		int maxSetsPerTest = 10;
		testParamSetting(erf, numTests, maxSetsPerTest);
	}
	
//	@Test
//	public void testUCERF3_ERFParamSetting() {
//		FaultSystemSolutionERF erf = new UCERF3_FaultSysSol_ERF();
//		erf.setSolution(ivfss_1);
//		int numTests = 50;
//		int maxSetsPerTest = 10;
//		testParamSetting(erf, numTests, maxSetsPerTest);
//	}
	
	private void testParamSetting(FaultSystemSolutionERF erf, int numTests, int maxSetsPerTest) {
		List<String> paramNames = Lists.newArrayList(paramsOptionsMap.keySet());
		paramNames.add(FaultSystemSolutionERF.FILE_PARAM_NAME);
		String directSol = "Direct Solution";
		paramNames.add(directSol);
		
		Random rand = new Random();
		
		Map<String, Object> paramsMap = getCurrentParamsMap(erf);
		
		for (int t=0; t<numTests; t++) {
			// set parameters
			int paramsToSet = rand.nextInt(maxSetsPerTest+1);
			System.out.println("Test "+t+". Setting "+paramsToSet+" params:");
			for (int i=0; i<paramsToSet; i++) {
				String paramName = paramNames.get(rand.nextInt(paramNames.size()));
				if (paramName.equals(FaultSystemSolutionERF.FILE_PARAM_NAME)) {
					System.out.println("\t"+i+". Setting file param");
					erf.setParameter(FaultSystemSolutionERF.FILE_PARAM_NAME, ivfss_1_file);
				} else if (paramName.equals(directSol)) {
					System.out.println("\t"+i+". Setting solution directly.");
					if (rand.nextBoolean())
						erf.setSolution(ivfss_1);
					else
						erf.setSolution(ivfss_2);
				} else {
					// regular parameter
					List<?> options = paramsOptionsMap.get(paramName);
					Object option = options.get(rand.nextInt(options.size()));
					// make sure it's not currently disabled
					if (!erf.getAdjustableParameterList().containsParameter(paramName)) {
						// make sure setting causes an exception
						try {
							erf.setParameter(paramName, null);
							fail("Should have thrown exception on set for nonexistant param");
						} catch (RuntimeException e) {
							// expected, do nothing
						}
						// pick a new parameter as this one is disabled
						i--;
						continue;
					}
					System.out.println("\t"+i+". Setting '"+paramName+"' to: "+option);
					erf.setParameter(paramName, option);
					paramsMap.put(paramName, option);
				}
			}
			if (erf.getParameter(ProbabilityModelParam.NAME).getValue() == ProbabilityModelOptions.WG02_BPT) {
				// make sure isn't mag dependent
				// white listed
				List<MagDependentAperiodicityOptions> allowed = Lists.newArrayList(
						MagDependentAperiodicityOptions.ALL_PT1_VALUES, MagDependentAperiodicityOptions.ALL_PT2_VALUES,
						MagDependentAperiodicityOptions.ALL_PT3_VALUES, MagDependentAperiodicityOptions.ALL_PT4_VALUES,
						MagDependentAperiodicityOptions.ALL_PT5_VALUES, MagDependentAperiodicityOptions.ALL_PT6_VALUES);
				if (!allowed.contains(erf.getParameter(MagDependentAperiodicityParam.NAME).getValue())) {
					MagDependentAperiodicityOptions newVal = allowed.get(rand.nextInt(allowed.size()));
					erf.setParameter(MagDependentAperiodicityParam.NAME, newVal);
					paramsMap.put(MagDependentAperiodicityParam.NAME, newVal);
				}
			}
			// test parameter settting
			erf.updateForecast();
			try {
				checkAllParams(paramsMap, erf);
			} catch (AssertionError e) {
				System.out.println("********* ASSERTION FAILED: "+e.getMessage());
				System.out.flush();
				throw e;
			}
		}
	}
	
	private static Map<String, Object> getCurrentParamsMap(FaultSystemSolutionERF erf) {
		Map<String, Object> paramsMap = Maps.newHashMap();
		
		paramsMap.put(FaultGridSpacingParam.NAME, erf.getParameter(FaultGridSpacingParam.NAME).getValue());
		paramsMap.put(AleatoryMagAreaStdDevParam.NAME, erf.getParameter(AleatoryMagAreaStdDevParam.NAME).getValue());
		paramsMap.put(ApplyGardnerKnopoffAftershockFilterParam.NAME,
				erf.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).getValue());
		paramsMap.put(IncludeBackgroundParam.NAME, erf.getParameter(IncludeBackgroundParam.NAME).getValue());
		if (erf.getParameter(IncludeBackgroundParam.NAME).getValue() != IncludeBackgroundOption.EXCLUDE)
			paramsMap.put(BackgroundRupParam.NAME, erf.getParameter(BackgroundRupParam.NAME).getValue());
		
		return paramsMap;
	}
	
	private static void checkAllParams(Map<String, Object> paramsMap, FaultSystemSolutionERF erf) {
		for (String paramName : paramsMap.keySet())
			checkParemterApplied(erf, paramName, paramsMap.get(paramName));
	}
	
	/**
	 * This method will ensure that the given parameter was actually applied. Assumes forecast has been updated
	 * @param erf
	 * @param paramName
	 * @param paramVal
	 */
	private static void checkParemterApplied(FaultSystemSolutionERF erf, String paramName, Object paramVal) {
		// already assumes that forecast has been updated
		
		// get num fault sources
		int numFaultSources = 0;
		for (int i=0; i<erf.getNumSources(); i++) {
			ProbEqkSource source = erf.getSource(i);
			assertNotNull("Source "+i+" is null!", source);
			if (erf.getSource(i) instanceof FaultRuptureSource)
				numFaultSources++;
			else
				break;
		}
		if (erf.getAdjustableParameterList().getParameter(
				IncludeBackgroundOption.class, IncludeBackgroundParam.NAME).getValue() != IncludeBackgroundOption.ONLY)
			// only check if we actually have fault system sources
			assertEquals("Num fault system sources inconsistent!", erf.getNumFaultSystemSources(), numFaultSources);
		FaultSystemSolution sol = erf.getSolution();
		FaultSystemRupSet rupSet = sol.getRupSet();
		double duration = erf.getTimeSpan().getDuration();
		
		String setMessage = "Setting '"+paramName+"' to '"+paramVal+"' failed.";
		String applyMessage = "Applying '"+paramName+"' to '"+paramVal+"' failed.";
		
		Random rand = new Random();
		
		boolean containsParam = erf.getAdjustableParameterList().containsParameter(paramName);
		
		// need to know this for some tests as we can't calculate our own probabilities
		ProbabilityModelOptions probModel = erf.getAdjustableParameterList().getParameter(
				ProbabilityModelOptions.class, ProbabilityModelParam.NAME).getValue();
		boolean timeDep = probModel != ProbabilityModelOptions.POISSON;
		
		if (paramName.equals(FaultGridSpacingParam.NAME)) {
			double spacing = (Double)paramVal;
			assertTrue("Nno grid spacing param", containsParam);
			// first check directly
			assertEquals(setMessage,
					spacing, (Double)erf.getParameter(paramName).getValue(), 1e-14);
			for (int i=0; i<10 && numFaultSources > 0; i++) {
				int srcID = rand.nextInt(numFaultSources);
				ProbEqkSource source = erf.getSource(srcID);
				double myGridSpacing = source.getRupture(0).getRuptureSurface().getAveGridSpacing();
				// spacing is a "max"
				assertTrue(applyMessage, myGridSpacing <= spacing);
				assertTrue(applyMessage, myGridSpacing > 0.5*spacing);
//				assertEquals(paramName+" not applied correctly", spacing, myGridSpacing, 0.2*spacing);
			}
		} else if (paramName.equals(AleatoryMagAreaStdDevParam.NAME)) {
			double var = (Double)paramVal;
			// first check directly
			assertEquals(setMessage,
					var, (Double)erf.getParameter(paramName).getValue(), 1e-14);
			DiscretizedFunc[] rupMFDs = erf.getSolution().getRupMagDists();
			for (int i=0; i<10 && numFaultSources > 0; i++) {
				int srcID = rand.nextInt(numFaultSources);
				FaultRuptureSource source = (FaultRuptureSource)erf.getSource(srcID);
				int invRup = getInvIndex(source);
				if (var == 0) {
					if (rupMFDs == null)
						assertEquals(applyMessage, source.getNumRuptures(), 1);
					else
						assertEquals(applyMessage, source.getNumRuptures(), rupMFDs[invRup].size());
				} else {
					// make sure greater than 1 and evenly spaced
					if (source.computeTotalProb() < 1e-15 && source.getNumRuptures() == 1)
						// check for the disable case for tiny probs
						continue;
					assertTrue(applyMessage, source.getNumRuptures() > 1);
					double spacing = Double.NaN;
					for (int rupIndex=1; rupIndex<source.getNumRuptures(); rupIndex++) {
						double mySpacing = source.getRupture(rupIndex).getMag()
								- source.getRupture(rupIndex-1).getMag();
						if (Double.isNaN(spacing))
							spacing = mySpacing;
						else
							assertEquals(applyMessage, spacing, mySpacing, 1e-8);
					}
				}
			}
		} else if (paramName.equals(ApplyGardnerKnopoffAftershockFilterParam.NAME)) {
			boolean apply = (Boolean)paramVal;
			// first check directly
			assertEquals(setMessage,
					apply, (Boolean)erf.getParameter(paramName).getValue());
			double aftRateCorr = 1;
			if(apply) aftRateCorr = FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS;
			boolean variability = (Double)erf.getParameter(AleatoryMagAreaStdDevParam.NAME).getValue() > 0;
			// can only validate when it's time independent
			for (int i=0; i<10 && numFaultSources > 0 && !timeDep; i++) {
				int srcID = rand.nextInt(numFaultSources);
				FaultRuptureSource source = (FaultRuptureSource)erf.getSource(srcID);
				int invRup = getInvIndex(source);
				double rate = sol.getRateForRup(invRup);
				if (variability) {
					double mag = rupSet.getMagForRup(invRup);
					double totMoRate = aftRateCorr*rate*MagUtils.magToMoment(mag);
					double actualTotMoRate = 0;
					for (ProbEqkRupture rup : source)
						actualTotMoRate += rup.getMeanAnnualRate(duration)*MagUtils.magToMoment(rup.getMag());
					assertEquals(applyMessage, totMoRate, actualTotMoRate, 1e-5*totMoRate);
				} else {
					double prob = 1-Math.exp(-aftRateCorr*rate*duration);
					assertEquals(applyMessage, prob, source.getRupture(0).getProbability(), 1e-5*prob);
				}
			}
		} else if (paramName.equals(IncludeBackgroundParam.NAME)) {
			IncludeBackgroundOption incl = (IncludeBackgroundOption)paramVal;
			// first check directly
			assertEquals(setMessage,
					incl, (IncludeBackgroundOption)erf.getParameter(paramName).getValue());
			int numSources = erf.getNumSources();
			switch (incl) {
			case EXCLUDE:
				assertEquals(applyMessage, numFaultSources, numSources);
				break;
			case INCLUDE:
				assertTrue(applyMessage, numSources > numFaultSources);
				for (int i=numFaultSources; i<numSources; i++) {
					ProbEqkSource source = erf.getSource(i);
					assertTrue(applyMessage, source instanceof PointSource13b
							|| source instanceof Point2Vert_FaultPoisSource);
				}
				break;
			case ONLY:
				assertEquals(applyMessage, 0, numFaultSources);
				assertTrue(applyMessage, numSources > 0);
				for (int i=0; i<numSources; i++) {
					ProbEqkSource source = erf.getSource(i);
					assertTrue(applyMessage, source instanceof PointSource13b
							|| source instanceof Point2Vert_FaultPoisSource);
				}
				break;

			default:
				break;
			}
		} else if (paramName.equals(BackgroundRupParam.NAME)) {
			IncludeBackgroundOption incl = (IncludeBackgroundOption) erf.getParameter(
					IncludeBackgroundParam.NAME).getValue();
			if (incl == IncludeBackgroundOption.EXCLUDE && !containsParam)
				// parameter was removed because it was excluded, this is fine
				return;
			BackgroundRupType type = (BackgroundRupType)paramVal;
			// first check directly
			assertEquals(setMessage+" (incl="+incl+")",
					type, (BackgroundRupType)erf.getParameter(paramName).getValue());
			int numSources = erf.getNumSources();
			if (incl == IncludeBackgroundOption.INCLUDE || incl == IncludeBackgroundOption.ONLY) {
				for (int i=numFaultSources; i<numSources; i++) {
					ProbEqkSource source = erf.getSource(i);
					switch (type) {
					case POINT:
						assertTrue(applyMessage+". instance: "+source.getClass().getName(),
								source instanceof PointSource13b);
						break;
					case FINITE:
						assertTrue(applyMessage+". instance: "+source.getClass().getName(),
								source instanceof Point2Vert_FaultPoisSource);
						assertFalse(applyMessage, ((Point2Vert_FaultPoisSource)source).isCrossHair());
						break;
					case CROSSHAIR:
						assertTrue(applyMessage+". instance: "+source.getClass().getName(),
								source instanceof Point2Vert_FaultPoisSource);
						assertTrue(applyMessage, ((Point2Vert_FaultPoisSource)source).isCrossHair());
						break;

					default:
						fail("unknown bg source type");
						break;
					}
				}
			}
		} else if (paramName.equals(ProbabilityModelParam.NAME)) {
			// first check directly
			assertEquals(setMessage,
					probModel, (ProbabilityModelOptions)erf.getParameter(paramName).getValue());
			// if ruptures have date of last event or historical open interval is non zero then probabilities should differ
			double aftRateCorr = 1;
			if ((Boolean)erf.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).getValue())
				aftRateCorr = FaultSystemSolutionERF.MO_RATE_REDUCTION_FOR_SUPRA_SEIS_RUPS;
			boolean variability = (Double)erf.getParameter(AleatoryMagAreaStdDevParam.NAME).getValue() > 0;
			// can only validate when it's time independent
			for (int i=0; i<10 && numFaultSources > 0 && !timeDep; i++) {
				int srcID = rand.nextInt(numFaultSources);
				FaultRuptureSource source = (FaultRuptureSource)erf.getSource(srcID);
				int invRup = getInvIndex(source);
				boolean shouldEqual;
				if (timeDep) {
					// if none of the sections have last event data, skip source
					boolean hasLast = false;
					for (FaultSection sectData : rupSet.getFaultSectionDataForRupture(invRup)) {
						if (sectData.getDateOfLastEvent() != Long.MIN_VALUE) {
							hasLast = true;
							break;
						}
					}
					if (!hasLast)
						continue;
					shouldEqual = false;
				} else {
					shouldEqual = true;
				}
				double rate = sol.getRateForRup(invRup);
				if (variability) {
					double mag = rupSet.getMagForRup(invRup);
					double totMoRate = aftRateCorr*rate*MagUtils.magToMoment(mag);
					double actualTotMoRate = 0;
					for (ProbEqkRupture rup : source)
						actualTotMoRate += rup.getMeanAnnualRate(duration)*MagUtils.magToMoment(rup.getMag());
					if (shouldEqual)
						assertEquals(applyMessage, totMoRate, actualTotMoRate, 1e-5*totMoRate);
					else
						assertFalse(applyMessage, totMoRate == actualTotMoRate);
				} else {
					double prob = 1-Math.exp(-aftRateCorr*rate*duration);
					if (shouldEqual)
						assertEquals(applyMessage, prob, source.getRupture(0).getProbability(), 1e-5*prob);
					else
						assertFalse(applyMessage, prob == source.getRupture(0).getProbability());
				}
			}
		} else if (paramName.equals(MagDependentAperiodicityParam.NAME)) {
			// can skip for pref blend
			boolean shouldntHaveParam = !timeDep || probModel == ProbabilityModelOptions.U3_PREF_BLEND;
			assertTrue("Non poisson and doesn't have MagDependentAperiodicityParam",
					shouldntHaveParam || containsParam);
			if (shouldntHaveParam && !containsParam)
				return;
			// first check directly
			MagDependentAperiodicityOptions val = (MagDependentAperiodicityOptions) paramVal;
			assertEquals(setMessage,
					val, erf.getParameter(paramName).getValue());
			// TODO check actually applied!!!
		} else if (paramName.equals(HistoricOpenIntervalParam.NAME)) {
			assertTrue("Non poisson and doesn't have HistoricOpenIntervalParam", !timeDep || containsParam);
			if (!timeDep && !containsParam)
				return;
			// first check directly
			Double val = (Double) paramVal;
			assertEquals(setMessage,
					val, (Double)erf.getParameter(paramName).getValue());
			// TODO check actually applied!!!
		}
	}
	
	@Test
	public void testInvIndex() {
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(ivfss_1);
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
		erf.updateForecast();
		
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			int invIndex = erf.getFltSysRupIndexForSource(sourceID);
			String sourceName = erf.getSource(sourceID).getName();
			assertTrue("Index "+invIndex+" not found in name: "+sourceName, sourceName.contains(invIndex+""));
		}
	}

}
