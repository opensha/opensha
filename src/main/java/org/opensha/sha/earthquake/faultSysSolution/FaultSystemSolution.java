package org.opensha.sha.earthquake.faultSysSolution;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import scratch.UCERF3.utils.U3FaultSystemIO;

/**
 * This class represents an Earthquake Rate Model solution for a fault system, possibly coming from an Inversion
 * or from a physics-based earthquake simulator.
 * <p>
 * It adds rate information to a FaultSystemRupSet.
 * 
 * @author Field, Milner, Page, and Powers
 *
 */
public class FaultSystemSolution extends ModuleContainer<OpenSHA_Module> implements ArchivableModule,
SubModule<ModuleArchive<OpenSHA_Module>> {
	
	protected FaultSystemRupSet rupSet;
	protected double[] rates;
	
	// archive that this came from
	ModuleArchive<OpenSHA_Module> archive;
	
	protected FaultSystemSolution() {
		
	}
	
	public FaultSystemSolution(FaultSystemRupSet rupSet, double[] rates) {
		super();
		init(rupSet, rates);
		
		// track the version of OpenSHA this was generated with
		if (!hasModule(BuildInfoModule.class)) {
			try {
				addModule(BuildInfoModule.detect());
			} catch (Exception e) {}
		}
	}
	
	protected void init(FaultSystemRupSet rupSet, double[] rates) {
		this.rupSet = rupSet;
		this.rates = rates;
		Preconditions.checkNotNull(rupSet, "Rupture set cannot be null");
		Preconditions.checkNotNull(rates, "Rates cannot be null");
		Preconditions.checkArgument(rates.length == rupSet.getNumRuptures(), "# rates and ruptures is inconsistent!");
	}
	
	/**
	 * Returns an archive that contains this solution. If this was loaded from an archive, this returns that
	 * original archive (which may also contain other things), otherwise a new archive is returned that only contains
	 * this solution and its rupture set.
	 * 
	 * @return archive containing this rupture set
	 */
	public ModuleArchive<OpenSHA_Module> getArchive() {
		if (archive == null)
			archive = new ModuleArchive<>();
		FaultSystemRupSet oRupSet = archive.getModule(FaultSystemRupSet.class);
		if (oRupSet == null)
			archive.addModule(rupSet);
		else
			Preconditions.checkState(rupSet.isEquivalentTo(archive.getModule(FaultSystemRupSet.class)));
		FaultSystemSolution sol = archive.getModule(FaultSystemSolution.class);
		if (sol == null)
			archive.addModule(this);
		else
			Preconditions.checkState(sol == this);
		return archive;
	}
	
	/**
	 * Writes this solution to a zip file. This is an alias to <code>getArchive().write(File)</code>.
	 * See {@link #getArchive()}.
	 * @param file
	 * @throws IOException
	 */
	public void write(File file) throws IOException {
		getArchive().write(file);
	}
	
	/**
	 * Writes this solution to the give {@link ArchiveOutput} This is an alias to <code>getArchive().write(ModuleArchiveOutput)</code>.
	 * See {@link #getArchive()}.
	 * @param output
	 * @throws IOException
	 */
	public void write(ArchiveOutput output) throws IOException {
		getArchive().write(output);
	}
	
	/**
	 * Writes this solution to the give {@link ArchiveOutput} This is an alias to <code>getArchive().write(ModuleArchiveOutput)</code>.
	 * See {@link #getArchive()}.
	 * @param output
	 * @param copyUnknownSourceFiles
	 * @throws IOException
	 */
	public void write(ArchiveOutput output, boolean copyUnknownSourceFiles) throws IOException {
		getArchive().write(output, copyUnknownSourceFiles);
	}
	
	public static boolean isSolution(ZipFile zip) {
		return zip.getEntry("solution/"+RATES_FILE_NAME) != null || zip.getEntry("rates.bin") != null;
	}
	
	public static boolean isSolution(ArchiveInput input) throws IOException {
		return input.hasEntry("solution/"+RATES_FILE_NAME) || input.hasEntry("rates.bin");
	}
	
	/**
	 * Loads a FaultSystemSolution from a zip file
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemSolution load(File file) throws IOException {
		return load(ArchiveInput.getDefaultInput(file));
	}
	
	/**
	 * Loads a FaultSystemSolution from a zip file, using the existing rupture set instead of the attached rupture set.
	 * This can be useful for quickly loading many solutions based on the same rupture set.
	 * 
	 * @param file
	 * @param rupSet
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemSolution load(File file, FaultSystemRupSet rupSet) throws IOException {
		return load(ArchiveInput.getDefaultInput(file), rupSet);
	}

	/**
	 * Loads a FaultSystemSolution from a zip file
	 * 
	 * @param zip
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemSolution load(ZipFile zip) throws IOException {
		return load(zip, null);
	}

	/**
	 * Loads a FaultSystemSolution from a zip file, using the existing rupture set instead of the attached rupture set.
	 * This can be useful for quickly loading many solutions based on the same rupture set.
	 * 
	 * @param zip
	 * @param rupSet
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemSolution load(ZipFile zip, FaultSystemRupSet rupSet) throws IOException {
		// see if it's an old rupture set
		if (rupSet == null && zip.getEntry("rup_sections.bin") != null && zip.getEntry("rates.bin") != null) {
			System.err.println("WARNING: this is a legacy fault sytem solution, that file format is deprecated. "
					+ "Will attempt to load it using the legacy file loader. "
					+ "See https://opensha.org/File-Formats for more information.");
			try {
				return U3FaultSystemIO.loadSolAsApplicable(zip, null);
			} catch (DocumentException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		return load(new ArchiveInput.ZipFileInput(zip), rupSet);
	}
	


	/**
	 * Loads a FaultSystemSolution from the given input
	 * 
	 * @param zip
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemSolution load(ArchiveInput input) throws IOException {
		return load(input, null);
	}

	/**
	 * Loads a FaultSystemSolution from the given input, using the existing rupture set instead of the attached rupture set.
	 * This can be useful for quickly loading many solutions based on the same rupture set.
	 * 
	 * @param zip
	 * @param rupSet
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemSolution load(ArchiveInput input, FaultSystemRupSet rupSet) throws IOException {
		if (rupSet == null && input.hasEntry("rup_sections.bin") && input.hasEntry("rates.bin")) {
			System.err.println("WARNING: this is a legacy fault sytem solution, that file format is deprecated. "
					+ "Will attempt to load it using the legacy file loader. "
					+ "See https://opensha.org/File-Formats for more information.");
			Preconditions.checkState(input instanceof ArchiveInput.FileBacked,
					"Can only do a deprecated load from zip files (this isn't file-backed)");
			try {
				return U3FaultSystemIO.loadSolAsApplicable(((ArchiveInput.FileBacked)input).getInputFile());
			} catch (DocumentException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		ModuleArchive<OpenSHA_Module> archive;
		if (rupSet == null) {
			archive = new ModuleArchive<>(input, FaultSystemSolution.class);
		} else {
			archive = new ModuleArchive<>(input);
			archive.addModule(rupSet);
		}
		
		FaultSystemSolution sol = archive.getModule(FaultSystemSolution.class);
		if (sol == null) {
			if (!input.hasEntry("modules.json") // doesn't have modules
					// but does have rup sections and solution rates
					&& input.hasEntry("ruptures/"+FaultSystemRupSet.RUP_SECTS_FILE_NAME)
					&& input.hasEntry("solution/rates.csv")) {
				// missing modules.json, try to load it as an unlisted module
				System.err.println("WARNING: solution archive is missing modules.json, trying to load it anyway");
				archive.loadUnlistedModule(FaultSystemRupSet.class, FaultSystemRupSet.NESTING_PREFIX);
				Preconditions.checkState(archive.hasModule(FaultSystemRupSet.class),
						"Failed to load unlisted rupture set module");
				archive.loadUnlistedModule(FaultSystemSolution.class, NESTING_PREFIX);
				Preconditions.checkState(archive.hasModule(FaultSystemSolution.class),
						"Failed to load unlisted solution module");
				sol = archive.getModule(FaultSystemSolution.class);
			}
		}
		Preconditions.checkState(sol != null, "Failed to load solution module from archive (see above error messages)");
		Preconditions.checkNotNull(sol.rupSet, "rupture set not loaded?");
		Preconditions.checkNotNull(sol.archive, "archive should have been set automatically");
		
		if (sol.hasAvailableModule(GridSourceList.class) && !sol.hasAvailableModule(GridSourceList.Precomputed.class)) {
			System.err.println("WARNING: solution archive refers to old GridSourceList module class that is now "
					+ "abstract, updating with Precomputed variant.");
			sol.addAvailableModule(new Callable<GridSourceList>() {

				@Override
				public GridSourceList call() throws Exception {
					return archive.loadUnlistedModule(GridSourceList.Precomputed.class, NESTING_PREFIX);
				}
			}, GridSourceList.class);
		}
		
		return sol;
	}

	@Override
	public String getName() {
		return "Solution";
	}
	
	public static final String NESTING_PREFIX = "solution/";

	@Override
	protected String getNestingPrefix() {
		return NESTING_PREFIX;
	}
	
	public static final String RATES_FILE_NAME = "rates.csv";
	
	public static CSVFile<String> buildRatesCSV(FaultSystemSolution sol) {
		CSVFile<String> ratesCSV = new CSVFile<>(true);
		ratesCSV.addLine("Rupture Index", "Annual Rate");
		double[] rates = sol.getRateForAllRups();
		for (int r=0; r<rates.length; r++)
			ratesCSV.addLine(r+"", rates[r]+"");
		return ratesCSV;
	}

	@Override
	public final void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		// CSV Files
		CSV_BackedModule.writeToArchive(buildRatesCSV(this), output, entryPrefix, RATES_FILE_NAME);
	}
	
	public static double[] loadRatesCSV(CSVFile<String> ratesCSV) {
		double[] rates = new double[ratesCSV.getNumRows()-1];
		for (int r=0; r<rates.length; r++) {
			Preconditions.checkState(ratesCSV.getInt(r+1, 0) == r, "Rates CSV out of order or not 0-based");
			rates[r] = ratesCSV.getDouble(r+1, 1);
		}
		return rates;
	}

	@Override
	public final void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		Preconditions.checkNotNull(archive, "archive must be set before initialization");
		if (rupSet == null)
			rupSet = archive.getModule(FaultSystemRupSet.class);
		Preconditions.checkNotNull(rupSet, "Rupture set not found in archive");
		
		if (verbose) System.out.println("\tLoading rates CSV...");
		CSVFile<String> ratesCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, RATES_FILE_NAME);
		rates = loadRatesCSV(ratesCSV);
		Preconditions.checkState(rates.length == rupSet.getNumRuptures(), "Unexpected number of rows in rates CSV");

		if (archive != null) {
			// see if any common modules are are present but unlised (either because modules.json is missing, or someone
			// added them manually)
			
			if (!hasAvailableModule(GridSourceProvider.class)) {
				if (input.hasEntry(entryPrefix+MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME)) {
					try {
						System.out.println("Trying to load unlisted MFDGridSourceProvider module");
						archive.loadUnlistedModule(MFDGridSourceProvider.Default.class, entryPrefix, this);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else if (input.hasEntry(entryPrefix+GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)) {
					try {
						System.out.println("Trying to load unlisted GridSourceList module");
						archive.loadUnlistedModule(GridSourceList.Precomputed.class, entryPrefix, this);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			if (input.hasEntry(entryPrefix+RupMFDsModule.FILE_NAME) && !hasAvailableModule(RupMFDsModule.class)) {
				try {
					System.out.println("Trying to load unlisted RupMFDsModule module");
					archive.loadUnlistedModule(RupMFDsModule.class, entryPrefix, this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			if (input.hasEntry(entryPrefix+SubSeismoOnFaultMFDs.DATA_FILE_NAME) && !hasAvailableModule(SubSeismoOnFaultMFDs.class)) {
				try {
					System.out.println("Trying to load unlisted SubSeismoOnFaultMFDs module");
					archive.loadUnlistedModule(SubSeismoOnFaultMFDs.class, entryPrefix, this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public final Class<? extends ArchivableModule> getLoadingClass() {
		// never let a subclass supply it's own loader, must always load as a regular FaultSystemSolution
		return FaultSystemSolution.class;
	}

	@Override
	public void setParent(ModuleArchive<OpenSHA_Module> parent) throws IllegalStateException {
		FaultSystemSolution oSol = parent.getModule(FaultSystemSolution.class, false);
		Preconditions.checkState(oSol == null || oSol == this);
		this.archive = parent;
	}

	@Override
	public ModuleArchive<OpenSHA_Module> getParent() {
		return archive;
	}

	@Override
	public FaultSystemSolution copy(ModuleArchive<OpenSHA_Module> newArchive)
			throws IllegalStateException {
		FaultSystemRupSet oRupSet = newArchive.getModule(FaultSystemRupSet.class);
		if (oRupSet == null)
			newArchive.addModule(rupSet);
		else
			Preconditions.checkState(rupSet.isEquivalentTo(oRupSet));
		FaultSystemRupSet copyRS = newArchive.getModule(FaultSystemRupSet.class);
		Preconditions.checkNotNull(copyRS);
		FaultSystemSolution copy = new FaultSystemSolution(copyRS, Arrays.copyOf(rates, rates.length));
		loadAllAvailableModules();
		for (OpenSHA_Module module : getModules(true)) {
			copy.addAvailableModule(new Callable<OpenSHA_Module>() {

				@Override
				public OpenSHA_Module call() throws Exception {
					return module;
				}
			}, module.getClass());
		}
		copy.loadAllAvailableModules();
		newArchive.addModule(copy);
		return copy;
	}
	
	/**
	 * Returns the fault system rupture set for this solution
	 * @return
	 */
	public FaultSystemRupSet getRupSet() {
		return rupSet;
	}
	
	/**
	 * These gives the long-term rate (events/yr) of the rth rupture
	 * @param rupIndex
	 * @return
	 */
	public double getRateForRup(int rupIndex) {
		return rates[rupIndex];
	}
	
	/**
	 * This gives the long-term rate (events/yr) of all ruptures
	 * @param rupIndex
	 * @return
	 */
	public double[] getRateForAllRups() {
		return rates;
	}
	
	/**
	 * This returns the total long-term rate (events/yr) of all fault-based ruptures
	 * (fault based in case off-fault ruptures are added to subclass)
	 * @return
	 */
	public double getTotalRateForAllFaultSystemRups() {
		double totRate=0;
		for(double rate:getRateForAllRups())
			totRate += rate;
		return totRate;
	}

	/**
	 * This is a general info String
	 * @return
	 */
	public String getInfoString() {
		InfoModule info = getModule(InfoModule.class);
		if (info != null)
			return info.getText();
		return null;
	}

	public void setInfoString(String info) {
		if (info == null)
			removeModuleInstances(InfoModule.class);
		else
			addModule(new InfoModule(info));
	}
	
	/**
	 * Returns GridSourceProvider
	 * @return
	 */
	public GridSourceProvider getGridSourceProvider() {
		return getModule(GridSourceProvider.class);
	}
	
	public void setGridSourceProvider(GridSourceProvider gridSourceProvider) {
		addModule(gridSourceProvider);
	}
	
	/*
	 * TODO move these to a helper class?
	 */
	


	/**
	 * This enables/disables visible progress bars for long calculations
	 * 
	 * @param showProgress
	 */
	public void setShowProgress(boolean showProgress) {
		rupSet.setShowProgress(showProgress);
	}
	
	public void clearCache() {
		rupSet.clearCache();
		clearSolutionCacheOnly();
	}
	
	public void clearSolutionCacheOnly() {
		particRatesCache.clear();
		nucleationRatesCache.clear();
		totParticRatesCache = null;
		paleoVisibleRatesCache = null;
	}

	/**
	 * This returns the rate that pairs of section rupture together.  
	 * Most entries are zero because the sections are far from each other, 
	 * so a sparse matrix might be in order if this bloats memory.
	 * @return
	 * TODO move?
	 */
	public double[][] getSectionPairRupRates() {
		double[][] rates = new double[rupSet.getNumSections()][rupSet.getNumSections()];
		for(int r=0; r<rupSet.getNumRuptures(); r++) {
			List<Integer> indices = rupSet.getSectionsIndicesForRup(r);
			double rate = getRateForRup(r);
			if (rate == 0)
				continue;
			for(int s=1;s<indices.size();s++) {
				rates[indices.get(s-1)][indices.get(s)] += rate;
				rates[indices.get(s)][indices.get(s-1)] += rate;    // fill in the symmetric point
			}
		}
		return rates;
	}

	private HashMap<String, double[]> particRatesCache = new HashMap<String, double[]>();
	
	/**
	 * This computes the participation rate (events/yr) of the sth section for magnitudes 
	 * greater and equal to magLow and less than magHigh.
	 * @param sectIndex
	 * @param magLow
	 * @param magHigh
	 * @return
	 */
	public double calcParticRateForSect(int sectIndex, double magLow, double magHigh) {
		return calcParticRateForAllSects(magLow, magHigh)[sectIndex];
	}
		
	private double doCalcParticRateForSect(int sectIndex, double magLow, double magHigh) {
		double partRate=0;
		RupMFDsModule mfds = getModule(RupMFDsModule.class);
		for (int r : rupSet.getRupturesForSection(sectIndex)) {
			double mag = rupSet.getMagForRup(r);
			DiscretizedFunc mfd = getRupMagDist(mfds, r);
			if (mfd == null || mfd.size() == 1) {
				if(mag>=magLow && mag<magHigh)
					partRate += getRateForRup(r);
			} else {
				// use rup MFDs
				for (Point2D pt : mfd) {
					if(pt.getX()>=magLow && pt.getX()<magHigh)
						partRate += pt.getY();
				}
			}
		}
		return partRate;
	}
	
	/**
	 * This computes the participation rate (events/yr) of all sections for magnitudes 
	 * greater and equal to magLow and less than magHigh.
	 * @param sectIndex
	 * @param magLow
	 * @param magHigh
	 * @return
	 */
	public synchronized double[] calcParticRateForAllSects(double magLow, double magHigh) {
		String key = (float)magLow+"_"+(float)magHigh;
		if (!particRatesCache.containsKey(key)) {
			double[] particRates = new double[rupSet.getNumSections()];
			CalcProgressBar p = null;
			if (rupSet.isShowProgress()) {
				p = new CalcProgressBar("Calculating Participation Rates", "Calculating Participation Rates");
			}
			for (int i=0; i<particRates.length; i++) {
				if (p != null) p.updateProgress(i, particRates.length);
				particRates[i] = doCalcParticRateForSect(i, magLow, magHigh);
			}
			if (p != null) p.dispose();
			particRatesCache.put(key, particRates);
		}
		return Arrays.copyOf(particRatesCache.get(key), rupSet.getNumSections());
	}

	private HashMap<String, double[]> nucleationRatesCache = new HashMap<String, double[]>();
	
	/**
	 * This computes the nucleation rate (events/yr) of the sth section for magnitudes 
	 * greater and equal to magLow and less than magHigh. This assumes a uniform distribution
	 * of possible hypocenters over the rupture surface.
	 * @param sectIndex
	 * @param magLow
	 * @param magHigh
	 * @return
	 */
	public double calcNucleationRateForSect(int sectIndex, double magLow, double magHigh) {
		return calcNucleationRateForAllSects(magLow, magHigh)[sectIndex];
	}
		
	private double doCalcNucleationRateForSect(int sectIndex, double magLow, double magHigh) {
		double nucleationRate=0;
		RupMFDsModule mfds = getModule(RupMFDsModule.class);
		for (int r : rupSet.getRupturesForSection(sectIndex)) {
			double mag = rupSet.getMagForRup(r);
			DiscretizedFunc mfd = getRupMagDist(mfds, r);
			if (mfd == null || mfd.size() == 1) {
				if(mag>=magLow && mag<magHigh) {
					double rate = getRateForRup(r);
					double sectArea = rupSet.getAreaForSection(sectIndex);
					double rupArea = rupSet.getAreaForRup(r);
					nucleationRate += rate * (sectArea / rupArea);
				}
			} else {
				// use rup MFDs
				double sectArea = rupSet.getAreaForSection(sectIndex);
				double rupArea = rupSet.getAreaForRup(r);
				for (Point2D pt : mfd) {
					if(pt.getX()>=magLow && pt.getX()<magHigh)
						nucleationRate += pt.getY() * (sectArea / rupArea);
				}
			}
			
		}
		return nucleationRate;
	}
	
	
	/**
	 * This computes the nucleation rate (events/yr) of all sections for magnitudes 
	 * greater and equal to magLow and less than magHigh.   This assumes a uniform distribution
	 * of possible hypocenters over the rupture surface.
	 * @param sectIndex
	 * @param magLow
	 * @param magHigh
	 * @return
	 */
	public synchronized double[] calcNucleationRateForAllSects(double magLow, double magHigh) {
		String key = (float)magLow+"_"+(float)magHigh;
		if (!nucleationRatesCache.containsKey(key)) {
			double[] nucleationRates = new double[rupSet.getNumSections()];
			CalcProgressBar p = null;
			if (rupSet.isShowProgress()) {
				p = new CalcProgressBar("Calculating Nucleation Rates", "Calculating Participation Rates");
			}
			for (int i=0; i<nucleationRates.length; i++) {
				if (p != null) p.updateProgress(i, nucleationRates.length);
				nucleationRates[i] = doCalcNucleationRateForSect(i, magLow, magHigh);
			}
			if (p != null) p.dispose();
			nucleationRatesCache.put(key, nucleationRates);
		}
		return Arrays.copyOf(nucleationRatesCache.get(key), rupSet.getNumSections());
	}
	
	private double[] totParticRatesCache;
	
	/**
	 * This computes the total participation rate (events/yr) of the sth section.
	 * 
	 * @param sectIndex
	 * @return
	 */
	public double calcTotParticRateForSect(int sectIndex) {
		return calcTotParticRateForAllSects()[sectIndex];
	}
	
	private double doCalcTotParticRateForSect(int sectIndex) {
		double partRate=0;
		for (int r : rupSet.getRupturesForSection(sectIndex))
			partRate += getRateForRup(r);
		return partRate;
	}
	
	
	/**
	 * This computes the total participation rate (events/yr) for all sections.
	 * 
	 * @return
	 */
	public synchronized double[] calcTotParticRateForAllSects() {
		if (totParticRatesCache == null) {
			totParticRatesCache = new double[rupSet.getNumSections()];
			CalcProgressBar p = null;
			if (rupSet.isShowProgress()) {
				p = new CalcProgressBar("Calculating Total Participation Rates", "Calculating Total Participation Rates");
			}
			for (int i=0; i<totParticRatesCache.length; i++) {
				if (p != null) p.updateProgress(i, totParticRatesCache.length);
				totParticRatesCache[i] = doCalcTotParticRateForSect(i);
			}
			if (p != null) p.dispose();
		}
		return Arrays.copyOf(totParticRatesCache, rupSet.getNumSections());
	}
	
	private Map<PaleoProbabilityModel, double[]> paleoVisibleRatesCache;
	
	/**
	 * This gives the total paleoseismically observable rate (events/yr) of the sth section.
	 * the probability of observing an event is given by the getProbPaleoVisible(mag)
	 * method.
	 * 
	 * @param sectIndex
	 * @return
	 */
	public double calcTotPaleoVisibleRateForSect(int sectIndex, PaleoProbabilityModel paleoProbModel) {
		if (paleoVisibleRatesCache != null) {
			double[] paleoRates = paleoVisibleRatesCache.get(paleoProbModel);
			if (paleoRates != null)
				return paleoRates[sectIndex];
		}
		return doCalcTotPaleoVisibleRateForSect(sectIndex, paleoProbModel);
	}
	
	private double doCalcTotPaleoVisibleRateForSect(int sectIndex, PaleoProbabilityModel paleoProbModel) {
		double partRate=0;
		for (int r : rupSet.getRupturesForSection(sectIndex))
			partRate += getRateForRup(r)*paleoProbModel.getProbPaleoVisible(rupSet, r, sectIndex);
		return partRate;
	}

	
	/**
	 * This gives the total paleoseismically observable rate of all sections.
	 * the probability of observing an event is given by the getProbPaleoVisible(mag)
	 * method
	 * 
	 * @return
	 */
	public synchronized double[] calcTotPaleoVisibleRateForAllSects(PaleoProbabilityModel paleoProbModel) {
		if (paleoVisibleRatesCache == null) {
			paleoVisibleRatesCache = Maps.newHashMap();
		}
		
		double[] paleoRates = paleoVisibleRatesCache.get(paleoProbModel);
		
		if (paleoRates == null) {
			paleoRates = new double[rupSet.getNumSections()];
			paleoVisibleRatesCache.put(paleoProbModel, paleoRates);
			CalcProgressBar p = null;
			if (rupSet.isShowProgress()) {
				p = new CalcProgressBar("Calculating Paleo Visible Rates", "Calculating Paleo Visible Rates");
			}
			for (int i=0; i<paleoRates.length; i++) {
				if (p != null) p.updateProgress(i, paleoRates.length);
				paleoRates[i] = doCalcTotPaleoVisibleRateForSect(i, paleoProbModel);
			}
			if (p != null) p.dispose();
		}
		return Arrays.copyOf(paleoRates, paleoRates.length);
	}
	
	/**
	 * This assumes a uniform probability of hypocenter location over the rupture surface
	 * @param parentSectionID
	 * @param minMag
	 * @param maxMag
	 * @param numMag
	 * @return
	 */
	public SummedMagFreqDist calcNucleationMFD_forParentSect(int parentSectionID, double minMag, double maxMag, int numMag) {
		SummedMagFreqDist mfd = new SummedMagFreqDist(minMag, maxMag, numMag);
		
		for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++) {
			if (rupSet.getFaultSectionData(sectIndex).getParentSectionId() != parentSectionID)
				continue;
			IncrementalMagFreqDist subMFD = calcNucleationMFD_forSect(sectIndex, minMag, maxMag, numMag);
			mfd.addIncrementalMagFreqDist(subMFD);
		}
		
		return mfd;
	}

	/**
	 * This give a Nucleation Mag Freq Dist (MFD) for the specified section.  Nucleation probability 
	 * is defined as the area of the section divided by the area of the rupture.  
	 * This preserves rates rather than moRates (can't have both)
	 * @param sectIndex
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @return IncrementalMagFreqDist
	 */
	public  IncrementalMagFreqDist calcNucleationMFD_forSect(int sectIndex, double minMag, double maxMag, int numMag) {
		return calcNucleationMFD_forSect(sectIndex, rupSet.getRupturesForSection(sectIndex), minMag, maxMag, numMag);
	}

	/**
	 * This give a Nucleation Mag Freq Dist (MFD) for the specified section and rupture list; it is assumed (but not checked)
	 * that every given rupture involves the given section..  Nucleation probability 
	 * is defined as the area of the section divided by the area of the rupture.  
	 * This preserves rates rather than moRates (can't have both)
	 * @param sectIndex
	 * @param rups
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @return IncrementalMagFreqDist
	 */
	public  IncrementalMagFreqDist calcNucleationMFD_forSect(int sectIndex, Collection<Integer> rups, double minMag, double maxMag, int numMag) {
		ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
		RupMFDsModule mfds = getModule(RupMFDsModule.class);
		if (rups != null) {
			double sectArea = rupSet.getAreaForSection(sectIndex);
			for (int r : rups) {
				double nucleationScalar = sectArea/rupSet.getAreaForRup(r);
				DiscretizedFunc rupMagDist = getRupMagDist(mfds, r);
				if (rupMagDist == null)
					mfd.addResampledMagRate(rupSet.getMagForRup(r), getRateForRup(r)*nucleationScalar, true);
				else
					for (Point2D pt : rupMagDist)
						mfd.addResampledMagRate(pt.getX(), pt.getY()*nucleationScalar, true);
			}
		}
		return mfd;
	}
	
	
	/**
	 * This give a Participation Mag Freq Dist for the specified section.
	 * This preserves rates rather than moRates (can't have both).
	 * @param sectIndex
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcParticipationMFD_forParentSect(int parentSectionID, double minMag, double maxMag, int numMag) {
		return calcParticipationMFD_forRups(rupSet.getRupturesForParentSection(parentSectionID), minMag, maxMag, numMag);
	}
	
	
	/**
	 * This give a Participation Mag Freq Dist for the specified set of ruptures.
	 * This preserves rates rather than moRates (can't have both).
	 * @param sectIndex
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcParticipationMFD_forRups(Collection<Integer> rupIndexes, double minMag, double maxMag, int numMag) {
		ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
		RupMFDsModule mfds = getModule(RupMFDsModule.class);
		if (rupIndexes != null) {
			for (int r : rupIndexes) {
				DiscretizedFunc rupMagDist = getRupMagDist(mfds, r);
				if (rupMagDist == null)
					mfd.addResampledMagRate(rupSet.getMagForRup(r), getRateForRup(r), true);
				else
					for (Point2D pt : rupMagDist)
						mfd.addResampledMagRate(pt.getX(), pt.getY(), true);
			}
		}
		return mfd;
	}
	
	
	/**
	 * This give a Participation Mag Freq Dist for the specified section.
	 * This preserves rates rather than moRates (can't have both).
	 * @param sectIndex
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcParticipationMFD_forSect(int sectIndex, double minMag, double maxMag, int numMag) {
		ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
		List<Integer> rups = rupSet.getRupturesForSection(sectIndex);
		RupMFDsModule mfds = getModule(RupMFDsModule.class);
		if (rups != null) {
			for (int r : rups) {
				DiscretizedFunc rupMagDist = getRupMagDist(mfds, r);
				if (rupMagDist == null)
					mfd.addResampledMagRate(rupSet.getMagForRup(r), getRateForRup(r), true);
				else
					for (Point2D pt : rupMagDist)
						mfd.addResampledMagRate(pt.getX(), pt.getY(), true);
			}
		}
		return mfd;
	}
	
	/**
	 * This gives the total nucleation Mag Freq Dist of this solution.  
	 * This preserves rates rather than moRates (can't have both).
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param delta - width of each mfd bin
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcTotalNucleationMFD(double minMag, double maxMag, double delta) {
		return calcNucleationMFD_forRegion(null, minMag, maxMag, delta, true);
	}

	/**
	 * This gives the total nucleation Mag Freq Dist inside the supplied region.  
	 * If <code>traceOnly == true</code>, only the rupture trace is examined in computing the fraction of the rupture 
	 * inside the region.  This preserves rates rather than moRates (can't have both).
	 * @param region - a Region object
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param delta - width of each mfd bin
	 * @param traceOnly - if true only fault traces will be used for fraction inside region calculations, otherwise the
	 * entire rupture surfaces will be used (slower)
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcNucleationMFD_forRegion(Region region, double minMag, double maxMag, double delta, boolean traceOnly) {
		return calcNucleationMFD_forRegion(region, minMag, maxMag, delta, traceOnly, null);
	}
	


	/**
	 * This gives the total nucleation Mag Freq Dist inside the supplied region.  
	 * If <code>traceOnly == true</code>, only the rupture trace is examined in computing the fraction of the rupture 
	 * inside the region.  This preserves rates rather than moRates (can't have both).
	 * @param region - a Region object
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param delta - width of each mfd bin
	 * @param traceOnly - if true only fault traces will be used for fraction inside region calculations, otherwise the
	 * entire rupture surfaces will be used (slower)
	 * @param trt - tectonic region type, will be compared against a {@link RupSetTectonicRegimes} module if non-null
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcNucleationMFD_forRegion(Region region, double minMag, double maxMag, double delta, boolean traceOnly, TectonicRegionType trt) {
		int numMag = (int)((maxMag - minMag) / delta+0.5) + 1;
		return calcNucleationMFD_forRegion(region, minMag, maxMag, numMag, traceOnly, trt);
	}

	/**
	 * This gives the total nucleation Mag Freq Dist inside the supplied region.  
	 * If <code>traceOnly == true</code>, only the rupture trace is examined in computing the fraction of the rupture
	 * inside the region.  This preserves rates rather than moRates (can't have both).
	 * @param region - a Region object
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @param traceOnly - if true only fault traces will be used for fraction inside region calculations, otherwise the
	 * entire rupture surfaces will be used (slower)
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcNucleationMFD_forRegion(Region region, double minMag, double maxMag, int numMag,
			boolean traceOnly) {
		return calcNucleationMFD_forRegion(region, minMag, maxMag, numMag, traceOnly, null);
	}
	


	/**
	 * This gives the total nucleation Mag Freq Dist inside the supplied region.  
	 * If <code>traceOnly == true</code>, only the rupture trace is examined in computing the fraction of the rupture
	 * inside the region.  This preserves rates rather than moRates (can't have both).
	 * @param region - a Region object
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @param traceOnly - if true only fault traces will be used for fraction inside region calculations, otherwise the
	 * entire rupture surfaces will be used (slower)
	 * @param trt - tectonic region type, will be compared against a {@link RupSetTectonicRegimes} module if non-null
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcNucleationMFD_forRegion(Region region, double minMag, double maxMag, int numMag,
			boolean traceOnly, TectonicRegionType trt) {
		ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
		double[] fractRupsInside = null;
		if (region != null)
			fractRupsInside = rupSet.getFractRupsInsideRegion(region, traceOnly);
		RupSetTectonicRegimes trts = null;
		if (trt != null)
			trts = getRupSet().requireModule(RupSetTectonicRegimes.class);
		RupMFDsModule mfds = getModule(RupMFDsModule.class);
		for(int r=0;r<rupSet.getNumRuptures();r++) {
			double fractInside = 1;
			if (region != null)
				fractInside = fractRupsInside[r];
			if (trt != null && trt != trts.get(r))
				continue;
//			if (fractInside < 1)
//				System.out.println("inside: "+fractInside+"\trate: "+rateInside+"\tID: "+r);
			if (fractInside > 0d) {
				DiscretizedFunc rupMagDist = getRupMagDist(mfds, r);
				if (rupMagDist == null)
					mfd.addResampledMagRate(rupSet.getMagForRup(r), getRateForRup(r)*fractInside, true);
				else
					for (Point2D pt : rupMagDist)
						mfd.addResampledMagRate(pt.getX(), pt.getY()*fractInside, true);
			}
		}
		return mfd;
	}
	
	private DiscretizedFunc getRupMagDist(RupMFDsModule mfds, int rupIndex) {
		if (mfds != null)
			return mfds.getRuptureMFD(rupIndex);
		return null;
	}
	
	/**
	 * This returns the total moment of the solution (this does not include any off fault moment).<br>
	 * <br>
	 * This is calculated as the sum of the rates or each rupture times its moment (which is calculated form the magnitude)
	 * @return
	 */
	public double getTotalFaultSolutionMomentRate() {
		// calculate the moment
		double totalSolutionMoment = 0;
		for (int rup=0; rup<rupSet.getNumRuptures(); rup++) 
			totalSolutionMoment += getRateForRup(rup)*MagUtils.magToMoment(rupSet.getMagForRup(rup));
		return totalSolutionMoment;
	}
	
	public static void main(String[] args) throws Exception {
		File baseDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		
		File inputFile = new File(baseDir, "fm3_1_ucerf3.zip");
		File destFile = new File("/tmp/new_format_u3.zip");
		FaultSystemSolution orig = U3FaultSystemIO.loadSol(inputFile);
		orig.getArchive().write(destFile);
		FaultSystemSolution loaded = load(destFile);
		Preconditions.checkState(orig.rupSet.isEquivalentTo(loaded.rupSet));
	}

}
