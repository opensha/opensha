package org.opensha.sha.earthquake.faultSysSolution.hazard.mpj;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.calc.sourceFilters.SourceFilterUtils;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.DistCachedERFWrapper;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.logicTree.ScalarIMRsLogicTreeNode;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.FaultSystemSolutionERF;

public abstract class AbstractSitewiseThreadedLogicTreeCalc {
	
	private ExecutorService exec;
	private int numSites;
	private SolutionLogicTree solTree;
	private LogicTree<?> tree;
	private boolean hasGMMLevels;
	private Map<TectonicRegionType, AttenRelSupplier> gmmRefs;
	private double[] periods;
	private IncludeBackgroundOption gridSeisOp;
	private GriddedSeismicitySettings griddedSettings;
	private boolean cacheGridSources = true;
	private boolean doGmmInputCache = false;
	private SourceFilterManager sourceFilters;
	
	private DiscretizedFunc[] xVals;
	private DiscretizedFunc[] logXVals;
	
	private LogicTreeBranch<?> prevBranch;
	private int prevBranchIndex = -1;
	private FaultSystemSolutionERF prevERF;
	private int[] prevSiteIndexes;
	private int gmmCacheBranchIndex = -1;
	private NSHMP_GMM_Wrapper[] gmmSiteCaches;

	public AbstractSitewiseThreadedLogicTreeCalc(ExecutorService exec, int numSites, SolutionLogicTree solTree,
			AttenRelSupplier gmmRef, double[] periods,
			IncludeBackgroundOption gridSeisOp, GriddedSeismicitySettings griddedSettings, SourceFilterManager sourceFilters) {
		this(exec, numSites, solTree, FaultSysHazardCalcSettings.wrapInTRTMap(gmmRef), periods, gridSeisOp, griddedSettings, sourceFilters);
	}

	public AbstractSitewiseThreadedLogicTreeCalc(ExecutorService exec, int numSites, SolutionLogicTree solTree,
			Map<TectonicRegionType, AttenRelSupplier> gmmRefs, double[] periods,
			IncludeBackgroundOption gridSeisOp, GriddedSeismicitySettings griddedSettings, SourceFilterManager sourceFilters) {
		this.exec = exec;
		this.numSites = numSites;
		this.solTree = solTree;
		this.gmmRefs = gmmRefs;
		this.periods = periods;
		this.gridSeisOp = gridSeisOp;
		this.griddedSettings = griddedSettings;
		this.sourceFilters = sourceFilters;
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
		return ScalarIMRsLogicTreeNode.class.isAssignableFrom(level.getType()) ||
				ScalarIMR_ParamsLogicTreeNode.class.isAssignableFrom(level.getType());
	}
	
	public abstract Site siteForIndex(int siteIndex, Map<TectonicRegionType, ScalarIMR> gmms);
	
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
			erf.setGriddedSeismicitySettings(griddedSettings);
			erf.setCacheGridSources(cacheGridSources);
			erf.updateForecast();
		}
		
		Map<TectonicRegionType, AttenRelSupplier> gmmSuppliers = FaultSysHazardCalcSettings.getGMM_Suppliers(branch, gmmRefs, true);
		
		boolean doGmmInputCache = false;
		if (hasGMMLevels && this.doGmmInputCache) {
			// see if we're a nshmp-haz wrapped gmm
			for (Supplier<ScalarIMR> gmmSupplier : gmmSuppliers.values()) {
				ScalarIMR gmm = gmmSupplier.get();
				if (gmm instanceof NSHMP_GMM_Wrapper) {
					doGmmInputCache = true;
				} else {
					doGmmInputCache = false;
					break;
				}
			}
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
			SiteCalcCall call = new SiteCalcCall(erf, siteIndex, gmmSuppliers, erfDeque);
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
			
			NSHMP_GMM_Wrapper cache = new NSHMP_GMM_Wrapper.InputCacheGen();
			
			Collection<SourceFilter> filters = sourceFilters.getEnabledFilters();
			
			EnumMap<TectonicRegionType, ScalarIMR> gmmMap = new EnumMap<>(TectonicRegionType.class);
			gmmMap.put(TectonicRegionType.ACTIVE_SHALLOW, cache);
			Site site = siteForIndex(siteIndex, gmmMap);
			
			cache.setSite(site);
			
			for (ProbEqkSource source : erf) {
				if (SourceFilterUtils.canSkipSource(filters, source, site))
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
		private Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmmSuppliers;
		private Deque<DistCachedERFWrapper> erfDeque;
		
		private NSHMP_GMM_Wrapper cacheSupplier;

		private SiteCalcCall(FaultSystemSolutionERF fssERF, int siteIndex,
				Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmmSuppliers,
				Deque<DistCachedERFWrapper> erfDeque) {
			this.fssERF = fssERF;
			this.siteIndex = siteIndex;
			this.gmmSuppliers = gmmSuppliers;
			this.erfDeque = erfDeque;
		}

		@Override
		public DiscretizedFunc[] call() throws Exception {
			Map<TectonicRegionType, ScalarIMR> gmms = FaultSysHazardCalcSettings.getGmmInstances(gmmSuppliers);
			Site site = siteForIndex(siteIndex, gmms);
			AbstractERF erf = null;
			for (ScalarIMR gmm : gmms.values()) {
				if (cacheSupplier != null && gmm instanceof NSHMP_GMM_Wrapper) {
					gmm.setSite(site);
					NSHMP_GMM_Wrapper nshmpGmm = (NSHMP_GMM_Wrapper)gmm;
					nshmpGmm.setCacheInputsPerRupture(true);
					nshmpGmm.copyPerRuptureCacheFrom(cacheSupplier);
					if (erf == null)
						// just use the FSS ERF as everything is already cached anyway
						erf = fssERF;
				} else {
					erf = checkOutERF(fssERF, erfDeque);
				}
			}
			
			DiscretizedFunc[] curves = new DiscretizedFunc[periods.length];
			
			HazardCurveCalculator calc = new HazardCurveCalculator(sourceFilters);
			
			for (int p=0; p<periods.length; p++) {
				FaultSysHazardCalcSettings.setIMforPeriod(gmms, periods[p]);
				
				DiscretizedFunc logHazCurve = logXVals[p].deepClone();
				
				calc.getHazardCurve(logHazCurve, site, gmms, erf);
				
				double sumY = logHazCurve.calcSumOfY_Vals();
				if (!Double.isFinite(sumY) || sumY <= 0d) {
					System.err.println("Hazard curve is non-finite or zero. sumY="+sumY);
					System.err.println("\tSite: "+site.getName()+", "+site.getLocation());
//					System.err.println("\tGMPE: "+gmpe.getName());
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
