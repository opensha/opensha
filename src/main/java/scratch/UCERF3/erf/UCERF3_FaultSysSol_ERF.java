package scratch.UCERF3.erf;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;


/**
 * This extends FaultSystemSolutionPoissonERF to include UCERF3 background seismicity as given
 * by FaultSystemSolution.getGridSourceProvider().
 * 
 * @author field & powers
 *
 */
@Deprecated
public class UCERF3_FaultSysSol_ERF extends FaultSystemSolutionPoissonERF {

	private GridSourceProvider gridSources;
	public static final String NAME = "UCERF3 Poisson ERF";
	private String name = NAME;
	
	/**
	 * No-arg constructor. This sets ERF to include background sources.
	 * All other parameters are as defaults.
	 */
	public UCERF3_FaultSysSol_ERF() {
		bgIncludeParam.setValue(IncludeBackgroundOption.INCLUDE);
	}
	
	/**
	 * Constructs a new ERF using the supplied {@code file}. {@code File} must
	 * be a zipped up fault system solution.
	 * @param file
	 */
	public UCERF3_FaultSysSol_ERF(File file) {
		bgIncludeParam.setValue(IncludeBackgroundOption.INCLUDE);
		fileParam.setValue(file);
	}
	
	/**
	 * Constructs a new ERF using an {@code FaultSystemSolution}.
	 * @param faultSysSolution
	 */
	public UCERF3_FaultSysSol_ERF(FaultSystemSolution faultSysSolution) {
		super(faultSysSolution);
		bgIncludeParam.setValue(IncludeBackgroundOption.INCLUDE);
	}
		
	@Override
	protected ProbEqkSource getOtherSource(int iSource) {
		return gridSources.getSource(iSource, timeSpan.getDuration(),
			applyAftershockFilter, bgRupType);
	}

	@Override
	protected boolean initOtherSources() {
		if(bgRupTypeChanged) {
			gridSources = getSolution().getGridSourceProvider();
			numOtherSources = gridSources.size();
			return true;
		}
		else {
			return false;
		}
	}	
	
	/**
	 * Sets the erf name. For UCERF3 erf this will commonly be the branch
	 * identifier string or similar.
	 * @param name
	 */
	public void setName(String name) {
		checkArgument(!StringUtils.isBlank(name), "Name cannot be empty");
		this.name = name;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
//		String f = "/Users/pmpowers/projects/OpenSHA/tmp/invSols/refGR/FM3_1_NEOK_EllB_DsrUni_GRConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_run5_sol.zip";
//		String f = "/Users/pmpowers/projects/OpenSHA/tmp/invSols/refCH/FM3_1_NEOK_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_run5_sol.zip";
		String f ="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";

		File file = new File(f);
				
		UCERF3_FaultSysSol_ERF erf = new UCERF3_FaultSysSol_ERF();
		erf.getParameter(FILE_PARAM_NAME).setValue(file);
		
		erf.getParameter(BackgroundRupParam.NAME).setValue(BackgroundRupType.CROSSHAIR);
		erf.updateForecast();
		
		erf.getParameter(AleatoryMagAreaStdDevParam.NAME).setValue(0.12);
		erf.updateForecast();
		


//		UCERF3_FaultSysSol_ERF erf = FaultSysSolutionERF_Calc.getUCERF3_ERF_Instance(file, SpatialSeisPDF.AVG_DEF_MODEL_OFF,SmallMagScaling.MO_REDUCTION);
//		int otherRups = 0;
//		for (int i=0; i<erf.gridSources.size(); i++) {
//			ProbEqkSource src = erf.gridSources.getSource(i, 1d, false, false);
//			otherRups += src.getNumRuptures();
//		}
//		System.out.println("NumOtherRups: " + otherRups);
//		System.out.println("src100rups: " + erf.getSource(100).getNumRuptures());
//		System.out.println();
	}

}
