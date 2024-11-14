package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.CSVReader;
import org.opensha.commons.data.CSVReader.Row;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.AverageableModule.ConstantAverageable;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.helpers.LargeCSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.SlipAlongRuptureModelBranchNode;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

public abstract class SlipAlongRuptureModel implements OpenSHA_Module {

	public static SlipAlongRuptureModel forModel(SlipAlongRuptureModelBranchNode slipAlong) {
		return slipAlong.getModel();
	}
	
	/**
	 * This gives the slip (SI units: m) on each section for the rth rupture
	 * @return slip (SI units: m) on each section for the rth rupture
	 */
	public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, AveSlipModule aveSlips, int rthRup) {
		return calcSlipOnSectionsForRup(rupSet, rthRup, aveSlips.getAveSlip(rthRup));
	}
	
	/**
	 * @return true if this is a uniform model (all returned slip values will be identical for a given rupture), otherwise false
	 */
	public boolean isUniform() {
		return false;
	}
	
	/**
	 * This gives the slip (SI units: m) on each section for the rth rupture
	 * @return slip (SI units: m) on each section for the rth rupture
	 */
	public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup, double aveSlip) {
		List<Integer> sectionIndices = rupSet.getSectionsIndicesForRup(rthRup);
		int numSects = sectionIndices.size();

		// compute rupture area
		double[] sectArea = new double[numSects];
		int index=0;
		for(Integer sectID: sectionIndices) {	
			//				FaultSectionPrefData sectData = getFaultSectionData(sectID);
			//				sectArea[index] = sectData.getTraceLength()*sectData.getReducedDownDipWidth()*1e6;	// aseismicity reduces area; 1e6 for sq-km --> sq-m
			sectArea[index] = rupSet.getAreaForSection(sectID);
			index += 1;
		}
		
		return calcSlipOnSectionsForRup(rupSet, rthRup, sectArea, aveSlip);
	}
	
	/**
	 * This computes the solution slip rate of the given section (SI units: m/yr)
	 * 
	 * @return
	 */
	public double calcSlipRateForSect(FaultSystemSolution sol, AveSlipModule aveSlips, int sectIndex) {
		return calcSlipRateForSects(sol, aveSlips)[sectIndex];
	}
	
	/**
	 * This computes the solution slip rate of all sections (SI units: m/yr)
	 * 
	 * @return
	 */
	public double[] calcSlipRateForSects(FaultSystemSolution sol, AveSlipModule aveSlips) {
		SolutionSlipRates cached = sol.getModule(SolutionSlipRates.class);
		
		if (cached == null) {
			synchronized (sol) {
				cached = sol.getModule(SolutionSlipRates.class);
				if (cached == null) {
					cached = SolutionSlipRates.calc(sol, aveSlips, this);
					sol.addModule(cached);
				}
			}
		}
		
		return cached.get();
	}
	
	/**
	 * This gives the slip (SI untis: m) on each section for the rth rupture where the participating section areas,
	 * moment rates, and rupture average slip are all known.
	 * 
	 * @param rupSet
	 * @param rthRup
	 * @param sectArea
	 * @param sectMoRate
	 * @param aveSlip
	 * @return slip (SI untis: m) on each section for the rth rupture
	 */
	public abstract double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet,
			int rthRup, double[] sectArea, double aveSlip);
	
	private static abstract class NamedSlipAlongRuptureModel extends SlipAlongRuptureModel implements ArchivableModule,
		ConstantAverageable<NamedSlipAlongRuptureModel>, SplittableRuptureModule<NamedSlipAlongRuptureModel>{
		
		@Override
		public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
			// do nothing (no serialization required, just must be listed)
		}

		@Override
		public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
			// do nothing (no deserialization required, just must be listed)
		}

		@Override
		public Class<NamedSlipAlongRuptureModel> getAveragingType() {
			return NamedSlipAlongRuptureModel.class;
		}

		@Override
		public boolean isIdentical(NamedSlipAlongRuptureModel module) {
			return this.getClass().equals(module.getClass());
		}

		@Override
		public NamedSlipAlongRuptureModel getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
			return this;
		}

		@Override
		public NamedSlipAlongRuptureModel getForSplitRuptureSet(FaultSystemRupSet splitRupSet,
				RuptureSetSplitMappings mappings) {
			return this;
		}
		
	}

	public static class Uniform extends NamedSlipAlongRuptureModel {
		
		public Uniform() {}

		@Override
		public String getName() {
			return "Uniform Slip Along Rupture";
		}

		@Override
		public boolean isUniform() {
			return true;
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup, double aveSlip) {
			return calcUniformSlipAlong(rupSet.getSectionsIndicesForRup(rthRup).size(), aveSlip);
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
				double[] sectArea, double aveSlip) {
			return calcUniformSlipAlong(sectArea.length, aveSlip);
		}
		
	}
	
	private static double[] calcUniformSlipAlong(int numSects, double aveSlip) {
		double[] slipsForRup = new double[numSects];
		
		for(int s=0; s<slipsForRup.length; s++)
			slipsForRup[s] = aveSlip;
		
		return slipsForRup;
	}
	
	/**
	 * Transient (won't be serialized) default implementation of the uniform slip-along-rupture model
	 * @author kevin
	 *
	 */
	public static class Default extends SlipAlongRuptureModel {
		
		public Default() {}

		@Override
		public String getName() {
			return "Default (Uniform) Slip Along Rupture";
		}

		@Override
		public boolean isUniform() {
			return true;
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup, double aveSlip) {
			return calcUniformSlipAlong(rupSet.getSectionsIndicesForRup(rthRup).size(), aveSlip);
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
				double[] sectArea, double aveSlip) {
			return calcUniformSlipAlong(sectArea.length, aveSlip);
		}
	}
	
	private static EvenlyDiscretizedFunc taperedSlipPDF, taperedSlipCDF;
	
	public static class Tapered extends NamedSlipAlongRuptureModel {
		
		public Tapered() {}

		@Override
		public String getName() {
			return "Tapered Slip Along Rupture";
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
				double[] sectArea, double aveSlip) {
			double[] slipsForRup = new double[sectArea.length];
			
			// note that the ave slip is partitioned by area, not length; this is so the final model is moment balanced.

			// make the taper function if hasn't been done yet
			if(taperedSlipCDF == null) {
				synchronized (SlipAlongRuptureModel.class) {
					if (taperedSlipCDF == null) {
						EvenlyDiscretizedFunc taperedSlipCDF = new EvenlyDiscretizedFunc(0, 5001, 0.0002);
						EvenlyDiscretizedFunc taperedSlipPDF = new EvenlyDiscretizedFunc(0, 5001, 0.0002);
						double x,y, sum=0;
						int num = taperedSlipPDF.size();
						for(int i=0; i<num;i++) {
							x = taperedSlipPDF.getX(i);
							y = Math.pow(Math.sin(x*Math.PI), 0.5);
							taperedSlipPDF.set(i,y);
							sum += y;
						}
						// now make final PDF & CDF
						y=0;
						for(int i=0; i<num;i++) {
							y += taperedSlipPDF.getY(i);
							taperedSlipCDF.set(i,y/sum);
							taperedSlipPDF.set(i,taperedSlipPDF.getY(i)/sum);
//							System.out.println(taperedSlipCDF.getX(i)+"\t"+taperedSlipPDF.getY(i)+"\t"+taperedSlipCDF.getY(i));
						}
						SlipAlongRuptureModel.taperedSlipCDF = taperedSlipCDF;
						SlipAlongRuptureModel.taperedSlipPDF = taperedSlipPDF;
					}
				}
			}
			double normBegin=0, normEnd, scaleFactor;
			for(int s=0; s<slipsForRup.length; s++) {
				normEnd = normBegin + sectArea[s]/rupSet.getAreaForRup(rthRup);
				// fix normEnd values that are just past 1.0
				//					if(normEnd > 1 && normEnd < 1.00001) normEnd = 1.0;
				if(normEnd > 1 && normEnd < 1.01) normEnd = 1.0;
				scaleFactor = taperedSlipCDF.getInterpolatedY(normEnd)-taperedSlipCDF.getInterpolatedY(normBegin);
				scaleFactor /= (normEnd-normBegin);
				Preconditions.checkState(normEnd>=normBegin, "End is before beginning!");
				Preconditions.checkState(aveSlip >= 0, "Negative ave slip: "+aveSlip);
				slipsForRup[s] = aveSlip*scaleFactor;
				normBegin = normEnd;
			}
			
			return slipsForRup;
		}
		
	}
	
	public static class WG02 extends NamedSlipAlongRuptureModel {
		
		public WG02() {}

		@Override
		public String getName() {
			return "WG02 Slip Along Rupture";
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
				double[] sectArea, double aveSlip) {
			double[] slipsForRup = new double[sectArea.length];
			
			// compute rupture area
			double[] sectMoRate = new double[sectArea.length];
			int index=0;
			for(Integer sectID : rupSet.getSectionsIndicesForRup(rthRup)) {	
				sectMoRate[index] = FaultMomentCalc.getMoment(sectArea[index], rupSet.getSlipRateForSection(sectID));
				index += 1;
			}
			
			List<Integer> sectsInRup = rupSet.getSectionsIndicesForRup(rthRup);
			double totMoRateForRup = 0;
			for(Integer sectID:sectsInRup) {
				double area = rupSet.getAreaForSection(sectID);
				totMoRateForRup += FaultMomentCalc.getMoment(area, rupSet.getSlipRateForSection(sectID));
			}
			for(int s=0; s<slipsForRup.length; s++) {
				slipsForRup[s] = aveSlip*sectMoRate[s]*rupSet.getAreaForRup(rthRup)/(totMoRateForRup*sectArea[s]);
			}
			
			return slipsForRup;
		}
		
	}
	
	public static class AVG_UCERF3 extends NamedSlipAlongRuptureModel {
		
		public AVG_UCERF3() {}

		@Override
		public String getName() {
			return "Mean UCERF3 Slip Along Rupture";
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
				double[] sectArea, double aveSlip) {
			double[] slipsForRup = new double[sectArea.length];
			
			// get mean weights
			List<Double> meanWeights = new ArrayList<>();
			List<SlipAlongRuptureModels> meanSALs = new ArrayList<>();

			double sum = 0;
			for (SlipAlongRuptureModels sal : SlipAlongRuptureModels.values()) {
				double weight = sal.getRelativeWeight(null);
				if (weight > 0) {
					meanWeights.add(weight);
					meanSALs.add(sal);
					sum += weight;
				}
			}
			if (sum != 0)
				for (int i=0; i<meanWeights.size(); i++)
					meanWeights.set(i, meanWeights.get(i)/sum);

			// calculate mean

			for (int i=0; i<meanSALs.size(); i++) {
				double weight = meanWeights.get(i);
				double[] subSlips = meanSALs.get(i).getModel().calcSlipOnSectionsForRup(
						rupSet, rthRup, sectArea, aveSlip);

				for (int j=0; j<slipsForRup.length; j++)
					slipsForRup[j] += weight*subSlips[j];
			}
			
			return slipsForRup;
		}
		
	}
	
	public static class Precomputed extends SlipAlongRuptureModel implements LargeCSV_BackedModule,
		AverageableModule<Precomputed>, SplittableRuptureModule<Precomputed> {
		
		public static final String FILE_NAME = "slip_along_ruptures.csv";
		
		private List<double[]> slipsAlong;
		
		@SuppressWarnings("unused") // used for deserialization, just not directly
		private Precomputed() {};
		
		public Precomputed(List<double[]> slipsAlong) {
			this.slipsAlong = slipsAlong;
		}

		@Override
		public String getName() {
			return "Precomputed Slip Along Ruptures";
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup, double[] sectArea,
				double aveSlip) {
			Preconditions.checkState(rupSet == null || rupSet.getNumRuptures() == slipsAlong.size(),
					"Rupture set has a different rupture count than we do");
			return slipsAlong.get(rthRup);
		}

		@Override
		public Precomputed getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
			List<double[]> subset = new ArrayList<>(mappings.getNumRetainedRuptures());
			for (int r=0; r<mappings.getNumRetainedRuptures(); r++)
				subset.add(slipsAlong.get(mappings.getOrigRupID(r)));
			return new Precomputed(subset);
		}

		@Override
		public Precomputed getForSplitRuptureSet(FaultSystemRupSet splitRupSet, RuptureSetSplitMappings mappings) {
			List<double[]> splitSet = new ArrayList<>(mappings.getNewNumRuptures());
			for (int r=0; r<mappings.getNewNumRuptures(); r++)
				splitSet.add(slipsAlong.get(mappings.getOrigRupID(r)));
			return new Precomputed(splitSet);
		}

		@Override
		public AveragingAccumulator<Precomputed> averagingAccumulator() {
			return new PrecomputedAverager();
		}

		@Override
		public String getFileName() {
			return FILE_NAME;
		}

		@Override
		public void writeCSV(CSVWriter writer) throws IOException {
			writer.write(List.of("Rupture Index", "Section Slip 1 (m)", "...", "Section Slip N (m)"));
			for (int r=0; r<slipsAlong.size(); r++) {
				double[] slips = slipsAlong.get(r);
				List<String> line = new ArrayList<>(slips.length+1);
				line.add(r+"");
				for (double slip : slips)
					line.add(slip+"");
				writer.write(line);
			}
		}

		@Override
		public void initFromCSV(CSVReader csv) {
			List<double[]> slipsAlong = new ArrayList<>();
			csv.read(); // skip the first line
			for (Row row : csv) {
				int index = row.getInt(0);
				Preconditions.checkState(index == slipsAlong.size(),
						"Rows out of order? Encountered rupture index %s, expected %s", index, slipsAlong.size());
				double[] slips = new double[row.columns()-1];
				for (int i=0; i<slips.length; i++)
					slips[i] = row.getDouble(i+1);
				slipsAlong.add(slips);
			}
			this.slipsAlong = slipsAlong;
		}
		
	}
	
	private static class PrecomputedAverager implements AveragingAccumulator<Precomputed> {
		
		private double sumWeights;
		private List<double[]> weightedSlipsAlong;

		@Override
		public Class<Precomputed> getType() {
			return Precomputed.class;
		}

		@Override
		public void process(Precomputed module, double relWeight) {
			// TODO Auto-generated method stub
			if (weightedSlipsAlong == null) {
				weightedSlipsAlong = new ArrayList<>(module.slipsAlong.size());
				for (int i=0; i<module.slipsAlong.size(); i++)
					weightedSlipsAlong.add(new double[module.slipsAlong.get(i).length]);
				sumWeights = 0d;
			}
			if (relWeight == 0d)
				return;
			
			Preconditions.checkState(module.slipsAlong.size() == weightedSlipsAlong.size(),
					"Passed in module has a different rupture count: %s != %s",
					module.slipsAlong.size(), weightedSlipsAlong.size());
			for (int r=0; r<weightedSlipsAlong.size(); r++) {
				double[] weightedSlips = weightedSlipsAlong.get(r);
				double[] moduleSlips = module.slipsAlong.get(r);
				Preconditions.checkState(moduleSlips.length == weightedSlips.length,
						"Passed in module has a different section count for rupture %s: %s != %s",
						r, moduleSlips.length, weightedSlips.length);
				for (int s=0; s<weightedSlips.length; s++)
					weightedSlips[s] += relWeight*moduleSlips[s];
			}
		}

		@Override
		public Precomputed getAverage() {
			Preconditions.checkNotNull(weightedSlipsAlong, "Never initialized (or was reused?)");
			Preconditions.checkState(sumWeights > 0d, "All weights were zero");
			
			if (sumWeights != 1d) {
				double scalar = 1d/sumWeights;
				for (int r=0; r<weightedSlipsAlong.size(); r++) {
					double[] weightedSlips = weightedSlipsAlong.get(r);
					for (int s=0; s<weightedSlips.length; s++)
						weightedSlips[s] *= scalar;
				}
			}
			Precomputed ret = new Precomputed(weightedSlipsAlong);
			
			weightedSlipsAlong = null;
			sumWeights = Double.NaN;
			return ret;
		}
		
	}

}
