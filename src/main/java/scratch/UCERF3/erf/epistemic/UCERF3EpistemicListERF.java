package scratch.UCERF3.erf.epistemic;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;

import javax.swing.JOptionPane;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EpistemicListERF;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.gui.HazardCurveApplication;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.mean.MeanUCERF3;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.UCERF3_Downloader;

public class UCERF3EpistemicListERF implements EpistemicListERF, ParameterChangeListener {
	
	public static final String NAME = "UCERF3 Epistemic List ERF";
	
	private CompletableFuture<SolutionLogicTree> fetchFuture;
	private LogicTree<?> tree;
	
	private FaultSystemSolutionERF paramERF;
	private ParameterList paramList;
	
	private static final String LOGIC_TREE_BRANCH_ALL_NAME = "(all)";
	
	private Map<LogicTreeLevel<? extends LogicTreeNode>, EnumParameter<?>> enumParamsMap;
	
	private List<LogicTreeBranch<?>> branches;
	
	private static final String COMPOUND_FILE_NAME = "full_logic_tree.zip";
	
	private static CompletableFuture<SolutionLogicTree> loadFetcher() throws ZipException, IOException {
		File storeDir = UCERF3_Downloader.getStoreDir();
		// allow errors so that app doesn't crash if can't download
		return MeanUCERF3.checkDownload(new File(storeDir, COMPOUND_FILE_NAME))
			.thenApply(treeFile -> {
			if (treeFile == null || !treeFile.exists()) {
				JOptionPane.showMessageDialog(null,
						"Failed to download " + COMPOUND_FILE_NAME +
						". Verify internet connection and restart. Server may be down.",
						"UCERF3EpistemicListERF", JOptionPane.ERROR_MESSAGE);
				return null;
			}
			try {
				return new ModuleArchive<>(treeFile, SolutionLogicTree.class)
						.requireModule(SolutionLogicTree.class);
			} catch (IOException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null, e.getMessage(),
						"UCERF3EpistemicListERF", JOptionPane.ERROR_MESSAGE);
				return null;
			}
		});
	}

	public UCERF3EpistemicListERF() throws ZipException, IOException {
		this(loadFetcher());
	}

	public UCERF3EpistemicListERF(CompletableFuture<SolutionLogicTree> fetchFuture) {
		this.fetchFuture = fetchFuture;
		this.enumParamsMap = new HashMap<>();
		fetchFuture.thenAccept(fetch -> {
			this.tree = fetch.getLogicTree().sorted(new ReadOptimizedBranchComparator());
			Preconditions.checkState(fetch != null && !tree.getBranches().isEmpty());
			// build enum parameters, allow every option in the fetcher
			for (LogicTreeLevel<?> level : tree.getLevels()) {
				EnumParameter<?> param = buildParam(tree, level);
				param.addParameterChangeListener(this);
				enumParamsMap.put(level, param);
			}
		});
		// just for parameter instantiation
		this.paramERF = new FaultSystemSolutionERF();
		// default to exclude back seis (will be much faster)
		paramERF.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
		createParamList();
	}
	
	private class ReadOptimizedBranchComparator implements Comparator<LogicTreeBranch<? extends LogicTreeNode>> {
		
		List<Class<? extends U3LogicTreeBranchNode<?>>> sortOrderClasses;
		
		public ReadOptimizedBranchComparator() {
			sortOrderClasses = new ArrayList<>();
			// default order is ideal
			sortOrderClasses.addAll(U3LogicTreeBranch.getLogicTreeNodeClasses());
		}

		@Override
		public int compare(LogicTreeBranch<? extends LogicTreeNode> b1, LogicTreeBranch<? extends LogicTreeNode> b2) {
			Preconditions.checkState(b1.size() == sortOrderClasses.size());
			Preconditions.checkState(b2.size() == sortOrderClasses.size());
			for (Class<? extends U3LogicTreeBranchNode<?>> clazz : sortOrderClasses) {
				LogicTreeNode val = b1.getValueUnchecked(clazz);
				LogicTreeNode oval = b2.getValueUnchecked(clazz);
				int cmp = val.getShortName().compareTo(oval.getShortName());
				if (cmp != 0)
					return cmp;
			}
			return 0;
		}
		
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
		
		fetchFuture.thenRun(() -> {
			for (LogicTreeLevel<?> level : tree.getLevels()) {
				EnumParameter<?> param = enumParamsMap.get(level);
				if (param != null)
					paramList.addParameter(param);
			}
		});
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static EnumParameter buildParam(LogicTree<?> tree, LogicTreeLevel<?> level) {
		HashSet<Enum> set = new HashSet<Enum>();
		
		Enum defaultValue = null;
		
		String name = level.getName();
		
		for (LogicTreeBranch<?> branch : tree) {
			Preconditions.checkState(branch.isFullySpecified());
			LogicTreeNode val = branch.getValueUnchecked(level.getType());
			Preconditions.checkNotNull(val);
			set.add((Enum)val);
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
		fetchFuture.thenRun(() -> {
			if (branches != null)
				return;
			branches = new ArrayList<>();
			for (LogicTreeBranch<?> branch : tree) {
				boolean keep = true;
				for (LogicTreeLevel<? extends LogicTreeNode> level : enumParamsMap.keySet()) {
					EnumParameter<?> enumParam = enumParamsMap.get(level);
					LogicTreeNode node = (LogicTreeNode) enumParam.getValue();
					if (node != null) {
						if (!branch.getValue(level.getType()).equals(node)) {
							keep = false;
							break;
						}
					 }
				}
				if (keep)
					branches.add(branch);
			}
			System.out.println("Retained "+branches.size()+" branches");
		});
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
		fetchFuture.join();
		LogicTreeBranch<?> branch = branches.get(index);
		// EpistemicListERF	interface requires evaluation here. As lazy as it gets.
		try {
			return loadERF(branch).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private CompletableFuture<FaultSystemSolutionERF> loadERF(LogicTreeBranch<?> branch) {
		System.out.println("LoadERF for "+branch.buildFileName());
		System.out.println("Loading solution...");
		return fetchFuture.thenApply(fetch -> {
			FaultSystemSolution sol;
			try {
				sol = fetch.forBranch(branch);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
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
		});
	}

	@Override
	public double getERF_RelativeWeight(int index) {
		updateBranches();
		return tree.getBranchWeight(branches.get(index));
	}

	@Override
	public ArrayList<Double> getRelativeWeightsList() {
		updateBranches();
		ArrayList<Double> weights = new ArrayList<>();
		for (LogicTreeBranch<?> branch : branches)
			weights.add(tree.getBranchWeight(branch));
		return weights;
	}

	@Override
	public int getNumERFs() {
		try {
			fetchFuture.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
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
//		UCERF3EpistemicListERF erf = new UCERF3EpistemicListERF(new SolutionLogicTree.UCERF3(
//				CompoundFaultSystemSolution.fromZipFile(
//						new File("/home/kevin/.opensha/ucerf3_erf/full_ucerf3_compound_sol.zip"))));
		
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
