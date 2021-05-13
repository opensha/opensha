package org.opensha.nshmp2.calc;

import java.io.IOException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.LocationList;
import org.opensha.nshmp2.util.Period;
import org.opensha.nshmp2.util.SourceIMR;
import org.opensha.sha.earthquake.EpistemicListERF;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;

import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.peter.ucerf3.calc.UC3_CalcUtils;

/**
 * Class manages multithreaded NSHMP hazard calculations. Farms out
 * {@code HazardCalc}s to locally available cores and pipes results to a
 * supplied {@code Queue}.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class ThreadedHazardCalc {

	private LocationList locs;
	private Period period;
	private boolean epiUncert;
	private HazardResultWriter writer;
	private EpistemicListERF erfList;
	private SourceIMR imr = null;

	private boolean determ = false;
	

	/**
	 * The supplied ERF should be ready to go, i.e. have had updateForecast()
	 * called.
	 */
	@SuppressWarnings("javadoc")
	public ThreadedHazardCalc(EpistemicListERF erfList, SourceIMR imr, 
			LocationList locs, Period period, boolean epiUncert, 
			HazardResultWriter writer, boolean determ) {
		this.locs = locs;
		this.period = period;
		this.writer = writer;
		this.epiUncert = epiUncert;
		this.imr = imr;
		this.erfList = erfList;
		this.determ = determ;
	}

	/**
	 * Initializes a new threaded hazard calculation with the specified ERF.
	 */
	@SuppressWarnings("javadoc")
	public ThreadedHazardCalc(ERF_ID erfID, SourceIMR imr, LocationList locs, 
			Period period, boolean epiUncert, HazardResultWriter writer) {
		this.locs = locs;
		this.period = period;
		this.writer = writer;
		this.epiUncert = epiUncert;
		this.imr = imr;
		erfList = erfID.instance();
		erfList.updateForecast();
	}
	
	/**
	 * Initializes a new threaded hazard calculation for the specified UC3 
	 * (averaged) solution.
	 */
	@SuppressWarnings("javadoc")
	public ThreadedHazardCalc(String solPath, SourceIMR imr, LocationList locs,
		Period period, boolean epiUncert, IncludeBackgroundOption bg, 
		HazardResultWriter writer, boolean nshmp) {
		this.locs = locs;
		this.period = period;
		this.writer = writer;
		this.epiUncert = epiUncert;
		this.imr = imr;
		System.out.println("Starting threaded hazard calc...");
		determ = nshmp && bg == IncludeBackgroundOption.EXCLUDE;
				
		FaultSystemSolutionERF erf = nshmp ? 
			UC3_CalcUtils.getNSHMP_UC3_ERF(solPath, bg, false, true, 1.0) :
			UC3_CalcUtils.getUC3_ERF(solPath, bg, false, true, 1.0);
		erfList = ERF_ID.wrapInList(erf);
		erfList.updateForecast();
	}

	/**
	 * Initializes a new threaded hazard calculation for the specified UC3 logic
	 * tree branch.
	 */
	@SuppressWarnings("javadoc")
	public ThreadedHazardCalc(String solPath, String branchID, LocationList locs,
		Period period, boolean epiUncert, HazardResultWriter writer) {
		this.locs = locs;
		this.period = period;
		this.writer = writer;
		this.epiUncert = epiUncert;
		FaultSystemSolutionERF erf = UC3_CalcUtils.getUC3_ERF(solPath, branchID,
			IncludeBackgroundOption.INCLUDE, false, true, 1.0);
		erfList = ERF_ID.wrapInList(erf);
		erfList.updateForecast();
	}

	/**
	 * Initializes a new threaded hazard calculation for the specified UC3 logic
	 * tree branch.
	 */
	@SuppressWarnings("javadoc")
	public ThreadedHazardCalc(String solPath, int solIdx, LocationList locs,
		Period period, boolean epiUncert, HazardResultWriter writer) {
		this.locs = locs;
		this.period = period;
		this.writer = writer;
		this.epiUncert = epiUncert;
		FaultSystemSolutionERF erf = UC3_CalcUtils.getUC3_ERF(solPath, solIdx,
			IncludeBackgroundOption.INCLUDE, false, true, 1.0);
		erfList = ERF_ID.wrapInList(erf);
		erfList.updateForecast();
	}

	/**
	 * Calculates hazard curves for the specified indices. Presently no index
	 * checking is performed. If {@code indices} is {@code null}, curves are
	 * calculated at all locations.
	 * 
	 * @param indices of locations to calculate curves for
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws IOException
	 */
	public void calculate(int[] indices) throws InterruptedException,
			ExecutionException, IOException {
		calculate(indices, Runtime.getRuntime().availableProcessors());
	}
	
	/**
	 * Calculates hazard curves for the specified indices. Presently no index
	 * checking is performed. If {@code indices} is {@code null}, curves are
	 * calculated at all locations.
	 * 
	 * @param indices of locations to calculate curves for
	 * @param numProc number of threads to launch
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws IOException
	 */
	public void calculate(int[] indices, int numProc) throws InterruptedException,
	ExecutionException, IOException {
		
		// set up to process all
		if (indices == null) indices = makeIndices(locs.size());
		
		// init thread mgr
		ExecutorService ex = Executors.newFixedThreadPool(numProc);
		CompletionService<HazardResult> ecs = 
				new ExecutorCompletionService<HazardResult>(ex);

		for (int index : indices) {
			Site site = new Site(locs.get(index));
			HazardCalc hc = HazardCalc.create(erfList, imr, site, period, epiUncert, determ);
			ecs.submit(hc);
		}
		ex.shutdown();
//		System.out.println("Jobs submitted: " + indices.length);

		// process results as they come in; ecs,take() blocks until result
		// is available
		for (int i = 0; i < indices.length; i++) {
			writer.write(ecs.take().get());
//			if (i % 10 == 0) System.out.println("Jobs completed: " + i);
		}
		
//		writer.close(); // not needed for sites writer
	}
	
	private int[] makeIndices(int size) {
		int[] indices = new int[size];
		for (int i=0; i<size; i++) {
			indices[i] = i;
		}
		return indices;
	}

}
