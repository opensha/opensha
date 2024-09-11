package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.List;
import java.util.stream.Collectors;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public abstract class ModSectMinMags implements SubModule<FaultSystemRupSet>, BranchAverageableModule<ModSectMinMags>,
SplittableRuptureModule<ModSectMinMags> {
	
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
	 * Checks if the given magnitude is below the section minimum magnitude, to 4-byte floating point precision.
	 * 
	 * @param sectIndex
	 * @param mag
	 * @return
	 */
	public boolean isBelowSectMinMag(int sectIndex, double mag) {
		return isBelowSectMinMag(sectIndex, mag, null);
	}
	
	/**
	 * Checks if the given magnitude is below the section minimum magnitude, to 4-byte floating point precision.
	 * If a reference gridding is supplied, then this will only return true if the given magnitude maps to a bin index
	 * that is lower than the bin index that contains the minimum magnitude.
	 * 
	 * @param sectIndex
	 * @param mag
	 * @param referenceGridding
	 * @return true if the magnitude is below the section minimum magnitude
	 */
	public boolean isBelowSectMinMag(int sectIndex, double mag, EvenlyDiscretizedFunc referenceGridding) {
		double minMag = getMinMagForSection(sectIndex);
		if ((float)mag >= (float)minMag)
			// it's above, simple
			return false;
		
		if (referenceGridding != null) {
			// only return true if it's mapped to a lower bin
			
			// add a tiny but in case it's perfectly on a bin edge
			int minIndex = referenceGridding.getClosestXIndex(minMag+1e-4);
			
			int magIndex = referenceGridding.getClosestXIndex(mag);
			
			return magIndex < minIndex;
		}
		
		return true;
	}
	
	/**
	 * Checks if the given rupture is below the minimum magnitude of any participation sections, according to
	 * {@link ModSectMinMags#isBelowSectMinMag(int, double)}. 
	 * 
	 * @param rupIndex
	 * @return
	 */
	public boolean isRupBelowSectMinMag(int rupIndex) {
		return isRupBelowSectMinMag(rupIndex, null);
	}
	
	/**
	 * Checks if the given rupture is below the minimum magnitude of any participation sections, according to
	 * {@link ModSectMinMags#isBelowSectMinMag(int, double, EvenlyDiscretizedFunc)}. 
	 * 
	 * @param rupIndex
	 * @param referenceGridding
	 * @return
	 */
	public boolean isRupBelowSectMinMag(int rupIndex, EvenlyDiscretizedFunc referenceGridding) {
		double mag = rupSet.getMagForRup(rupIndex);
		for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex))
			if (isBelowSectMinMag(sectIndex, mag, referenceGridding))
				return true;
		
		return false;
	}
	
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

		@Override
		public AveragingAccumulator<ModSectMinMags> averagingAccumulator() {
			return new AveragingAccumulator<>() {
				
				private double[] avgValues = null;
				private double sumWeight = 0d;

				@Override
				public void process(ModSectMinMags module, double relWeight) {
					double[] modVals = module.getMinMagForSections();
					if (avgValues == null)
						avgValues = new double[modVals.length];
					else
						Preconditions.checkState(modVals.length == avgValues.length);
					
					for (int i=0; i< avgValues.length; i++)
						avgValues[i] += modVals[i]*relWeight;
					sumWeight += relWeight;
				}

				@Override
				public ModSectMinMags getAverage() {
					AverageableModule.scaleToTotalWeight(avgValues, sumWeight);
					// rupture set will be attached when it's added to one later
					Precomputed ret = new Precomputed();
					ret.sectMinMags = avgValues;
					return ret;
				}

				@Override
				public Class<ModSectMinMags> getType() {
					return ModSectMinMags.class;
				}
			};
		}

		@Override
		public ModSectMinMags getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
			double[] remapped = new double[mappings.getNumRetainedSects()];
			for (int s=0; s<remapped.length; s++)
				remapped[s] = sectMinMags[mappings.getOrigSectID(s)];
			return new Precomputed(rupSubSet, remapped);
		}

		@Override
		public ModSectMinMags getForSplitRuptureSet(FaultSystemRupSet splitRupSet, RuptureSetSplitMappings mappings) {
			double[] remapped = new double[splitRupSet.getNumSections()];
			for (int s=0; s<remapped.length; s++)
				remapped[s] = sectMinMags[mappings.getOrigSectID(s)];
			return new Precomputed(splitRupSet, remapped);
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
