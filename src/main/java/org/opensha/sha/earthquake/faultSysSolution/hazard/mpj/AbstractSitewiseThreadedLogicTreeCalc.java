package org.opensha.sha.earthquake.faultSysSolution.hazard.mpj;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.params.filters.FixedDistanceCutoffFilter;
import org.opensha.sha.calc.params.filters.SourceFilter;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.DistCachedERFWrapper;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.logicTree.ScalarIMR_LogicTreeNode;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import scratch.UCERF3.erf.FaultSystemSolutionERF;

public abstract class AbstractSitewiseThreadedLogicTreeCalc {
	
	private ExecutorService exec;
	private int numSites;
	private SolutionLogicTree solTree;
	private LogicTree<?> tree;
	private boolean hasGMMLevels;
	private AttenRelRef gmmRef;
	private double[] periods;
	private IncludeBackgroundOption gridSeisOp;
	private boolean cacheGridSources = false;
	private boolean doGmmInputCache = false;
	private double maxDistance;
	
	private DiscretizedFunc[] xVals;
	private DiscretizedFunc[] logXVals;
	
	private LogicTreeBranch<?> prevBranch;
	private int prevBranchIndex = -1;
	private FaultSystemSolutionERF prevERF;
	private int[] prevSiteIndexes;
	private int gmmCacheBranchIndex = -1;
	private NSHMP_GMM_Wrapper[] gmmSiteCaches;

	public AbstractSitewiseThreadedLogicTreeCalc(ExecutorService exec, int numSites, SolutionLogicTree solTree,
			AttenRelRef gmmRef, double[] periods, IncludeBackgroundOption gridSeisOp, double maxDistance) {
		this.exec = exec;
		this.numSites = numSites;
		this.solTree = solTree;
		this.gmmRef = gmmRef;
		this.periods = periods;
		this.gridSeisOp = gridSeisOp;
		this.maxDistance = maxDistance;
		this.tree = solTree.getLogicTree();
		for (LogicTreeLevel<?> level : tree.getLevels()) {
			if (isGMMLevel(level) && level.getNodes().size() > 1) {
				hasGMMLevels = true;
				break;
			}
		}
		
		xVals = new DiscretizedFunc[periods.length];
		logXVals = new DiscretizedFunc[periods.length];
		IMT_Info imtInfo = new IMT_Info();
		for (int p=0; p<periods.length; p++) {
			if (periods[p] == -1d)
				xVals[p] = imtInfo.getDefaultHazardCurve(PGV_Param.NAME);
			else if (periods[p] == 0d)
				xVals[p] = imtInfo.getDefaultHazardCurve(PGA_Param.NAME);
			else
				xVals[p] = imtInfo.getDefaultHazardCurve(SA_Param.NAME);
			logXVals[p] = new ArbitrarilyDiscretizedFunc();
			for (Point2D pt : xVals[p])
				logXVals[p].set(Math.log(pt.getX()), 0d);
		}
		
		debug("hasGMMLevels="+hasGMMLevels);
	}
	
	public void setCacheGridSources(boolean cacheGridSources) {
		this.cacheGridSources = cacheGridSources;
	}
	
	public void setDoGmmInputCache(boolean doGmmInputCache) {
		this.doGmmInputCache = doGmmInputCache;
	}
	
	private static boolean isGMMLevel(LogicTreeLevel<?> level) {
		return ScalarIMR_LogicTreeNode.class.isAssignableFrom(level.getType()) ||
				ScalarIMR_ParamsLogicTreeNode.class.isAssignableFrom(level.getType());
	}
	
	public abstract Site siteForIndex(int siteIndex, ScalarIMR gmm);
	
	public abstract void debug(String message);
	
	public DiscretizedFunc[] getXVals() {
		return xVals;
	}

	public DiscretizedFunc[] getLogXVals() {
		return logXVals;
	}

	public DiscretizedFunc[][] calcForBranch(int branchIndex) throws IOException {
		int[] siteIndexes = new int[numSites];
		for (int s=0; s<numSites; s++)
			siteIndexes[s] = s;
		return calcForBranch(branchIndex, siteIndexes);
	}
	
	public DiscretizedFunc[][] calcForBranch(int branchIndex, int[] siteIndexes) throws IOException {
		LogicTreeBranch<?> branch = tree.getBranch(branchIndex);
		
		FaultSystemSolutionERF erf = null;
		if (hasGMMLevels && prevBranch != null) {
			// see if we can reuse the erf
//			debug("Seeing if we can reuse ERF from "+prevBranchIndex+" for "+branchIndex);
			boolean onlyGmmDifferent = true;
			for (int l=0; l<branch.size(); l++) {
				if (!branch.getValue(l).equals(prevBranch.getValue(l))) {
					if (!isGMMLevel(branch.getLevel(l))) {
						onlyGmmDifferent = false;
						debug("More than GMM different between "+branchIndex+" and "+prevBranchIndex
								+": '"+branch.getValue(l)+"' != '"+prevBranch.getValue(l)+"'");
						break;
					}
				}
			}
			if (onlyGmmDifferent) {
				// can reuse the erf
				debug("Re-using ERF from branch "+prevBranchIndex+" for branch "+branchIndex);
				erf = prevERF;
			} else {
				prevSiteIndexes = null;
				gmmSiteCaches = null;
			}
		}
		if (erf == null) {
			// need to load it
			debug("Building ERF for branch "+branchIndex);
			prevSiteIndexes = null;
			gmmSiteCaches = null;
			FaultSystemSolution sol = solTree.forBranch(branch);
//			double sumRate = 0d;
//			for (double rate : sol.getRateForAllRups())
//				sumRate += rate;
//			System.out.println("Sum rate: "+sumRate);
			erf = new FaultSystemSolutionERF(sol);
			if (gridSeisOp == IncludeBackgroundOption.INCLUDE || gridSeisOp == IncludeBackgroundOption.ONLY)
				Preconditions.checkNotNull(sol.getGridSourceProvider(),
						"Grid source provider is null, but gridded seis option is %s", gridSeisOp);
			erf.setParameter(IncludeBackgroundParam.NAME, gridSeisOp);
			erf.getTimeSpan().setDuration(1d);
			erf.setCacheGridSources(cacheGridSources);
			erf.updateForecast();
		}
		
		Supplier<ScalarIMR> gmmSupplier = MPJ_LogicTreeHazardCalc.getGMM_Supplier(branch, gmmRef);
		
		boolean doGmmInputCache = false;
		if (hasGMMLevels && this.doGmmInputCache) {
			// see if we're a nshmp-haz wrapped gmm
			ScalarIMR gmm = gmmSupplier.get();
			doGmmInputCache = gmm instanceof NSHMP_GMM_Wrapper;
		}
		
		Deque<DistCachedERFWrapper> erfDeque = new ArrayDeque<>();
		
		if (doGmmInputCache) {
			if (prevSiteIndexes == null || (gmmSiteCaches != null && !Arrays.equals(siteIndexes, prevSiteIndexes))) {
				prevSiteIndexes = null;
				gmmSiteCaches = null;
			}
			
			if (gmmSiteCaches == null) {
				// pre-cache
				debug("Pre-caching GMM inputs for branch "+branchIndex);
				List<Future<NSHMP_GMM_Wrapper>> futures = new ArrayList<>(siteIndexes.length);
				for (int siteIndex : siteIndexes)
					futures.add(exec.submit(new SitePrecacheCall(erf, siteIndex, erfDeque)));
				debug("DONE pre-caching GMM inputs for branch "+branchIndex);
				
				gmmSiteCaches = new NSHMP_GMM_Wrapper[siteIndexes.length];
				for (int s=0; s<siteIndexes.length; s++) {
					try {
						gmmSiteCaches[s] = futures.get(s).get();
					} catch (InterruptedException | ExecutionException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
				gmmCacheBranchIndex = branchIndex;
			} else {
				debug("Re-using GMM inputs from branch "+gmmCacheBranchIndex+" for branch "+branchIndex);
			}
		}
		 
		debug("Calculaing "+siteIndexes.length+" sites for branch "+branchIndex);
		List<Future<DiscretizedFunc[]>> futures = new ArrayList<>(siteIndexes.length);
		for (int s=0; s<siteIndexes.length; s++) {
			int siteIndex = siteIndexes[s];
			SiteCalcCall call = new SiteCalcCall(erf, siteIndex, gmmSupplier, erfDeque);
			if (doGmmInputCache)
				call.cacheSupplier = gmmSiteCaches[s];
			
			futures.add(exec.submit(call));
		}
		debug("DONE calculaing "+siteIndexes.length+" sites for branch "+branchIndex);
		
		DiscretizedFunc[][] curves = new DiscretizedFunc[siteIndexes.length][];
		for (int s=0; s<siteIndexes.length; s++) {
			try {
				curves[s] = futures.get(s).get();
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		prevERF = erf;
		prevBranch = branch;
		prevBranchIndex = branchIndex;
		prevSiteIndexes = siteIndexes;
		
		return curves;
	}
	
	private class SitePrecacheCall implements Callable<NSHMP_GMM_Wrapper> {
		
		private FaultSystemSolutionERF fssERF;
		private int siteIndex;
		private Deque<DistCachedERFWrapper> erfDeque;
		
		private SitePrecacheCall(FaultSystemSolutionERF fssERF, int siteIndex, Deque<DistCachedERFWrapper> erfDeque) {
			super();
			this.fssERF = fssERF;
			this.siteIndex = siteIndex;
			this.erfDeque = erfDeque;
		}

		@Override
		public NSHMP_GMM_Wrapper call() throws Exception {
			DistCachedERFWrapper erf = checkOutERF(fssERF, erfDeque);
			
			NSHMP_GMM_Wrapper cache = new NSHMP_GMM_Wrapper(null, false);
			
			// TODO: support more complicated filters
			Collection<SourceFilter> sourceFilters = Collections.singleton(new FixedDistanceCutoffFilter(maxDistance));
			
			Site site = siteForIndex(siteIndex, cache);
			
			cache.setSite(site);
			
			for (ProbEqkSource source : erf) {
				if (HazardCurveCalculator.canSkipSource(sourceFilters, source, site))
					continue;
				
				for (ProbEqkRupture rup : source)
					cache.setEqkRupture(rup);
			}
			
			checkInERF(erf, erfDeque);
			
			return cache;
		}
	}
	
	private class SiteCalcCall implements Callable<DiscretizedFunc[]> {
		
		private FaultSystemSolutionERF fssERF;
		private int siteIndex;
		private Supplier<ScalarIMR> gmmSupplier;
		private Deque<DistCachedERFWrapper> erfDeque;
		
		private NSHMP_GMM_Wrapper cacheSupplier;

		private SiteCalcCall(FaultSystemSolutionERF fssERF, int siteIndex, Supplier<ScalarIMR> gmmSupplier, Deque<DistCachedERFWrapper> erfDeque) {
			this.fssERF = fssERF;
			this.siteIndex = siteIndex;
			this.gmmSupplier = gmmSupplier;
			this.erfDeque = erfDeque;
		}

		@Override
		public DiscretizedFunc[] call() throws Exception {
			ScalarIMR gmm = gmmSupplier.get();
			Site site = siteForIndex(siteIndex, gmm);
			AbstractERF erf;
			if (cacheSupplier != null && gmm instanceof NSHMP_GMM_Wrapper) {
				gmm.setSite(site);
				NSHMP_GMM_Wrapper nshmpGmm = (NSHMP_GMM_Wrapper)gmm;
				nshmpGmm.setCacheInputsPerRupture(true);
				nshmpGmm.copyPerRuptureCacheFrom(cacheSupplier);
				erf = fssERF; // just use the FSS ERF as everything is already cached anyway
			} else {
				erf = checkOutERF(fssERF, erfDeque);
			}
			
			ScalarIMR gmpe = gmmSupplier.get();
			
			DiscretizedFunc[] curves = new DiscretizedFunc[periods.length];
			
			HazardCurveCalculator calc = new HazardCurveCalculator();
			calc.setMaxSourceDistance(maxDistance); // TODO: support more complicated filters (here and above when caching)
			
			for (int p=0; p<periods.length; p++) {
				if (periods[p] == -1d) {
					gmpe.setIntensityMeasure(PGV_Param.NAME);
				} else if (periods[p] == 0d) {
					gmpe.setIntensityMeasure(PGA_Param.NAME);
				} else {
					Preconditions.checkState(periods[p] > 0d);
					gmpe.setIntensityMeasure(SA_Param.NAME);
					SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), periods[p]);
				}
				
				DiscretizedFunc logHazCurve = logXVals[p].deepClone();
				
				calc.getHazardCurve(logHazCurve, site, gmpe, erf);
				
				double sumY = logHazCurve.calcSumOfY_Vals();
				if (!Double.isFinite(sumY) || sumY <= 0d) {
					System.err.println("Hazard curve is non-finite or zero. sumY="+sumY);
					System.err.println("\tSite: "+site.getName()+", "+site.getLocation());
					System.err.println("\tGMPE: "+gmpe.getName());
					System.err.println("\tLog Curve:\n"+logHazCurve);
				}
				Preconditions.checkState(Double.isFinite(sumY), "Non-finite hazard curve");
				
				curves[p] = xVals[p].deepClone();
				Preconditions.checkState(curves[p].size() == logHazCurve.size());
				for (int i=0; i<logHazCurve.size(); i++)
					curves[p].set(i, logHazCurve.getY(i));
			}
			
			if (erf instanceof DistCachedERFWrapper)
				checkInERF((DistCachedERFWrapper)erf, erfDeque);
			
			return curves;
		}
		
	}
	
	private synchronized DistCachedERFWrapper checkOutERF(FaultSystemSolutionERF fssERF, Deque<DistCachedERFWrapper> deque) {
		if (!deque.isEmpty()) {
			return deque.pop();
		}
		return new DistCachedERFWrapper(fssERF);
	}
	
	private synchronized void checkInERF(DistCachedERFWrapper erf, Deque<DistCachedERFWrapper> deque) {
		deque.push(erf);
	}

}
