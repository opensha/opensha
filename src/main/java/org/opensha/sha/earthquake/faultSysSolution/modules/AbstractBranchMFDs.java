package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.modules.ModuleHelper;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.primitives.Doubles;

@ModuleHelper
abstract class AbstractBranchMFDs implements FileBackedModule {
	
	IncrementalMagFreqDist refMFD;
	float[][][] branchSectMFDs;
	short[][] branchSectMinMagIndexes;
	double[] weights;
	
	BiMap<Integer, Integer> parentIDtoIndexMap;

	private static final DecimalFormat rateDF = new DecimalFormat("0.###E0");
	
	static abstract class Builder<E extends AbstractBranchMFDs> implements BranchModuleBuilder<FaultSystemSolution, E> {
		
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
		
		protected abstract SingleBranchMFDs getCachedMFDs(FaultSystemSolution sol);
		
		protected abstract void cacheMFDs(FaultSystemSolution sol, SingleBranchMFDs branchMFDs);
		
		protected abstract IncrementalMagFreqDist calcBranchSectMFD(
				FaultSystemSolution sol, int sectID, EvenlyDiscretizedFunc refMFD);
		
		private boolean parents;
		private List<Integer> parentIDs;
		
		public synchronized void process(FaultSystemSolution sol, LogicTreeBranch<?> branch, double weight) {
			int numSects;
			if (branchSectMFDs == null) {
				// first time, initialize lists
				branchSectMFDs = new ArrayList<>();
				branchSectMinMagIndexes = new ArrayList<>();
				weights = new ArrayList<>();
				
				parents = init().isParentSections();
				if (parents) {
					parentIDs = new ArrayList<>();
					HashSet<Integer> prevParents = new HashSet<>();
					for (FaultSection sect : sol.getRupSet().getFaultSectionDataList()) {
						int parentID = sect.getParentSectionId();
						Preconditions.checkState(parentID >= 0);
						if (!prevParents.contains(parentID)) {
							parentIDs.add(parentID);
							prevParents.add(parentID);
						}
					}
					Preconditions.checkState(!parentIDs.isEmpty());
					numSects = parentIDs.size();
				} else {
					numSects = sol.getRupSet().getNumSections();
				}
			} else {
				if (parents) {
					numSects = branchSectMFDs.get(0).length;
				} else {
					// ensure section count is consistent
					numSects = sol.getRupSet().getNumSections();
					Preconditions.checkState(numSects == branchSectMFDs.get(0).length);
				}
			}
			// see if we've already built these distributions and can reuse them
			// that happens for logic-tree branch average creation
			
			SingleBranchMFDs branchMFDs = getCachedMFDs(sol);
			
			if (branchMFDs == null) {
				// need to build them
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
					int sectID = parents ? parentIDs.get(sectIndex) : sectIndex;
					IncrementalMagFreqDist mfd = calcBranchSectMFD(sol, sectID, refMFD);
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
				branchMFDs = new SingleBranchMFDs(minMagIndexes, sectMFDs, branchMinIndex, branchMaxIndex);
				
				// cache them
				cacheMFDs(sol, branchMFDs);
			} else {
				// process them for min/max mag
				minMagIndex = Integer.min(minMagIndex, branchMFDs.branchMinIndex);
				maxMagIndex = Integer.max(maxMagIndex, branchMFDs.branchMaxIndex);
			}
			
			branchSectMFDs.add(branchMFDs.sectMFDs);
			branchSectMinMagIndexes.add(branchMFDs.sectMinMagIndexes);
			weights.add(weight);
		}
		
		protected abstract E init();
		
		public E build() {
			E ret = init();
			
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
			
			if (parents) {
				ret.parentIDtoIndexMap = HashBiMap.create();
				for (int i=0; i<parentIDs.size(); i++)
					ret.parentIDtoIndexMap.put(parentIDs.get(i), i);
			}
			
			return ret;
		}
	}
	
	static class SingleBranchMFDs {
		
		private short[] sectMinMagIndexes;
		private float[][] sectMFDs;
		private int branchMinIndex;
		private int branchMaxIndex;

		public SingleBranchMFDs(short[] sectMinMagIndexes, float[][] sectMFDs, int branchMinIndex,
				int branchMaxIndex) {
			super();
			this.sectMinMagIndexes = sectMinMagIndexes;
			this.sectMFDs = sectMFDs;
			this.branchMinIndex = branchMinIndex;
			this.branchMaxIndex = branchMaxIndex;
		}

		public SingleBranchMFDs(SingleBranchMFDs branchMFDs) {
			super();
			this.sectMinMagIndexes = branchMFDs.sectMinMagIndexes;
			this.sectMFDs = branchMFDs.sectMFDs;
			this.branchMinIndex = branchMFDs.branchMinIndex;
			this.branchMaxIndex = branchMFDs.branchMaxIndex;
		}
		
	}

	@Override
	public void writeToStream(OutputStream out) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(out);
		
		int numBranches = branchSectMFDs.length;
		int numSections = branchSectMFDs[0].length;
		
		// write counts
		CSVFile.writeLine(writer, new String[] {"Num Branches", "Num Sections"});
		CSVFile.writeLine(writer, new String[] {numBranches+"", numSections+""});
		
		// write x values
		CSVFile.writeLine(writer, new String[] {"Magnitudes"});
		List<String> xValsHeader = new ArrayList<>(refMFD.size());
		for (int i=0; i<refMFD.size(); i++)
			xValsHeader.add((float)refMFD.getX(i)+"");
		CSVFile.writeLine(writer, xValsHeader);
		
		String[] commonBranchHeader = {"Branch Index", "Branch Weight"};
		String indexHeader = isParentSections() ? "Parent Section ID" : "Section Index";
		String[] commonMFDHeader = {indexHeader, "Min Mag Index", "Rate1", "Rate2", "...", "RateN"};
		
		for (int b=0; b<numBranches; b++) {
			CSVFile.writeLine(writer, commonBranchHeader);
			CSVFile.writeLine(writer, new String[] { b+"", weights[b]+""});
			CSVFile.writeLine(writer, commonMFDHeader);
			Preconditions.checkState(branchSectMFDs[b].length == numSections);
			for (int s=0; s<numSections; s++) {
				float[] mfdVals = branchSectMFDs[b][s];
				int minMagIndex = branchSectMinMagIndexes[b][s];
				int sectID = isParentSections() ? parentIDtoIndexMap.inverse().get(s) : s;
				if (mfdVals == null || mfdVals.length == 0) {
					// empty MFD
					CSVFile.writeLine(writer, new String[] { sectID+"", -1+"" });
				} else {
					String[] line = new String[2+mfdVals.length];
					int cnt = 0;
					line[cnt++] = sectID+"";
					line[cnt++] = minMagIndex+"";
					for (int i=0; i<mfdVals.length; i++) {
						if (mfdVals[i] == 0f)
							line[cnt++] = "0";
						else
							line[cnt++] = rateDF.format(mfdVals[i]);
					}
					CSVFile.writeLine(writer, line);
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
		
		boolean parents = isParentSections();
		if (parents)
			parentIDtoIndexMap = HashBiMap.create(numSects);
		
		for (int b=0; b<numBranches; b++) {
			reader.readLine(); // branch header
			List<String> weightLine = CSVFile.loadLine(reader.readLine());
			Preconditions.checkState(weightLine.size() == 2);
			Preconditions.checkState(b == Integer.parseInt(weightLine.get(0)));
			weights[b] = Double.parseDouble(weightLine.get(1));
			reader.readLine(); // mfds header
			for (int s=0; s<numSects; s++) {
				List<String> mfdLine = CSVFile.loadLine(reader.readLine());
				if (parents) {
					int parentID = Integer.parseInt(mfdLine.get(0));
					if (parentIDtoIndexMap.containsKey(parentID))
						Preconditions.checkState(s == parentIDtoIndexMap.get(parentID));
					else
						parentIDtoIndexMap.put(parentID, s);
				} else {
					Preconditions.checkState(s == Integer.parseInt(mfdLine.get(0)));
				}
				Preconditions.checkState(mfdLine.size() >= 2 && mfdLine.size() <= 2+refMFD.size());
				branchSectMinMagIndexes[b][s] = Short.parseShort(mfdLine.get(1));
				branchSectMFDs[b][s] = new float[mfdLine.size()-2];
				for (int i=2; i<mfdLine.size(); i++)
					branchSectMFDs[b][s][i-2] = Float.parseFloat(mfdLine.get(i));
			}
		}
	}
	
	public abstract boolean isParentSections();
	
	public int getNumBranches() {
		return weights.length;
	}
	
	public double getBranchWeight(int branchIndex) {
		return weights[branchIndex];
	}
	
	public IncrementalMagFreqDist getSectionMFD(int branchIndex, int sectIndex) {
		if (isParentSections())
			// convert parent ID to section index
			sectIndex = parentIDtoIndexMap.get(sectIndex);
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		float[] vals = branchSectMFDs[branchIndex][sectIndex];
		if (vals == null)
			return ret;
		for (int i=0; i<vals.length; i++)
			if (vals[i] > 0f)
				ret.set(i+branchSectMinMagIndexes[branchIndex][sectIndex], vals[i]);
		return ret;
	}

}
