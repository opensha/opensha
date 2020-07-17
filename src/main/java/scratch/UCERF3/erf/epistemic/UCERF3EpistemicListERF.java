package scratch.UCERF3.erf.epistemic;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EpistemicListERF;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.FaultSystemSolutionFetcher;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.mean.MeanUCERF3;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public class UCERF3EpistemicListERF implements EpistemicListERF, ParameterChangeListener {
	
	public static final String NAME = "UCERF3 Epistemic List ERF";
	
	private FaultSystemSolutionFetcher fetch;
	private LogicTreeBranch branch0;
	
	private FaultSystemSolutionERF paramERF;
	private ParameterList paramList;
	
	private static final String LOGIC_TREE_BRANCH_ALL_NAME = "(all)";
	
	private Map<Class<? extends LogicTreeBranchNode<?>>, EnumParameter<?>> enumParamsMap;
	
	private List<LogicTreeBranch> branches;
	
	private static final String COMPOUND_FILE_NAME = "full_ucerf3_compound_sol.zip";
	
	private static FaultSystemSolutionFetcher loadFetcher() throws ZipException, IOException {
		File storeDir = MeanUCERF3.getStoreDir();
		
		File compoundFile = new File(storeDir, COMPOUND_FILE_NAME);
		
		// allow errors so that app doesn't crash if can't download
		MeanUCERF3.checkDownload(compoundFile, true);
		
		if (!compoundFile.exists())
			return null;
		
		return CompoundFaultSystemSolution.fromZipFile(compoundFile);
	}

	public UCERF3EpistemicListERF() throws ZipException, IOException {
		this(loadFetcher());
	}

	public UCERF3EpistemicListERF(FaultSystemSolutionFetcher fetch) {
		if (fetch instanceof CompoundFaultSystemSolution)
			fetch = new HazardOptimizedCFSS((CompoundFaultSystemSolution)fetch);
		this.fetch = fetch;
		
		Preconditions.checkState(fetch != null && !fetch.getBranches().isEmpty());
		branch0 = fetch.getBranches().iterator().next();
		
		enumParamsMap = new HashMap<>();
		
		// build enum paramters, allow every option in the fetcher
		Collection<LogicTreeBranch> branches = fetch.getBranches();
		List<Class<? extends LogicTreeBranchNode<?>>> logicTreeNodeClasses = LogicTreeBranch.getLogicTreeNodeClasses();
		for (int i=0; i < logicTreeNodeClasses.size(); i++) {
			Class<? extends LogicTreeBranchNode<?>> clazz = logicTreeNodeClasses.get(i);
			EnumParameter<?> param = buildParam(clazz, branches);
			param.addParameterChangeListener(this);
			enumParamsMap.put(clazz, param);
		}
		
		// just for parameter instantiation
		paramERF = new FaultSystemSolutionERF();
		// default to exclude back seis (will be much faster)
		paramERF.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
		createParamList();
	}
	
	private HashSet<Parameter<?>> listeningParams = new HashSet<>();
	
	private void createParamList() {
		paramList = new ParameterList();
		
		for (Parameter<?> param : paramERF.getAdjustableParameterList()) {
			if (!param.getName().equals(FaultSystemSolutionERF.FILE_PARAM_NAME)) {
				if (!listeningParams.contains(param)) {
					param.addParameterChangeListener(this);
					listeningParams.add(param);
				}
				paramList.addParameter(param);
			}
		}
		
		for (Class<? extends LogicTreeBranchNode<?>> clazz : LogicTreeBranch.getLogicTreeNodeClasses()) {
			EnumParameter<?> param = enumParamsMap.get(clazz);
			if (param != null)
				paramList.addParameter(param);
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static EnumParameter buildParam(
			Class<? extends LogicTreeBranchNode<?>> clazz, Collection<LogicTreeBranch> branches) {
		HashSet<Enum> set = new HashSet<Enum>();
		
		Enum defaultValue = null;
		
		String name = null;
		
		for (LogicTreeBranch branch : branches) {
			Preconditions.checkState(branch.isFullySpecified());
			LogicTreeBranchNode<?> val = branch.getValueUnchecked(clazz);
			Preconditions.checkNotNull(val);
			set.add((Enum)val);
			if (name == null)
				name = val.getBranchLevelName();
		}
		
		Preconditions.checkState(!set.isEmpty());
		EnumSet choices = EnumSet.copyOf(set);

		if (set.size() == 1) {
			defaultValue = set.iterator().next();
			return new EnumParameter(name, choices, defaultValue, null);
		}
		
		return new EnumParameter(name, choices, defaultValue, LOGIC_TREE_BRANCH_ALL_NAME);
	}
	
	private synchronized void updateBranches() {
		if (branches != null)
			return;
		branches = new ArrayList<>();
		for (LogicTreeBranch branch : fetch.getBranches()) {
			boolean keep = true;
			for (Class<? extends LogicTreeBranchNode<?>> clazz : enumParamsMap.keySet()) {
				EnumParameter<?> enumParam = enumParamsMap.get(clazz);
				LogicTreeBranchNode<?> node = (LogicTreeBranchNode<?>) enumParam.getValue();
				if (node != null) {
					if (!branch.getValueUnchecked(clazz).equals(node)) {
						keep = false;
						break;
					}
 				}
			}
			if (keep)
				branches.add(branch);
		}
		System.out.println("Retained "+branches.size()+" branches");
	}

	@Override
	public void updateForecast() {
		updateBranches();
	}

	@Override
	public void setTimeSpan(TimeSpan time) {
		paramERF.setTimeSpan(time);
	}

	@Override
	public TimeSpan getTimeSpan() {
		return paramERF.getTimeSpan();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setParameter(String name, Object value) {
		paramList.getParameter(name).setValue(value);
	}

	@Override
	public ParameterList getAdjustableParameterList() {
		return paramList;
	}

	@Override
	public Region getApplicableRegion() {
		return null;
	}

	@Override
	public ArrayList<TectonicRegionType> getIncludedTectonicRegionTypes() {
		ArrayList<TectonicRegionType> list = new ArrayList<TectonicRegionType>();
		list.add(TectonicRegionType.ACTIVE_SHALLOW);
		return list;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public int compareTo(BaseERF o) {
		return getName().compareToIgnoreCase(o.getName());
	}
	
	@Override
	public ERF getERF(int index) {
		updateBranches();
		LogicTreeBranch branch = branches.get(index);
		return loadERF(branch);
	}
	
	private FaultSystemSolutionERF loadERF(LogicTreeBranch branch) {
		System.out.println("LoadERF for "+branch.buildFileName());
		System.out.println("Loading solution...");
		FaultSystemSolution sol = fetch.getSolution(branch);
		System.out.println("DONE loading");
		
		System.out.println("Building ERF...");
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(sol);
		erf.setCacheGridSources(false);
		for (Parameter<?> param : paramERF.getAdjustableParameterList())
			if (!param.getName().equals(FaultSystemSolutionERF.FILE_PARAM_NAME))
				erf.setParameter(param.getName(), param.getValue());
		erf.setTimeSpan(paramERF.getTimeSpan());
		System.out.println("Updating forecast...");
		erf.updateForecast();
		System.out.println("DONE LoadERF");
		return erf;
	}

	@Override
	public double getERF_RelativeWeight(int index) {
		updateBranches();
		return branches.get(index).getAprioriBranchWt();
	}

	@Override
	public ArrayList<Double> getRelativeWeightsList() {
		updateBranches();
		ArrayList<Double> weights = new ArrayList<>();
		for (LogicTreeBranch branch : branches)
			weights.add(branch.getAprioriBranchWt());
		return weights;
	}

	@Override
	public int getNumERFs() {
		updateBranches();
		return branches.size();
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (enumParamsMap.containsValue(event.getParameter())) {
			branches = null;
		}
		createParamList();
	}
	
	public static void main(String[] args) throws ZipException, IOException {
		UCERF3EpistemicListERF erf = new UCERF3EpistemicListERF();
		
		Stopwatch totalWatch = Stopwatch.createStarted();
		
		erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.INCLUDE);
		erf.setParameter("Fault Model", FaultModels.FM3_1);
		erf.setParameter("Deformation Model", DeformationModels.ZENGBB);
		erf.setParameter("Scaling Relationship", ScalingRelationships.SHAW_2009_MOD);
		
		erf.updateForecast();
		
		HazardCurveCalculator calc = new HazardCurveCalculator();
		
		ScalarIMR gmpe = AttenRelRef.ASK_2014.instance(null);
		gmpe.setParamDefaults();
		Site site = new Site(new Location(34, -118));
		site.addParameterList(gmpe.getSiteParams());
		
		DiscretizedFunc xVals = IMT_Info.getUSGS_SA_Function();
		DiscretizedFunc logXVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : xVals)
			logXVals.set(Math.log(pt.getX()), 0d);
		
		double totCalcSecs = 0;
		double totLoadSecs = 0;
		
		for (int i=0; i<erf.getNumERFs(); i++) {
			Stopwatch buildWatch = Stopwatch.createStarted();
			ERF subERF = erf.getERF(i);
			buildWatch.stop();
			double secs = buildWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
			totLoadSecs += secs;
			System.out.println("Took "+(float)secs+" s to build");
			System.out.println("Calculating...");
			Stopwatch calcWatch = Stopwatch.createStarted();
			calc.getHazardCurve(logXVals, site, gmpe, subERF);
			calcWatch.stop();
			secs = calcWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
			totCalcSecs += secs;
			System.out.println("Took "+(float)secs+" s to calc");
		}
		
		totalWatch.stop();
		System.out.println("TOTAL took "+totalWatch.elapsed(TimeUnit.SECONDS)+" s");
		System.out.println("\t"+(float)totLoadSecs+" s loading");
		System.out.println("\t"+(float)totCalcSecs+" s calculating");
	}

}
