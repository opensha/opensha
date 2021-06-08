package org.opensha.sha.earthquake.faultSysSolution;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.CSV_BackedModule;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gui.infoTools.CalcProgressBar;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import scratch.UCERF3.utils.FaultSystemIO;

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
	private double[] sectSlipRates;
	private double[] sectSlipRateStdDevs; // TODO: make a module?
	private double[] rakes;
	private double[] rupAreas;
	private double[] rupLengths;
	private double[] sectAreas;
	private List<List<Integer>> sectionForRups;
	
	// archive that this came from
	ModuleArchive<OpenSHA_Module> archive;
	
	protected FaultSystemRupSet() {
		
	}

	/**
	 * Initialized a FaultSystemRupSet object with all core data.
	 * 
	 * @param faultSectionData fault section data list (CANNOT be null)
	 * @param sectSlipRates slip rates for each fault section with any reductions applied (CAN be null)
	 * @param sectSlipRateStdDevs slip rate std deviations for each fault section (CAN be null)
	 * @param sectAreas areas for each fault section (CAN be null)
	 * @param sectionForRups list of fault section indexes for each rupture (CANNOT be null)
	 * @param mags magnitudes for each rupture (CANNOT be null)
	 * @param rakes rakes for each rupture (CANNOT be null)
	 * @param rupAreas areas for each rupture (CANNOT be null)
	 * @param rupLengths lengths for each rupture (CAN be null)
	 * @param info metadata string
	 */
	public FaultSystemRupSet(
			List<? extends FaultSection> faultSectionData,
			@Nullable double[] sectSlipRates,
			@Nullable double[] sectSlipRateStdDevs,
			@Nullable double[] sectAreas,
			List<List<Integer>> sectionForRups,
			double[] mags,
			double[] rakes,
			double[] rupAreas,
			@Nullable double[] rupLengths) {
		Preconditions.checkNotNull(faultSectionData, "Fault Section Data cannot be null");
		this.faultSectionData = ImmutableList.copyOf(faultSectionData);
		Preconditions.checkNotNull(faultSectionData, "Magnitudes cannot be null");
		this.mags = mags;

		int numRups = mags.length;
		int numSects = this.faultSectionData.size();

		Preconditions.checkArgument(sectSlipRates == null
				|| sectSlipRates.length == numSects, "array sizes inconsistent!");
		this.sectSlipRates = sectSlipRates;

		Preconditions.checkArgument(sectSlipRateStdDevs == null
				|| sectSlipRateStdDevs.length == numSects, "array sizes inconsistent!");
		this.sectSlipRateStdDevs = sectSlipRateStdDevs;

		Preconditions.checkArgument(rakes.length == numRups, "array sizes inconsistent!");
		this.rakes = rakes;

		Preconditions.checkArgument(rupAreas == null ||
				rupAreas.length == numRups, "array sizes inconsistent!");
		this.rupAreas = rupAreas;

		Preconditions.checkArgument(rupLengths == null ||
				rupLengths.length == numRups, "array sizes inconsistent!");
		this.rupLengths = rupLengths;

		Preconditions.checkArgument(sectAreas == null ||
				sectAreas.length == numSects, "array sizes inconsistent!");
		this.sectAreas = sectAreas;

		Preconditions.checkArgument(sectionForRups.size() == numRups, "array sizes inconsistent!");
		this.sectionForRups = ImmutableList.copyOf(sectionForRups);
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

	@Override
	public void setParent(ModuleArchive<OpenSHA_Module> parent) throws IllegalStateException {
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
	
	public static FaultSystemRupSet load(File file) throws IOException {
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(file, FaultSystemRupSet.class);
		
		FaultSystemRupSet rupSet = archive.getModule(FaultSystemRupSet.class);
		Preconditions.checkState(rupSet != null, "Failed to load rupture set module from archive (see above error messages)");
		rupSet.archive = archive;
		
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

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		// TODO make final or allow subclasses to do extra stuff at the write stage?
		
		// CSV Files
		CSV_BackedModule.writeToArchive(buildRupturesCSV(), zout, entryPrefix, "ruptures.csv");
		CSV_BackedModule.writeToArchive(buildSectsCSV(), zout, entryPrefix, "sections.csv");
		
		// fault sections
		// TODO: retire the old XML format
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		scratch.UCERF3.utils.FaultSystemIO.fsDataToXML(root, FaultSectionPrefData.XML_METADATA_NAME+"List",
				null, null, getFaultSectionDataList());
		zout.putNextEntry(new ZipEntry(ArchivableModule.getEntryName(entryPrefix, "fault_sections.xml")));
		XMLWriter writer = new XMLWriter(new BufferedWriter(new OutputStreamWriter(zout)),
				OutputFormat.createPrettyPrint());
		writer.write(doc);
		writer.flush();
		zout.flush();
		zout.closeEntry();
	}
	
	private CSVFile<String> buildRupturesCSV() {
		CSVFile<String> csv = new CSVFile<>(false);
		
		csv.addLine("Rupture Index", "Magnitude", "Average Rake (degrees)", "Area (m^2)", "Length (m)",
				"Num Sections", "Section Index 1", "Section Index N");
		
		double[] lengths = getLengthForAllRups();
		for (int r=0; r<getNumRuptures(); r++) {
			List<Integer> sectIDs = getSectionsIndicesForRup(r);
			List<String> line = new ArrayList<>(5+sectIDs.size());
			
			line.add(r+"");
			line.add(getMagForRup(r)+"");
			line.add(getAveRakeForRup(r)+"");
			line.add(getAreaForRup(r)+"");
			if (lengths == null)
				line.add("");
			else
				line.add(lengths[r]+"");
			line.add(sectIDs.size()+"");
			for (int s : sectIDs)
				line.add(s+"");
			csv.addLine(line);
		}
		
		return csv;
	}
	
	private CSVFile<String> buildSectsCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		
		// TODO: what should we put in here? how should we represent sections in these files?
		csv.addLine("Section Index", "Section Name", "Area (m^2)", "Slip Rate (m/yr)", "Slip Rate Standard Deviation (m/yr)");
		double[] sectAreas = getAreaForAllSections();
		double[] sectSlipRates = getSlipRateForAllSections();
		double[] sectSlipRateStdDevs = getSlipRateStdDevForAllSections();
		for (int s=0; s<getNumSections(); s++) {
			FaultSection sect = getFaultSectionData(s);
			Preconditions.checkState(sect.getSectionId() == s, "Section ID mismatch, expected %s but is %s", s, sect.getSectionId());
			List<String> line = new ArrayList<>(5);
			line.add(s+"");
			line.add(sect.getSectionName());
			line.add(sectAreas == null ? "" : sectAreas[s]+"");
			line.add(sectSlipRates == null ? "" : sectSlipRates[s]+"");
			line.add(sectSlipRateStdDevs == null ? "" : sectSlipRateStdDevs[s]+"");
			csv.addLine(line);
		}
		
		return csv;
	}

	@Override
	public final void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		System.out.println("\tLoading ruptures CSV...");
		CSVFile<String> rupturesCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, "ruptures.csv");
		System.out.println("\tLoading sections CSV...");
		CSVFile<String> sectsCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, "sections.csv");
		
		// fault sections
		// TODO: retire old XML format
		ZipEntry fsdEntry = zip.getEntry(ArchivableModule.getEntryName(entryPrefix, "fault_sections.xml"));
		System.out.println("\tLoading sections XML...");
		Document doc;
		try {
			doc = XMLUtils.loadDocument(
					new BufferedInputStream(zip.getInputStream(fsdEntry)));
		} catch (DocumentException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		Element fsEl = doc.getRootElement().element(FaultSectionPrefData.XML_METADATA_NAME+"List");
		List<FaultSection> sections = scratch.UCERF3.utils.FaultSystemIO.fsDataFromXML(fsEl);
		
		int numRuptures = rupturesCSV.getNumRows()-1;
		Preconditions.checkState(numRuptures > 0, "No ruptures found in CSV file");
		int numSections = sectsCSV.getNumRows()-1;
		
		// load rupture data
		double[] mags = new double[numRuptures];
		double[] rakes = new double[numRuptures];
		double[] areas = new double[numRuptures];
		double[] lengths = null;
		List<List<Integer>> rupSectsList = new ArrayList<>(numRuptures);
		boolean shortSafe = numSections < Short.MAX_VALUE;
		System.out.println("\tParsing ruptures CSV");
		for (int r=0; r<numRuptures; r++) {
			int row = r+1;
			int col = 0;
			Preconditions.checkState(r == rupturesCSV.getInt(row, col++),
					"Ruptures out of order or not 0-based in CSV file, expected id=%s at row %s", r, row);
			mags[r] = rupturesCSV.getDouble(row, col++);
			rakes[r] = rupturesCSV.getDouble(row, col++);
			areas[r] = rupturesCSV.getDouble(row, col++);
			String lenStr = rupturesCSV.get(row, col++);
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
			int numRupSects = rupturesCSV.getInt(row, col++);
			Preconditions.checkState(numRupSects > 0, "Rupture %s has no sections!", r);
			List<Integer> rupSects;
			if (shortSafe) {
				short[] sectIDs = new short[numRupSects];
				for (int i=0; i<numRupSects; i++)
					sectIDs[i] = (short)rupturesCSV.getInt(row, col++);
				rupSects = new ShortListWrapper(sectIDs);
			} else {
				int[] sectIDs = new int[numRupSects];
				for (int i=0; i<numRupSects; i++)
					sectIDs[i] = rupturesCSV.getInt(row, col++);
				rupSects = new IntListWrapper(sectIDs);
			}
			int rowSize = rupturesCSV.getLine(row).size();
			Preconditions.checkState(col == rowSize,
					"Unexpected line lenth for rupture %s, have %s columns but expected %s", r, rowSize, col);
			rupSectsList.add(rupSects);
		}
		
		double[] sectAreas = null;
		double[] sectSlipRates = null;
		double[] sectSlipRateStdDevs = null;
		System.out.println("\tParsing sections CSV");
		for (int s=0; s<numSections; s++) {
			int row = s+1;
			int col = 0;
			Preconditions.checkState(s == sectsCSV.getInt(row, col++),
					"Sections out of order or not 0-based in CSV file, expected id=%s at row %s", s, row);
			
			col++; // don't need name
			String areaStr = sectsCSV.get(row, col++);
			String rateStr = sectsCSV.get(row, col++);
			String rateStdDevStr = sectsCSV.get(row, col++);
			
			
			if (s == 0) {
				if (!areaStr.isBlank())
					sectAreas = new double[numSections];
				if (!rateStr.isBlank())
					sectSlipRates = new double[numSections];
				if (!rateStdDevStr.isBlank())
					sectSlipRateStdDevs = new double[numSections];
			}
			if (sectAreas != null)
				sectAreas[s] = Double.parseDouble(areaStr);
			if (sectSlipRates != null)
				sectSlipRates[s] = Double.parseDouble(rateStr);
			if (sectSlipRateStdDevs != null)
				sectSlipRateStdDevs[s] = Double.parseDouble(rateStdDevStr);
			int rowSize = sectsCSV.getLine(row).size();
			Preconditions.checkState(col == rowSize,
					"Unexpected line lenth for section %s, have %s columns but expected %s", s, rowSize, col);
		}
		
		init(sections, sectSlipRates, sectSlipRateStdDevs, sectAreas,
				rupSectsList, mags, rakes, areas, lengths);
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
	        if (Integer.class.isAssignableFrom(oList.get(0).getClass()))
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
	        if (Integer.class.isAssignableFrom(oList.get(0).getClass()))
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
		init(rupSet.getFaultSectionDataList(), rupSet.getSlipRateForAllSections(),
				rupSet.getSlipRateStdDevForAllSections(), rupSet.getAreaForAllSections(),
				rupSet.getSectionIndicesForAllRups(), rupSet.getMagForAllRups(), rupSet.getAveRakeForAllRups(),
				rupSet.getAreaForAllRups(), rupSet.getLengthForAllRups());
		copyCacheFrom(rupSet);
		loadAllAvailableModules();
		for (OpenSHA_Module module : rupSet.getModules())
			addModule(module);
	}
	
	/**
	 * Sets all parameters
	 * 
	 * @param faultSectionData fault section data list (CANNOT be null)
	 * @param sectSlipRates slip rates for each fault section with any reductions applied (CAN be null)
	 * @param sectSlipRateStdDevs slip rate std deviations for each fault section (CAN be null)
	 * @param sectAreas areas for each fault section (CAN be null)
	 * @param sectionForRups list of fault section indexes for each rupture (CANNOT be null)
	 * @param mags magnitudes for each rupture (CANNOT be null)
	 * @param rakes rakes for each rupture (CANNOT be null)
	 * @param rupAreas areas for each rupture (CANNOT be null)
	 * @param rupLengths lengths for each rupture (CAN be null)
	 * @param info metadata string
	 */
	protected void init(
			List<? extends FaultSection> faultSectionData,
			double[] sectSlipRates,
			double[] sectSlipRateStdDevs,
			double[] sectAreas,
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
		int numSects = faultSectionData.size();
		
		Preconditions.checkArgument(sectSlipRates == null
				|| sectSlipRates.length == numSects, "array sizes inconsistent!");
		this.sectSlipRates = sectSlipRates;
		
		Preconditions.checkArgument(sectSlipRateStdDevs == null
				|| sectSlipRateStdDevs.length == numSects, "array sizes inconsistent!");
		this.sectSlipRateStdDevs = sectSlipRateStdDevs;
		
		Preconditions.checkArgument(rakes.length == numRups, "array sizes inconsistent!");
		this.rakes = rakes;
		
		Preconditions.checkArgument(rupAreas == null ||
				rupAreas.length == numRups, "array sizes inconsistent!");
		this.rupAreas = rupAreas;
		
		Preconditions.checkArgument(rupLengths == null ||
				rupLengths.length == numRups, "array sizes inconsistent!");
		this.rupLengths = rupLengths;
		
		Preconditions.checkArgument(sectAreas == null ||
				sectAreas.length == numSects, "array sizes inconsistent!");
		this.sectAreas = sectAreas;
		
		Preconditions.checkArgument(sectionForRups.size() == numRups, "array sizes inconsistent!");
		this.sectionForRups = sectionForRups;
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

	/**
	 * @return Area (SI units: sq-m)
	 */
	public double[] getAreaForAllSections() {
		return sectAreas;
	}

	/**
	 * @param sectIndex
	 * @return Area (SI units: sq-m)
	 */
	public double getAreaForSection(int sectIndex) {
		return sectAreas[sectIndex];
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

	/**
	 * This returns the section slip rate of the given section. It can differ from what is returned by
	 * getFaultSectionData(index).get*AveSlipRate() if there are any reductions for creep or subseismogenic ruptures.
	 * @return slip rate (SI units: m)
	 */
	public double getSlipRateForSection(int sectIndex) {
		return sectSlipRates[sectIndex];
	}

	/**
	 * This returns the section slip rate of all sections. It can differ from what is returned by
	 * getFaultSectionData(index).get*AveSlipRate() if there are any reductions for creep or subseismogenic ruptures.
	 * @return slip rate (SI units: m)
	 */
	public double[] getSlipRateForAllSections() {
		return sectSlipRates;
	}

	/**
	 * This returns the standard deviation of the the slip rate for the given section. It can differ from what is returned by
	 * getFaultSectionData(index).getSlipRateStdDev() if there are any reductions for creep or subseismogenic ruptures.
	 * @return slip rate standard deviation (SI units: m)
	 */
	public double getSlipRateStdDevForSection(int sectIndex) {
		return sectSlipRateStdDevs[sectIndex];
	}

	/**
	 * This differs from what is returned by getFaultSectionData(int).getSlipRateStdDev()
	 * where there has been a modification (i.e., moment rate reductions for smaller events).
	 * @return slip rate standard deviation (SI units: m)
	 */
	public double[] getSlipRateStdDevForAllSections() {
		return sectSlipRateStdDevs;
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
		for (int s=0; s<getNumSections(); s++) {
			FaultSection mySect = getFaultSectionData(s);
			FaultSection oSect = other.getFaultSectionData(s);
			if (mySect.getParentSectionId() != oSect.getParentSectionId())
				return false;
			if (!mySect.getSectionName().equals(oSect.getSectionName()))
				return false;
		}
		
		// check ruptures
		if (getNumRuptures() != other.getNumRuptures())
			return false;
		for (int r=0; r<getNumRuptures(); r++) {
			List<Integer> mySects = getSectionsIndicesForRup(r);
			List<Integer> oSects = other.getSectionsIndicesForRup(r);
			if (!mySects.equals(oSects))
				return false;
		}
		return true;
	}
	
	public static void main(String[] args) throws Exception {
		File baseDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		
		File inputFile = new File(baseDir, "fm3_1_ucerf3.zip");
		File destFile = new File("/tmp/new_format_u3.zip");
		FaultSystemRupSet orig = FaultSystemIO.loadRupSet(inputFile);
		orig.getArchive().writeArchive(destFile);
		FaultSystemRupSet loaded = load(destFile);
		Preconditions.checkState(orig.isEquivalentTo(loaded));
	}

}
