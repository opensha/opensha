package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public abstract class ModSectMinMags implements SubModule<FaultSystemRupSet> {
	
	FaultSystemRupSet rupSet;

	protected ModSectMinMags(FaultSystemRupSet rupSet) {
		super();
		this.rupSet = rupSet;
	}

	@Override
	public String getName() {
		return "Modified Section Minimum Magnitudes";
	}
	
	public abstract double getMinMagForSection(int sectIndex);
	
	public abstract double[] getMinMagForSections();
	
	/**
	 * Sets the section minimum magnitudes as the maximum value of the supplied system-wide minimum magnitude
	 * and the rupture set section minimum magnitude. If useMaxForParent == true, then it will also ensure that
	 * each section along a parent section has the same minimum magnitude (the maximum such for that parent).
	 * 
	 * @param rupSet
	 * @param systemWideMinMag
	 * @param useMaxForParent
	 * @return
	 */
	public static ModSectMinMags above(FaultSystemRupSet rupSet, double systemWideMinMag, boolean useMaxForParent) {
		double[] minMags = new double[rupSet.getNumSections()];
		for (int s=0; s<minMags.length; s++)
			minMags[s] = Math.max(systemWideMinMag, rupSet.getMinMagForSection(s));
		if (useMaxForParent) {
			for (List<? extends FaultSection> parentSects : rupSet.getFaultSectionDataList().stream().collect(
					Collectors.groupingBy(S -> S.getParentSectionId())).values()) {
				double maxMin = 0d;
				for (FaultSection sect : parentSects)
					maxMin = Math.max(maxMin, minMags[sect.getSectionId()]);
				for (FaultSection sect : parentSects)
					minMags[sect.getSectionId()] = Math.max(minMags[sect.getSectionId()], maxMin);
			}
		}
		return new Precomputed(rupSet, minMags);
	}
	
	public static ModSectMinMags instance(FaultSystemRupSet rupSet, double[] sectMinMags) {
		return new Precomputed(rupSet, sectMinMags);
	}

	public static class Precomputed extends ModSectMinMags implements CSV_BackedModule {

		private double[] sectMinMags;

		private Precomputed() {
			super(null);
		}

		private Precomputed(FaultSystemRupSet rupSet, double[] sectMinMags) {
			super(rupSet);
			Preconditions.checkNotNull(rupSet);
			Preconditions.checkNotNull(sectMinMags);
			Preconditions.checkState(rupSet.getNumSections() == sectMinMags.length);
			this.sectMinMags = sectMinMags;
		}

		@Override
		public double getMinMagForSection(int sectIndex) {
			return sectMinMags[sectIndex];
		}

		@Override
		public double[] getMinMagForSections() {
			return sectMinMags;
		}

		@Override
		public String getFileName() {
			return "mod_sect_min_mags.csv";
		}

		@Override
		public CSVFile<?> getCSV() {
			CSVFile<String> csv = new CSVFile<>(true);
			csv.addLine("Section Index", "Minimum Magnitude");
			for (int s=0; s<sectMinMags.length; s++)
				csv.addLine(s+"", sectMinMags[s]+"");
			return csv;
		}

		@Override
		public void initFromCSV(CSVFile<String> csv) {
			int numSects = rupSet.getNumSections();
			Preconditions.checkState(csv.getNumRows() == numSects+1,
					"Expected 1 header row and %s section rows, have %s", numSects, csv.getNumRows());

			double[] sectMinMags = new double[numSects];
			for (int r=0; r<numSects; r++) {
				int row = r+1;
				Preconditions.checkState(csv.getInt(row, 0) == r, "Data not in order (or not 0-based)");
				sectMinMags[r] = csv.getDouble(row, 1);
			}
			this.sectMinMags = sectMinMags;
		}
		
		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
			Preconditions.checkState(rupSet.getNumSections() == newParent.getNumSections());
			
			return new Precomputed(newParent, sectMinMags);
		}

	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		if (this.rupSet != null)
			Preconditions.checkState(rupSet.getNumSections() == parent.getNumSections());
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}

}
