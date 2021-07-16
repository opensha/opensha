package org.opensha.sha.earthquake.faultSysSolution.modules;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.math3.stat.StatUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
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
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.io.Files;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.MFD_InversionConstraint;

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
		List<MFD_InversionConstraint> constrs = new ArrayList<>();
		for (Region reg : new Region[] {null, new CaliforniaRegions.LA_BOX(), new CaliforniaRegions.RELM_NOCAL_GRIDDED()})
			constrs.add(new MFD_InversionConstraint(fakeMFD(), reg));
		InversionTargetMFDs.Precomputed module = new InversionTargetMFDs.Precomputed(demoRupSet, fakeMFD(), fakeMFD(),
				fakeMFD(), fakeMFD(), constrs, fakeSubSeismoMFDs());
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
