package org.opensha.sra.calc.parallel;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sra.calc.parallel.MPJ_CondLossCalc.SiteResult;

import com.google.common.base.Preconditions;

public class ThreadedCondLossCalc {
	
	protected ERF[] erfs;
	protected ScalarIMR[] imrs;
	protected Site[] sites;
	
	protected Deque<SiteResult> stack;
	private ArbitrarilyDiscretizedFunc magThreshFunc;
	
	public ThreadedCondLossCalc(ERF[] erfs, ScalarIMR[] imrs, ArbitrarilyDiscretizedFunc magThreshFunc) {
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
		
		this.erfs = erfs;
		this.imrs = imrs;
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
	
	public synchronized SiteResult popAsset() {
		try {
			return stack.pop();
		} catch (Exception e) {
			return null;
		}
	}
	
	public void calculateBatch(Deque<SiteResult> stack) throws InterruptedException {
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
			SiteResult result = popAsset();
			
			while (result != null) {
				result.calculate(erf, imr, site, magThreshFunc);
				
//				System.out.println("Calculated EAL: "+asset.getAssetEAL());
				
				result = popAsset();
			}
		}
	}

	public ERF[] getERFs() {
		return erfs;
	}

	public ScalarIMR[] getIMRs() {
		return imrs;
	}
	
	
}
