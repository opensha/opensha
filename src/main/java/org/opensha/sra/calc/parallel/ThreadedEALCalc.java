package org.opensha.sra.calc.parallel;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sra.gui.portfolioeal.Asset;
import org.opensha.sra.gui.portfolioeal.CalculationExceptionHandler;

import com.google.common.base.Preconditions;

public class ThreadedEALCalc {
	
	protected List<Asset> assets;
	protected ERF[] erfs;
	protected ScalarIMR[] imrs;
	protected Site[] sites;
	
	private CalculationExceptionHandler handler;
	
	protected Deque<Asset> stack;
	private double maxSourceDistance;
	private ArbitrarilyDiscretizedFunc magThreshFunc;
	
	public ThreadedEALCalc(List<Asset> assets, ERF[] erfs, ScalarIMR[] imrs, CalculationExceptionHandler handler,
			double maxSourceDistance, ArbitrarilyDiscretizedFunc magThreshFunc) {
		Preconditions.checkNotNull(assets);
		Preconditions.checkArgument(!assets.isEmpty());
		Preconditions.checkNotNull(erfs);
		Preconditions.checkNotNull(imrs);
		Preconditions.checkArgument(imrs.length > 0);
		for (ScalarIMR imr : imrs) {
			Preconditions.checkNotNull(imr);
		}
		Preconditions.checkArgument(erfs.length > 0);
		for (ERF erf : erfs)
			Preconditions.checkNotNull(erf);
		if (erfs.length > 1)
			Preconditions.checkState(erfs.length == imrs.length);
		
		this.assets = assets;
		this.erfs = erfs;
		this.imrs = imrs;
		this.handler = handler;
		this.maxSourceDistance = maxSourceDistance;
		this.magThreshFunc = magThreshFunc;
		
		sites = new Site[imrs.length];
		for (int i=0; i<imrs.length; i++) {
			ScalarIMR imr = imrs[i];
			// initialize the site with IMR params. location will be overridden.
			sites[i] = new Site(new Location(34, -118));
			Iterator<Parameter<?>> it = imr.getSiteParamsIterator();
			while (it.hasNext())
				sites[i].addParameter((Parameter)it.next().clone());
		}
	}
	
	public double[] calculateBatch(int[] batch) throws InterruptedException {
		ArrayDeque<Asset> deque = new ArrayDeque<Asset>();
		for (int index : batch)
			deque.add(assets.get(index));
		calculateBatch(deque);
		
		double[] results = new double[batch.length];
		for (int i=0; i<batch.length; i++)
			results[i] = assets.get(batch[i]).getAssetEAL();
		return results;
	}
	
	public synchronized Asset popAsset() {
		try {
			return stack.pop();
		} catch (Exception e) {
			return null;
		}
	}
	
	private void calculateBatch(Deque<Asset> stack) throws InterruptedException {
		this.stack = stack;
		int numThreads = imrs.length;
		
		ArrayList<Thread> threads = new ArrayList<Thread>();
		
		for (int i=0; i<numThreads; i++) {
			ERF erf;
			if (erfs.length > 1)
				erf = erfs[i];
			else
				erf = erfs[0];
			threads.add(new CalcThread(erf, imrs[i], sites[i]));
		}
		
		// start the threSiteads
		for (Thread t : threads) {
			t.start();
		}
		
		// join the threads
		for (Thread t : threads) {
			t.join();
		}
	}
	
	private class CalcThread extends Thread {
		
		private ScalarIMR imr;
		private Site site;
		private ERF erf;
		public CalcThread(ERF erf, ScalarIMR imr, Site site) {
			this.imr = imr;
			this.site = site;
			this.erf = erf;
		}
		
		@Override
		public void run() {
			Asset asset = popAsset();
			
			while (asset != null) {
				asset.calculateEAL(imr, maxSourceDistance, magThreshFunc, site, erf, handler);
				
//				System.out.println("Calculated EAL: "+asset.getAssetEAL());
				
				asset = popAsset();
			}
		}
	}

	public List<Asset> getAssets() {
		return assets;
	}

	public ERF[] getERFs() {
		return erfs;
	}

	public ScalarIMR[] getIMRs() {
		return imrs;
	}
	
	
}
