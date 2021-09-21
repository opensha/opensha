package org.opensha.sha.earthquake.faultSysSolution;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.RoundingMode;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectAreas;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gui.infoTools.CalcProgressBar;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
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
		FaultSystemRupSet oRupSet = parent.getModule(FaultSystemRupSet.class, false);
		Preconditions.checkState(oRupSet == null || oRupSet == this);
		this.archive = parent;
	}

	@Override
	public ModuleArchive<OpenSHA_Module> getParent() {
		return archive;
	}

	@Override
	public SubModule<ModuleArchive<OpenSHA_Module>> copy(ModuleArchive<OpenSHA_Module> newArchive)
			throws IllegalStateException {
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
		return load(new ZipFile(file));
	}
	
	/**
	 * Loads a FaultSystemRupSet from a zip file
	 * 
	 * @param zip
	 * @return
	 * @throws IOException
	 */
	public static FaultSystemRupSet load(ZipFile zip) throws IOException {
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(zip, FaultSystemRupSet.class);
		
		FaultSystemRupSet rupSet = archive.getModule(FaultSystemRupSet.class);
		if (rupSet == null) {
			// see if it's an old rupture set
			if (zip.getEntry("rup_sections.bin") != null) {
				System.err.println("WARNING: this is a legacy fault sytem rupture set, that file format is deprecated. "
						+ "Will attempt to load it using the legacy file loader. "
						+ "See https://opensha.org/File-Formats for more information.");
				try {
					return U3FaultSystemIO.loadRupSetAsApplicable(zip, null);
				} catch (DocumentException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
			if (zip.getEntry("modules.json") == null && zip.getEntry("ruptures/"+RUP_SECTS_FILE_NAME) != null) {
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

	@Override
	protected String getNestingPrefix() {
		return "ruptures/";
	}
	
	public static final String SECTS_FILE_NAME = "fault_sections.geojson";

	@Override
	public final void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		// CSV Files
		CSV_BackedModule.writeToArchive(buildRupSectsCSV(this), zout, entryPrefix, RUP_SECTS_FILE_NAME);
		CSV_BackedModule.writeToArchive(new RuptureProperties(this).buildCSV(), zout, entryPrefix, RUP_PROPS_FILE_NAME);
		
		// fault sections
		FileBackedModule.initEntry(zout, entryPrefix, SECTS_FILE_NAME);
		OutputStreamWriter writer = new OutputStreamWriter(zout);
		GeoJSONFaultReader.writeFaultSections(writer, faultSectionData);
		writer.flush();
		zout.flush();
		zout.closeEntry();
		
		// write README
		FileBackedModule.initEntry(zout, null, "README");
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
					+": CSV file giving the annual rate of occurence for each rupture\n"));
			if (solution.hasAvailableModule(GridSourceProvider.class)) {
				readme.write("This solution has optional gridded seismicity information. Files related to that are:\n");
				readme.write(" - "+ArchivableModule.getEntryName(solPrefix, AbstractGridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME
						+": GeoJSON file giving the location of each gridded seismicity node\n"));
				readme.write(" - "+ArchivableModule.getEntryName(solPrefix, AbstractGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME
						+": CSV file giving the relative weights of each gridded seismicity focal mechanism at each grid node\n"));
				readme.write(" - "+ArchivableModule.getEntryName(solPrefix, AbstractGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME
						+": CSV file giving the magnitude-frequency distribution of sub-seismogenic ruptures at each gridded "
						+ "seismicity node that are associated with at least one fault\n"));
				readme.write(" - "+ArchivableModule.getEntryName(solPrefix, AbstractGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME
						+": CSV file giving the magnitude-frequency distribution of off-fault ruptures at each gridded "
						+ "seismicity node (those that are not associated with at any fault)\n"));
			}
			
		}
		readme.flush();
		writer.flush();
		zout.flush();
		zout.closeEntry();
	}
	
	public static final String RUP_SECTS_FILE_NAME = "indices.csv";
	
	public static CSVFile<String> buildRupSectsCSV(FaultSystemRupSet rupSet) {
		CSVFile<String> csv = new CSVFile<>(false);
		
		int maxNumSects = 0;
		for (int r=0; r<rupSet.getNumRuptures(); r++)
			maxNumSects = Integer.max(maxNumSects, rupSet.getSectionsIndicesForRup(r).size());
		
		List<String> header = new ArrayList<>(List.of("Rupture Index", "Num Sections"));
		
		for (int s=0; s<maxNumSects; s++)
			header.add("# "+(s+1));
		
		csv.addLine(header);
		
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			List<Integer> sectIDs = rupSet.getSectionsIndicesForRup(r);
			List<String> line = new ArrayList<>(2+sectIDs.size());
			
			line.add(r+"");
			line.add(sectIDs.size()+"");
			for (int s : sectIDs)
				line.add(s+"");
			csv.addLine(line);
		}
		
		return csv;
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
		
		public CSVFile<String> buildCSV() {
			CSVFile<String> csv = new CSVFile<>(true);
			
			List<String> header = new ArrayList<>(List.of("Rupture Index", "Magnitude", "Average Rake (degrees)",
					"Area (m^2)", "Length (m)"));
			
			csv.addLine(header);
			
			for (int r=0; r<mags.length; r++) {
				List<String> line = new ArrayList<>(5);
				
				line.add(r+"");
				line.add(mags[r]+"");
				line.add(rakes[r]+"");
				line.add(areas[r]+"");
				if (lengths == null)
					line.add("");
				else
					line.add(lengths[r]+"");
				csv.addLine(line);
			}
			
			return csv;
		}
	}
	
	public static List<List<Integer>> loadRupSectsCSV(CSVFile<String> rupSectsCSV, int numSections) {
		int numRuptures = rupSectsCSV.getNumRows()-1;
		List<List<Integer>> rupSectsList = new ArrayList<>(numRuptures);
		boolean shortSafe = numSections < Short.MAX_VALUE;
		for (int r=0; r<numRuptures; r++) {
			int row = r+1;
			int col = 0;
			// load rupture sections
			Preconditions.checkState(r == rupSectsCSV.getInt(row, col++),
					"Ruptures out of order or not 0-based in CSV file, expected id=%s at row %s", r, row);
			int numRupSects = rupSectsCSV.getInt(row, col++);
			Preconditions.checkState(numRupSects > 0, "Rupture %s has no sections!", r);
			List<Integer> rupSects;
			if (shortSafe) {
				short[] sectIDs = new short[numRupSects];
				for (int i=0; i<numRupSects; i++)
					sectIDs[i] = (short)rupSectsCSV.getInt(row, col++);
				rupSects = new ShortListWrapper(sectIDs);
			} else {
				int[] sectIDs = new int[numRupSects];
				for (int i=0; i<numRupSects; i++)
					sectIDs[i] = rupSectsCSV.getInt(row, col++);
				rupSects = new IntListWrapper(sectIDs);
			}
			int rowSize = rupSectsCSV.getLine(row).size();
			while (col < rowSize) {
				// make sure any further columns are empty
				String str = rupSectsCSV.get(row, col++);
				Preconditions.checkState(str.isBlank(),
						"Rupture has %s sections, but data exists in %s column %s: %s", RUP_SECTS_FILE_NAME, col, str);
			}
			for (int sectID : rupSects)
				Preconditions.checkState(sectID >= 0 && sectID < numSections,
						"Bad sectionID=%s for rupture %s", sectID, r);
			rupSectsList.add(rupSects);
		}
		return rupSectsList;
	}

	@Override
	public final void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		System.out.println("\tLoading ruptures CSV...");
		CSVFile<String> rupSectsCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, RUP_SECTS_FILE_NAME);
		CSVFile<String> rupPropsCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, RUP_PROPS_FILE_NAME);
		
		// fault sections
		List<GeoJSONFaultSection> sections = GeoJSONFaultReader.readFaultSections(
				new InputStreamReader(FileBackedModule.getInputStream(zip, entryPrefix, SECTS_FILE_NAME)));
		for (int s=0; s<sections.size(); s++)
			Preconditions.checkState(sections.get(s).getSectionId() == s,
			"Fault sections must be provided in order starting with ID=0");
		
		int numRuptures = rupSectsCSV.getNumRows()-1;
		Preconditions.checkState(numRuptures > 0, "No ruptures found in CSV file");
		Preconditions.checkState(rupSectsCSV.getNumRows() == rupPropsCSV.getNumRows(),
				"Rupture sections and properites CSVs have different lengths");
		
		// load rupture data
		System.out.println("\tParsing rupture properties CSV");
		RuptureProperties props = new RuptureProperties(rupPropsCSV);
		System.out.println("\tParsing rupture sections CSV");
		List<List<Integer>> rupSectsList = loadRupSectsCSV(rupSectsCSV, sections.size());
		
		init(sections, rupSectsList, props.mags, props.rakes, props.areas, props.lengths);
		
		if (archive != null && zip.getEntry(entryPrefix+"modules.json") == null) {
			// we're missing an index, see if any default modules are present that we can manually load
			
			if (zip.getEntry(entryPrefix+SectAreas.DATA_FILE_NAME) != null) {
				try {
					System.out.println("Trying to load unlisted SectAreas module");
					archive.loadUnlistedModule(SectAreas.Precomputed.class, entryPrefix, this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			if (zip.getEntry(entryPrefix+SectSlipRates.DATA_FILE_NAME) != null) {
				try {
					System.out.println("Trying to load unlisted SectSlipRates module");
					archive.loadUnlistedModule(SectSlipRates.Precomputed.class, entryPrefix, this);
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
	private static class ShortListWrapper extends AbstractList<Integer> {
		
		private short[] vals;
		
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
	private static class IntListWrapper extends AbstractList<Integer> {
		
		private int[] vals;
		
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
		this.rupLengths = rupLengths;
		
		Preconditions.checkArgument(sectionForRups.size() == numRups, "array sizes inconsistent!");
		this.sectionForRups = sectionForRups;
		
		if (!hasAvailableModule(SectAreas.class)) {
			addAvailableModule(new Callable<SectAreas>() {

				@Override
				public SectAreas call() throws Exception {
					return SectAreas.fromFaultSectData(FaultSystemRupSet.this);
				}
				
			}, SectAreas.class);
		}
		if (!hasAvailableModule(SectSlipRates.class)) {
			addAvailableModule(new Callable<SectSlipRates>() {

				@Override
				public SectSlipRates call() throws Exception {
					return SectSlipRates.fromFaultSectData(FaultSystemRupSet.this);
				}
				
			}, SectSlipRates.class);
		}
		if (!hasAvailableModule(SlipAlongRuptureModel.class)) {
			addAvailableModule(new Callable<SlipAlongRuptureModel>() {

				@Override
				public SlipAlongRuptureModel call() throws Exception {
					// see if we have a logic tree branch
					LogicTreeBranch<?> branch = getModule(LogicTreeBranch.class);
					if (branch != null && branch.hasValue(SlipAlongRuptureModels.class))
						return branch.getValue(SlipAlongRuptureModels.class).getModel();
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
	}

	public void copyCacheFrom(FaultSystemRupSet rupSet) {
		if (rupSet.getNumRuptures() != getNumRuptures() || rupSet.getNumSections() != getNumSections())
			return;
		rupturesForSectionCache = rupSet.rupturesForSectionCache;
		rupturesForParentSectionCache = rupSet.rupturesForParentSectionCache;
		fractRupsInsideRegions = rupSet.fractRupsInsideRegions;
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

	/**
	 * This returns the magnitude of the largest rupture involving this section or NaN
	 * if no ruptures involve this section.
	 * @param sectIndex
	 * @return
	 */
	public double getMaxMagForSection(int sectIndex) {
		List<Integer> rups = getRupturesForSection(sectIndex);
		if (rups.isEmpty())
			return Double.NaN;
		double maxMag = 0;
		for (int rupIndex : getRupturesForSection(sectIndex)) {
			double mag = getMagForRup(rupIndex);
			if (mag > maxMag)
				maxMag = mag;
		}
		return maxMag;
	}
	
	/**
	 * This returns the magnitude of the smallest rupture involving this section or NaN
	 * if no ruptures involve this section.
	 * @param sectIndex
	 * @return
	 */
	public double getMinMagForSection(int sectIndex) {
		List<Integer> rups = getRupturesForSection(sectIndex);
		if (rups.isEmpty())
			return Double.NaN;
		double minMag = Double.POSITIVE_INFINITY;
		for (int rupIndex : getRupturesForSection(sectIndex)) {
			double mag = getMagForRup(rupIndex);
			if (mag < minMag)
				minMag = mag;
		}
		return minMag;
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
		ArrayList<FaultSection> datas = new ArrayList<FaultSection>();
		for (int ind : inds)
			datas.add(getFaultSectionData(ind));
		return datas;
	}

	private class RupSurfaceCache {
		private double prevGridSpacing = Double.NaN;
		private Map<Integer, RuptureSurface> rupSurfaceCache;

		private RupSurfaceCache() {
			rupSurfaceCache = new HashMap<>();
		}

		private synchronized RuptureSurface getSurfaceForRupture(int rupIndex, double gridSpacing) {
			if (prevGridSpacing != gridSpacing) {
				rupSurfaceCache.clear();
				prevGridSpacing = gridSpacing;
			}
			RuptureSurface surf = rupSurfaceCache.get(rupIndex);
			if (surf != null)
				return surf;
			List<RuptureSurface> rupSurfs = Lists.newArrayList();
			for (FaultSection fltData : getFaultSectionDataForRupture(rupIndex))
				rupSurfs.add(fltData.getFaultSurface(gridSpacing, false, true));
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
		return surfCache.getSurfaceForRupture(rupIndex, gridSpacing);
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
	
	private SectSlipRates getSectSlipRates() {
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
		if (!fractRupsInsideRegions.contains(region, traceOnly)) {
			if (fractRupsInsideRegions.size() > 10) { // max cache size
				Set<Cell<Region, Boolean, double[]>> cells = fractRupsInsideRegions.cellSet();
				cells.remove(cells.iterator().next());
			}
			double[] fractSectsInside = new double[getNumSections()];
			double gridSpacing=1;
			int[] numPtsInSection = new int[getNumSections()];
			int numRuptures = getNumRuptures();

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

			double[] fractRupsInside = new double[numRuptures];

			for(int rup=0; rup<numRuptures; rup++) {
				List<Integer> sectionsIndicesForRup = getSectionsIndicesForRup(rup);
				int totNumPts = 0;
				for(Integer s:sectionsIndicesForRup) {
					fractRupsInside[rup] += fractSectsInside[s]*numPtsInSection[s];
					totNumPts += numPtsInSection[s];
				}
				fractRupsInside[rup] /= totNumPts;
			}
			fractRupsInsideRegions.put(region, traceOnly, fractRupsInside);
		}
		return fractRupsInsideRegions.get(region, traceOnly);
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
				ArrayList<List<Integer>> rupturesForSectionCache = new ArrayList<List<Integer>>();
				for (int secID=0; secID<getNumSections(); secID++)
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
				// note this assumes that sections are in order
				rupturesForParentSectionCache = Maps.newConcurrentMap();

				int numRups = getNumRuptures();
				for (int rupID=0; rupID<numRups; rupID++) {
					if (p != null) p.updateProgress(rupID, numRups);
					HashSet<Integer> parents = new HashSet<Integer>();
					for (int secID : getSectionsIndicesForRup(rupID)) {
						int parent = getFaultSectionData(secID).getParentSectionId();
						if (parent < 0)
							continue;
						if (!parents.contains(parent))
							parents.add(parent);
					}
					for (int parent : parents) {
						List<Integer> rupsForParent = rupturesForParentSectionCache.get(parent);
						if (rupsForParent == null) {
							rupsForParent = new ArrayList<Integer>();
							rupturesForParentSectionCache.put(parent, rupsForParent);
						}
						rupsForParent.add(rupID);
					}
				}

				// now make the immutable
				for (Integer key : rupturesForParentSectionCache.keySet())
					rupturesForParentSectionCache.put(key, Collections.unmodifiableList(rupturesForParentSectionCache.get(key)));
				if (p != null) p.dispose();
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
		if (getNumSections() != other.getNumSections())
			return false;
//		System.out.println("sect count equal");
		for (int s=0; s<getNumSections(); s++) {
			FaultSection mySect = getFaultSectionData(s);
			FaultSection oSect = other.getFaultSectionData(s);
			if (mySect.getParentSectionId() != oSect.getParentSectionId())
				return false;
			if (!mySect.getSectionName().equals(oSect.getSectionName()))
				return false;
		}
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
	 * {@link Builder#rupMags(double[])} or via {@link Builder#forScalingRelationship(ScalingRelationships)}.
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
	 * {@link Builder#rupMags(double[])} or via {@link Builder#forScalingRelationship(ScalingRelationships)}.
	 * 
	 * @param faultSectionData
	 * @param sectionForRups section indices for each rupture
	 * @return builder instance
	 */
	public static Builder builderForClusterRups(List<? extends FaultSection> faultSectionData,
			List<ClusterRupture> rups) {
		List<List<Integer>> sectionForRups = new ArrayList<>();
		for (ClusterRupture rup : rups) {
			List<Integer> ids = new ArrayList<>();
			for (FaultSection sect : rup.buildOrderedSectionList())
				ids.add(sect.getSectionId());
			sectionForRups.add(ids);
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
		
		public Builder forU3Branch(U3LogicTreeBranch branch) {
			// build magnitudes from the scaling relationship and add ave slip module
			forScalingRelationship(branch.getValue(ScalingRelationships.class));
			return u3BranchModules(branch);
		}
		
		public Builder u3BranchModules(U3LogicTreeBranch branch) {
			// set logic tree branch
			addModule(branch);
			// add slip along rupture model information
			slipAlongRupture(branch.getValue(SlipAlongRuptureModels.class));
			// add modified section minimum magnitudes
			addModule(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return ModSectMinMags.instance(rupSet, FaultSystemRupSetCalc.computeMinSeismoMagForSections(
							rupSet, InversionFaultSystemRupSet.MIN_MAG_FOR_SEISMOGENIC_RUPS));
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return ModSectMinMags.class;
				}
			});
			FaultModels fm = branch.getValue(FaultModels.class);
			addModule(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					if (fm == FaultModels.FM3_1 || fm == FaultModels.FM3_2) {
						try {
							return FaultPolyMgr.loadSerializedUCERF3(fm);
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
					return FaultPolyMgr.create(rupSet.getFaultSectionDataList(), U3InversionTargetMFDs.FAULT_BUFFER);
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return PolygonFaultGridAssociations.class;
				}
			});
			// add inversion target MFDs
			addModule(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return new U3InversionTargetMFDs(rupSet, branch, rupSet.requireModule(ModSectMinMags.class),
							rupSet.requireModule(PolygonFaultGridAssociations.class));
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return U3InversionTargetMFDs.class;
				}
			});
			// add target slip rates (modified for sub-seismogenic ruptures)
			addModule(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					InversionTargetMFDs invMFDs = rupSet.requireModule(InversionTargetMFDs.class);
					return InversionFaultSystemRupSet.computeTargetSlipRates(rupSet,
							branch.getValue(InversionModels.class), branch.getValue(MomentRateFixes.class), invMFDs);
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return SectSlipRates.class;
				}
			});
			return this;
		}
		
		/**
		 * Sets magnitudes from the given UCERF3 scaling relationships enum
		 * @param scale
		 * @return
		 */
		public Builder forScalingRelationship(ScalingRelationships scale) {
			this.mags = new double[sectionForRups.size()];
			for (int r=0; r<mags.length; r++) {
				double totArea = 0d;
				double totOrigArea = 0;
				double totLength = 0;
				for (int s : sectionForRups.get(r)) {
					FaultSection sect = faultSectionData.get(s);
					totArea += sect.getArea(true);	// sq-m
					totOrigArea += sect.getArea(false);	// sq-m
					totLength += sect.getTraceLength()*1e3;	// km --> m
				}
				double origDDW = totOrigArea/totLength;
				mags[r] = scale.getMag(totArea, origDDW);
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
		
		public Builder slipAlongRupture(SlipAlongRuptureModels slipAlong) {
			addModule(new ModuleBuilder() {
				
				@Override
				public OpenSHA_Module build(FaultSystemRupSet rupSet) {
					return SlipAlongRuptureModel.forModel(slipAlong);
				}

				@Override
				public Class<? extends OpenSHA_Module> getType() {
					return SlipAlongRuptureModel.class;
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
		
		public FaultSystemRupSet build() {
			return build(false);
		}
		
		public FaultSystemRupSet build(boolean round) {
			Preconditions.checkNotNull(mags, "Must set magnitudes");
			int numRups = sectionForRups.size();
			double[] rakes = this.rakes;
			double[] rupAreas = this.rupAreas;
			if (rakes == null || rupAreas == null) {
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
				for (int r=0; r<numRups; r++) {
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
					if (Double.isNaN(rupAreas[r]))
						rupAreas[r] = totArea;
					if (Double.isNaN(rakes[r]))
						rakes[r] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(mySectAreas, mySectRakes));
				}
			}
			
			double[] rupLengths = this.rupLengths;
			if (rupLengths == null) {
				rupLengths = new double[numRups];
				for (int r=0; r<numRups; r++) {
					for (int s : sectionForRups.get(r)) {
						FaultSection sect = faultSectionData.get(s);
						double length = sect.getTraceLength()*1e3;	// km --> m
						rupLengths[r] += length;
					}
				}
			}
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
