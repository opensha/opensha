package org.opensha.sha.earthquake.faultSysSolution.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.math3.stat.StatUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetSaveLoadTests;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.Files;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

public class StandardFaultSysModulesTest {

	private static File tempDir;
	
	private static ZipFile demoRupSetZip;
	private static ZipFile demoSolZip;
	
	private static FaultSystemRupSet demoRupSet;
	private static FaultSystemSolution demoSol;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tempDir = Files.createTempDir();
		
		demoRupSetZip = new ZipFile(new File(RupSetSaveLoadTests.FSS_TEST_RESOURCES_DIR, "demo_rup_set.zip"));
		demoSolZip = new ZipFile(new File(RupSetSaveLoadTests.FSS_TEST_RESOURCES_DIR, "demo_sol.zip"));
		
		demoRupSet = FaultSystemRupSet.load(demoRupSetZip);
		demoSol = FaultSystemSolution.load(demoSolZip);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		FileUtils.deleteRecursive(tempDir);
	}
	
	/*
	 * Rup set modules
	 */

	@Test
	public void testAveSlip() throws IOException {
		AveSlipModule module = AveSlipModule.forModel(demoRupSet, ScalingRelationships.SHAW_2009_MOD);
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, AveSlipModule.class);
	}

	@Test
	public void testClusterRuptures() throws IOException {
		ClusterRuptures origRups = demoRupSet.getModule(ClusterRuptures.class);
		ClusterRuptures serMod = ClusterRuptures.instance(demoRupSet, origRups.getAll(), true);
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, serMod, ClusterRuptures.class);
	}

	@Test
	public void testInfo() throws IOException {
		InfoModule testInfo = new InfoModule("This is my test string\na new line\nanother line\n");
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, testInfo, InfoModule.class);
	}

	@Test
	public void testBuildInfo() throws IOException {
		BuildInfoModule testInfo = BuildInfoModule.detect();
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, testInfo, BuildInfoModule.class);
	}

	@Test
	public void testModMinMags() throws IOException {
		ModSectMinMags module = ModSectMinMags.instance(demoRupSet, randArray(demoRupSet.getNumSections()));
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, ModSectMinMags.class);
	}

	@Test
	public void testSectAreas() throws IOException {
		SectAreas module = SectAreas.precomputed(demoRupSet, randArray(demoRupSet.getNumSections()));
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, SectAreas.class);
	}

	@Test
	public void testSectSlipRates() throws IOException {
		SectSlipRates module = SectSlipRates.precomputed(demoRupSet, randArray(demoRupSet.getNumSections()),
				randArray(demoRupSet.getNumSections()));
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, SectSlipRates.class);
	}

	@Test
	public void testLogicTreeBranch() throws IOException {
		U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT;
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, branch, U3LogicTreeBranch.class);
	}

	@Test
	public void testPolygonAssociations() throws IOException {
		PolygonFaultGridAssociations module = FaultPolyMgr.create(demoRupSet.getFaultSectionDataList(), 10d);
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, PolygonFaultGridAssociations.class);
	}

	@Test
	public void testInvTargetMFDs() throws IOException {
		List<IncrementalMagFreqDist> constrs = new ArrayList<>();
		for (Region reg : new Region[] {null, new CaliforniaRegions.LA_BOX(), new CaliforniaRegions.RELM_NOCAL_GRIDDED()}) {
			IncrementalMagFreqDist mfd = fakeMFD();
			mfd.setRegion(reg);
			constrs.add(mfd);
		}
		InversionTargetMFDs.Precomputed module = new InversionTargetMFDs.Precomputed(demoRupSet, fakeMFD(), fakeMFD(),
				fakeMFD(), fakeMFD(), constrs, fakeSubSeismoMFDs(), null);
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, InversionTargetMFDs.class);
		// now add supra-seis (just use sub seis as supra, doesn't matter since it's fake anyway)
		module = new InversionTargetMFDs.Precomputed(demoRupSet, fakeMFD(), fakeMFD(),
				fakeMFD(), fakeMFD(), constrs, fakeSubSeismoMFDs(), fakeSubSeismoMFDs().getAll());
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, InversionTargetMFDs.class);
	}
	
	private static IncrementalMagFreqDist fakeMFD() {
		return new GutenbergRichterMagFreqDist(1d, Math.random(), 5.05, 7.05, 21);
	}
	
	private static SubSeismoOnFaultMFDs fakeSubSeismoMFDs() {
		List<IncrementalMagFreqDist> subSeismoMFDs = new ArrayList<>();
		for (int s=0; s<demoRupSet.getNumSections(); s++)
			subSeismoMFDs.add(fakeMFD());
		return new SubSeismoOnFaultMFDs(subSeismoMFDs);
	}

	/*
	 * Solution modules
	 */

	@Test
	public void testRupMFDs() throws IOException {
		DiscretizedFunc[] rupMFDs = new DiscretizedFunc[demoRupSet.getNumRuptures()];
		for (int r=0; r<demoRupSet.getNumRuptures(); r++) {
			if (Math.random() > 0.2) {
				rupMFDs[r] = new ArbitrarilyDiscretizedFunc();
				double rateLeft = demoSol.getRateForRup(r);
				double origMag = demoRupSet.getMagForRup(r);
				while ((float)rateLeft > 0f) {
					double rate = Math.random() > 0.25 ? rateLeft : 0.5*rateLeft;
					rateLeft -= rate;
					rupMFDs[r].set(origMag+Math.random()-0.5, rate);
				}
			}
		}
		RupMFDsModule mfds = new RupMFDsModule(demoSol, rupMFDs);
		testModuleSerialization(demoSol.getArchive(), demoSol, mfds, RupMFDsModule.class);
	}
	
	@Test
	public void testSubSeismoMFDs() throws IOException {
		SubSeismoOnFaultMFDs mfds = fakeSubSeismoMFDs();
		testModuleSerialization(demoSol.getArchive(), demoSol, mfds, SubSeismoOnFaultMFDs.class);
	}

	@Test
	public void testGriddedSeis() throws IOException {
		GridSourceProvider gridSources = demoSol.getGridSourceProvider();
		testModuleSerialization(demoSol.getArchive(), demoSol, gridSources, GridSourceProvider.class);
	}

	@Test
	public void testIndividualSolRates() throws IOException {
		double[] origRates = demoSol.getRateForAllRups();
		int numSols = 10;
		int numRups = demoSol.getRupSet().getNumRuptures();
		List<double[]> indvRates = new ArrayList<>();
		for (int s=0; s<numSols; s++)
			indvRates.add(new double[numRups]);
		for (int r=0; r<numRups; r++) {
			double[] weights = new double[numSols];
			for (int s=0; s<numSols; s++)
				weights[s] = Math.random();
			double totWeight = StatUtils.sum(weights);
			double scale = (double)numSols/totWeight;
			for (int s=0; s<numSols; s++)
				indvRates.get(s)[r] = origRates[r]*weights[s]*scale;
		}
		IndividualSolutionRates module = new IndividualSolutionRates(demoSol, indvRates);
		testModuleSerialization(demoSol.getArchive(), demoSol, module, IndividualSolutionRates.class);
	}

	@Test
	public void testInversionMisfits() throws IOException {
		InversionMisfits misfits = fakeMisfits();
		testModuleSerialization(demoSol.getArchive(), demoSol, misfits, InversionMisfits.class);
	}

	@Test
	public void testInversionMisfitStatss() throws IOException {
		InversionMisfitStats misfits = fakeMisfits().getMisfitStats();
		testModuleSerialization(demoSol.getArchive(), demoSol, misfits, InversionMisfitStats.class);
	}
	
	private static InversionMisfits fakeMisfits() {
		int rows = 1000;
		List<ConstraintRange> ranges = new ArrayList<>();
		double[] d=null, d_ineq=null, misfit=null, misfit_ineq=null;
		for (boolean ineq : new boolean[] {false, true}) {
			int startRow = 0;
			double[] myD, myMisfit;
			if (ineq) {
				d_ineq = new double[rows];
				myD = d_ineq;
				misfit_ineq = new double[rows];
				myMisfit = misfit_ineq;
			} else {
				d = new double[rows];
				myD = d;
				misfit = new double[rows];
				myMisfit = misfit;
			}
			Random r = new Random();
			while (startRow < rows) {
				int endRow = Integer.min(rows, startRow + 10 + r.nextInt(100));
				
				int num = ranges.size()+1;
				ranges.add(new ConstraintRange("Constraint "+num, "Constr"+num, startRow, endRow, ineq, 1d, ConstraintWeightingType.NORMALIZED));
				startRow = endRow;
			}
			for (int i=0; i<rows; i++) {
				myD[i] = r.nextDouble();
				myMisfit[i] = r.nextGaussian();
			}
		}
		return new InversionMisfits(ranges, misfit, d, misfit_ineq, d_ineq);
	}
	
	@Test
	public void testNamedFaults() throws IOException {
		Map<String, List<Integer>> namedFaults = new HashMap<>();
		HashSet<Integer> parentIDs = new HashSet<>();
		for(FaultSection sect : demoRupSet.getFaultSectionDataList())
			parentIDs.add(sect.getParentSectionId());
		namedFaults.put("Master Fault", new ArrayList<>(parentIDs));
		NamedFaults module = new NamedFaults(demoRupSet, namedFaults);
		
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, NamedFaults.class);
	}
	
	@Test
	public void testPaleoData() throws IOException {
		List<SectMappedUncertainDataConstraint> paleoData = new ArrayList<>();
		Location fakeLoc = new Location(34d, -118);
		paleoData.add(new SectMappedUncertainDataConstraint("name1", 0, "Sect Name", fakeLoc, 1e-2,
				BoundedUncertainty.fromMeanAndBounds(UncertaintyBoundType.TWO_SIGMA, 1e-2, 0.5e-2, 2e-2)));
		List<SectMappedUncertainDataConstraint> slipData = new ArrayList<>();
		slipData.add(new SectMappedUncertainDataConstraint("name1", 0, "Sect Name", fakeLoc, 5d,
				BoundedUncertainty.fromMeanAndBounds(UncertaintyBoundType.TWO_SIGMA, 5d, 4d, 6d)));
		PaleoseismicConstraintData module = new PaleoseismicConstraintData(demoRupSet,
				paleoData, UCERF3_PaleoProbabilityModel.load(), slipData, U3AveSlipConstraint.slip_prob_model);
		
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, PaleoseismicConstraintData.class);
	}
	
	@Test
	public void testRegionsOfInterest() throws IOException {
		RegionsOfInterest module = new RegionsOfInterest(new CaliforniaRegions.LA_BOX(), new CaliforniaRegions.SF_BOX());
		
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, RegionsOfInterest.class);
		
		List<Region> regions = module.getRegions();
		List<IncrementalMagFreqDist> mfds = new ArrayList<>();
		for (int i=0; i<regions.size(); i++)
			mfds.add(fakeMFD());
		module = new RegionsOfInterest(regions, mfds);
		
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, RegionsOfInterest.class);
	}
	
	@Test
	public void testSolutionSlipRates() throws IOException {
		SolutionSlipRates module = SolutionSlipRates.calc(demoSol,
				AveSlipModule.forModel(demoSol.getRupSet(), ScalingRelationships.ELLSWORTH_B),
				new SlipAlongRuptureModel.Default());
		
		testModuleSerialization(demoSol.getArchive(), demoSol, module, SolutionSlipRates.class);
	}
	
	@Test
	public void testConnectivityClusters() throws IOException {
		ConnectivityClusters module = ConnectivityClusters.build(demoRupSet);
		
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, ConnectivityClusters.class);
	}
	
	@Test
	public void testRupSubSet() throws IOException {
		BiMap<Integer, Integer> sectIDs_newToOld = HashBiMap.create();
		int newSectID = 0;
		for (int origSectID=0; origSectID<500; origSectID++)
			if (Math.random() < 0.1)
				sectIDs_newToOld.put(origSectID, newSectID++);
		BiMap<Integer, Integer> rupIDs_newToOld = HashBiMap.create();
		int newRupID = 0;
		for (int origRupID=0; origRupID<1000; origRupID++)
			if (Math.random() < 0.05)
				rupIDs_newToOld.put(origRupID, newRupID++);
		RuptureSubSetMappings module = new RuptureSubSetMappings(sectIDs_newToOld, rupIDs_newToOld, null);
		
		testModuleSerialization(demoRupSet.getArchive(), demoRupSet, module, RuptureSubSetMappings.class);
	}
	
	private static double[] randArray(int len) {
		double[] ret = new double[len];
		for (int i=0; i<len; i++)
			ret[i] = Math.random();
		return ret;
	}
	
	private static <E extends OpenSHA_Module> void testModuleSerialization(ModuleArchive<? super E> archive,
			ModuleContainer<? super E> container, E module, Class<E> clazz) throws IOException {
		System.out.println("Testing "+module.getName());
		assertTrue(module.getName()+" isn't archivable!", module instanceof ArchivableModule);
		String cname = ClassUtils.getClassNameWithoutPackage(module.getClass()).replaceAll("\\W+", "_");
		File withoutFile = new File(tempDir, "without_"+cname+".zip");
		container.removeModuleInstances(clazz);
		assertFalse("Still has module? "+clazz.getName(), container.hasModule(clazz));
		archive.write(withoutFile, false);
		File withFile = new File(tempDir, "with_"+cname+".zip");
		container.addModule(module);
		archive.write(withFile, false);
		ModuleArchive<OpenSHA_Module> loaded;
		if (container instanceof OpenSHA_Module)
			loaded = new ModuleArchive<>(withFile, (Class<E>)container.getClass());
		else
			loaded = new ModuleArchive<>(withFile);
		
		File rewrittenFile = new File(tempDir, "rewritten_"+cname+".zip");
		loaded.write(rewrittenFile, false);
		
		// find the files that are unique to this module
		ZipFile withoutZip = new ZipFile(withoutFile);
		HashSet<String> origEntries = new HashSet<>(getEntryNames(withoutZip));
		ZipFile withZip = new ZipFile(withFile);
		List<ZipEntry> uniqueEntries = new ArrayList<>();
		for (String name : getEntryNames(withZip)) {
			if (name.endsWith("modules.json") || name.endsWith("/"))
				continue;
			if (!origEntries.contains(name))
				uniqueEntries.add(withZip.getEntry(name));
		}
		
		ZipFile rewrittenZip = new ZipFile(rewrittenFile);
		getEntryNames(rewrittenZip);
		int numTests = 0;
		for (ZipEntry origEntry : uniqueEntries) {
			String name = origEntry.getName();
			System.out.println("*** testing: "+name);
			ZipEntry rewrittenEntry = rewrittenZip.getEntry(name);
			assertNotNull(name+" is missing from rewritten zip file", rewrittenEntry);
			
			List<String> origLines = getISLines(withZip.getInputStream(origEntry));
			List<String> newLines = getISLines(rewrittenZip.getInputStream(rewrittenEntry));
			
//			System.out.println("********************");
//			System.out.println(Joiner.on("\n").join(origLines));
//			System.out.println("********************");
//			System.out.println(Joiner.on("\n").join(newLines));
//			System.out.println("********************");
			
			int i1 = 0;
			int i2 = 0;
			
			while (i1 < origLines.size() || i2 < newLines.size()) {
				String line1 = null;
				while (i1 < origLines.size()) {
					line1 = origLines.get(i1++).trim();
					if (line1.isBlank())
						line1 = null;
					else
						break;
				}
				String line2 = null;
				while (i2 < newLines.size()) {
					line2 = newLines.get(i2++).trim();
					if (line2.isBlank())
						line2 = null;
					else
						break;
				}
				if (line1 == null) {
					assertNull("Rewritten "+name+" has an extra line: "+line2, line2);
				} else if (line2 == null) {
					assertNull("Original "+name+" has an extra line: "+line1, line1);
				} else {
					// compare them
//					System.out.println(i1+":\t"+line1);
//					System.out.println(i2+":\t"+line2);
					assertEquals("Mismatch in "+name+" at line "+i1+"/"+i2+"\n"+line1+"\n"+line2+"\n", line1, line2);
				}
			}
			numTests++;
		}
		
		withoutZip.close();
		withZip.close();
		rewrittenZip.close();
		
		assertTrue("No unique files found for "+module.getName(), numTests > 0);
	}
	
	private static List<String> getEntryNames(ZipFile file) {
		Enumeration<? extends ZipEntry> entries = file.entries();
		
		List<String> names = new ArrayList<>();
		System.out.println(file.getName());
		while (entries.hasMoreElements()) {
			String name = entries.nextElement().getName();
			System.out.println("\t"+name);
			names.add(name);
		}
		return names;
	}
	
	private static List<String> getISLines(InputStream is) throws IOException {
		BufferedReader read = new BufferedReader(new InputStreamReader(is));
		
		String line = null;
		List<String> lines = new ArrayList<>();
		while ((line = read.readLine()) != null)
			lines.add(line);
		read.close();
		return lines;
	}

}
