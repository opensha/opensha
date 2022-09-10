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
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class BranchSectNuclMFDs implements FileBackedModule {
	
	private IncrementalMagFreqDist[][] branchSectMFDs;
	private double[] weights;
	
	public static class Builder {
		
		private EvenlyDiscretizedFunc refMFD;
		private List<IncrementalMagFreqDist[]> branchSectMFDs;
		private List<Double> weights;
		
		private int minMagIndex = Integer.MAX_VALUE;
		private int maxMagIndex = 0;
		
		public synchronized void process(FaultSystemSolution sol, double weight) {
			int numSects = sol.getRupSet().getNumSections();
			if (branchSectMFDs == null) {
				// first time, build ref MFD and initialize lists
				// this will start at a low magnitude that should be well below the supra-seis min, pad the upper end
				refMFD = SupraSeisBValInversionTargetMFDs.buildRefXValues(sol.getRupSet().getMaxMag()+2d);
				branchSectMFDs = new ArrayList<>();
				weights = new ArrayList<>();
			} else {
				Preconditions.checkState(numSects == branchSectMFDs.get(0).length);
			}
			IncrementalMagFreqDist[] sectMFDs = new IncrementalMagFreqDist[numSects];
			for (int sectIndex=0; sectIndex<numSects; sectIndex++) {
				sectMFDs[sectIndex] = sol.calcNucleationMFD_forSect(sectIndex, refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
				for (int i=0; i<refMFD.size(); i++) {
					if (sectMFDs[sectIndex].getY(i) > 0d) {
						minMagIndex = Integer.min(minMagIndex, i);
						maxMagIndex = Integer.max(maxMagIndex, i);
					}
				}
			}
			
			branchSectMFDs.add(sectMFDs);
			weights.add(weight);
		}
		
		public BranchSectNuclMFDs build() {
			BranchSectNuclMFDs ret = new BranchSectNuclMFDs();
			
			int numBranches = weights.size();
			Preconditions.checkState(numBranches > 0);
			ret.weights = Doubles.toArray(weights);
			int numSects = branchSectMFDs.get(0).length;
			
			// trim the MFDs
			ret.branchSectMFDs = new IncrementalMagFreqDist[numBranches][numSects];
			EvenlyDiscretizedFunc trimmedRefMFD = new EvenlyDiscretizedFunc(
					refMFD.getX(minMagIndex), 1+maxMagIndex-minMagIndex, refMFD.getDelta());
			for (int b=0; b<numBranches; b++) {
				for (int s=0; s<numSects; s++) {
					ret.branchSectMFDs[b][s] = new IncrementalMagFreqDist(
							trimmedRefMFD.getMinX(), trimmedRefMFD.size(), trimmedRefMFD.getDelta());
					IncrementalMagFreqDist sectMFD = branchSectMFDs.get(b)[s];
					for (int i=0; i<trimmedRefMFD.size(); i++)
						ret.branchSectMFDs[b][s].set(i, sectMFD.getY(i+minMagIndex));
				}
			}
			
			return ret;
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
		IncrementalMagFreqDist refMFD = branchSectMFDs[0][0];
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
				IncrementalMagFreqDist mfd = branchSectMFDs[b][s];
				int minMagIndex = -1;
				int maxMagIndex = 0;
				Preconditions.checkState(mfd.size() == refMFD.size());
				Preconditions.checkState((float)mfd.getDelta() == (float)refMFD.getDelta());
				Preconditions.checkState((float)mfd.getMinX() == (float)refMFD.getMinX());
				for (int i=0; i<mfd.size(); i++) {
					if (mfd.getY(i) > 0) {
						if (minMagIndex < 0)
							minMagIndex = i;
						maxMagIndex = i;
					}
				}
				if (minMagIndex < 0) {
					// empty MFD
					writer.write(CSVFile.getLineStr(new String[] { s+"", -1+"" }));
					writer.write('\n');
				} else {
					String[] line = new String[3+maxMagIndex-minMagIndex];
					int cnt = 0;
					line[cnt++] = s+"";
					line[cnt++] = minMagIndex+"";
					for (int i=minMagIndex; i<=maxMagIndex; i++) {
						double y = mfd.getY(i);
						if (y == 0d)
							line[cnt++] = "0";
						else
							line[cnt++] = rateDF.format(mfd.getY(i));
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
		EvenlyDiscretizedFunc refMFD = new EvenlyDiscretizedFunc(
				Double.parseDouble(magStrs.get(0)), Double.parseDouble(magStrs.get(magStrs.size()-1)), magStrs.size());
		
		branchSectMFDs = new IncrementalMagFreqDist[numBranches][numSects];
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
				int minMagIndex = Integer.parseInt(mfdLine.get(1));
				branchSectMFDs[b][s] = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
				for (int i=2; i<mfdLine.size(); i++) {
					double y = Double.parseDouble(mfdLine.get(i));
					branchSectMFDs[b][s].set(i+minMagIndex-2, y);
				}
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
		return branchSectMFDs[branchIndex];
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
		EvenlyDiscretizedFunc refMFD = cumulative ? branchSectMFDs[0][0].getCumRateDistWithOffset() : branchSectMFDs[0][0];
		
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
				if (sectFracts != null && sectFracts[s] == 0d)
					continue;
				IncrementalMagFreqDist mfd = branchSectMFDs[b][s];
				Preconditions.checkState(branchMFD.size() == mfd.size());
				for (int i=0; i<mfd.size(); i++) {
					double y = mfd.getY(i)*scalar;
					branchMFD.add(i, y);
				}
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
