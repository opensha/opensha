package org.opensha.sha.earthquake.rupForecastImpl.nshm23.erf;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import javax.swing.JOptionPane;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.MultilineStringParameter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_Downloader;
import org.opensha.sha.util.TectonicRegionType;

/**
 * USGS 2023 NSHM Branch Averaged ERF. This ERF supports multiple model versions, which can represent revisions to the
 * model or different regions (e.g., WUS vs full CONUS, Alaska, Cascadia).
 */
public class NSHM23_BranchAveragedERF extends BaseFaultSystemSolutionERF {
	
	private static final long serialVersionUID = 2L;
	
	public static final ModelVersions MODEL_DEFAULT = ModelVersions.WUS_R2;
	public static final String NAME = "USGS NSHM23 - Branch Averaged ERF";
	private static final boolean D = false;
	
	private NSHM23_Downloader downloader;
	
	public static final String MODEL_PARAM_NAME = "Model Version";
	private EnumParameter<ModelVersions> modelParam;
	private ModelVersions model;
	
	private static final String METADATA_PARAM_NAME = "Model Information";
	private MultilineStringParameter modelInfoParam;
	
	public enum ModelVersions {
		WUS_R2("WUS-Crustal R2 (excluding Cascadia)", "WUS_branch_averaged_gridded_simplified_R2",
				"""
				Revision 2 of western U.S. crustal sources (longitude<-105), including active crustal fault sources and \
				both active and stable gridded seismicity sources. Cascadia subduction interface and intraslab seismicity \
				are excluded.
				
				This is revision 2 of the model described in Powers et al. (2026), primarily updated to reclassify stable \
				continental gridded seismicity in the Intermountain West as 2/3 active crustal and 1/3 stable continental. \
				This version also contains minor updates to the rounding of crustal rupture rakes, and updates stable \
				continental gridded seismicity rupture properties to match those used in NSHMP-Haz.""",
				EnumSet.of(TectonicRegionType.ACTIVE_SHALLOW, TectonicRegionType.STABLE_SHALLOW)),
		
		WUS_R1("WUS-Crustal R1 (excluding Cascadia)", "WUS_branch_averaged_gridded_simplified",
				"""
				Western U.S. crustal sources (longitude<-105), including active crustal fault sources and both active and \
				stable gridded seismicity sources. Cascadia subduction interface and intraslab seismicity are excluded.
				
				This is the original published NSHM23 model as implemented and released in OpenSHA (revision 1). Note \
				that a subsequent revision (R2) includes additional tweaks to better align with the official USGS \
				implementation, including rounding of rake values (which can affect rupture fault-style assignment) \
				and updated stable continental gridded seismicity rupture properties (Ztor, Zhyp).""",
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

		@Override
		public String toString() {
			return displayName;
		}
	}
	
	/**
	 * Convenience method to download (if necessary) and load the given model
	 * 
	 * @param storeDir
	 * @param model
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemSolution loadSolution(File storeDir, ModelVersions model) throws IOException {
		File file = new NSHM23_Downloader(storeDir).updateFile(model.prefix).join();
		return FaultSystemSolution.load(file);
	}
	
	/**
	 * Convenience method to download (if necessary) and load the given model
	 * 
	 * @param model
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemSolution loadSolution(ModelVersions model) throws IOException {
		File file = new NSHM23_Downloader().updateFile(model.prefix).join();
		return FaultSystemSolution.load(file);
	}
	
	/**
	 * Noarg constructor uses default storeDir for NSHM23 files and default NSHM23 model
	 * (Recommended Constructor)
	 */
	public NSHM23_BranchAveragedERF() {
		this(/*storeDir=*/null, MODEL_DEFAULT); // Use default storeDir and model
	}
	
	/**
	 * Constructor uses default storeDir for NSHM23 files and allows specifying model version
	 * @param model
	 */
	public NSHM23_BranchAveragedERF(ModelVersions model) {
		this(/*storeDir=*/null, model); // Use default storeDir
	}
	
	/**
	 * Allow specifying where to download files and the model version
	 * @param storeDir
	 * @param model
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
		
		modelInfoParam = new MultilineStringParameter(METADATA_PARAM_NAME);
		modelInfoParam.setTextEditable(false); // this is display-only metadata
		modelInfoParam.setRows(7); // make it a little longer in the apps
		modelInfoParam.setIncludedInMetadata(false); // don't include in the parameter value metadata strings
		
		initParams();
		initTimeSpan();
		
		modelChanged();
	}
	
	private void modelChanged() {
		setSolution(null);
		this.model = modelParam.getValue();
		// set the TRTs explicitly so that getIncludedTectonicRegionTypes() knows them without updating the forecast first
		this.erfTRTs = model.trts;
		fireTRTChangeEvent();
		
		modelInfoParam.setValue(model.metadata);
		modelInfoParam.refreshEditor();
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
		adjustableParams.addParameter(1, modelInfoParam);
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
							"NSHM23_BranchAveragedERF", JOptionPane.ERROR_MESSAGE);
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
		if (D) System.out.println("NSHM23_BranchAveragedERF.updateForecast()");
		if (getSolution() == null) {
			fetchSolution();
		}
		super.updateForecast();
	}
	
	public static void main(String[] args) {
		new NSHM23_BranchAveragedERF(ModelVersions.WUS_R1).updateForecast();
	}
}
