package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class BranchSectNuclMFDs implements FileBackedModule {

	private IncrementalMagFreqDist refMFD;
	private float[][][] branchSectMFDs;
	private short[][] branchSectMinMagIndexes;
	private double[] weights;
	
	public static class Builder {
		
		// oversized reference MFD that starts at M=0 and goes to M=12
		// will be reduced to the actual magnitude range when the final module is built
		private static final EvenlyDiscretizedFunc refMFD = new EvenlyDiscretizedFunc(
				0.05, 120, 0.1);
		private static final double refMinMag = refMFD.getX(0)-0.5*refMFD.getDelta();
		private static final double refMaxMag = refMFD.getX(refMFD.size()-1)+0.5*refMFD.getDelta();
		
		private List<float[][]> branchSectMFDs;
		private List<short[]> branchSectMinMagIndexes;
		private List<Double> weights;
		
		private int minMagIndex = Integer.MAX_VALUE;
		private int maxMagIndex = 0;
		
		public synchronized void process(FaultSystemSolution sol, double weight) {
			int numSects = sol.getRupSet().getNumSections();
			if (branchSectMFDs == null) {
				// first time, initialize lists
				branchSectMFDs = new ArrayList<>();
				branchSectMinMagIndexes = new ArrayList<>();
				weights = new ArrayList<>();
			} else {
				// ensure section count is consistent
				Preconditions.checkState(numSects == branchSectMFDs.get(0).length);
			}
			// see if we've already built these distributions and can reuse them
			// that happens for logic-tree branch average creation
			SingleBranchNuclMFDs branchMFDs = sol.getModule(SingleBranchNuclMFDs.class);
			if (branchMFDs == null) {
				double branchMinMag = sol.getRupSet().getMinMag();
				double branchMaxMag = sol.getRupSet().getMaxMag();
				Preconditions.checkState(branchMinMag >= refMinMag,
						"Branch has extreme magnitudes outside of reference range: min=%s, refRange=[%s,%s]",
						branchMinMag, refMinMag, refMaxMag);
				Preconditions.checkState(branchMaxMag <= refMaxMag,
						"Branch has extreme magnitudes outside of reference range: max=%s, refRange=[%s,%s]",
						branchMaxMag, refMinMag, refMaxMag);
				float[][] sectMFDs = new float[numSects][];
				short[] minMagIndexes = new short[numSects];
				int branchMinIndex = Integer.MAX_VALUE;
				int branchMaxIndex = 0;
				for (int sectIndex=0; sectIndex<numSects; sectIndex++) {
					IncrementalMagFreqDist mfd = sol.calcNucleationMFD_forSect(
							sectIndex, refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
					int myMinIndex = Integer.MAX_VALUE;
					int myMaxIndex = 0;
					for (int i=0; i<refMFD.size(); i++) {
						if (mfd.getY(i) > 0d) {
							myMinIndex = Integer.min(myMinIndex, i);
							myMaxIndex = Integer.max(myMaxIndex, i);
						}
					}
					if (myMinIndex <= myMaxIndex) {
						// we have a nonzero value
						sectMFDs[sectIndex] = new float[1+myMaxIndex-myMinIndex];
						for (int i=0; i<sectMFDs[sectIndex].length; i++)
							sectMFDs[sectIndex][i] = (float)mfd.getY(i+myMinIndex);
						minMagIndexes[sectIndex] = (short)myMinIndex;
						branchMinIndex = Integer.min(branchMinIndex, myMinIndex);
						branchMaxIndex = Integer.max(branchMaxIndex, myMaxIndex);
					}
				}
				minMagIndex = Integer.min(minMagIndex, branchMinIndex);
				maxMagIndex = Integer.max(maxMagIndex, branchMaxIndex);
				branchMFDs = new SingleBranchNuclMFDs(minMagIndexes, sectMFDs, branchMinIndex, branchMaxIndex);
				sol.addModule(branchMFDs);
			} else {
				// process them for min/max mag
				minMagIndex = Integer.min(minMagIndex, branchMFDs.branchMinIndex);
				maxMagIndex = Integer.max(maxMagIndex, branchMFDs.branchMaxIndex);
			}
			
			branchSectMFDs.add(branchMFDs.sectMFDs);
			branchSectMinMagIndexes.add(branchMFDs.sectMinMagIndexes);
			weights.add(weight);
		}
		
		public BranchSectNuclMFDs build() {
			BranchSectNuclMFDs ret = new BranchSectNuclMFDs();
			
			int numBranches = weights.size();
			Preconditions.checkState(numBranches > 0);
			ret.weights = Doubles.toArray(weights);
			int numSects = branchSectMFDs.get(0).length;
			
			Preconditions.checkState(minMagIndex <= maxMagIndex,
					"No sections with non-zero rates? minIndex=%s > maxIndex=%s", minMagIndex, maxMagIndex);
			
			// trim the MFDs
			ret.refMFD = new IncrementalMagFreqDist(refMFD.getX(minMagIndex), 1+maxMagIndex-minMagIndex, refMFD.getDelta());
			ret.branchSectMFDs = new float[numBranches][][];
			ret.branchSectMinMagIndexes = new short[numBranches][numSects];
			for (int b=0; b<numBranches; b++) {
				ret.branchSectMFDs[b] = branchSectMFDs.get(b);
				for (int s=0; s<numSects; s++) {
					if (ret.branchSectMFDs[b][s] == null)
						continue;
					int origMinMagIndex = branchSectMinMagIndexes.get(b)[s];
					int modMinMagIndex = origMinMagIndex - minMagIndex;
					Preconditions.checkState(modMinMagIndex >= 0);
					ret.branchSectMinMagIndexes[b][s] = (short)modMinMagIndex;
				}
			}
			
			return ret;
		}
	}
	
	/**
	 * Transient module for storing branch-specific section MFDs. this allows them to be reused in the case that
	 * we perform multiple averaging operations, reducing memory requirements
	 * @author kevin
	 *
	 */
	private static class SingleBranchNuclMFDs implements OpenSHA_Module {
		
		private short[] sectMinMagIndexes;
		private float[][] sectMFDs;
		private int branchMinIndex;
		private int branchMaxIndex;

		public SingleBranchNuclMFDs(short[] sectMinMagIndexes, float[][] sectMFDs, int branchMinIndex,
				int branchMaxIndex) {
			super();
			this.sectMinMagIndexes = sectMinMagIndexes;
			this.sectMFDs = sectMFDs;
			this.branchMinIndex = branchMinIndex;
			this.branchMaxIndex = branchMaxIndex;
		}

		@Override
		public String getName() {
			return "Single-Branch Section Nucleation MFDs";
		}
		
	}
	
	private BranchSectNuclMFDs() {}

	@Override
	public String getFileName() {
		return "branch_sect_nucl_mfds.csv";
	}

	@Override
	public String getName() {
		return "Branch Section Nucleation MFDs";
	}
	
	private static final DecimalFormat rateDF = new DecimalFormat("0.###E0");

	@Override
	public void writeToStream(BufferedOutputStream out) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(out);
		
		int numBranches = branchSectMFDs.length;
		int numSections = branchSectMFDs[0].length;
		
		// write counts
		writer.write(CSVFile.getLineStr(new String[] {"Num Branches", "Num Sections"}));
		writer.write('\n');
		writer.write(CSVFile.getLineStr(new String[] {numBranches+"", numSections+""}));
		writer.write('\n');
		
		// write x values
		writer.write(CSVFile.getLineStr(new String[] {"Magnitudes"}));
		writer.write('\n');
		List<String> xValsHeader = new ArrayList<>(refMFD.size());
		for (int i=0; i<refMFD.size(); i++)
			xValsHeader.add((float)refMFD.getX(i)+"");
		writer.write(CSVFile.getLineStr(xValsHeader));
		writer.write('\n');
		
		String[] commonBranchHeader = {"Branch Index", "Branch Weight"};
		String[] commonMFDHeader = {"Section Index", "Min Mag Index", "Rate1", "Rate2", "...", "RateN"};
		
		for (int b=0; b<numBranches; b++) {
			writer.write(CSVFile.getLineStr(commonBranchHeader));
			writer.write('\n');
			writer.write(CSVFile.getLineStr(new String[] { b+"", weights[b]+""}));
			writer.write('\n');
			writer.write(CSVFile.getLineStr(commonMFDHeader));
			writer.write('\n');
			Preconditions.checkState(branchSectMFDs[b].length == numSections);
			for (int s=0; s<numSections; s++) {
				float[] mfdVals = branchSectMFDs[b][s];
				int minMagIndex = branchSectMinMagIndexes[b][s];
				if (mfdVals == null || mfdVals.length == 0) {
					// empty MFD
					writer.write(CSVFile.getLineStr(new String[] { s+"", -1+"" }));
					writer.write('\n');
				} else {
					String[] line = new String[2+mfdVals.length];
					int cnt = 0;
					line[cnt++] = s+"";
					line[cnt++] = minMagIndex+"";
					for (int i=0; i<mfdVals.length; i++) {
						if (mfdVals[i] == 0f)
							line[cnt++] = "0";
						else
							line[cnt++] = rateDF.format(mfdVals[i]);
					}
					writer.write(CSVFile.getLineStr(line));
					writer.write('\n');
				}
			}
		}
		
		writer.flush();
	}

	@Override
	public void initFromStream(BufferedInputStream in) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		reader.readLine(); // branch and section count header
		List<String> countsLine = CSVFile.loadLine(reader.readLine());
		Preconditions.checkState(countsLine.size() == 2);
		int numBranches = Integer.parseInt(countsLine.get(0));
		int numSects = Integer.parseInt(countsLine.get(1));
		reader.readLine(); // magnitudes header
		List<String> magStrs = CSVFile.loadLine(reader.readLine());
		
		refMFD = new IncrementalMagFreqDist(
				Double.parseDouble(magStrs.get(0)), Double.parseDouble(magStrs.get(magStrs.size()-1)), magStrs.size());
		branchSectMFDs = new float[numBranches][numSects][];
		branchSectMinMagIndexes = new short[numBranches][numSects];
		weights = new double[numBranches];
		
		for (int b=0; b<numBranches; b++) {
			reader.readLine(); // branch header
			List<String> weightLine = CSVFile.loadLine(reader.readLine());
			Preconditions.checkState(weightLine.size() == 2);
			Preconditions.checkState(b == Integer.parseInt(weightLine.get(0)));
			weights[b] = Double.parseDouble(weightLine.get(1));
			reader.readLine(); // mfds header
			for (int s=0; s<numSects; s++) {
				List<String> mfdLine = CSVFile.loadLine(reader.readLine());
				Preconditions.checkState(s == Integer.parseInt(mfdLine.get(0)));
				Preconditions.checkState(mfdLine.size() >= 2 && mfdLine.size() <= 2+refMFD.size());
				branchSectMinMagIndexes[b][s] = Short.parseShort(mfdLine.get(1));
				branchSectMFDs[b][s] = new float[mfdLine.size()-2];
				for (int i=2; i<mfdLine.size(); i++)
					branchSectMFDs[b][s][i-2] = Float.parseFloat(mfdLine.get(i));
			}
		}
	}
	
	public int getNumBranches() {
		return weights.length;
	}
	
	public double getBranchWeight(int branchIndex) {
		return weights[branchIndex];
	}
	
	public IncrementalMagFreqDist[] getSectionMFDs(int branchIndex) {
		IncrementalMagFreqDist[] ret = new IncrementalMagFreqDist[branchSectMFDs[branchIndex].length];
		for (int sectIndex=0; sectIndex<ret.length; sectIndex++)
			ret[sectIndex] = getSectionMFD(branchIndex, sectIndex);
		return ret;
	}
	
	public IncrementalMagFreqDist getSectionMFD(int branchIndex, int sectIndex) {
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		float[] vals = branchSectMFDs[branchIndex][sectIndex];
		if (vals == null)
			return ret;
		for (int i=0; i<vals.length; i++)
			if (vals[i] > 0f)
				ret.set(i+branchSectMinMagIndexes[branchIndex][sectIndex], vals[i]);
		return ret;
	}
	
	public IncrementalMagFreqDist[] calcIncrementalSectFractiles(Collection<Integer> sectIDs, double... fractiles) {
		double[] sectFracts = new double[branchSectMFDs[0].length];
		for (int sectID : sectIDs)
			sectFracts[sectID] = 1d;
		return calcIncrementalFractiles(sectFracts, fractiles);
	}
	
	public IncrementalMagFreqDist[] calcIncrementalFractiles(double[] sectFracts, double... fractiles) {
		return (IncrementalMagFreqDist[])calcFractiles(sectFracts, fractiles, false);
	}
	
	public EvenlyDiscretizedFunc[] calcCumulativeSectFractiles(Collection<Integer> sectIDs, double... fractiles) {
		double[] sectFracts = new double[branchSectMFDs[0].length];
		for (int sectID : sectIDs)
			sectFracts[sectID] = 1d;
		return calcCumulativeFractiles(sectFracts, fractiles);
	}
	
	public EvenlyDiscretizedFunc[] calcCumulativeFractiles(double[] sectFracts, double... fractiles) {
		return calcFractiles(sectFracts, fractiles, true);
	}
	
	private EvenlyDiscretizedFunc[] calcFractiles(double[] sectFracts, double[] fractiles, boolean cumulative) {
		EvenlyDiscretizedFunc refMFD = cumulative ? this.refMFD.getCumRateDistWithOffset() : this.refMFD;
		
		ArbDiscrEmpiricalDistFunc[] dists = new ArbDiscrEmpiricalDistFunc[refMFD.size()];
		
		for (int i=0; i<dists.length; i++)
			dists[i] = new ArbDiscrEmpiricalDistFunc();
		
		for (int b=0; b<branchSectMFDs.length; b++) {
			IncrementalMagFreqDist branchMFD = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
			for (int s=0; s<branchSectMFDs[b].length; s++) {
				double scalar = 1d;
				if (sectFracts != null) {
					if (sectFracts[s] == 0d)
						continue;
					scalar = sectFracts[s];
				}
				float[] mfdVals = branchSectMFDs[b][s];
				if (sectFracts != null && sectFracts[s] == 0d || mfdVals == null || mfdVals.length == 0)
					continue;
				int minIndex = branchSectMinMagIndexes[b][s];
				Preconditions.checkState(branchMFD.size() >= minIndex+mfdVals.length);
				for (int i=0; i<mfdVals.length; i++)
					if (mfdVals[i] > 0f)
						branchMFD.add(i+minIndex, mfdVals[i]*scalar);
			}
			if (cumulative) {
				EvenlyDiscretizedFunc branchCmlMFD = branchMFD.getCumRateDistWithOffset();
				for (int i=0; i<branchCmlMFD.size(); i++)
					dists[i].set(branchCmlMFD.getY(i), weights[b]);
			} else {
				for (int i=0; i<branchMFD.size(); i++)
					dists[i].set(branchMFD.getY(i), weights[b]);
			}
		}
		
		EvenlyDiscretizedFunc[] ret = cumulative ?
				new EvenlyDiscretizedFunc[fractiles.length] : new IncrementalMagFreqDist[fractiles.length];
		
		for (int f=0; f<ret.length; f++) {
			Preconditions.checkState(fractiles[f] >= 0d && fractiles[f] <= 1d);
			if (cumulative)
				ret[f] = new EvenlyDiscretizedFunc(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
			else
				ret[f] = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
			for (int i=0; i<dists.length; i++)
				ret[f].set(i, dists[i].getInterpolatedFractile(fractiles[f]));
		}
		
		return ret;
	}
	
	public static void main(String[] args) throws IOException {
		File dir = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2022_08_22-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR");
		File resultsFile = new File(dir, "results.zip");
		SolutionLogicTree slt = SolutionLogicTree.load(resultsFile);
		LogicTree<?> tree = slt.getLogicTree();
		File outputFile = new File(dir, "results_NSHM23_v2_CoulombRupSet_branch_averaged_sect_mfds.zip");
		BranchAverageSolutionCreator baCreator = new BranchAverageSolutionCreator(tree.getWeightProvider());
		int count = 0;
		for (LogicTreeBranch<?> branch : tree) {
			System.out.println("Loading solution for branch "+count);
			FaultSystemSolution sol = slt.forBranch(branch, false);
			baCreator.addSolution(sol, branch);
			System.out.println("DONE branch "+count);
			count++;
		}
		FaultSystemSolution ba = baCreator.build();
		ba.write(outputFile);
	}

}
