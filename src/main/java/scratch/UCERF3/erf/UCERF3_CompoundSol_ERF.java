package scratch.UCERF3.erf;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipException;

import javax.swing.JOptionPane;

import scratch.UCERF3.U3CompoundFaultSystemSolution;
import scratch.UCERF3.U3FaultSystemSolutionFetcher;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.erf.mean.MeanUCERF3;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;

import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * This is a Single Branch UCERF3 ERF which loads solutions from a compound fault system solution.
 * If not already cached locally, the (large) compound solution file will be downloaded from
 * opensha.usc.edu.
 * 
 * @author kevin
 *
 */
public class UCERF3_CompoundSol_ERF extends FaultSystemSolutionERF {
	
	private static final long serialVersionUID = 1L;

	private static final boolean D = false;
	
	public static final String NAME = "UCERF3 Single Branch ERF";
	
	private Map<Class<? extends U3LogicTreeBranchNode<?>>, EnumParameter<?>> enumParamsMap;
	
	private U3FaultSystemSolutionFetcher fetch;
	
	private boolean solutionStale = true;
	
	private static final String COMPOUND_FILE_NAME = "full_ucerf3_compound_sol.zip";
	
	private CompletableFuture<U3FaultSystemSolutionFetcher> fetchFuture;
	
	private static CompletableFuture<U3FaultSystemSolutionFetcher> loadFetcher() throws ZipException, IOException {
		File storeDir = MeanUCERF3.getStoreDir();
		return MeanUCERF3.checkDownload(new File(storeDir, COMPOUND_FILE_NAME))
			.thenApply(compoundFile -> {
			if (compoundFile == null || !compoundFile.exists()) {
				JOptionPane.showMessageDialog(null,
						"Failed to download " + COMPOUND_FILE_NAME +
						". Verify internet connection and restart. Server may be down.",
						"UCERF3_CompoundSol_ERF", JOptionPane.ERROR_MESSAGE);
				return null;
			}
			try {
				return U3CompoundFaultSystemSolution.fromZipFile(compoundFile);
			} catch (IOException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null, e.getMessage(),
						"UCERF3_CompoundSol_ERF", JOptionPane.ERROR_MESSAGE);
				return null;
			}
		});
	}
	
	/**
	 * Default constructor will download or load locally cached default compound solution
	 * 
	 * @throws ZipException
	 * @throws IOException
	 */
	public UCERF3_CompoundSol_ERF() throws ZipException, IOException {
		this(loadFetcher(), null);
	}
	
	/**
	 * Constructor for already loaded fault system solution fetcher (usually a CompoundFaultSystemSolution)
	 * 
	 * @param fetch
	 * @param initial
	 */
	public UCERF3_CompoundSol_ERF(CompletableFuture<U3FaultSystemSolutionFetcher> fetchFuture, U3LogicTreeBranch initial) {
		this.fetchFuture = fetchFuture;
		fetchFuture.thenAccept(fetch -> {
			enumParamsMap = Maps.newHashMap();
			Preconditions.checkState(initial == null || initial.isFullySpecified(),
				"Initial branch must be null or fully specified");
			if (fetch != null && !fetch.getBranches().isEmpty()) {
				// build enum paramters, allow every option in the fetcher
				// note that not-present combinations may still be possible
				Collection<U3LogicTreeBranch> branches = fetch.getBranches();
				List<Class<? extends U3LogicTreeBranchNode<?>>> logicTreeNodeClasses = U3LogicTreeBranch.getLogicTreeNodeClasses();
				for (int i=0; i < logicTreeNodeClasses.size(); i++) {
					Class<? extends U3LogicTreeBranchNode<?>> clazz = logicTreeNodeClasses.get(i);
					EnumParameter<?> param = buildParam(clazz, branches, initial);
					param.addParameterChangeListener(this);
					enumParamsMap.put(clazz, param);
				}
			}
			createParamList();
		});
	}
	
	@Override
	protected void createParamList() {
		super.createParamList();
		if (enumParamsMap == null)
			return;
		List<Class<? extends U3LogicTreeBranchNode<?>>> logicTreeNodeClasses = U3LogicTreeBranch.getLogicTreeNodeClasses();
		for (int i=0; i < logicTreeNodeClasses.size(); i++) {
			Class<? extends U3LogicTreeBranchNode<?>> clazz = logicTreeNodeClasses.get(i);
			EnumParameter<?> param = enumParamsMap.get(clazz);
			if (param != null)
				adjustableParams.addParameter(i, param);
		}
		if (adjustableParams.containsParameter(FILE_PARAM_NAME))
			adjustableParams.removeParameter(fileParam);
	}
	
	public void setLogicTreeBranch(U3LogicTreeBranch branch) {
		fetchFuture.thenAccept(fetch -> {
			Preconditions.checkArgument(branch.isFullySpecified(), "Branch must be fully specified");
			Preconditions.checkArgument(fetch.getBranches().contains(branch), "Branch not present in compound solution");
			
			for (U3LogicTreeBranchNode<? extends Enum<?>> node : branch)
				setParameter(node.getBranchLevelName(), node);
		});
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static EnumParameter buildParam(
			Class<? extends U3LogicTreeBranchNode<?>> clazz, Collection<U3LogicTreeBranch> branches,
			U3LogicTreeBranch initial) {
		HashSet<Enum> set = new HashSet<Enum>();
		
		Enum defaultValue;
		if (initial != null)
			defaultValue = (Enum) initial.getValueUnchecked(clazz);
		else
			defaultValue = null;
		
		String name = null;
		
		for (U3LogicTreeBranch branch : branches) {
			Preconditions.checkState(branch.isFullySpecified());
			U3LogicTreeBranchNode<?> val = branch.getValueUnchecked(clazz);
			Preconditions.checkNotNull(val);
			set.add((Enum)val);
			if (defaultValue == null)
				defaultValue = (Enum)val;
			if (name == null)
				name = val.getBranchLevelName();
		}
		
		EnumSet choices = EnumSet.copyOf(set);
		
		return new EnumParameter(name, choices, defaultValue, null);
	}
	
	@Override
	public void updateForecast() {
		System.out.println("updateForecast called");
		if (solutionStale) {
			// this means that we have to load the solution (parameter change or never loaded)
			fetchSolution();
		}
		fetchFuture.join();
		super.updateForecast();
	}
	
	private void fetchSolution() {
		fetchFuture.thenAccept(fetch -> {
			if (fetch == null)
				return;
			
			List<U3LogicTreeBranchNode<?>> vals = Lists.newArrayList();
			for (EnumParameter<?> param : enumParamsMap.values()) {
				vals.add((U3LogicTreeBranchNode<?>) param.getValue());
			}
			U3LogicTreeBranch branch = U3LogicTreeBranch.fromValues(vals);
			Preconditions.checkState(branch.isFullySpecified(), "Somehow branch from enums isn't fully specified");
			
			FaultSystemSolution sol = fetch.getSolution(branch);
			setSolution(sol);
			solutionStale = false;
		});
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		// check if it's one of our enum params
		if (enumParamsMap != null && enumParamsMap.values().contains(event.getParameter())) {
			solutionStale = true;
		} else {
			super.parameterChange(event);
		}
	}

	public static void main(String[] args) {
		UCERF3_CompoundSol_ERF erf;
		try {
			erf = new UCERF3_CompoundSol_ERF();
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
//		FM3_1_ZENGBB_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.9_NoFix_SpatSeisU2
		U3LogicTreeBranch branch = U3LogicTreeBranch.fromValues(FaultModels.FM3_1, DeformationModels.ZENGBB,
				ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.UNIFORM, InversionModels.CHAR_CONSTRAINED,
				TotalMag5Rate.RATE_7p9, MaxMagOffFault.MAG_7p9, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2);
		erf.setLogicTreeBranch(branch);
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
		erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
		erf.setParameter(HistoricOpenIntervalParam.NAME, (double)(FaultSystemSolutionERF.START_TIME_DEFAULT-1875));
		MagDependentAperiodicityOptions[] testCOVs = {MagDependentAperiodicityOptions.LOW_VALUES,
				MagDependentAperiodicityOptions.MID_VALUES, MagDependentAperiodicityOptions.HIGH_VALUES};
		for (BPTAveragingTypeOptions aveType : BPTAveragingTypeOptions.values()) {
			erf.setParameter(BPTAveragingTypeParam.NAME, aveType);
			for (MagDependentAperiodicityOptions cov : testCOVs) {
				erf.setParameter(MagDependentAperiodicityParam.NAME, cov);
				erf.updateForecast();
				System.out.println("Testing "+aveType.name()+", "+cov.name()+", "
						+((InversionFaultSystemSolution)erf.getSolution()).getLogicTreeBranch());
				for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
					ProbEqkSource source = erf.getSource(sourceID);
					for (int rupID=0; rupID<source.getNumRuptures(); rupID++)
						Preconditions.checkState(!Double.isNaN(source.getRupture(rupID).getProbability()),
								"Source "+sourceID+", Rup "+rupID+" is NaN");
				}
			}
		}
	}

}
