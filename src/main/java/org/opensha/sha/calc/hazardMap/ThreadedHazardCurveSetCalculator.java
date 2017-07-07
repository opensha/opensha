package org.opensha.sha.calc.hazardMap;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.opensha.commons.data.Site;

public class ThreadedHazardCurveSetCalculator {
	
	private HazardCurveSetCalculator[] calcs;
	private Deque<Site> stack;
	
	public ThreadedHazardCurveSetCalculator(HazardCurveSetCalculator[] calcs) {
		this.calcs = calcs;
	}
	
	public void calculateCurves(List<Site> sites) throws IOException, InterruptedException {
		populateNameField(sites, null);
		calculateCurves(new ArrayDeque<Site>(sites));
	}
	
	public void calculateCurves(List<Site> sites, int[] indices) throws IOException, InterruptedException {
		populateNameField(sites, indices);
		ArrayDeque<Site> deque = new ArrayDeque<Site>();
		for (int index : indices)
			deque.add(sites.get(index));
		calculateCurves(deque);
	}
	
	private void populateNameField(List<Site> sites, int[] indices) {
		// this stores site index in the name field
		if (indices == null) {
			for (int i=0; i<sites.size(); i++)
				sites.get(i).setName(i+"");
		} else {
			for (int ind : indices) {
				sites.get(ind).setName(ind+"");
			}
		}
	}
	
	private void calculateCurves(Deque<Site> sites) throws IOException, InterruptedException {
		this.stack = sites;
		int numThreads = calcs.length;
		
		ArrayList<Thread> threads = new ArrayList<Thread>();
		
		for (int i=0; i<numThreads; i++) {
			threads.add(new Thread(new CalcRunnable(calcs[i])));
		}
		
		// start the threads
		for (Thread t : threads) {
			t.start();
		}
		
		// join the threads
		for (Thread t : threads) {
			t.join();
		}
	}
	
	private synchronized Site popSite() {
		try {
			return stack.pop();
		} catch (Exception e) {
			return null;
		}
	}
	
	private class CalcRunnable implements Runnable {
		
		private HazardCurveSetCalculator calc;
		
		public CalcRunnable(HazardCurveSetCalculator calc) {
			this.calc = calc;
		}

		@Override
		public void run() {
			try {
				Site site;
				while (true) {
					site = popSite();
					if (site == null)
						break;
					int index = Integer.parseInt(site.getName());
					calc.calculateCurves(site, index);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		
	}
	
	public void close() {
		calcs[0].close();
	}

}
