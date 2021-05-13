package org.opensha.sha.calc.hazardMap.components;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import org.dom4j.Element;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.calc.hazardMap.BinaryHazardCurveReader;
import org.opensha.sha.gui.infoTools.IMT_Info;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class TestBinaryCurveArchiver {
	
	private static List<File> tempDirs;
	private static List<Site> sites;
	private static Map<String, DiscretizedFunc> xVals;
	private static final int num_threads = 10;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tempDirs = Lists.newArrayList();
		
		sites = Lists.newArrayList();
		for (int i=0; i<100; i++) {
			Site site = new Site(new Location(34d+2*Math.random(), -118d+2*Math.random()));
			site.setName(i+"");
			sites.add(site);
		}
		
		xVals = Maps.newHashMap();
		xVals.put("pga", IMT_Info.getUSGS_PGA_Function().deepClone());
		xVals.put("asdf", new EvenlyDiscretizedFunc(0d, 10d, 55));
	}
	
	@AfterClass
	public static void tearDownAfterClass() {
		for (File tempDir : tempDirs)
			if (tempDir.exists())
				FileUtils.deleteRecursive(tempDir);
	}

	@Test
	public void testSingleIMT() throws Exception {
		doTest("pga");
		doTest("pga");
		doTest("pga");
	}

	@Test
	public void testMultipleIMT() throws Exception {
		doTest("pga", "asdf");
		doTest("pga", "asdf");
		doTest("pga", "asdf");
	}
	
	private void doTest(String... imts) throws Exception {
		File tempDir = FileUtils.createTempDir();
		tempDirs.add(tempDir);
		
		Map<String, DiscretizedFunc[]> funcs = Maps.newHashMap();
		for (String imt : imts)
			funcs.put(imt, new DiscretizedFunc[sites.size()]);
		
		Deque<Site> queue = new ArrayDeque<Site>(sites);
		
		List<Thread> threads = Lists.newArrayList();
		for (int i=0; i<num_threads; i++) {
			BinaryCurveArchiver archive = new BinaryCurveArchiver(tempDir, sites.size(), xVals);
			threads.add(new Thread(new TestRunnable(funcs, queue, archive)));
		}
		
		// shuffle threads
		Collections.shuffle(threads);
		
		for (Thread thread : threads)
			thread.start();
		
		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		// first make sure every curve has been populated
		for (String imt : imts)
			for (DiscretizedFunc func : funcs.get(imt))
				assertNotNull(func);
		
		for (String imt : imts) {
			File file = new File(tempDir, imt+".bin");
			assertTrue("File "+file.getAbsolutePath()+" doesn't exist!", file.exists());
			BinaryHazardCurveReader reader = new BinaryHazardCurveReader(file.getAbsolutePath());
			DiscretizedFunc myXVals = xVals.get(imt);
			
			int cnt = 0;
			ArbitrarilyDiscretizedFunc curve = reader.nextCurve();
			while (curve != null) {
				Location loc = reader.currentLocation();
				assertEquals("Location doesn't mat", sites.get(cnt).getLocation(), loc);
				assertEquals("X value count wront", myXVals.size(), curve.size());
				DiscretizedFunc calcCurve = funcs.get(imt)[cnt];
				for (int i=0; i<curve.size(); i++) {
					assertEquals(myXVals.getX(i), curve.getX(i), 1e-10);
					assertEquals(calcCurve.getX(i), calcCurve.getX(i), 1e-10);
					assertEquals(calcCurve.getY(i), calcCurve.getY(i), 1e-10);
				}
				
				cnt++;
				curve = reader.nextCurve();
			}
			
			assertEquals("loaded curve count wrong", sites.size(), cnt); 
		}
	}
	
	private static synchronized Site popSite(Deque<Site> sites) {
		try {
			return sites.pop();
		} catch (NoSuchElementException e) {
			return null;
		}
	}
	
	private class TestRunnable implements Runnable {
		
		private Map<String, DiscretizedFunc[]> funcs;
		private Deque<Site> sites;
		private BinaryCurveArchiver archive;

		public TestRunnable(Map<String, DiscretizedFunc[]> funcs, Deque<Site> sites, BinaryCurveArchiver archive) {
			this.funcs = funcs;
			this.sites = sites;
			this.archive = archive;
		}

		@Override
		public void run() {
			try {
				Site site;
				while (true) {
					site = popSite(this.sites);
					if (site == null)
						break;
					int index = Integer.parseInt(site.getName());
					for (String imt : funcs.keySet()) {
						DiscretizedFunc myXVals = xVals.get(imt);
						ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
						for (int i=0; i<myXVals.size(); i++)
							func.set(myXVals.getX(i), Math.random());
						archive.archiveCurve(func, new CurveMetadata(site, index, null, imt));
						Preconditions.checkState(funcs.get(imt)[index] == null);
						funcs.get(imt)[index] = func;
					}
				}
			} catch (IOException e) {
				fail(e.getMessage());
			}
		}
		
	}

}
