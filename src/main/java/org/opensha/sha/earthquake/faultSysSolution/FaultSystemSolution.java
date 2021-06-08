package org.opensha.sha.earthquake.faultSysSolution;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.CSV_BackedModule;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;

import com.google.common.base.Preconditions;

import scratch.UCERF3.utils.FaultSystemIO;

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
	
	public static FaultSystemSolution load(File file) throws IOException {
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(file, FaultSystemSolution.class);
		
		FaultSystemSolution sol = archive.getModule(FaultSystemSolution.class);
		Preconditions.checkState(sol != null, "Failed to load solution module from archive (see above error messages)");
		Preconditions.checkNotNull(sol.rupSet, "rupture set not loaded?");
		Preconditions.checkNotNull(sol.archive, "archive should have been set automatically");
		
		return sol;
	}

	@Override
	public String getName() {
		return "Solution";
	}

	@Override
	protected String getNestingPrefix() {
		return "solution/";
	}

	@Override
	public final void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		CSVFile<String> ratesCSV = new CSVFile<>(true);
		ratesCSV.addLine("Rupture Index", "Annual Rate");
		for (int r=0; r<rates.length; r++)
			ratesCSV.addLine(r+"", rates[r]+"");
		
		// CSV Files
		CSV_BackedModule.writeToArchive(ratesCSV, zout, entryPrefix, "rates.csv");
	}

	@Override
	public final void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		Preconditions.checkNotNull(archive, "archive must be set before initialization");
		if (rupSet == null)
			rupSet = archive.getModule(FaultSystemRupSet.class);
		Preconditions.checkNotNull(rupSet, "Rupture set not found in archive");
		
		System.out.println("\tLoading rates CSV...");
		CSVFile<String> ratesCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, "rates.csv");
		rates = new double[rupSet.getNumRuptures()];
		Preconditions.checkState(ratesCSV.getNumRows() == rupSet.getNumRuptures()+1, "Unexpected number of rows in rates CSV");
		for (int r=0; r<rates.length; r++) {
			Preconditions.checkState(ratesCSV.getInt(r+1, 0) == r, "Rates CSV out of order or not 0-based");
			rates[r] = ratesCSV.getDouble(r+1, 1);
		}
	}

	@Override
	public final Class<? extends ArchivableModule> getLoadingClass() {
		// never let a subclass supply it's own loader, must always load as a regular FaultSystemSolution
		return FaultSystemSolution.class;
	}

	@Override
	public void setParent(ModuleArchive<OpenSHA_Module> parent) throws IllegalStateException {
		FaultSystemSolution oSol = parent.getModule(FaultSystemSolution.class);
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
		FaultSystemSolution copy = new FaultSystemSolution(newArchive.getModule(FaultSystemRupSet.class),
				Arrays.copyOf(rates, rates.length));
		loadAllAvailableModules();
		for (OpenSHA_Module module : getModules())
			copy.addModule(module);
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
	
	public static void main(String[] args) throws Exception {
		File baseDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		
		File inputFile = new File(baseDir, "fm3_1_ucerf3.zip");
		File destFile = new File("/tmp/new_format_u3.zip");
		FaultSystemSolution orig = FaultSystemIO.loadSol(inputFile);
		orig.getArchive().write(destFile);
		FaultSystemSolution loaded = load(destFile);
		Preconditions.checkState(orig.rupSet.isEquivalentTo(loaded.rupSet));
	}

}
