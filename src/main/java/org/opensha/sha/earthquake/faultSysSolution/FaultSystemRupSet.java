package org.opensha.sha.earthquake.faultSysSolution;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVReader;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectAreas;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SplittableRuptureModule;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.util.SlipAlongRuptureModelBranchNode;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.U3FaultSystemIO;

/**
 * This class represents the attributes of ruptures in a fault system, 
 * where the latter is composed of some number of fault sections.
 * <p>
 * Only the core fields common to rupture sets from all models are included. Extra attributes can be attached to this
 * rupture set as modules instances. Examples of modules include: logic tree branches, gridded seismicity,
 * plausibility configurations, cluster rupture representations, RSQSim event mappings
 * 
 * @author Field, Milner, Page, & Powers
 *
 */
public class FaultSystemRupSet extends ModuleContainer<OpenSHA_Module> implements ArchivableModule,
SubModule<ModuleArchive<OpenSHA_Module>> {

	// data arrays/lists
	private List<? extends FaultSection> faultSectionData;
	private double[] mags;
	private double[] rakes;
	private double[] rupAreas;
	private double[] rupLengths;
	private List<List<Integer>> sectionForRups;
	
	// archive that this came from
	ModuleArchive<OpenSHA_Module> archive;
	
	/**
	 * Protected constructor, must call one of the init(...) methods externally
	 */
	protected FaultSystemRupSet() {
		
	}

	/**
	 * Initialized a FaultSystemRupSet object with all core data.
	 * 
	 * @param faultSectionData fault section data list (CANNOT be null)
	 * @param sectionForRups list of fault section indexes for each rupture (CANNOT be null)
	 * @param mags magnitudes for each rupture (CANNOT be null)
	 * @param rakes rakes for each rupture (CANNOT be null)
	 * @param rupAreas areas for each rupture (CANNOT be null)
	 * @param rupLengths lengths for each rupture (CAN be null)
	 * @param info metadata string
	 */
	public FaultSystemRupSet(
			List<? extends FaultSection> faultSectionData,
			List<List<Integer>> sectionForRups,
			double[] mags,
			double[] rakes,
			double[] rupAreas,
			@Nullable double[] rupLengths) {
		init(faultSectionData, sectionForRups, mags, rakes, rupAreas, rupLengths);
		
		// track the version of OpenSHA this was generated with
		if (!hasModule(BuildInfoModule.class)) {
			try {
				addModule(BuildInfoModule.detect());
			} catch (Exception e) {}
		}
	}
	
	/**
	 * Returns an archive that contains this rupture set. If this was loaded from an archive, this returns that
	 * original archive (which may also contain other things, such as a solution), otherwise a new archive is returned
	 * that only contains this rupture set.
	 * 
	 * @return archive containing this rupture set
	 */
	public ModuleArchive<OpenSHA_Module> getArchive() {
		if (archive == null)
			archive = new ModuleArchive<>();
		FaultSystemRupSet rupSet = archive.getModule(FaultSystemRupSet.class);
		if (rupSet == null)
			archive.addModule(this);
		else
			Preconditions.checkState(rupSet == this);
		return archive;
	}
	
	/**
	 * Writes this rupture set to a zip file. This is an alias to <code>getArchive().write(File)</code>.
	 * See {@link #getArchive()}.
	 * @param file
	 * @throws IOException
	 */
	public void write(File file) throws IOException {
		getArchive().write(file);
	}

	@Override
	public void setParent(ModuleArchive<OpenSHA_Module> parent) throws IllegalStateException {
		if (parent != null) {
			FaultSystemRupSet oRupSet = parent.getModule(FaultSystemRupSet.class, false);
			Preconditions.checkState(oRupSet == null || oRupSet == this);
		}
		this.archive = parent;
	}

	@Override
	public ModuleArchive<OpenSHA_Module> getParent() {
		return archive;
	}

	@Override
	public SubModule<ModuleArchive<OpenSHA_Module>> copy(ModuleArchive<OpenSHA_Module> newArchive)
			throws IllegalStateException {
		if (this.archive == null) {
			// just set the archive and return this
			this.archive = newArchive;
			return this;
		}
		// copy it to a new archive
		FaultSystemRupSet copy = new FaultSystemRupSet();
		copy.init(this);
		newArchive.addModule(copy);
		return copy;
	}
	
	/**
	 * Loads a FaultSystemRupSet from a zip file
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemRupSet load(File file) throws IOException {
		return load(ArchiveInput.getDefaultInput(file));
	}
	
	/**
	 * Loads a FaultSystemRupSet from a zip file
	 * 
	 * @param zip
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemRupSet load(ZipFile zip) throws IOException {
		// see if it's an old rupture set
		if (zip.getEntry("rup_sections.bin") != null) {
			System.err.println("WARNING: this is a legacy fault system rupture set, that file format is deprecated. "
					+ "Will attempt to load it using the legacy file loader. "
					+ "See https://opensha.org/File-Formats for more information.");
			try {
				return U3FaultSystemIO.loadRupSetAsApplicable(zip, null);
			} catch (DocumentException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		return load(new ArchiveInput.ZipFileInput(zip));
	}
	
	/**
	 * Loads a FaultSystemRupSet from the given input
	 * 
	 * @param zip
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemRupSet load(ArchiveInput input) throws IOException {
		if (input.hasEntry("rup_sections.bin")) {
			System.err.println("WARNING: this is a legacy fault system rupture set, that file format is deprecated. "
					+ "Will attempt to load it using the legacy file loader. "
					+ "See https://opensha.org/File-Formats for more information.");
			Preconditions.checkState(input instanceof ArchiveInput.FileBacked,
					"Can only do a deprecated load from zip files (this isn't file-backed)");
			try {
				return U3FaultSystemIO.loadRupSetAsApplicable(((ArchiveInput.FileBacked)input).getInputFile());
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(input, FaultSystemRupSet.class);
		
		FaultSystemRupSet rupSet = archive.getModule(FaultSystemRupSet.class);
		if (rupSet == null) {
			if (!input.hasEntry(ModuleArchive.MODULE_FILE_NAME) && input.hasEntry("ruptures/"+RUP_SECTS_FILE_NAME)) {
				// missing modules.json, try to load it as an unlisted module
				System.err.println("WARNING: rupture set archive is missing modules.json, trying to load it anyway");
				archive.loadUnlistedModule(FaultSystemRupSet.class, "ruptures/");
				rupSet = archive.getModule(FaultSystemRupSet.class);
			}
		}
		Preconditions.checkState(rupSet != null, "Failed to load rupture set module from archive (see above error messages)");
		Preconditions.checkNotNull(rupSet.archive, "archive should have been set automatically");
		
		return rupSet;
	}

	@Override
	public String getName() {
		return "Rupture Set";
	}
	
	public static final String NESTING_PREFIX = "ruptures/";

	@Override
	protected String getNestingPrefix() {
		return NESTING_PREFIX;
	}
	
	public static final String SECTS_FILE_NAME = "fault_sections.geojson";

	@Override
	public final void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		// CSV Files
		FileBackedModule.initEntry(output, entryPrefix, RUP_SECTS_FILE_NAME);
		CSVWriter csvWriter = new CSVWriter(output.getOutputStream(), false);
		buildRupSectsCSV(this, csvWriter);
		csvWriter.flush();
		output.closeEntry();

		FileBackedModule.initEntry(output, entryPrefix, RUP_PROPS_FILE_NAME);
		csvWriter = new CSVWriter(output.getOutputStream(), true);
		new RuptureProperties(this).buildCSV(csvWriter);
		csvWriter.flush();
		output.closeEntry();
		
		// fault sections
		FileBackedModule.initEntry(output, entryPrefix, SECTS_FILE_NAME);
		OutputStreamWriter writer = new OutputStreamWriter(output.getOutputStream());
		GeoJSONFaultReader.writeFaultSections(writer, faultSectionData);
		writer.flush();
		output.closeEntry();
		
		// write README
		FileBackedModule.initEntry(output, null, "README");
		writer = new OutputStreamWriter(output.getOutputStream());
		BufferedWriter readme = new BufferedWriter(writer);
		FaultSystemSolution solution = this.archive == null ? null : this.archive.getModule(FaultSystemSolution.class);
		if (solution != null) {
			readme.write("This is an OpenSHA Fault System Solution zip file.\n\n");
		} else {
			readme.write("This is an OpenSHA Fault System Rupture Set zip file.\n\n");
		}
		readme.write("The file format is described in detail at https://opensha.org/Modular-Fault-System-Solution\n\n");
		readme.write("Rupture information is stored in the '"+entryPrefix+"' sub-directory. Optional files may exist, "
				+ "but the core (required) files are:\n");
		readme.write(" - "+ArchivableModule.getEntryName(entryPrefix, SECTS_FILE_NAME
				+": GeoJSON file listing the fault trace and properties of each fault section\n"));
		readme.write(" - "+ArchivableModule.getEntryName(entryPrefix, RUP_SECTS_FILE_NAME
				+": CSV file listing the fault section indices that comprise each rupture\n"));
		readme.write(" - "+ArchivableModule.getEntryName(entryPrefix, RUP_PROPS_FILE_NAME
				+": CSV file listing the properties of each rupture, including magnitude and rake\n"));
		if (solution != null) {
			readme.write("\n");
			String solPrefix = solution.getNestingPrefix();
			readme.write("Rate information is stored in the '"+solPrefix+"' sub-directory. "
					+ "Optional files may exist, but there is only one required file:\n");
			readme.write(" - "+ArchivableModule.getEntryName(solPrefix, FaultSystemSolution.RATES_FILE_NAME
					+": CSV file giving the annual rate of occurrence for each rupture\n"));
			GridSourceProvider gridProv = solution.getModule(GridSourceProvider.class);
			if (gridProv != null) {
				readme.write("This solution has optional gridded seismicity information. Files related to that are:\n");
				if (gridProv instanceof MFDGridSourceProvider) {
					readme.write(" - "+ArchivableModule.getEntryName(solPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME
							+": GeoJSON file giving the location of each gridded seismicity node\n"));
					readme.write(" - "+ArchivableModule.getEntryName(solPrefix, MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME
							+": CSV file giving the relative weights of each gridded seismicity focal mechanism at each grid node\n"));
					readme.write(" - "+ArchivableModule.getEntryName(solPrefix, MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME
							+": CSV file giving the magnitude-frequency distribution of sub-seismogenic ruptures at each gridded "
							+ "seismicity node that are associated with at least one fault\n"));
					readme.write(" - "+ArchivableModule.getEntryName(solPrefix, MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME
							+": CSV file giving the magnitude-frequency distribution of off-fault ruptures at each gridded "
							+ "seismicity node (those that are not associated with at any fault)\n"));
				} else if (gridProv instanceof GridSourceList) {
					if (gridProv.getGriddedRegion() != null)
						readme.write(" - "+ArchivableModule.getEntryName(solPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME
								+": Optional GeoJSON defining the region for which this gridded seismicity model applies\n"));
					
					readme.write(" - "+ArchivableModule.getEntryName(solPrefix, GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME
							+": CSV file giving the index and location and of each gridded seismicity source\n"));
					readme.write(" - "+ArchivableModule.getEntryName(solPrefix, GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME
							+": CSV file listing each gridded seismicity rupture. Grid indexes in this file reference the "
							+ "locations listed in "+ArchivableModule.getEntryName(solPrefix, GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)+"\n"));
				}
			}
			
		}
		readme.flush();
		writer.flush();
		output.closeEntry();
	}
	
	public static final String RUP_SECTS_FILE_NAME = "indices.csv";


	public static void buildRupSectsCSV(FaultSystemRupSet rupSet, CSVWriter writer) throws IOException {
		int maxNumSects = 0;
		for (int r = 0; r < rupSet.getNumRuptures(); r++)
			maxNumSects = Integer.max(maxNumSects, rupSet.getSectionsIndicesForRup(r).size());

		List<String> header = new ArrayList<>(List.of("Rupture Index", "Num Sections"));

		for (int s = 0; s < maxNumSects; s++)
			header.add("# " + (s + 1));

		writer.write(header);

		for (int r = 0; r < rupSet.getNumRuptures(); r++) {
			List<Integer> sectIDs = rupSet.getSectionsIndicesForRup(r);
			List<String> line = new ArrayList<>(2 + sectIDs.size());

			line.add(r + "");
			line.add(sectIDs.size() + "");
			for (int s : sectIDs)
				line.add(s + "");
			writer.write(line);
		}
		
		writer.flush();
	}
	
	public static final String RUP_PROPS_FILE_NAME = "properties.csv";
	
	public static class RuptureProperties {
		public final double[] mags;
		public final double[] rakes;
		public final double[] areas;
		public final double[] lengths;
		
		public RuptureProperties(CSVFile<String> rupPropsCSV) {
			int numRuptures = rupPropsCSV.getNumRows()-1;
			double[] mags = new double[numRuptures];
			double[] rakes = new double[numRuptures];
			double[] areas = new double[numRuptures];
			double[] lengths = null;
			for (int r=0; r<numRuptures; r++) {
				int row = r+1;
				int col = 0;
				// load rupture properties
				Preconditions.checkState(r == rupPropsCSV.getInt(row, col++),
						"Ruptures out of order or not 0-based in CSV file, expected id=%s at row %s", r, row);
				mags[r] = rupPropsCSV.getDouble(row, col++);
				rakes[r] = rupPropsCSV.getDouble(row, col++);
				areas[r] = rupPropsCSV.getDouble(row, col++);
				String lenStr = rupPropsCSV.get(row, col++);
				if ((r == 0 && !lenStr.isBlank())) {
					lengths = new double[numRuptures];
					lengths[r] = Double.parseDouble(lenStr);
				} else {
					if (lengths != null)
						lengths[r] = Double.parseDouble(lenStr);
					else
						Preconditions.checkState(lenStr.isBlank(),
								"Rupture lenghts must be populated for all ruptures, or omitted for all. We have a length for "
								+ "rupture %s but the first rupture did not have a length.", r);
				}
			}
			
			this.mags = mags;
			this.rakes = rakes;
			this.areas = areas;
			this.lengths = lengths;
		}
		
		public RuptureProperties(FaultSystemRupSet rupSet) {
			int numRuptures = rupSet.getNumRuptures();
			this.mags = new double[numRuptures];
			this.rakes = new double[numRuptures];
			this.areas = new double[numRuptures];
			this.lengths = rupSet.getLengthForAllRups();
			for (int r=0; r<numRuptures; r++) {
				mags[r] = rupSet.getMagForRup(r);
				rakes[r] = rupSet.getAveRakeForRup(r);
				areas[r] = rupSet.getAreaForRup(r);
			}
		}

		public void buildCSV(CSVWriter writer) throws IOException {

			List<String> header = new ArrayList<>(List.of("Rupture Index", "Magnitude", "Average Rake (degrees)",
					"Area (m^2)", "Length (m)"));

			writer.write(header);

			for (int r = 0; r < mags.length; r++) {
				List<String> line = new ArrayList<>(5);

				line.add(r + "");
				line.add(mags[r] + "");
				line.add(rakes[r] + "");
				line.add(areas[r] + "");
				if (lengths == null)
					line.add("");
				else
					line.add(lengths[r] + "");
				writer.write(line);
			}
			
			writer.flush();
		}
	}
	
	public static List<List<Integer>> loadRupSectsCSV(CSVReader rupSectsCSV, int numSections, int numRuptures) {
		List<List<Integer>> rupSectsList = new ArrayList<>(numRuptures);
		boolean shortSafe = numSections < Short.MAX_VALUE;
		rupSectsCSV.read(); // skip header row
		for (int r=0; r<numRuptures; r++) {
			int row = r+1;
			int col = 0;
			CSVReader.Row csvRow = rupSectsCSV.read();
			Preconditions.checkState(csvRow != null, "Ruptures CSV file has too few rows.");
			// load rupture sections
			Preconditions.checkState(r == csvRow.getInt(col++),
					"Ruptures out of order or not 0-based in CSV file, expected id=%s at row %s", r, row);
			int numRupSects = csvRow.getInt(col++);
			Preconditions.checkState(numRupSects > 0, "Rupture %s has no sections!", r);
			List<Integer> rupSects;
			if (shortSafe) {
				short[] sectIDs = new short[numRupSects];
				for (int i=0; i<numRupSects; i++)
					sectIDs[i] = (short)csvRow.getInt(col++);
				rupSects = new ShortListWrapper(sectIDs);
			} else {
				int[] sectIDs = new int[numRupSects];
				for (int i=0; i<numRupSects; i++)
					sectIDs[i] = csvRow.getInt(col++);
				rupSects = new IntListWrapper(sectIDs);
			}
			int rowSize = csvRow.getLine().size();
			while (col < rowSize) {
				// make sure any further columns are empty
				String str = csvRow.get(col++);
				Preconditions.checkState(str.isBlank(),
						"Rupture has %s sections, but data exists in %s column %s: %s", RUP_SECTS_FILE_NAME, col, str);
			}
			for (int sectID : rupSects)
				Preconditions.checkState(sectID >= 0 && sectID < numSections,
						"Bad sectionID=%s for rupture %s", sectID, r);
			rupSectsList.add(rupSects);
		}
		Preconditions.checkState(rupSectsCSV.read() == null, "Rupture CSV file has too many rows.");

		try {
			rupSectsCSV.close();
		}catch(IOException x) {
			throw new RuntimeException(x);
		}

		return rupSectsList;
	}

	@Override
	public final void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		System.out.println("\tLoading ruptures CSV...");
		CSVReader rupSectsCSV = CSV_BackedModule.loadLargeFileFromArchive(input, entryPrefix, RUP_SECTS_FILE_NAME);
		CSVFile<String> rupPropsCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, RUP_PROPS_FILE_NAME);
		
		// fault sections
		List<GeoJSONFaultSection> sections = GeoJSONFaultReader.readFaultSections(
				new InputStreamReader(FileBackedModule.getInputStream(input, entryPrefix, SECTS_FILE_NAME)));
		for (int s=0; s<sections.size(); s++)
			Preconditions.checkState(sections.get(s).getSectionId() == s,
			"Fault sections must be provided in order starting with ID=0");

		// load rupture data
		System.out.println("\tParsing rupture properties CSV");
		RuptureProperties props = new RuptureProperties(rupPropsCSV);
		System.out.println("\tParsing rupture sections CSV");
		List<List<Integer>> rupSectsList = loadRupSectsCSV(rupSectsCSV, sections.size(), props.mags.length);

		int numRuptures = rupSectsList.size();
		Preconditions.checkState(numRuptures > 0, "No ruptures found in CSV file");
		Preconditions.checkState(numRuptures + 1 == rupPropsCSV.getNumRows(),
				"Rupture sections and properites CSVs have different lengths");
		
		init(sections, rupSectsList, props.mags, props.rakes, props.areas, props.lengths);
		
		boolean hasManifest = input.hasEntry(entryPrefix+ModuleArchive.MODULE_FILE_NAME);
		if (archive != null) {
			// see if any common modules are are present but unlised (either because modules.json is missing, or someone
			// added them manually)
			if (input.hasEntry(entryPrefix+SectAreas.DATA_FILE_NAME) && !hasAvailableModule(SectAreas.Precomputed.class)) {
				// make sure it really was just the default implementation (and not some other implementation)
				boolean doLoad = !hasManifest || getModule(SectAreas.class) instanceof SectAreas.Default;
				if (doLoad) {
					try {
						System.out.println("Trying to load unlisted precomputed SectAreas module");
						archive.loadUnlistedModule(SectAreas.Precomputed.class, entryPrefix, this);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			if (input.hasEntry(entryPrefix+SectSlipRates.DATA_FILE_NAME) && !hasAvailableModule(SectSlipRates.Precomputed.class)) {
				// make sure it really was just the default implementation (and not some other implementation)
				boolean doLoad = !hasManifest || getModule(SectSlipRates.class) instanceof SectSlipRates.Default;
				if (doLoad) {
					try {
						System.out.println("Trying to load unlisted SectSlipRates module");
						archive.loadUnlistedModule(SectSlipRates.Precomputed.class, entryPrefix, this);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			if (input.hasEntry(entryPrefix+AveSlipModule.DATA_FILE_NAME) && !hasAvailableModule(AveSlipModule.class)) {
				try {
					System.out.println("Trying to load unlisted AveSlipModule module");
					archive.loadUnlistedModule(AveSlipModule.Precomputed.class, entryPrefix, this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			if (input.hasEntry(entryPrefix+RupSetTectonicRegimes.DATA_FILE_NAME) && !hasAvailableModule(RupSetTectonicRegimes.class)) {
				try {
					System.out.println("Trying to load unlisted RupSetTectonicRegimes module");
					archive.loadUnlistedModule(RupSetTectonicRegimes.class, entryPrefix, this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Memory efficient list that is backed by an array of short values for memory efficiency
	 * @author kevin
	 *
	 */
	public static class ShortListWrapper extends AbstractList<Integer> {
		
		private short[] vals;
		
		public ShortListWrapper(List<Integer> ints) {
			vals = new short[ints.size()];
			for (int i=0; i<vals.length; i++) {
				int val = ints.get(i);
				Preconditions.checkState(val < Short.MAX_VALUE);
				vals[i] = (short)val;
			}
		}
		
		public ShortListWrapper(short[] vals) {
			this.vals = vals;
		}

		@Override
		public Integer get(int index) {
			return (int)vals[index];
		}

		@Override
		public int size() {
			this.equals(null);
			return vals.length;
		}
		
		public boolean equals(Object o) {
	        if (o == this)
	            return true;
	        if (!(o instanceof List))
	            return false;
	        List<?> oList = (List<?>)o;
	        if (size() != oList.size())
	        	return false;
	        if (size() == 0)
	        	return true;
	        if (!Integer.class.isAssignableFrom(oList.get(0).getClass()))
	        	return false;
	        
	        for (int i=0; i<vals.length; i++)
	        	if ((int)vals[i] != ((Integer)oList.get(i)).intValue())
	        		return false;
	        return true;
	    }
		
	}
	
	/**
	 * Memory efficient list that is backed by an array of integer values for memory efficiency
	 * @author kevin
	 *
	 */
	public static class IntListWrapper extends AbstractList<Integer> {
		
		private int[] vals;
		
		public IntListWrapper(List<Integer> ints) {
			vals = new int[ints.size()];
			for (int i=0; i<vals.length; i++)
				vals[i] = ints.get(i);
		}
		
		public IntListWrapper(int[] vals) {
			this.vals = vals;
		}

		@Override
		public Integer get(int index) {
			return vals[index];
		}

		@Override
		public int size() {
			this.equals(null);
			return vals.length;
		}
		
		public boolean equals(Object o) {
	        if (o == this)
	            return true;
	        if (!(o instanceof List))
	            return false;
	        List<?> oList = (List<?>)o;
	        if (size() != oList.size())
	        	return false;
	        if (size() == 0)
	        	return true;
	        if (!Integer.class.isAssignableFrom(oList.get(0).getClass()))
	        	return false;
	        
	        for (int i=0; i<vals.length; i++)
	        	if (vals[i] != ((Integer)oList.get(i)).intValue())
	        		return false;
	        return true;
	    }
		
	}

	@Override
	public final Class<? extends ArchivableModule> getLoadingClass() {
		// never let a subclass supply it's own loader, must always load as a regular FaultSystemRupSet
		return FaultSystemRupSet.class;
	}
	
	/**
	 * Initialize from another rupSet
	 * @param rupSet
	 */
	protected void init(FaultSystemRupSet rupSet) {
		init(rupSet.getFaultSectionDataList(), rupSet.getSectionIndicesForAllRups(), rupSet.getMagForAllRups(),
				rupSet.getAveRakeForAllRups(), rupSet.getAreaForAllRups(), rupSet.getLengthForAllRups());
		copyCacheFrom(rupSet);
		for (OpenSHA_Module module : rupSet.getModules(true))
			addModule(module);
	}
	
	/**
	 * Sets all parameters
	 * 
	 * @param faultSectionData fault section data list (CANNOT be null)
	 * @param sectionForRups list of fault section indexes for each rupture (CANNOT be null)
	 * @param mags magnitudes for each rupture (CANNOT be null)
	 * @param rakes rakes for each rupture (CANNOT be null)
	 * @param rupAreas areas for each rupture (CANNOT be null)
	 * @param rupLengths lengths for each rupture (CAN be null)
	 */
	protected void init(
			List<? extends FaultSection> faultSectionData,
			List<List<Integer>> sectionForRups,
			double[] mags,
			double[] rakes,
			double[] rupAreas,
			double[] rupLengths) {
		Preconditions.checkNotNull(faultSectionData, "Fault Section Data cannot be null");
		int numSects = faultSectionData.size();
		for (int s=0; s<numSects; s++) {
			FaultSection sect = faultSectionData.get(s);
			Preconditions.checkNotNull(sect, "Section %s is null", s);
			Preconditions.checkState(sect.getSectionId() == s,
					"Section indexes and IDs must match. Instead, section %s has ID %s with name: %s",
					s, sect.getSectionId(), sect.getSectionName());
		}
		this.faultSectionData = faultSectionData;
		Preconditions.checkNotNull(faultSectionData, "Magnitudes cannot be null");
		this.mags = mags;
		
		int numRups = mags.length;
		
		Preconditions.checkArgument(rakes.length == numRups, "array sizes inconsistent!");
		this.rakes = rakes;
		
		Preconditions.checkArgument(rupAreas == null ||
				rupAreas.length == numRups, "array sizes inconsistent!");
		this.rupAreas = rupAreas;
		
		Preconditions.checkArgument(rupLengths == null ||
				rupLengths.length == numRups, "array sizes inconsistent!");
		if (rupLengths == null)
			rupLengths = rupLengthsDefault(faultSectionData, sectionForRups);
		this.rupLengths = rupLengths;
		
		Preconditions.checkArgument(sectionForRups.size() == numRups, "array sizes inconsistent!");
		for (int r=0; r<numRups; r++) {
			for (int s : sectionForRups.get(r)) {
				Preconditions.checkState(s >= 0 && s < numSects,
						"Bad sectIndex=%s in sectionForRups for rupIndex=%s", s, r);
			}
		}
		this.sectionForRups = sectionForRups;
		
		// add default model implementations, but only if not already set
		if (!hasAvailableModule(SectAreas.class)) {
			addAvailableModule(new Callable<SectAreas>() {

				@Override
				public SectAreas call() throws Exception {
					return SectAreas.fromFaultSectData(FaultSystemRupSet.this);
				}
				
			}, SectAreas.Default.class);
		}
		if (!hasAvailableModule(SectSlipRates.class)) {
			addAvailableModule(new Callable<SectSlipRates>() {

				@Override
				public SectSlipRates call() throws Exception {
					return SectSlipRates.fromFaultSectData(FaultSystemRupSet.this);
				}
				
			}, SectSlipRates.Default.class);
		}
		if (!hasAvailableModule(SlipAlongRuptureModel.class)) {
			addAvailableModule(new Callable<SlipAlongRuptureModel>() {

				@Override
				public SlipAlongRuptureModel call() throws Exception {
					// see if we have a logic tree branch
					LogicTreeBranch<?> branch = getModule(LogicTreeBranch.class);
					if (branch != null && branch.hasValue(SlipAlongRuptureModelBranchNode.class))
						return branch.getValue(SlipAlongRuptureModelBranchNode.class).getModel();
					// add default (uniform) slip along rupture model
					return new SlipAlongRuptureModel.Default();
				}
			}, SlipAlongRuptureModel.class);
		}
	}

	// if true, caching operations will show a graphical progress bar
	protected boolean showProgress = false;

	/**
	 * This enables/disables visible progress bars for long calculations
	 * 
	 * @param showProgress
	 */
	public void setShowProgress(boolean showProgress) {
		this.showProgress = showProgress;
	}

	public boolean isShowProgress() {
		return showProgress;
	}

	public void clearCache() {
		rupturesForSectionCache.clear();
		rupturesForParentSectionCache.clear();
		fractRupsInsideRegions.clear();
		fractSectsInsideRegions.clear();
	}

	public void copyCacheFrom(FaultSystemRupSet rupSet) {
		if (rupSet.getNumRuptures() != getNumRuptures() || rupSet.getNumSections() != getNumSections())
			return;
		rupturesForSectionCache = rupSet.rupturesForSectionCache;
		rupturesForParentSectionCache = rupSet.rupturesForParentSectionCache;
		fractRupsInsideRegions = rupSet.fractRupsInsideRegions;
		fractSectsInsideRegions = rupSet.fractSectsInsideRegions;
	}

	/**
	 * The total number of ruptures in the fault system
	 * @return
	 */
	public int getNumRuptures() {
		return mags.length;
	}

	/**
	 * The total number of ruptures in the fault system
	 * @return
	 */
	public int getNumSections() {
		return faultSectionData.size();
	}

	/**
	 * This returns which sections are used by the each rupture
	 * @param rupIndex
	 * @return
	 */
	public List<List<Integer>> getSectionIndicesForAllRups() {
		return sectionForRups;
	}

	/**
	 * This returns which sections are used by the rth rupture
	 * @param rupIndex
	 * @return
	 */
	public List<Integer> getSectionsIndicesForRup(int rupIndex) {
		return sectionForRups.get(rupIndex);
	}
	
	private double[] minMagsCache = null;
	private double[] maxMagsCache = null;
	
	private void checkInitMagsCaches() {
		if (minMagsCache == null) {
			synchronized (this) {
				if (minMagsCache == null) {
					double[] mins = new double[getNumSections()];
					for (int s=0; s<mins.length; s++)
						mins[s] = Double.POSITIVE_INFINITY;
					double[] maxs = new double[getNumSections()];
					
					for (int rupIndex=0; rupIndex<getNumRuptures(); rupIndex++) {
						double mag = getMagForRup(rupIndex);
						for (int sectIndex : getSectionsIndicesForRup(rupIndex)) {
							if (mag > maxs[sectIndex])
								maxs[sectIndex] = mag;
							if (mag < mins[sectIndex])
								mins[sectIndex] = mag;
						}
					}
					for (int s=0; s<mins.length; s++) {
						if (Double.isInfinite(mins[s])) {
							// no ruptures for this section
							mins[s] = Double.NaN;
							maxs[s] = Double.NaN;
						}
					}
					
					maxMagsCache = maxs;
					minMagsCache = mins;
				}
			}
		}
	}

	/**
	 * This returns the magnitude of the largest rupture involving this section or NaN
	 * if no ruptures involve this section.
	 * @param sectIndex
	 * @return
	 */
	public double getMaxMagForSection(int sectIndex) {
		checkInitMagsCaches();
		return maxMagsCache[sectIndex];
	}
	
	/**
	 * This returns the magnitude of the smallest rupture involving this section or NaN
	 * if no ruptures involve this section.
	 * @param sectIndex
	 * @return
	 */
	public double getMinMagForSection(int sectIndex) {
		checkInitMagsCaches();
		return minMagsCache[sectIndex];
	}

	/**
	 * This gives the magnitude for each rth rupture
	 * @return
	 */
	public double[] getMagForAllRups() {
		return mags;
	}

	/**
	 * This gives the magnitude for the rth rupture
	 * @param rupIndex
	 * @return
	 */
	public double getMagForRup(int rupIndex) {
		return mags[rupIndex];
	}

	/**
	 * This gives the average rake for all ruptures
	 * @return
	 */
	public double[] getAveRakeForAllRups() {
		return rakes;
	}

	/**
	 * This gives the average rake for the rth rupture
	 * @param rupIndex
	 * @return
	 */
	public double getAveRakeForRup(int rupIndex) {
		return rakes[rupIndex];
	}

	/**
	 * @return Area (SI units: sq-m)
	 */
	public double[] getAreaForAllRups() {
		return rupAreas;
	}

	/**
	 * @param rupIndex
	 * @return Area (SI units: sq-m)
	 */
	public double getAreaForRup(int rupIndex) {
		return rupAreas[rupIndex];
	}
	
	private SectAreas getSectAreas() {
		if (!hasModule(SectAreas.class))
			addModule(SectAreas.fromFaultSectData(this));
		return requireModule(SectAreas.class);
	}

	/**
	 * @return Area (SI units: sq-m)
	 */
	public double[] getAreaForAllSections() {
		return getSectAreas().getSectAreas();
	}

	/**
	 * @param sectIndex
	 * @return Area (SI units: sq-m)
	 */
	public double getAreaForSection(int sectIndex) {
		return getSectAreas().getSectArea(sectIndex);
	}

	/**
	 * This returns a list of all fault-section data
	 * @return
	 */
	public List<? extends FaultSection> getFaultSectionDataList() {
		return faultSectionData;
	}

	/**
	 * The returns the fault-section data for the sth section
	 * @param sectIndex
	 * @return
	 */
	public FaultSection getFaultSectionData(int sectIndex) {
		return faultSectionData.get(sectIndex);
	}

	/**
	 * This gets a list of fault-section data for the specified rupture
	 * @param rupIndex
	 * @return
	 */
	public List<FaultSection> getFaultSectionDataForRupture(int rupIndex) {
		List<Integer> inds = getSectionsIndicesForRup(rupIndex);
		ArrayList<FaultSection> datas = new ArrayList<FaultSection>(inds.size());
		for (int ind : inds)
			datas.add(getFaultSectionData(ind));
		return datas;
	}

	private class RupSurfaceCache {
		private double prevGridSpacing = Double.NaN;
		private boolean prevAseisReducesArea = false;
		private Map<Integer, RuptureSurface> rupSurfaceCache;

		private synchronized RuptureSurface getSurfaceForRupture(int rupIndex, double gridSpacing, boolean aseisReducesArea) {
			if (rupSurfaceCache == null)
				rupSurfaceCache = new HashMap<>();
			if (prevGridSpacing != gridSpacing || aseisReducesArea != prevAseisReducesArea) {
				rupSurfaceCache.clear();
				prevGridSpacing = gridSpacing;
				prevAseisReducesArea = aseisReducesArea;
			}
			RuptureSurface surf = rupSurfaceCache.get(rupIndex);
			if (surf != null)
				return surf;
			List<FaultSection> fltDatas =  getFaultSectionDataForRupture(rupIndex);
			List<RuptureSurface> rupSurfs = new ArrayList<>(fltDatas.size());
			for (FaultSection fltData : fltDatas)
				rupSurfs.add(fltData.getFaultSurface(gridSpacing, false, aseisReducesArea));
			if (rupSurfs.size() == 1)
				surf = rupSurfs.get(0);
			else
				surf = new CompoundSurface(rupSurfs);
			rupSurfaceCache.put(rupIndex, surf);
			return surf;
		}
	}

	protected transient RupSurfaceCache surfCache = new RupSurfaceCache();

	/**
	 * This creates a CompoundGriddedSurface for the specified rupture.  This applies aseismicity as
	 * a reduction of area and sets preserveGridSpacingExactly=false so there are no cut-off ends
	 * (but variable grid spacing)
	 * @param rupIndex
	 * @param gridSpacing
	 * @return
	 */
	public RuptureSurface getSurfaceForRupture(int rupIndex, double gridSpacing) {
		return getSurfaceForRupture(rupIndex, gridSpacing, true);
	}

	/**
	 * This creates a CompoundGriddedSurface for the specified rupture.  This sets preserveGridSpacingExactly=false so 
	 * there are no cut-off ends (but variable grid spacing).
	 * 
	 *  If <code>aseisReducesArea</code> is true, it applies aseismicity as a reduction of area.
	 * @param rupIndex
	 * @param gridSpacing
	 * @param aseisReducesArea
	 * @return
	 */
	public RuptureSurface getSurfaceForRupture(int rupIndex, double gridSpacing, boolean aseisReducesArea) {
		return surfCache.getSurfaceForRupture(rupIndex, gridSpacing, aseisReducesArea);
	}

	/**
	 * This returns the length (SI units: m) of each rupture.
	 * @return
	 */
	public double[] getLengthForAllRups() {
		return rupLengths;
	}

	/**
	 * This returns the length (SI units: m) of the specified rupture.
	 * @param rupIndex
	 * @return
	 */
	public double getLengthForRup(int rupIndex) {
		return rupLengths[rupIndex];
	}

	/**
	 * This returns the width (SI units: m) of the specified rupture 
	 * (calculated as getAreaForRup(rupIndex)/getLengthForRup(rupIndex))
	 * @param rupIndex
	 * @return
	 */
	public double getAveWidthForRup(int rupIndex) {
		return getAreaForRup(rupIndex)/getLengthForRup(rupIndex);
	}
	
	/**
	 * This returns section slip rates. If no custom slip rates (e.g., after adjustment for sub-seismogenic ruptures)
	 * have been loaded, then slip rates will be calculated from fault section data.
	 * 
	 * @return section slip rates
	 */
	public SectSlipRates getSectSlipRates() {
		if (!hasModule(SectSlipRates.class))
			addModule(SectSlipRates.fromFaultSectData(this));
		return requireModule(SectSlipRates.class);
	}

	/**
	 * This returns the section slip rate of the given section. It can differ from what is returned by
	 * getFaultSectionData(index).get*AveSlipRate() if there are any reductions for creep or subseismogenic ruptures.
	 * @return slip rate (SI units: m)
	 */
	public double getSlipRateForSection(int sectIndex) {
		return getSectSlipRates().getSlipRate(sectIndex);
	}

	/**
	 * This returns the section slip rate of all sections. It can differ from what is returned by
	 * getFaultSectionData(index).get*AveSlipRate() if there are any reductions for creep or subseismogenic ruptures.
	 * @return slip rate (SI units: m)
	 */
	public double[] getSlipRateForAllSections() {
		return getSectSlipRates().getSlipRates();
	}

	/**
	 * This returns the standard deviation of the the slip rate for the given section. It can differ from what is returned by
	 * getFaultSectionData(index).getSlipRateStdDev() if there are any reductions for creep or subseismogenic ruptures.
	 * @return slip rate standard deviation (SI units: m)
	 */
	public double getSlipRateStdDevForSection(int sectIndex) {
		return getSectSlipRates().getSlipRateStdDev(sectIndex);
	}

	/**
	 * This differs from what is returned by getFaultSectionData(int).getSlipRateStdDev()
	 * where there has been a modification (i.e., moment rate reductions for smaller events).
	 * @return slip rate standard deviation (SI units: m)
	 */
	public double[] getSlipRateStdDevForAllSections() {
		return getSectSlipRates().getSlipRateStdDevs();
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

	private Table<Region, Boolean, double[]> fractRupsInsideRegions = HashBasedTable.create();
	private Table<Region, Boolean, double[]> fractSectsInsideRegions = HashBasedTable.create();
	private int[] sectNumPtsTraceOnly = null;
	private int[] sectNumPtsFull = null;
	
	public double[] getFractSectsInsideRegion(Region region, boolean traceOnly) {
		return getFractSectsInsideRegion(region, traceOnly, new int[getNumSections()]);
	}
	
	private double[] getFractSectsInsideRegion(Region region, boolean traceOnly, int[] numPtsInSection) {
		double[] fractSectsInside;
		synchronized (fractSectsInsideRegions) {
			fractSectsInside = fractSectsInsideRegions.get(region, traceOnly);
			int[] cachedPtsInSection = traceOnly ? sectNumPtsTraceOnly : sectNumPtsFull;
			if (cachedPtsInSection == null) {
				// should never happen, but would need to recalc anyway
				fractSectsInside = null;
			} else if (fractSectsInside != null) {
				// copy them over
				Preconditions.checkState(numPtsInSection.length == cachedPtsInSection.length);
				System.arraycopy(cachedPtsInSection, 0, numPtsInSection, 0, cachedPtsInSection.length);
			}
		}
		if (fractSectsInside == null) {
			synchronized (fractSectsInsideRegions) {
				if (fractSectsInsideRegions.size() > 50) { // max cache size
					Set<Cell<Region, Boolean, double[]>> cells = fractSectsInsideRegions.cellSet();
					cells.remove(cells.iterator().next());
				}
			}
			fractSectsInside = new double[getNumSections()];
			double gridSpacing=1;
			for(int s=0;s<getNumSections(); s++) {
				RuptureSurface surf = getFaultSectionData(s).getFaultSurface(gridSpacing, false, true);
				if (traceOnly) {
					FaultTrace trace = surf.getEvenlyDiscritizedUpperEdge();
					numPtsInSection[s] = trace.size();
					fractSectsInside[s] = RegionUtils.getFractionInside(region, trace);
				} else {
					LocationList surfLocs = surf.getEvenlyDiscritizedListOfLocsOnSurface();
					numPtsInSection[s] = surfLocs.size();
					fractSectsInside[s] = RegionUtils.getFractionInside(region, surfLocs);
				}
			}
			synchronized (fractSectsInsideRegions) {
				fractSectsInsideRegions.put(region, traceOnly, fractSectsInside);
				if (traceOnly)
					sectNumPtsTraceOnly = Arrays.copyOf(numPtsInSection, numPtsInSection.length);
				else
					sectNumPtsFull = Arrays.copyOf(numPtsInSection, numPtsInSection.length);
			}
		}
		return fractSectsInside;
	}

	/**
	 * 
	 * @param region
	 * @param traceOnly
	 * @return
	 */
	public double[] getFractRupsInsideRegion(Region region, boolean traceOnly) {
		if (region == null) {
			double[] ret = new double[getNumRuptures()];
			for (int r=0; r<ret.length; r++)
				ret[r] = 1d;
			return ret;
		}
		double[] fractRupsInside;
		synchronized (fractRupsInsideRegions) {
			fractRupsInside = fractRupsInsideRegions.get(region, traceOnly);
		}
		if (fractRupsInside == null) {
			synchronized (fractRupsInsideRegions) {
				if (fractRupsInsideRegions.size() > 10) { // max cache size
					Set<Cell<Region, Boolean, double[]>> cells = fractRupsInsideRegions.cellSet();
					cells.remove(cells.iterator().next());
				}
			}
			int[] numPtsInSection = new int[getNumSections()];
			double[] fractSectsInside = getFractSectsInsideRegion(region, traceOnly, numPtsInSection);
//			System.out.println(StatUtils.sum(fractSectsInside)+" sects inside of "+region.getName());
			int numRuptures = getNumRuptures();

			fractRupsInside = new double[numRuptures];

			for(int rup=0; rup<numRuptures; rup++) {
				List<Integer> sectionsIndicesForRup = getSectionsIndicesForRup(rup);
				int totNumPts = 0;
				for(Integer s:sectionsIndicesForRup) {
					fractRupsInside[rup] += fractSectsInside[s]*numPtsInSection[s];
					totNumPts += numPtsInSection[s];
				}
				fractRupsInside[rup] /= totNumPts;
			}
//			System.out.println(StatUtils.sum(fractRupsInside)+" rups inside of "+region.getName());
			synchronized (fractRupsInsideRegions) {
				fractRupsInsideRegions.put(region, traceOnly, fractRupsInside);
			}
		}
		return fractRupsInside;
	}

	/**
	 * this caches the ruptures involving each section
	 */
	private List<List<Integer>> rupturesForSectionCache = null;

	/**
	 * This returns the a list of all ruptures that occur on each section
	 * @param secIndex
	 * @return
	 */
	public final List<Integer> getRupturesForSection(int secIndex) {
		if (rupturesForSectionCache == null) {
			synchronized (this) {
				if (rupturesForSectionCache != null)
					return rupturesForSectionCache.get(secIndex);
				CalcProgressBar p = null;
				if (showProgress) {
					p = new CalcProgressBar("Calculating Ruptures for each Section", "Calculating Ruptures for each Section");
				}
				int numSects = getNumSections();
				ArrayList<List<Integer>> rupturesForSectionCache = new ArrayList<List<Integer>>(numSects);
				for (int secID=0; secID<numSects; secID++)
					rupturesForSectionCache.add(new ArrayList<Integer>());

				int numRups = getNumRuptures();
				for (int rupID=0; rupID<numRups; rupID++) {
					if (p != null) p.updateProgress(rupID, numRups);
					for (int secID : getSectionsIndicesForRup(rupID)) {
						rupturesForSectionCache.get(secID).add(rupID);
					}
				}
				// now make the immutable
				for (int i=0; i<rupturesForSectionCache.size(); i++)
					rupturesForSectionCache.set(i, Collections.unmodifiableList(rupturesForSectionCache.get(i)));
				this.rupturesForSectionCache = rupturesForSectionCache;
				if (p != null) p.dispose();
			}
		}

		return rupturesForSectionCache.get(secIndex);
	}

	/**
	 * this caches the ruptures involving each section
	 */
	private Map<Integer, List<Integer>> rupturesForParentSectionCache = null;

	/**
	 * This returns the a list of all ruptures that occur on each parent section
	 * @param secIndex
	 * @return
	 */
	public final List<Integer> getRupturesForParentSection(int parentSectID) {
		if (rupturesForParentSectionCache == null) {
			synchronized (this) {
				if (rupturesForParentSectionCache != null)
					return rupturesForParentSectionCache.get(parentSectID);
				CalcProgressBar p = null;
				if (showProgress) {
					p = new CalcProgressBar("Calculating Ruptures for each Parent Section", "Calculating Ruptures for each Parent Section");
				}
				Map<Integer, List<Integer>> rupturesForParentSectionCache = Maps.newConcurrentMap();

				int numRups = getNumRuptures();
				for (int rupID=0; rupID<numRups; rupID++) {
					if (p != null) p.updateProgress(rupID, numRups);
					int prevParent = -1;
					for (int secID : getSectionsIndicesForRup(rupID)) {
						int parent = getFaultSectionData(secID).getParentSectionId();
						if (parent < 0 || parent == prevParent)
							// no parent, or the same as the previous section
							continue;
						// potentially new parent
						List<Integer> rupsForParent = rupturesForParentSectionCache.get(parent);
						if (rupsForParent == null) {
							rupsForParent = new ArrayList<Integer>();
							rupturesForParentSectionCache.put(parent, rupsForParent);
						}
						// add this rupture, but make sure we haven't already added this rupture (possible if the
						// rupture jumps back and forth between parents). if we have already added it, the last item in
						// the list will be this rupture
						if (rupsForParent.isEmpty() || rupsForParent.get(rupsForParent.size()-1) != rupID)
							rupsForParent.add(rupID);
						prevParent = parent;
					}
				}

				// now make the immutable
				for (Integer key : rupturesForParentSectionCache.keySet())
					rupturesForParentSectionCache.put(key, Collections.unmodifiableList(rupturesForParentSectionCache.get(key)));
				if (p != null) p.dispose();
				this.rupturesForParentSectionCache = rupturesForParentSectionCache;
			}
		}

		return rupturesForParentSectionCache.get(parentSectID);
	}

	public final List<Integer> getParentSectionsForRup(int rupIndex) {
		List<Integer> parents = Lists.newArrayList();
		for (int sectIndex : getSectionsIndicesForRup(rupIndex)) {
			int parent = getFaultSectionData(sectIndex).getParentSectionId();
			if (!parents.contains(parent))
				parents.add(parent);
		}
		return parents;
	}

	/**
	 * This returns the maximum magnitude of this rupture set
	 * @return
	 */
	public double getMaxMag() {
		return StatUtils.max(getMagForAllRups());
	}

	/**
	 * This returns the maximum magnitude of this rupture set
	 * @return
	 */
	public double getMinMag() {
		return StatUtils.min(getMagForAllRups());
	}
	
	/**
	 * Returns true if the given rupture set is equivalent to this one. Equivalence is determined by the following
	 * criteria:
	 * 
	 * * Same number of ruptures
	 * * Each rupture uses the same fault sections, listed in the same order
	 * * Same number of sections
	 * * Each section has the same name & parent section ID
	 * 
	 * @param other
	 * @return true if the given rupture set is equivalent to this one (same ruptures and sections in same order,
	 *  maybe different properties)
	 */
	public boolean isEquivalentTo(FaultSystemRupSet other) {
		// check sections
		if (!areSectionsEquivalentTo(other))
			return false;
//		System.out.println("sects equal");
		
		// check ruptures
		if (getNumRuptures() != other.getNumRuptures())
			return false;
//		System.out.println("rupture count equal");
		for (int r=0; r<getNumRuptures(); r++) {
			List<Integer> mySects = getSectionsIndicesForRup(r);
			List<Integer> oSects = other.getSectionsIndicesForRup(r);
			if (!mySects.equals(oSects)) {
//				System.out.println("Sects different!");
//				System.out.println("\tMINE:\t"+mySects);
//				System.out.println("\tOTHER:\t"+oSects);
				return false;
			}
		}
//		System.out.println("rupture sects equal");
		return true;
	}
	
	/**
	 * Returns true if the given rupture set uses the same set of fault sections. Equivalence is determined by the
	 * following criteria:
	 * 
	 * * Same number of sections
	 * * Each section has the same name & parent section ID
	 * 
	 * @param other
	 * @return true if the fault sections in the given rupture set are equivalent to this one (same sections in same order,
	 *  maybe different properties)
	 */
	public boolean areSectionsEquivalentTo(FaultSystemRupSet other) {
		return areSectionsEquivalentTo(other.getFaultSectionDataList());
	}
	
	/**
	 * Returns true if the given rupture set uses the same set of fault sections. Equivalence is determined by the
	 * following criteria:
	 * 
	 * * Same number of sections
	 * * Each section has the same name & parent section ID
	 * 
	 * @param other
	 * @return true if the fault sections in the given rupture set are equivalent to this one (same sections in same order,
	 *  maybe different properties)
	 */
	public boolean areSectionsEquivalentTo(List<? extends FaultSection> sects) {
		// check sections
		if (getNumSections() != sects.size())
			return false;
//		System.out.println("sect count equal");
		for (int s=0; s<getNumSections(); s++) {
			FaultSection mySect = getFaultSectionData(s);
			FaultSection oSect = sects.get(s);
			if (mySect.getParentSectionId() != oSect.getParentSectionId())
				return false;
			if (!mySect.getSectionName().equals(oSect.getSectionName()))
				return false;
		}
		
		return true;
	}
	
	/**
	 * Builds a rupture subset using only the given section IDs. Fault section instances will be duplicated and assigned
	 * new section IDs in the returned rupture set, and only ruptures involving those sections will be retained. If any
	 * ruptures utilize both retained and non-retained section IDs, an {@link IllegalStateException} will be thrown.
	 * <br>
	 * All modules that implement {@link SplittableRuptureModule} will be copied over to the returned rupture set.
	 * <br>
	 * Mappings between original and new section/rupture IDs can be retrieved via the {@link RuptureSubSetMappings} module
	 * that will be attached to the rupture subset.
	 * 
	 * @param retainedSectIDs
	 * @return rupture subset containing only the given sections and their corresponding ruptures
	 * @throws IllegalStateException if there are ruptures that utilize both retained and non-retained sections
	 */
	public FaultSystemRupSet getForSectionSubSet(Collection<Integer> retainedSectIDs) throws IllegalStateException {
		return getForSectionSubSet(retainedSectIDs, null);
	}
	
	/**
	 * Builds a rupture subset using only the given section IDs. Fault section instances will be duplicated and assigned
	 * new section IDs in the returned rupture set, and only ruptures involving those sections will be retained. If any
	 * ruptures utilize both retained and non-retained section IDs, an {@link IllegalStateException} will be thrown.
	 * <br>
	 * All modules that implement {@link SplittableRuptureModule} will be copied over to the returned rupture set.
	 * <br>
	 * Mappings between original and new section/rupture IDs can be retrieved via the {@link RuptureSubSetMappings} module
	 * that will be attached to the rupture subset.
	 * 
	 * @param retainedSectIDs
	 * @param rupExclusionModel if non null, only ruptures allowed by the given model will be retained and ruptures that
	 * don't will not trigger an exception if they utilize sections both within and outside of this subset
	 * @return rupture subset containing only the given sections and their corresponding ruptures
	 * @throws IllegalStateException if there are ruptures that utilize both retained and non-retained sections
	 */
	public FaultSystemRupSet getForSectionSubSet(Collection<Integer> retainedSectIDs,
			BinaryRuptureProbabilityCalc rupExclusionModel) throws IllegalStateException {
		List<FaultSection> remappedSects = new ArrayList<>();
		int sectIndex = 0;
		BiMap<Integer, Integer> sectIDs_newToOld = HashBiMap.create(retainedSectIDs.size());
		for (int origID=0; origID<this.faultSectionData.size(); origID++) {
			if (retainedSectIDs.contains(origID)) {
				FaultSection remappedSect = this.faultSectionData.get(origID).clone();
				remappedSect.setSectionId(sectIndex);
				remappedSects.add(remappedSect);
				sectIDs_newToOld.put(sectIndex, origID);
				sectIndex++;
			}
		}
		
		System.out.println("Building rupture sub-set, retaining "+sectIDs_newToOld.size()+"/"+getNumSections()+" sections");
		
		int rupIndex = 0;
		BiMap<Integer, Integer> rupIDs_newToOld = HashBiMap.create(retainedSectIDs.size());
		ClusterRuptures cRups = rupExclusionModel == null ? null : requireModule(ClusterRuptures.class);
		for (int origID=0; origID<this.getNumRuptures(); origID++) {
			if (rupExclusionModel != null && !rupExclusionModel.isRupAllowed(cRups.get(origID), false))
				continue;
			boolean allRetained = true;
			boolean anyRetained = false;
			for (int s : this.sectionForRups.get(origID)) {
				boolean sectRetained = retainedSectIDs.contains(s);
				allRetained = allRetained && sectRetained;
				anyRetained = anyRetained || sectRetained;
			}
			Preconditions.checkState(anyRetained == allRetained,
					"Rupture %s involves sections that are retained and excluded in the rupture subset, can only build "
					+ "subsets for independent clusters of sections.", origID);
			if (anyRetained)
				rupIDs_newToOld.put(rupIndex++, origID);
		}
		System.out.println("Retaining "+rupIDs_newToOld.size()+"/"+getNumRuptures()+" ruptures");
		
		boolean shortSafe = sectIDs_newToOld.size() < Short.MAX_VALUE;
		
		double[] modMags = new double[rupIndex];
		double[] modRakes = new double[rupIndex];
		double[] modRupAreas = new double[rupIndex];
		double[] modRupLengths = rupLengths == null ? null : new double[rupIndex];
		List<List<Integer>> modSectionForRups = new ArrayList<>();
		BiMap<Integer, Integer> sectIDs_oldToNew = sectIDs_newToOld.inverse();
		for (rupIndex=0; rupIndex<rupIDs_newToOld.size(); rupIndex++) {
			int origID = rupIDs_newToOld.get(rupIndex);
			modMags[rupIndex] = mags[origID];
			modRakes[rupIndex] = rakes[origID];
			modRupAreas[rupIndex] = rupAreas[origID];
			if (modRupLengths != null)
				modRupLengths[rupIndex] = rupLengths[origID];
			List<Integer> sectForRup = new ArrayList<>();
			for (int origSectIndex : sectionForRups.get(origID)) {
				Integer newSectID = sectIDs_oldToNew.get(origSectIndex);
				Preconditions.checkNotNull(newSectID,
						"Rupture (newID=%s, oldID=%s) uses origSectID=%s which is not retained",
						rupIndex, origID, origID);
				sectForRup.add(newSectID);
			}
			if (shortSafe)
				modSectionForRups.add(new ShortListWrapper(sectForRup));
			else
				modSectionForRups.add(new IntListWrapper(sectForRup));
		}
		
		FaultSystemRupSet modRupSet = new FaultSystemRupSet(remappedSects, modSectionForRups,
				modMags, modRakes, modRupAreas, modRupLengths);
		
		// add mappings module
		RuptureSubSetMappings mappings = new RuptureSubSetMappings(sectIDs_newToOld, rupIDs_newToOld, this);
		modRupSet.addModule(mappings);
		
		// now copy over any modules we can
		for (OpenSHA_Module module : getModulesAssignableTo(SplittableRuptureModule.class, true)) {
			OpenSHA_Module modModule = ((SplittableRuptureModule<?>)module).getForRuptureSubSet(
					modRupSet, mappings);
			if (modModule != null)
				modRupSet.addModule(modModule);
		}
		
		return modRupSet;
	}
	
	/**
	 * Builds a rupture subset keeping only the given ruptures.
	 * <br>
	 * All modules that implement {@link SplittableRuptureModule} will be copied over to the returned rupture set.
	 * <br>
	 * Mappings between original and new rupture IDs can be retrieved via the {@link RuptureSubSetMappings} module
	 * that will be attached to the rupture subset.
	 * 
	 * @param retainedRuptureIDs
	 * @return rupture subset containing only the given ruptures
	 * @throws IllegalStateException if the input set is empty (or doesn't match the actual rupture count)
	 */
	public FaultSystemRupSet getForRuptureSubSet(Collection<Integer> retainedRuptureIDs) throws IllegalStateException {
		Preconditions.checkState(!retainedRuptureIDs.isEmpty(), "Must retain at least 1 rupture");
		
		System.out.println("Building rupture sub-set, retaining "+retainedRuptureIDs.size()+"/"+getNumRuptures()+" ruptures");
		
		int rupIndex = 0;
		BiMap<Integer, Integer> rupIDs_newToOld = HashBiMap.create(retainedRuptureIDs.size());
		for (int origID=0; origID<this.getNumRuptures(); origID++)
			if (retainedRuptureIDs.contains(origID))
				rupIDs_newToOld.put(rupIndex++, origID);
		Preconditions.checkState(rupIDs_newToOld.size() == retainedRuptureIDs.size(), "Retained IDs mismatch?");
		
		BiMap<Integer, Integer> sectIDs_newToOld = HashBiMap.create(getNumSections());
		for (int s=0; s<getNumSections(); s++)
			sectIDs_newToOld.put(s, s);
		
		double[] modMags = new double[rupIndex];
		double[] modRakes = new double[rupIndex];
		double[] modRupAreas = new double[rupIndex];
		double[] modRupLengths = rupLengths == null ? null : new double[rupIndex];
		List<List<Integer>> modSectionForRups = new ArrayList<>();
		for (rupIndex=0; rupIndex<rupIDs_newToOld.size(); rupIndex++) {
			int origID = rupIDs_newToOld.get(rupIndex);
			modMags[rupIndex] = mags[origID];
			modRakes[rupIndex] = rakes[origID];
			modRupAreas[rupIndex] = rupAreas[origID];
			if (modRupLengths != null)
				modRupLengths[rupIndex] = rupLengths[origID];
			modSectionForRups.add(getSectionsIndicesForRup(rupIndex));
		}
		
		FaultSystemRupSet modRupSet = new FaultSystemRupSet(getFaultSectionDataList(), modSectionForRups,
				modMags, modRakes, modRupAreas, modRupLengths);
		
		// add mappings module
		RuptureSubSetMappings mappings = new RuptureSubSetMappings(sectIDs_newToOld, rupIDs_newToOld, this);
		modRupSet.addModule(mappings);
		
		// now copy over any modules we can
		for (OpenSHA_Module module : getModulesAssignableTo(SplittableRuptureModule.class, true)) {
			OpenSHA_Module modModule = ((SplittableRuptureModule<?>)module).getForRuptureSubSet(
					modRupSet, mappings);
			if (modModule != null)
				modRupSet.addModule(modModule);
		}
		
		return modRupSet;
	}
	
	/*
	 * Builder, providing default implementations of initial rupture properties
	 */
	
	/**
	 * Creates a new {@link Builder} initialized with an existing {@link FaultSystemRupSet}. Any modules attached
	 * to the rupture set will be copied to the new rupture set.
	 * 
	 * @param rupSet
	 * @return builder instance
	 */
	public static Builder buildFromExisting(FaultSystemRupSet rupSet) {
		return buildFromExisting(rupSet, true);
	}

	
	/**
	 * Creates a new {@link Builder} initialized with an existing {@link FaultSystemRupSet}. If copyModules is true,
	 * then any modules attached to the rupture set will be copied to the new rupture set.
	 * 
	 * @param rupSet
	 * @param copyModules if true, modules will be copied to the new rupture set
	 * @return builder instance
	 */
	public static Builder buildFromExisting(FaultSystemRupSet rupSet, boolean copyModules) {
		Builder builder = new Builder(rupSet.faultSectionData, rupSet.sectionForRups, rupSet.mags, rupSet.rakes,
				rupSet.rupAreas, rupSet.rupLengths);
		
		if (copyModules)
			for (OpenSHA_Module module : rupSet.getModules(true))
				builder.addModule(module);
		
		return builder;
	}
	
	/**
	 * Initializes a {@link Builder} for the given fault sections and ruptures. The user must, at a minimum, supply
	 * magnitudes for each rupture before building the rupture set. This can be done via
	 * {@link Builder#rupMags(double[])} or via {@link Builder#forScalingRelationship(RupSetScalingRelationship)}.
	 * 
	 * @param faultSectionData
	 * @param sectionForRups section indices for each rupture
	 * @return builder instance
	 */
	public static Builder builder(List<? extends FaultSection> faultSectionData, List<List<Integer>> sectionForRups) {
		return new Builder(faultSectionData, sectionForRups);
	}
	
	/**
	 * Initializes a {@link Builder} for the given fault sections and cluster ruptures. The user must, at a minimum,
	 * supply magnitudes for each rupture before building the rupture set. This can be done via
	 * {@link Builder#rupMags(double[])} or via {@link Builder#forScalingRelationship(RupSetScalingRelationship)}.
	 * 
	 * @param faultSectionData
	 * @param sectionForRups section indices for each rupture
	 * @return builder instance
	 */
	public static Builder builderForClusterRups(List<? extends FaultSection> faultSectionData,
			List<ClusterRupture> rups) {
		List<List<Integer>> sectionForRups = new ArrayList<>();
		boolean shortSafe = faultSectionData.get(faultSectionData.size()-1).getSectionId() < Short.MAX_VALUE;
		if(shortSafe) {
			for (ClusterRupture rup : rups) {
				List<FaultSection> sections= rup.buildOrderedSectionList();
				short[] ids = new short[sections.size()];
				for(int s = 0; s < ids.length; s++) {
					ids[s] = (short) sections.get(s).getSectionId();
				}
				sectionForRups.add(new ShortListWrapper(ids));
			}
		} else {
			for (ClusterRupture rup : rups) {
				List<FaultSection> sections= rup.buildOrderedSectionList();
				int[] ids = new int[sections.size()];
				for(int s = 0; s < ids.length; s++) {
					ids[s] = sections.get(s).getSectionId();
				}
				sectionForRups.add(new IntListWrapper(ids));
			}
		}
		Builder builder = new Builder(faultSectionData, sectionForRups);
		builder.setClusterRuptures(rups);
		return builder;
	}
	
	/**
	 * Interface for an {@link OpenSHA_Module} that will be constructed when a {@link FaultSystemRupSet}
	 * is built by a {@link Builder}.
	 * 
	 * @author kevin
	 *
	 */
	public static interface ModuleBuilder {
		public OpenSHA_Module build(FaultSystemRupSet rupSet);
		
		public Class<? extends OpenSHA_Module> getType();
	}
	
	private static <E extends OpenSHA_Module> Callable<E> builderCallable(
			FaultSystemRupSet rupSet, ModuleBuilder builder, Class<E> clazz) {
		return new Callable<E>() {

			@SuppressWarnings("unchecked")
			@Override
			public E call() throws Exception {
				// TODO Auto-generated method stub
				return (E)builder.build(rupSet);
			}
		};
	}
	
	private static double[] rupLengthsDefault(List<? extends FaultSection> faultSectionData,
				List<List<Integer>> sectionForRups) {
		int numRups = sectionForRups.size();
		double[] rupLengths = new double[numRups];
		for (int r=0; r<numRups; r++) {
			for (int s : sectionForRups.get(r)) {
				FaultSection sect = faultSectionData.get(s);
				double length = sect.getTraceLength()*1e3;	// km --> m
				rupLengths[r] += length;
			}
		}
		return rupLengths;
	}
	
	public static class Builder {
		
		// core data objects
		private List<? extends FaultSection> faultSectionData;
		private double[] mags;
		private double[] rakes;
		private double[] rupAreas;
		private double[] rupLengths;
		private List<List<Integer>> sectionForRups;
		
		private List<ModuleBuilder> modules;
		
		private Builder(
				List<? extends FaultSection> faultSectionData,
				List<List<Integer>> sectionForRups) {
			Preconditions.checkState(faultSectionData != null && !faultSectionData.isEmpty(),
					"Must supply fault sections");
			this.faultSectionData = faultSectionData;
			Preconditions.checkState(sectionForRups != null && !sectionForRups.isEmpty(),
					"Must supply ruptures");
			this.sectionForRups = sectionForRups;
			
			modules = new ArrayList<>();
			
		}
		
		private Builder(
				List<? extends FaultSection> faultSectionData,
				List<List<Integer>> sectionForRups,
				@Nullable double[] mags,
				@Nullable double[] rakes,
				@Nullable double[] rupAreas,
				@Nullable double[] rupLengths) {
			Preconditions.checkState(faultSectionData != null && !faultSectionData.isEmpty(),
					"Must supply fault sections");
			this.faultSectionData = faultSectionData;
			this.mags = mags;
			this.rakes = rakes;
			this.rupAreas = rupAreas;
			this.rupLengths = rupLengths;
			Preconditions.checkState(sectionForRups != null && !sectionForRups.isEmpty(),
					"Must supply ruptures");
			this.sectionForRups = sectionForRups;
			
			modules = new ArrayList<>();
		}
		
		public Builder info(String info) {
			addModule(new InfoModule(info));
			return this;
		}
		
		public Builder setClusterRuptures(List<ClusterRupture> rups) {
			addModule(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return ClusterRuptures.instance(rupSet, rups);
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return ClusterRuptures.class;
				}
			});
			return this;
		}
		
		public Builder replaceFaultSections(List<? extends FaultSection> newSects) {
			Preconditions.checkState(newSects.size() == faultSectionData.size());
			this.faultSectionData = newSects;
			this.rupAreas = null;
			return this;
		}
		
		private void checkBuildRakesAndAreas() {
			if (this.rakes == null || this.rupAreas == null) {
				double[] rakes = this.rakes;
				double[] rupAreas = this.rupAreas;
				int numRups = sectionForRups.size();
				if (rakes == null) {
					rakes = new double[numRups];
					for (int r=0; r<numRups; r++)
						rakes[r] = Double.NaN;
				}
				if (rupAreas == null) {
					rupAreas =new double[numRups];
					for (int r=0; r<numRups; r++)
						rupAreas[r] = Double.NaN;
				}
				double[] finalRakes = rakes;
				double[] finalRupAreas = rupAreas;
				IntStream.range(0, numRups).parallel().forEach(r -> {
					List<Double> mySectAreas = new ArrayList<>();
					List<Double> mySectRakes = new ArrayList<>();
					double totArea = 0d;
					for (int s : sectionForRups.get(r)) {
						FaultSection sect = faultSectionData.get(s);
						double area = sect.getArea(true);	// sq-m
						totArea += area;
						mySectAreas.add(area);
						mySectRakes.add(sect.getAveRake());
					}
					if (Double.isNaN(finalRupAreas[r]))
						finalRupAreas[r] = totArea;
					if (Double.isNaN(finalRakes[r]))
						finalRakes[r] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(mySectAreas, mySectRakes));
				});
				this.rakes = rakes;
				this.rupAreas = rupAreas;
			}
		}

		private void checkBuildLengths() {
			if (rupLengths == null) {
				rupLengths(rupLengthsDefault(faultSectionData, sectionForRups));
			}
		}
		
		/**
		 * Sets magnitudes from the given scaling relationship
		 * @param scale
		 * @return
		 */
		public Builder forScalingRelationship(RupSetScalingRelationship scale) {
			this.mags = new double[sectionForRups.size()];
			checkBuildRakesAndAreas();
			checkBuildLengths();
			for (int r=0; r<mags.length; r++) {
				double totOrigArea = 0;
				for (int s : sectionForRups.get(r)) {
					FaultSection sect = faultSectionData.get(s);
					totOrigArea += sect.getArea(false);	// sq-m
				}
				double origDDW = totOrigArea / rupLengths[r];
				mags[r] = scale.getMag(rupAreas[r], rupLengths[r], rupAreas[r]/rupLengths[r], origDDW, rakes[r]);
			}
			modules.add(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return AveSlipModule.forModel(rupSet, scale);
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return AveSlipModule.class;
				}
			});
			return this;
		}
		
		public Builder tectonicRegime(TectonicRegionType regime) {
			modules.add(new ModuleBuilder() {

				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return RupSetTectonicRegimes.constant(rupSet, regime);
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return RupSetTectonicRegimes.class;
				}
				
			});
			return this;
		}
		
		public Builder tectonicRegimes(TectonicRegionType[] regimes) {
			Preconditions.checkState(sectionForRups.size() == regimes.length);
			modules.add(new ModuleBuilder() {

				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return new RupSetTectonicRegimes(rupSet, regimes);
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return RupSetTectonicRegimes.class;
				}
				
			});
			return this;
		}
		
		public Builder slipAlongRupture(SlipAlongRuptureModelBranchNode slipAlong) {
			return slipAlongRupture(SlipAlongRuptureModel.forModel(slipAlong));
		}
		
		public Builder slipAlongRupture(SlipAlongRuptureModel slipAlong) {
			addModule(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return slipAlong;
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return SlipAlongRuptureModel.class;
				}
			});
			return this;
		}
		
		public Builder modSectMinMagsAbove(double systemWideMinMag, boolean useMaxForParent) {
			addModule(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return ModSectMinMags.above(rupSet, systemWideMinMag, useMaxForParent);
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return ModSectMinMags.class;
				}
			});
			return this;
		}
		
		public Builder modSectMinMags(double[] minMags) {
			Preconditions.checkState(minMags.length == faultSectionData.size(),
					"Have %s sections but given %s min mags", faultSectionData.size(), minMags.length);
			addModule(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return ModSectMinMags.instance(rupSet, minMags);
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return ModSectMinMags.class;
				}
			});
			return this;
		}
		
		public Builder rupMags(double[] mags) {
			Preconditions.checkArgument(mags.length == this.sectionForRups.size());
			this.mags = mags;
			return this;
		}
		
		public Builder rupRakes(double[] rakes) {
			Preconditions.checkArgument(rakes.length == this.sectionForRups.size());
			this.rakes = rakes;
			return this;
		}
		
		public Builder rupAreas(double[] rupAreas) {
			Preconditions.checkArgument(rupAreas.length == this.sectionForRups.size());
			this.rupAreas = rupAreas;
			return this;
		}
		
		public Builder rupLengths(double[] rupLengths) {
			Preconditions.checkArgument(rupLengths == null
					|| rupLengths.length == this.sectionForRups.size());
			this.rupLengths = rupLengths;
			return this;
		}
		
		public Builder addModule(ModuleBuilder module) {
			this.modules.add(module);
			return this;
		}
		
		public Builder addModule(OpenSHA_Module module) {
			this.modules.add(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return module;
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return module.getClass();
				}
			});
			return this;
		}
		
//		private 
		
		public FaultSystemRupSet build() {
			return build(false);
		}
		
		public FaultSystemRupSet build(boolean round) {
			Preconditions.checkNotNull(mags, "Must set magnitudes");
			checkBuildRakesAndAreas();
			checkBuildLengths();
			
			if (round) {
				mags = roundFixed(mags, 3);
				rakes = roundFixed(rakes, 1);
				rupAreas = DataUtils.roundSigFigs(rupAreas, 6);
				rupLengths = DataUtils.roundSigFigs(rupLengths, 6);
			}
			FaultSystemRupSet rupSet = new FaultSystemRupSet(faultSectionData, sectionForRups, mags,
					rakes, rupAreas, rupLengths);
			for (ModuleBuilder module : modules) {
				rupSet.removeModuleInstances(module.getType());
				rupSet.addAvailableModule(new Callable<OpenSHA_Module>() {

					@Override
					public OpenSHA_Module call() throws Exception {
						OpenSHA_Module instance = module.build(rupSet);
						Preconditions.checkState(module.getType().isAssignableFrom(instance.getClass()),
								"Instance is of type %s, but was declared as type %s", instance.getClass(), module.getType());
						return instance;
					}
					
				}, module.getType());
			}
			return rupSet;
		}
		
		private static double[] roundFixed(double[] values, int scale) {
			double[] ret = new double[values.length];
			for (int i=0; i<ret.length; i++)
				ret[i] = DataUtils.roundFixed(values[i], scale);
			return ret;
		}
		
	}
	
	public static void main(String[] args) throws Exception {
//		File baseDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
//		
//		File inputFile = new File(baseDir, "fm3_1_ucerf3.zip");
//		File destFile = new File("/tmp/new_format_u3.zip");
//		FaultSystemRupSet orig = FaultSystemIO.loadRupSet(inputFile);
//		orig.getArchive().write(destFile);
//		FaultSystemRupSet loaded = load(destFile);
//		Preconditions.checkState(orig.isEquivalentTo(loaded));
		
		FaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(U3LogicTreeBranch.DEFAULT);
		File destFile = new File("/tmp/new_ivfrs.zip");
		rupSet.getArchive().write(destFile);
		FaultSystemRupSet loaded = load(destFile);
		loaded.loadAllAvailableModules();
		System.out.println(loaded.getModule(U3LogicTreeBranch.class));
	}

}
