package org.opensha.sha.earthquake.faultSysSolution;

import static org.junit.Assert.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectAreas;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;

import com.google.common.io.Files;

import scratch.UCERF3.U3FaultSystemRupSet;
import scratch.UCERF3.U3FaultSystemSolution;

public class RupSetSaveLoadTests {

	private static File tempDir;

	private static ZipFile demoRupSetZip;
	private static ZipFile demoSolZip;
	private static ZipFile demoOldRupSetZip;
	private static ZipFile demoOldSolZip;
	
	private static FaultSystemRupSet demoRupSet;
	private static FaultSystemSolution demoSol;
	
	public static final File FSS_TEST_RESOURCES_DIR =
			new File("src/test/resources/org/opensha/sha/earthquake/faultSysSolution");
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tempDir = Files.createTempDir();
		
		demoRupSetZip = new ZipFile(new File(FSS_TEST_RESOURCES_DIR, "demo_rup_set.zip"));
		demoSolZip = new ZipFile(new File(FSS_TEST_RESOURCES_DIR, "demo_sol.zip"));
		demoOldRupSetZip = new ZipFile(new File(FSS_TEST_RESOURCES_DIR, "demo_old_rup_set.zip"));
		demoOldSolZip = new ZipFile(new File(FSS_TEST_RESOURCES_DIR, "demo_old_sol.zip"));
		
		try {
			demoRupSet = FaultSystemRupSet.load(demoRupSetZip);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			demoSol = FaultSystemSolution.load(demoSolZip);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		FileUtils.deleteRecursive(tempDir);

		demoRupSetZip.close();
		demoSolZip.close();
		demoOldRupSetZip.close();
		demoOldSolZip.close();
	}

	@Test
	public void testLoadDemoRupSet() {
		// simply test that we can load this file
		FaultSystemRupSet rupSet = null;
		try {
			rupSet = FaultSystemRupSet.load(demoRupSetZip);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load demo rupture set: "+e.getMessage());
		}
		assertNotNull(rupSet);
	}

	@Test
	public void testLoadDemoSol() {
		// simply test that we can load this file
		FaultSystemSolution sol = null;
		try {
			sol = FaultSystemSolution.load(demoSolZip);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load demo solution set: "+e.getMessage());
		}
		assertNotNull(sol);
	}

	@Test
	public void testLoadDemoSolAsRupSet() {
		// test that we can load a solution as a rupture set
		FaultSystemRupSet rupSet = null;
		try {
			rupSet = FaultSystemRupSet.load(demoSolZip);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load demo rupture set: "+e.getMessage());
		}
		assertNotNull(rupSet);
	}

	@Test
	public void testLoadOldRupSetAsNew() {
		for (boolean directZip : new boolean[] {false, true}) {
			FaultSystemRupSet rupSet = null;
			try {
				if (directZip)
					rupSet = FaultSystemRupSet.load(demoOldRupSetZip);
				else
					rupSet = FaultSystemRupSet.load(new File(demoOldRupSetZip.getName()));
			} catch (Exception e) {
				fail("Failed to load old rupture set with new load method: "+e.getMessage());
			}
			assertNotNull(rupSet);
			assertTrue("Old rupture set should have been loaded in as a U3 rup set", rupSet instanceof U3FaultSystemRupSet);
			assertTrue("Old isn't compatible with new", demoRupSet.isEquivalentTo(rupSet));
		}
	}

	@Test
	public void testLoadSolSetAsNew() {
		for (boolean directZip : new boolean[] {false, true}) {
			FaultSystemSolution sol = null;
			try {
				if (directZip)
					sol = FaultSystemSolution.load(demoOldSolZip);
				else
					sol = FaultSystemSolution.load(new File(demoOldSolZip.getName()));
			} catch (Exception e) {
				fail("Failed to load old solution with new load method: "+e.getMessage());
			}
			assertNotNull(sol);
			assertTrue("Old solution set should have been loaded in as a U3 sol", sol instanceof U3FaultSystemSolution);
			assertTrue("Old isn't compatible with new", demoRupSet.isEquivalentTo(sol.getRupSet()));
			double[] oldRates = sol.getRateForAllRups();
			double[] newRates = demoSol.getRateForAllRups();
			for (int r=0; r<oldRates.length; r++)
				assertEquals("Old vs new rate mismatch for rupture "+r, oldRates[r], newRates[r], 1e-16);
		}
	}

	@Test
	public void testLoadRupSetWithoutRupSetModules() throws IOException {
		File tempFile = new File(tempDir, "rup_set_without_rs_modules.zip");
		writeZipWithout(demoRupSetZip, tempFile, "ruptures/modules.json");
		
		FaultSystemRupSet rupSet = null;
		try {
			rupSet = FaultSystemRupSet.load(tempFile);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load demo rupture set after we deleted ruptures/modules.json: "+e.getMessage());
		}
		assertNotNull(rupSet);
		assertTrue("loaded w/o modules doesn't match original", demoRupSet.isEquivalentTo(rupSet));
	}

	@Test
	public void testLoadSolWithoutRupSetSolModules() throws IOException {
		File tempFile = new File(tempDir, "sol_without_sol_modules.zip");
		writeZipWithout(demoSolZip, tempFile, "ruptures/modules.json", "solution/modules.json");
		
		FaultSystemSolution sol = null;
		try {
			sol = FaultSystemSolution.load(tempFile);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load demo sol after we deleted */modules.json: "+e.getMessage());
		}
		assertNotNull(sol);
		assertTrue("loaded w/o modules doesn't match original", demoRupSet.isEquivalentTo(sol.getRupSet()));
	}

	@Test
	public void testLoadRupSetWithoutAnyModules() throws IOException {
		File tempFile = new File(tempDir, "rup_set_without_modules.zip");
		writeZipWithout(demoRupSetZip, tempFile, "modules.json", "ruptures/modules.json");
		
		FaultSystemRupSet rupSet = null;
		try {
			rupSet = FaultSystemRupSet.load(tempFile);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load demo rupture set after we deleted all modules.json: "+e.getMessage());
		}
		assertNotNull(rupSet);
		assertTrue("loaded w/o modules doesn't match original", demoRupSet.isEquivalentTo(rupSet));
	}

	@Test
	public void testLoadSolWithoutAnyModules() throws IOException {
		File tempFile = new File(tempDir, "sol_without_modules.zip");
		writeZipWithout(demoSolZip, tempFile, "modules.json", "ruptures/modules.json", "solution/modules.json");
		
		FaultSystemSolution sol = null;
		try {
			sol = FaultSystemSolution.load(tempFile);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load demo sol after we deleted all modules.json: "+e.getMessage());
		}
		assertNotNull(sol);
		assertTrue("loaded w/o modules doesn't match original", demoRupSet.isEquivalentTo(sol.getRupSet()));
	}
	
	private static void writeZipWithout(ZipFile zip, File destFile, String... entriesToRemove) throws IOException {
		Enumeration<? extends ZipEntry> entries = zip.entries();
		
		ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destFile)));
		
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			
			String name = entry.getName().trim();
			boolean skip = false;
			for (String remove : entriesToRemove)
				if (name.equals(remove.trim()))
					skip = true;
			if (skip) {
				System.out.println("Skipping entry: "+name);
				continue;
			}
			
			// copy it over
			zout.putNextEntry(new ZipEntry(entry.getName()));
			
			BufferedInputStream bin = new BufferedInputStream(zip.getInputStream(entry));
			bin.transferTo(zout);
			zout.flush();
			
			zout.closeEntry();
		}
		
		zout.close();
	}
	
	@Test
	public void testDefaultRupSetModules() throws IOException {
		// first clear out the rup set modules index
		File tempFile = new File(tempDir, "rup_set_without_rs_modules.zip");
		writeZipWithout(demoRupSetZip, tempFile, "ruptures/modules.json");
		
		FaultSystemRupSet rupSet = null;
		try {
			rupSet = FaultSystemRupSet.load(tempFile);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load demo rupture set after we deleted ruptures/modules.json: "+e.getMessage());
		}
		assertNotNull(rupSet);
		
		// now check for default modules
		assertTrue("Default module SectAreas was not attached", rupSet.hasModule(SectAreas.class));
		assertTrue("Default module SectSlipRates was not attached", rupSet.hasModule(SectSlipRates.class));
	}
	
	@Test
	public void testUnlistedDefaultRupSetModules() throws IOException {
		// first clear out the rup set modules index
		File tempFileWith = new File(tempDir, "rup_set_with_default.zip");
		File tempFileWithout = new File(tempDir, "rup_set_with_default_without_modules.zip");
		FaultSystemRupSet origRupSet = FaultSystemRupSet.load(demoRupSetZip);
		double[] fakeAreas = new double[origRupSet.getNumSections()];
		for (int i=0; i<fakeAreas.length; i++)
			fakeAreas[i] = i;
		origRupSet.addModule(SectAreas.precomputed(origRupSet, fakeAreas));
		double[] fakeSlipRates = new double[origRupSet.getNumSections()];
		double[] fakeSlipRateStdDevs = new double[origRupSet.getNumSections()];
		for (int i=0; i<fakeSlipRates.length; i++) {
			fakeSlipRates[i] = i+100;
			fakeSlipRateStdDevs[i] = i+200;
		}
		origRupSet.addModule(SectSlipRates.precomputed(origRupSet, fakeSlipRates, fakeSlipRateStdDevs));
		
		origRupSet.write(tempFileWith);
		// now clear out the modules index
		writeZipWithout(new ZipFile(tempFileWith), tempFileWithout, "ruptures/modules.json");
		
		FaultSystemRupSet rupSet = null;
		try {
			rupSet = FaultSystemRupSet.load(tempFileWithout);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load demo rupture set after we deleted ruptures/modules.json: "+e.getMessage());
		}
		assertNotNull(rupSet);
		
		// now check for default modules
		assertTrue("Default unlised module SectAreas was not attached", rupSet.hasModule(SectAreas.class));
		assertTrue("Default unlised module SectSlipRates was not attached", rupSet.hasModule(SectSlipRates.class));
		
		// now make sure they actually loaded in the unliseted versions
		double[] areas = rupSet.getAreaForAllSections();
		for (int i=0; i<areas.length; i++)
			assertEquals("Failed to load in ulisted SectAreas module", fakeAreas[i], areas[i], 1e-16);
		double[] slipRates = rupSet.getSlipRateForAllSections();
		double[] slipRateStdDevs = rupSet.getSlipRateStdDevForAllSections();
		for (int i=0; i<areas.length; i++) {
			assertEquals("Failed to load in ulisted SectSlipRates module",
					fakeSlipRates[i], slipRates[i], 1e-16);
			assertEquals("Failed to load in ulisted SectSlipRates module",
					fakeSlipRateStdDevs[i], slipRateStdDevs[i], 1e-16);
		}
	}
	
	@Test
	public void testUnlistedGridSources() throws IOException {
		File tempFile = new File(tempDir, "sol_without_modules_index.zip");
		writeZipWithout(demoSolZip, tempFile, "solution/modules.json");
		
		FaultSystemSolution sol = null;
		try {
			sol = FaultSystemSolution.load(tempFile);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load demo solution after we deleted solution/modules.json: "+e.getMessage());
		}
		assertNotNull(sol);
		
		// now check for grid source provider
		GridSourceProvider loadedProv = sol.getGridSourceProvider();
		assertNotNull("Didn't find unlisted grid source provider", loadedProv);
		
		GridSourceProvider origProv = demoSol.getGridSourceProvider();
		assertEquals("Unlisted grid source region doesn't match",
				origProv.getGriddedRegion(), loadedProv.getGriddedRegion());
	}

}
