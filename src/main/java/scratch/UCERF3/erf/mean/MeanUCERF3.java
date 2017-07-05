package scratch.UCERF3.erf.mean;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import javax.swing.JOptionPane;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.gui.infoTools.CalcProgressBar;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.LastEventData;
import scratch.UCERF3.utils.UCERF3_DataUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * This is the MeanUCERF3 ERF. It extends UCERF3_FaultSysSol_ERF, but allows and facilitates creation
 * of solution subsets with different approximations:
 * <br>
 * <br><b>Upper Depth Tolerance:</b>
 * <br>Some sections have varying upper depths on different branches due to variable aseismicity values.
 * This parameter combines upper depths within the given tolerance to reduce the subsection (and thus
 * rupture) count. The "Use Mean Upper Depth" parameter allows you to use the average upper depth
 * (default uses the shallower depth).
 * <br><b>Magnitude Tolerance:</b>
 * <br>Ruptures have varying magnitudes on different branches due to different scaling relationships
 * and even variable aseismicity values. This parameter averages magnitudes within the given tolerance
 * to reduce the ruptures substantially. <b>NOTE: enabling aleatory magnitude variability will first
 * average magnitudes for each rupture!<b>
 * <br><b>Rake Combining:</b>
 * <br>Each Deformation Model supplies its own rakes. You can either use the DM specific rakes for each
 * rupture, or combine otherwise identical ruptures. In this case you can use the weighted averaged
 * rake or the rakes from a specific deformation model.
 * <br><br>
 * @author kevin
 *
 */
public class MeanUCERF3 extends FaultSystemSolutionERF {
	
	private static final boolean D = true;
	
	public static final String NAME = "Mean UCERF3";
	
	static final String DOWNLOAD_URL = "http://"+ServerPrefUtils.SERVER_PREFS.getHostName()+"/ftp/ucerf3_erf/";
	static final String RAKE_BASIS_FILE_NAME = "rake_basis.zip";
	static final String TRUE_MEAN_FILE_NAME = "mean_ucerf3_sol.zip";
	
	private EnumParameter<Presets> presetsParam;
	
	public static final String UPPER_DEPTH_TOL_PARAM_NAME = "Sect Upper Depth Averaging Tolerance";
	public static final double UPPER_DEPTH_TOL_MIN = 0d;
	public static final double UPPER_DEPTH_TOL_MAX = 100d;
	private DoubleParameter upperDepthTolParam;
	private double upperDepthTol;
	
	private BooleanParameter upperDepthUseMeanParam;
	private boolean upperDepthUseMean;
	
	private DoubleParameter magTolParam;
	private double magTol;
	
	private StringParameter rakeBasisParam;
	private String rakeBasisStr;
	public static final String RAKE_BASIS_NONE = "Do Not Combine";
	public static final String RAKE_BASIS_MEAN = "Def. Model Mean";
	private Map<Set<String>, Double> rakeBasis;
	
	public static final String FAULT_MODEL_PARAM_NAME = "Fault Model(s)";
	private StringParameter faultModelParam;
	private String faultModelStr;
	public static final String FAULT_MODEL_BOTH = "Both";
	
	private BooleanParameter ignoreCacheParam;
	private boolean ignoreCache;
	
	private File storeDir;
	private FaultSystemSolution meanTotalSol;
	private DiscretizedFunc[] meanTotalMFDs;
	
	private Map<Integer, List<LastEventData>> lastEventData;
	
	public static boolean show_progress = true;
	
	public enum Presets {
		FM3_1_BRANCH_AVG("FM3.1 Branch Averaged", FaultModels.FM3_1, UPPER_DEPTH_TOL_MAX, true, 1d, RAKE_BASIS_MEAN),
		FM3_1_MAG_VAR("FM3.1 BA w/ Alt Mags", FaultModels.FM3_1, UPPER_DEPTH_TOL_MAX, true, 0d, RAKE_BASIS_MEAN),
		FM3_2_BRANCH_AVG("FM3.2 Branch Averaged", FaultModels.FM3_2, UPPER_DEPTH_TOL_MAX, true, 1d, RAKE_BASIS_MEAN),
		FM3_2_MAG_VAR("FM3.2 BA w/ Alt Mags", FaultModels.FM3_2, UPPER_DEPTH_TOL_MAX, true, 0d, RAKE_BASIS_MEAN),
		BOTH_FM_BRANCH_AVG("(POISSON ONLY) Both FM Branch Averaged", null, UPPER_DEPTH_TOL_MAX, true, 1d, RAKE_BASIS_MEAN),
		BOTH_FM_MAG_VAR("(POISSON ONLY) Both FM BA w/ Alt Mags", null, UPPER_DEPTH_TOL_MAX, true, 0d, RAKE_BASIS_MEAN),
		COMPLETE_MODEL("(POISSON ONLY LARGE MEM) Complete Model", null, 0d, true, 0d, RAKE_BASIS_NONE);
		
		private String name;
		private FaultModels fm;
		private double upperDepthTol;
		private boolean upperDepthUseMean;
		private double magTol;
		private String rakeBasisStr;
		
		private Presets(String name, FaultModels fm, double upperDepthTol, boolean upperDepthUseMean,
				double magTol, String rakeBasisStr) {
			this.name = name;
			this.fm = fm;
			this.upperDepthTol = upperDepthTol;
			this.upperDepthUseMean = upperDepthUseMean;
			this.magTol = magTol;
			this.rakeBasisStr = rakeBasisStr;
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
	
	public static File getStoreDir() {
		// first check user prop
		String path = System.getProperty("uc3.store");
		if (path != null) {
			File file = new File(path);
			if (!file.exists())
				Preconditions.checkState(file.mkdir(),
						"Couldn't create uc3.store location: "+file.getAbsolutePath());
			return file;
		}
		
		// now see if we're running in eclispe
		File scratchDir = UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR;
		if (scratchDir.exists()) {
			File meanDir = new File(scratchDir, "UCERF3_ERF");
			if (!meanDir.exists())
				Preconditions.checkState(meanDir.mkdir(),
						"Couldn't create UCERF3 ERF eclipse location: "+meanDir.getAbsolutePath());
			return meanDir;
		}
		
		// just use user.home
		path = System.getProperty("user.home");
		File homeDir = new File(path);
		Preconditions.checkState(homeDir.exists(), "user.home dir doesn't exist: "+path);
		File openSHADir = new File(homeDir, ".opensha");
		if (!openSHADir.exists())
			Preconditions.checkState(openSHADir.mkdir(),
					"Couldn't create OpenSHA store location: "+openSHADir.getAbsolutePath());
		File uc3Dir = new File(openSHADir, "ucerf3_erf");
		if (!uc3Dir.exists())
			Preconditions.checkState(uc3Dir.mkdir(),
					"Couldn't create UCERF3 ERF store location: "+uc3Dir.getAbsolutePath());
		return uc3Dir;
	}
	
	public MeanUCERF3() {
		this(getStoreDir());
	}
	
	public MeanUCERF3(File storeDir) {
		this(null, storeDir);
	}

	public MeanUCERF3(FaultSystemSolution meanTotalSol) {
		this(meanTotalSol, getStoreDir());
	}
	
	public MeanUCERF3(FaultSystemSolution meanTotalSol, File storeDir) {
		super();
		this.fileParamChanged = false;
		this.meanTotalSol = meanTotalSol;
		this.storeDir = storeDir;
		System.out.println("MeanUCERF3 store dir: "+storeDir);
		Preconditions.checkState(storeDir.exists(), "Store dir doesn't exist: "+storeDir.getAbsolutePath());
		
		presetsParam = new EnumParameter<MeanUCERF3.Presets>("Mean UCERF3 Presets",
				EnumSet.allOf(Presets.class), Presets.FM3_1_BRANCH_AVG, "(custom)");
		presetsParam.addParameterChangeListener(this);
		
		upperDepthTolParam = new DoubleParameter(UPPER_DEPTH_TOL_PARAM_NAME, UPPER_DEPTH_TOL_MIN, UPPER_DEPTH_TOL_MAX);
		upperDepthTolParam.setValue(0d);
		upperDepthTolParam.setUnits("km");
		upperDepthTolParam.setInfo("Some fault sections have different aseismicity values across UCERF3" +
				"\nlogic tree branches. These values change the upper depth of the fault section. If > 0," +
				"\nsections with upper depths within the given tolerance of the mean will be combined in order" +
				"\nto reduce the overall section and rupture count.");
		upperDepthTolParam.addParameterChangeListener(this);
		upperDepthTol = upperDepthTolParam.getValue();
		
		upperDepthUseMeanParam = new BooleanParameter("Use Mean Upper Depth", true);
		upperDepthUseMeanParam.setInfo("If true and upper depth combine tolerance is > 0, mean upper" +
				"\ndepth will be used, else the shallowest upper depth will be used when averaging." +
				"\nNote that averaging does not incorporate participation rates, it is an unweighted mean" +
				"\nand may not be representative.");
		upperDepthUseMeanParam.addParameterChangeListener(this);
		upperDepthUseMean = upperDepthUseMeanParam.getValue();
		
		magTolParam = new DoubleParameter("Rup Mag Averaging Tolerance", 0d, 1d);
		magTolParam.setValue(0d);
		magTolParam.setInfo("Each rupture has a suite of magnitudes from the different scaling relationships." +
				"\nThese magnitudes can be averaged (within a tolerance) in order to reduce the total rupture" +
				"\ncount. Magnitudes are averaged weighted by their rate. Set to '1' to average all mags" +
				"\nfor each rupture.");
		magTolParam.addParameterChangeListener(this);
		magTol = magTolParam.getValue();
		
		ArrayList<String> rakeBasisStrs = Lists.newArrayList(RAKE_BASIS_NONE, RAKE_BASIS_MEAN);
		for (DeformationModels dm : DeformationModels.values())
			if (dm.getRelativeWeight(null) > 0)
				rakeBasisStrs.add(dm.name());
		rakeBasisParam = new StringParameter("Rupture Rake To Use", rakeBasisStrs, RAKE_BASIS_NONE);
		rakeBasisParam.setInfo("Each deformation model supplies rake values for each fault section" +
				"\n(and thus each rupture). Invididual rakes can be used, or the rupture count can" +
				"\nbe reduced by either using the rate-averaged rake or rakes from a specific" +
				"\ndeformation model.");
		rakeBasisParam.addParameterChangeListener(this);
		rakeBasisStr = rakeBasisParam.getValue();
		
		ArrayList<String> faultModelStrs = Lists.newArrayList(FAULT_MODEL_BOTH);
		for (FaultModels fm : FaultModels.values())
			if (fm.getRelativeWeight(null) > 0)
				faultModelStrs.add(fm.name());
		faultModelParam = new StringParameter(FAULT_MODEL_PARAM_NAME, faultModelStrs, FAULT_MODEL_BOTH);
		faultModelParam.setInfo("There are two equally weighted Fault Models in UCERF3. You can optionally" +
				"\nselect a single fault model with this parameter.");
		faultModelParam.addParameterChangeListener(this);
		faultModelStr = faultModelParam.getValue();
		
		ignoreCacheParam = new BooleanParameter("Ignore Cache", false);
		ignoreCacheParam.setInfo("MeanUCERF3 caches reduced solutions to save time. Setting this to" +
				"\ntrue will disable loading cached versions.");
		ignoreCacheParam.addParameterChangeListener(this);
		ignoreCache = ignoreCacheParam.getValue();
		
		// ensure disabled by default
		aleatoryMagAreaStdDevParam.setValue(0d);
		
		doSetPreset(presetsParam.getValue());
		
		loadRakeBasis();
		
		createParamList();
	}
	
	@Override
	protected void createParamList() {
		super.createParamList();
		
		if (upperDepthTolParam == null)
			// called during super constructor, wait until next time
			return;
		
		adjustableParams.addParameter(0, presetsParam);
		
		adjustableParams.addParameter(upperDepthTolParam);
		adjustableParams.addParameter(upperDepthUseMeanParam);
		adjustableParams.addParameter(magTolParam);
		adjustableParams.addParameter(rakeBasisParam);
		adjustableParams.addParameter(faultModelParam);
		adjustableParams.addParameter(ignoreCacheParam);
		
		if (adjustableParams.containsParameter(FILE_PARAM_NAME))
			adjustableParams.removeParameter(fileParam);
	}
	
	@Override
	public void updateForecast() {
		if (D) System.out.println("updateForecast called");
		if (getSolution() == null) {
			// this means that we have to load/build the solution (parameter change or never loaded)
			fetchSolution();
		}
		checkTimeDepCompatibility(true);
		if (getParameter(ProbabilityModelParam.NAME).getValue() != ProbabilityModelOptions.POISSON) {
			Preconditions.checkState(isTimeDepCompatible(), "The given parameterization is not compatible with time dependence."
					+ " When multiple fault models are used, or upper depth tolerance < 10,"
					+ " MeanUCERF3 creates multiple instances of each subsection, each of"
					+ " which has a portion of the rate, and thus a longer recurrence interval. This throws"
					+ " off the BPT calculations which use the subsection recurrence interval.");
			if (lastEventData == null) {
				try {
					lastEventData = LastEventData.load();
				} catch (IOException e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
			LastEventData.populateSubSects(getSolution().getRupSet().getFaultSectionDataList(), lastEventData);
		}
		super.updateForecast();
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		// we set the solution to null as a flag that something has changed
		// and we need to rebuild the solution on the next update forecast call
		
		Presets preset = null;
		if (presetsParam != null)
			preset = presetsParam.getValue();
		
		Parameter<?> param = event.getParameter();
		if (param == upperDepthTolParam) {
			upperDepthTol = upperDepthTolParam.getValue();
			if (preset != null && upperDepthTol != preset.upperDepthTol)
				setPreset(null);
			setSolution(null);
			checkTimeDepCompatibility(false);
		} else if (param == upperDepthUseMeanParam) {
			upperDepthUseMean = upperDepthUseMeanParam.getValue();
			if (preset != null && upperDepthUseMean != preset.upperDepthUseMean)
				setPreset(null);
			setSolution(null);
		} else if (param == magTolParam) {
			magTol = magTolParam.getValue();
			if (preset != null && magTol != preset.magTol)
				setPreset(null);
			setSolution(null);
		} else if (param == rakeBasisParam) {
			rakeBasisStr = rakeBasisParam.getValue();
			if (preset != null && !rakeBasisStr.equals(preset.rakeBasisStr))
				setPreset(null);
			loadRakeBasis();
			setSolution(null);
		} else if (param == faultModelParam) {
			faultModelStr = faultModelParam.getValue();
			if (preset != null) {
				if (preset.fm == null) {
					if (!faultModelStr.equals(FAULT_MODEL_BOTH))
						setPreset(null);
				} else {
					if (!preset.fm.name().equals(faultModelStr))
						setPreset(null);
				}
			}
			setTrueMeanSol(null);
			setSolution(null);
			checkTimeDepCompatibility(false);
		} else if (param == ignoreCacheParam) {
			ignoreCache = ignoreCacheParam.getValue();
			setSolution(null);
		} else if (param == presetsParam) {
			doSetPreset(preset);
		} else {
			super.parameterChange(event);
		}
	}
	
	public void setPreset(Presets preset) {
		presetsParam.setValue(preset);
		presetsParam.getEditor().refreshParamEditor();
	}
	
	private void doSetPreset(Presets preset) {
		if (preset == null)
			return;
		upperDepthTolParam.setValue(preset.upperDepthTol);
		upperDepthTolParam.getEditor().refreshParamEditor();
		upperDepthUseMeanParam.setValue(preset.upperDepthUseMean);
		upperDepthUseMeanParam.getEditor().refreshParamEditor();
		magTolParam.setValue(preset.magTol);
		magTolParam.getEditor().refreshParamEditor();
		rakeBasisParam.setValue(preset.rakeBasisStr);
		rakeBasisParam.getEditor().refreshParamEditor();
		FaultModels fm = preset.fm;
		if (fm == null)
			faultModelParam.setValue(FAULT_MODEL_BOTH);
		else
			faultModelParam.setValue(fm.name());
		faultModelParam.getEditor().refreshParamEditor();
	}
	
	private void checkTimeDepCompatibility(boolean warn) {
		if (!isTimeDepCompatible() && !isPoisson()) {
			if (warn) {
				String title = "Setting Probability Model to Poisson";
				String message = "The given parameterization is not compatible with time dependence.\n"
						+ "When multiple fault models are used, or upper depth tolerance < 10,\n"
						+ "MeanUCERF3 creates multiple instances of each subsection, each of which\n"
						+ "has a portion of the rate, and thus a longer recurrence interval. This throws\n"
						+ "off the BPT calculations which use the subsection recurrence interval.\n"
						+ "\nIt has now been set to Poisson.";
				JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
			}
			setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			getParameter(ProbabilityModelParam.NAME).getEditor().refreshParamEditor();
		}
			
	}
	
	private boolean isTimeDepCompatible() {
		if (faultModelStr.equals(FAULT_MODEL_BOTH))
			return false;
		if (upperDepthTol < 10d)
			return false;
		return true;
	}

	/**
	 * This loads the rake basis for rake-combining
	 */
	private void loadRakeBasis() {
		if (D) System.out.println("loading rake basis for: "+rakeBasisStr);
		if (rakeBasisStr.equals(RAKE_BASIS_NONE) || rakeBasisStr.equals(RAKE_BASIS_MEAN)) {
			rakeBasis = null;
			return;
		}
		
		File rakeBasisFile = checkDownload(RAKE_BASIS_FILE_NAME, false);
		
		DeformationModels dm = null;
		for (DeformationModels testDM : DeformationModels.values()) {
			if (testDM.name().equals(rakeBasisStr)) {
				dm = testDM;
				break;
			}
		}
		Preconditions.checkState(dm != null, "Couldn't find DM: "+rakeBasisStr);
		
		try {
			ZipFile zip = new ZipFile(rakeBasisFile);
			
			rakeBasis = RakeBasisWriter.loadRakeBasis(zip, dm);
		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
	}
	
	public boolean isTrueMean() {
		return upperDepthTol == 0 && magTol == 0 && rakeBasisStr.equals(RAKE_BASIS_NONE);
	}
	
	public void setTrueMeanSol(FaultSystemSolution meanTotalSol) {
		this.meanTotalSol = meanTotalSol;
		if (meanTotalSol == null)
			this.meanTotalMFDs = null;
		else
			this.meanTotalMFDs = meanTotalSol.getRupMagDists();
	}
	
	/**
	 * This loads the solution with all reductions applied. It will cache
	 * solutions locally and laod from that cache if already computed
	 */
	private void fetchSolution() {
		if (D) System.out.println("fetchSolution called with "+upperDepthTol+"="+upperDepthTol
				+", upperDepthUseMean="+upperDepthUseMean+", rakeBasisStr='"+rakeBasisStr+"'"
				+", magTol="+magTol+", faultModelStr="+faultModelStr);
		
		// first see if what we already need is cached
		
		// don't use mag here since we keep that until later
		String fName = "dep"+(float)upperDepthTol;
		if (upperDepthUseMean && upperDepthTol > 0)
			fName += "_depMean";
		else
			fName += "_depShallow";
		if (rakeBasisStr.equals(RAKE_BASIS_MEAN))
			fName += "_rakeMean";
		else if (rakeBasisStr.equals(RAKE_BASIS_NONE))
			fName += "_rakeAll";
		else
			fName += "_rake"+rakeBasisStr;
		
		String prefix;
		if (faultModelStr.equals(FAULT_MODEL_BOTH))
			prefix = "";
		else
			prefix = faultModelStr+"_";
		fName = prefix+fName;
		
		File solFile = new File(storeDir, "cached_"+fName+".zip");
		
		if (!ignoreCache) {
			if (!solFile.exists()) {
				// see if we can download it (precomputed)
				checkDownload(solFile.getName(), true);
			}
			if (solFile.exists()) {
				// already cached or we just downloaded it
				if (D) System.out.println("already cached: "+solFile.getName());
				try {
					FaultSystemSolution sol = FaultSystemIO.loadSol(solFile);
					checkCombineMags(sol);
					setSolution(sol);
					return;
				} catch (Exception e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
		}
		
		// if we've gotten this far, we'll need the mean
		if (meanTotalSol == null) {
			// not loaded yet
			if (D) System.out.println("loading mean sol");
			File meanSolFile = checkDownload(prefix+TRUE_MEAN_FILE_NAME, false);
			
			try {
				setTrueMeanSol(FaultSystemIO.loadSol(meanSolFile));
			} catch (Exception e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		
		if (isTrueMean()) {
			// simple case, just use the mean
			if (D) System.out.println("isTrueMean() = true");
			setSolution(meanTotalSol);
			return;
		}
		
		boolean combineRakes = !rakeBasisStr.equals(RAKE_BASIS_NONE);
		
		FaultSystemSolution reducedSol;
		if (upperDepthTol > 0 || combineRakes) {
			if (D) System.out.println("getting reduced sol");
			reducedSol = RuptureCombiner.getCombinedSolution(meanTotalSol, upperDepthTol,
					upperDepthUseMean, combineRakes, rakeBasis);
			
			// cache it
			try {
				if (D) System.out.println("caching reduced sol to: "+solFile.getName());
				FaultSystemIO.writeSol(reducedSol, solFile);
			} catch (Exception e) {
				// don't fail on caching
				e.printStackTrace();
			}
		} else {
			// must be just mags, create a new sol
			if (D) System.out.println("no reduce, just copying");
			reducedSol = new FaultSystemSolution(meanTotalSol.getRupSet(), meanTotalSol.getRateForAllRups());
			reducedSol.setRupMagDists(meanTotalMFDs);
			reducedSol.setGridSourceProvider(meanTotalSol.getGridSourceProvider());
		}
		
		// deal with mags
		checkCombineMags(reducedSol);
		
		// set the solution
		setSolution(reducedSol);
		
		if (D) System.out.println("fetchSolution done");
	}
	
	public void setMeanParams(double upperDepthTol, boolean upperDepthUseMean, double magTol, String rakeBasisStr) {
		upperDepthTolParam.setValue(upperDepthTol);
		upperDepthUseMeanParam.setValue(upperDepthUseMean);
		magTolParam.setValue(magTol);
		rakeBasisParam.setValue(rakeBasisStr);
	}
	
	public void setCachingEnabled(boolean enabled) {
		ignoreCacheParam.setValue(!enabled);
	}
	
	private void checkCombineMags(FaultSystemSolution sol) {
		if (magTol > 0) {
			if (D) System.out.println("combining mags");
			if (magTol >= 10)
				sol.setRupMagDists(null);
			else
				sol.setRupMagDists(RuptureCombiner.combineMFDs(magTol, sol.getRupMagDists()));
		}
	}
	
	/**
	 * This downloads the selected file from the OpenSHA server if not already cached locally
	 * 
	 * @param fName
	 */
	private File checkDownload(String fName, boolean ignoreErrors) {
		File file = new File(storeDir, fName);
		return checkDownload(file, ignoreErrors);
	}
	
	public static File checkDownload(File file, boolean ignoreErrors) {
		// TODO allow some sort of server side versioning so that clients know to update
		if (file.exists())
			return file;
		String fName = file.getName();
		CalcProgressBar progress = null;
		// try to show progress bar
		try {
			if (show_progress)
				progress = new CalcProgressBar("Downloading MeanUCERF3 Files", "downloading "+fName);
		} catch (Throwable t) {}
		String url = DOWNLOAD_URL+fName;
//		if (!ignoreErrors)
			System.out.print("Downloading "+url+" to "+file.getAbsolutePath());
		try {
			FileUtils.downloadURL(url, file);
		} catch (Exception e) {
			if (progress != null) {
				// not headless
				progress.setVisible(false);
				progress.dispose();
				if (!ignoreErrors) {
					String message = "Error downloading "+fName+".\nServer down or file moved, try again later.";
					JOptionPane.showMessageDialog(null, message, "Download Error", JOptionPane.ERROR_MESSAGE);
				}
			}
			if (ignoreErrors)
				return null;
			else
				ExceptionUtils.throwAsRuntimeException(e);
		}
		System.out.println("DONE.");
		if (progress != null) {
			progress.setVisible(false);
			progress.dispose();
		}
		return file;
	}
	
	public static void main(String[] args) {
		File solFile = new File(getStoreDir(), "mean_ucerf3_sol.zip");
		FaultSystemSolution sol;
		try {
			sol = FaultSystemIO.loadSol(solFile);
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		MeanUCERF3 muc3 = new MeanUCERF3(sol);
	}

}
