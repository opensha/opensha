package org.opensha.sha.earthquake.rupForecastImpl.nshm23.erf;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import javax.swing.JOptionPane;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_Downloader;
import org.opensha.sha.util.TectonicRegionType;

/**
 * USGS 2023 NSHM ERF, Western U.S., Branch Averaged ERF
 */
public class NSHM23_BranchAveragedERF extends BaseFaultSystemSolutionERF {
	
	// TODO updated UID
	private static final long serialVersionUID = 277613161331416141L;
	
	// TODO updated default to R2 when file is posted
	public static final ModelVersions MODEL_DEFAULT = ModelVersions.WUS_R1;
	public static final String NAME = "NSHM23 Branch Averaged ERF";
	private static final boolean D = false;
	
	private NSHM23_Downloader downloader;
	
	public static final String MODEL_PARAM_NAME = "Model Version";
	private EnumParameter<ModelVersions> modelParam;
	private ModelVersions model;
	
	public enum ModelVersions {
		WUS_R2("WUS-Crustal R2 (excl. Cascadia)", "WUS_branch_averaged_gridded_simplified_R2",
				"""
				Western U.S. crustal sources west of lon=-105, including active crustal fault sources and both active and \
				stable gridded seismicity sources. Cascadia subduction interface and intraslab seismicity are excluded.
				
				This is revision 2 of the model described in Powers et al. (2026), primarily updated to reclassify stable \
				continental gridded seismicity in the Intermountain West as 2/3 active crustal and 1/3 stable continental. \
				This version also contains minor updates to the rounding of crustal rupture rakes, and updates stable \
				continental gridded seismicity rupture properties to match thosed used in NSHMP-Haz.""",
				EnumSet.of(TectonicRegionType.ACTIVE_SHALLOW, TectonicRegionType.STABLE_SHALLOW)),
		
		WUS_R1("WUS-Crustal R1 (excl. Cascadia)", "WUS_branch_averaged_gridded_simplified",
				"""
				Western U.S. crustal sources west of lon=-105, including active crustal fault sources and both active and \
				stable gridded seismicity sources. Cascadia subduction interface and intraslab seismicity are excluded.
				
				This is the original published NSHM23 model.""",
				EnumSet.of(TectonicRegionType.ACTIVE_SHALLOW, TectonicRegionType.STABLE_SHALLOW));
		
		public final String displayName;
		public final String prefix;
		public final String metadata;
		public final EnumSet<TectonicRegionType> trts;

		private ModelVersions(String displayName, String prefix, String metadata, EnumSet<TectonicRegionType> trts) {
			this.displayName = displayName;
			this.prefix = prefix;
			this.metadata = metadata;
			this.trts = trts;
		}
	}
	
	/**
	 * Noarg constructor uses default storeDir for NSHM23 files
	 * (Recommended Constructor)
	 */
	public NSHM23_BranchAveragedERF() {
		this(/*storeDir=*/null, MODEL_DEFAULT); // Use default storeDir
	}
	
	/**
	 * Noarg constructor uses default storeDir for NSHM23 files
	 * (Recommended Constructor)
	 */
	public NSHM23_BranchAveragedERF(ModelVersions model) {
		this(/*storeDir=*/null, model); // Use default storeDir
	}
	
	/**
	 * Allow specifying where to download files
	 * @param storeDir
	 */
	public NSHM23_BranchAveragedERF(File storeDir, ModelVersions model) {
		super(false); // false here means don't do the init calls yet, we do them at the end
		if (storeDir == null) {
			this.downloader = new NSHM23_Downloader();
		} else {
			this.downloader = new NSHM23_Downloader(storeDir);
		}
		this.setName(NAME);
		
		modelParam = new EnumParameter<>(MODEL_PARAM_NAME, EnumSet.allOf(ModelVersions.class), model, null);
		modelParam.addParameterChangeListener((e) -> { modelChanged();});
		modelChanged();
		
		initParams();
		initTimeSpan();
	}
	
	private void modelChanged() {
		this.model = modelParam.getValue();
		// set the TRTs explicitly so that getIncludedTectonicRegionTypes() knows them without updating the forecast first
		this.erfTRTs = model.trts;
		fireTRTChangeEvent();
	}
	
	/**
	 * Put parameters in the ParameterList
	 */
	@Override
	protected void postCreateParamListHook() {
		super.postCreateParamListHook();
		if (adjustableParams.containsParameter(FILE_PARAM_NAME))
			adjustableParams.removeParameter(fileParam);
		adjustableParams.addParameter(0, modelParam);
		
	}
	
	/**
	 * Loads the latest solution available for download
	 */
	private void fetchSolution() {
		downloader.updateFile(model.prefix).thenAccept(solFile -> {
			try {
				if (solFile == null || !solFile.exists()) {
					JOptionPane.showMessageDialog(null,
							"Failed to download " + model.prefix +
							". Verify internet connection and restart. Server may be down.",
							"NSHM23_WUS_BranchAveragedERF", JOptionPane.ERROR_MESSAGE);
				} else {
					FaultSystemSolution sol = FaultSystemSolution.load(solFile);
					setSolution(sol);
				}
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}).join();
	}

	/**
	 * Ensure our solution is fetched and loaded and then update the forecast.
	 * Only checks for newer models if not already loaded in this session.
	 */
	@Override
	public void updateForecast() {
		if (D) System.out.println("NSHM23_WUS_BranchAveragedERF.updateForecast()");
		if (getSolution() == null) {
			fetchSolution();
		}
		super.updateForecast();
	}
	
	public static void main(String[] args) {
		new NSHM23_BranchAveragedERF(ModelVersions.WUS_R1).updateForecast();
	}
}
