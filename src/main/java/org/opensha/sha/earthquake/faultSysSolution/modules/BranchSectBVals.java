package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBValuePlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBValuePlot.BValEstimate;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

public class BranchSectBVals implements ArchivableModule {
	
	private double[] weights;
	private int[] parentIDs;
	private boolean parentsSorted = false;
	private float[][] sectBVals;
	private float[][] parentBVals;
	private float[][] sectTargetBVals;
	private float[][] parentTargetBVals;
	
	public static class Builder implements BranchModuleBuilder<FaultSystemSolution, BranchSectBVals> {
		private List<Double> weights;
		private int[] parentIDs;
		private List<float[]> sectBVals;
		private List<float[]> parentBVals;
		private List<float[]> sectTargetBVals;
		private List<float[]> parentTargetBVals;
		
		public synchronized void process(FaultSystemSolution sol, LogicTreeBranch<?> branch, double weight) {
			int numSects = sol.getRupSet().getNumSections();
			if (sectBVals == null) {
				// first time, initialize lists
				sectBVals = new ArrayList<>();
				HashSet<Integer> parentIDs = new HashSet<>();
				for (FaultSection sect : sol.getRupSet().getFaultSectionDataList()) {
					int parentID = sect.getParentSectionId();
					if (parentID < 0) {
						parentIDs = null;
						break;
					}
					parentIDs.add(parentID);
				}
				if (parentIDs != null) {
					parentBVals = new ArrayList<>();
					List<Integer> sorted = new ArrayList<>(parentIDs);
					Collections.sort(sorted);
					this.parentIDs = Ints.toArray(sorted);
				}
				// see if we have targets
				InversionTargetMFDs targetMFDs = sol.getRupSet().getModule(InversionTargetMFDs.class);
				if (targetMFDs != null && targetMFDs.getOnFaultSupraSeisNucleationMFDs() != null) {
					sectTargetBVals = new ArrayList<>();
					if (parentIDs != null)
						parentTargetBVals = new ArrayList<>();
				}
				weights = new ArrayList<>();
			} else {
				// ensure section count is consistent
				Preconditions.checkState(numSects == sectBVals.get(0).length);
			}
			weights.add(weight);
			// calculate them
			sectBVals.add(bValArray(SectBValuePlot.estSectBValues(sol)));
			if (this.parentIDs != null)
				parentBVals.add(bValArray(SectBValuePlot.estParentSectBValues(sol)));
			if (sectTargetBVals != null) {
				InversionTargetMFDs targetMFDs = sol.getRupSet().getModule(InversionTargetMFDs.class);
				if (targetMFDs != null && targetMFDs.getOnFaultSupraSeisNucleationMFDs() != null) {
					List<? extends IncrementalMagFreqDist> supraTargets = targetMFDs.getOnFaultSupraSeisNucleationMFDs();
					sectTargetBVals.add(bValArray(SectBValuePlot.estSectTargetBValues(supraTargets)));
					if (this.parentIDs != null)
						parentTargetBVals.add(bValArray(SectBValuePlot.estParentSectTargetBValues(sol, supraTargets)));
				} else {
					// not all have them, remove
					sectTargetBVals = null;
					parentTargetBVals = null;
				}
			}
		}
		
		private float[] bValArray(BValEstimate[] bVals) {
			float[] ret = new float[bVals.length];
			for (int i=0; i<ret.length; i++)
				ret[i] = (float)bVals[i].b;
			return ret;
		}
		
		private float[] bValArray(Map<Integer, BValEstimate> bVals) {
			float[] ret = new float[parentIDs.length];
			for (int i=0; i<ret.length; i++) {
				BValEstimate bVal = bVals.get(parentIDs[i]);
				Preconditions.checkNotNull(bVal, "no b-value for section %s", parentIDs[i]);
				ret[i] = (float)bVal.b;
			}
			return ret;
		}
		
		public BranchSectBVals build() {
			BranchSectBVals ret = new BranchSectBVals();
			
			int numBranches = weights.size();
			Preconditions.checkState(numBranches > 0);
			ret.weights = Doubles.toArray(weights);
			int numSects = sectBVals.get(0).length;
			
			ret.sectBVals = new float[numBranches][];
			if (parentBVals != null) {
				ret.parentIDs = parentIDs;
				ret.parentsSorted = true;
				ret.parentBVals = new float[numBranches][];
			}
			if (sectTargetBVals != null)
				ret.sectTargetBVals = new float[numBranches][];
			if (parentTargetBVals != null)
				ret.parentTargetBVals = new float[numBranches][];
			for (int b=0; b<numBranches; b++) {
				ret.sectBVals[b] = sectBVals.get(b);
				Preconditions.checkArgument(ret.sectBVals[b].length == numSects);
				if (parentBVals != null)
					ret.parentBVals[b] = parentBVals.get(b);
				if (sectTargetBVals != null)
					ret.sectTargetBVals[b] = sectTargetBVals.get(b);
				if (parentTargetBVals != null)
					ret.parentTargetBVals[b] = parentTargetBVals.get(b);
			}
			
			return ret;
		}
	}

	@Override
	public String getName() {
		return "Branch Section b-values";
	}
	
	private static final String SECT_FILE_NAME = "branch_sect_b_vals.csv";
	private static final String PARENT_FILE_NAME = "branch_parent_b_vals.csv";
	private static final String SECT_TARGET_FILE_NAME = "branch_sect_target_b_vals.csv";
	private static final String PARENT_TARGET_FILE_NAME = "branch_parent_target_b_vals.csv";

	@Override
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		writeBValCSV(FileBackedModule.initOutputStream(output, entryPrefix, SECT_FILE_NAME), sectBVals, false);
		output.closeEntry();
		if (parentBVals != null) {
			writeBValCSV(FileBackedModule.initOutputStream(output, entryPrefix, PARENT_FILE_NAME), parentBVals, true);
			output.closeEntry();
		}
		if (sectTargetBVals != null) {
			writeBValCSV(FileBackedModule.initOutputStream(output, entryPrefix, SECT_TARGET_FILE_NAME), sectTargetBVals, false);
			output.closeEntry();
		}
		if (parentTargetBVals != null) {
			writeBValCSV(FileBackedModule.initOutputStream(output, entryPrefix, PARENT_TARGET_FILE_NAME), parentTargetBVals, true);
			output.closeEntry();
		}
	}
	
	private void writeBValCSV(OutputStream out, float[][] sectBVals, boolean parents) throws IOException {
		CSVWriter csv = new CSVWriter(out, true);
		List<String> header = new ArrayList<>(sectBVals.length+2);
		header.add("Branch Index");
		header.add("Branch Weight");
		if (parents) {
			for (int id : parentIDs)
				header.add(id+"");
		} else {
			for (int s=0; s<sectBVals[0].length; s++)
				header.add(s+"");
		}
		csv.write(header);
		
		Preconditions.checkState(sectBVals.length == weights.length);
		
		for (int b=0; b<weights.length; b++) {
			List<String> line = new ArrayList<>(header.size());
			line.add(b+"");
			line.add(weights[b]+"");
			for (double bVal : sectBVals[b])
				line.add(bDF.format(bVal));
			csv.write(line);
		}
		csv.flush();
	}
	
	private static final DecimalFormat bDF = new DecimalFormat("0.##");

	@Override
	public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		sectBVals = loadBValCSV(CSV_BackedModule.loadFromArchive(input, entryPrefix, SECT_FILE_NAME), false);
		if (FileBackedModule.hasEntry(input, entryPrefix, PARENT_FILE_NAME))
			parentBVals = loadBValCSV(CSV_BackedModule.loadFromArchive(input, entryPrefix, PARENT_FILE_NAME), true);
		if (FileBackedModule.hasEntry(input, entryPrefix, SECT_TARGET_FILE_NAME))
			sectTargetBVals = loadBValCSV(CSV_BackedModule.loadFromArchive(input, entryPrefix, SECT_TARGET_FILE_NAME), false);
		if (FileBackedModule.hasEntry(input, entryPrefix, PARENT_TARGET_FILE_NAME))
			parentTargetBVals = loadBValCSV(CSV_BackedModule.loadFromArchive(input, entryPrefix, PARENT_TARGET_FILE_NAME), true);
	}
	
	private float[][] loadBValCSV(CSVFile<String> csv, boolean parents) {
		int numSects = csv.getLine(0).size()-2;
		if (parents) {
			// parse parent section IDs
			int[] parentIDs = new int[numSects];
			for (int i=0; i<parentIDs.length; i++)
				parentIDs[i] = csv.getInt(0, i+2);
			
			if (this.parentIDs == null) {
				this.parentIDs = parentIDs;
				parentsSorted = true;
				for (int i=1; parentsSorted && i<parentIDs.length; i++)
					parentsSorted = parentIDs[i] > parentIDs[i-1];
			} else {
				Preconditions.checkState(parentIDs.length == this.parentIDs.length);
				for (int i=0; i<parentIDs.length; i++)
					Preconditions.checkState(parentIDs[i] == this.parentIDs[i]);
			}
		}
		float[][] ret = new float[csv.getNumRows()-1][numSects];
		double[] weights = new double[ret.length];
		for (int b=0; b<ret.length; b++) {
			int row = b+1;
			weights[b] = csv.getDouble(row, 1);
			for (int s=0; s<numSects; s++)
				ret[b][s] = csv.getFloat(row, s+2);
		}
		if (this.weights == null) {
			this.weights = weights;
		} else {
			Preconditions.checkState(weights.length == this.weights.length);
			for (int i=0; i<weights.length; i++)
				Preconditions.checkState((float)weights[i] == (float)this.weights[i]);
		}
		return ret;
	}
	
	public int getNumBranches() {
		return weights.length;
	}
	
	public boolean hasParentBVals() {
		return parentBVals != null;
	}
	
	public boolean hasTargetBVals() {
		return sectTargetBVals != null;
	}
	
	public ArbDiscrEmpiricalDistFunc getSectBValDist(int sectIndex) {
		return buildBValDist(sectBVals, sectIndex);
	}
	
	public ArbDiscrEmpiricalDistFunc getParentBValDist(int parentID) {
		return buildBValDist(parentBVals, parentIndex(parentID));
	}
	
	public ArbDiscrEmpiricalDistFunc getSectTargetBValDist(int sectIndex) {
		return buildBValDist(sectTargetBVals, sectIndex);
	}
	
	public ArbDiscrEmpiricalDistFunc getParentTargetBValDist(int parentID) {
		return buildBValDist(parentTargetBVals, parentIndex(parentID));
	}
	
	private int parentIndex(int parentID) {
		if (parentsSorted) {
			int index = Arrays.binarySearch(parentIDs, parentID);
			Preconditions.checkState(index >= 0, "Parent not found: %s", parentID);
			return index;
		}
		for (int i=0; i<parentIDs.length; i++) {
			if (parentIDs[i] == parentID)
				return i;
		}
		throw new IllegalStateException("Parent not found: "+parentID);
	}
	
	private ArbDiscrEmpiricalDistFunc buildBValDist(float[][] bVals, int sectIndex) {
		ArbDiscrEmpiricalDistFunc dist = new ArbDiscrEmpiricalDistFunc();
		
		for (int i=0; i<bVals.length; i++)
			dist.set(bVals[i][sectIndex], weights[i]);
		
		return dist;
	}
	
	public double getSectMeanBVal(int sectIndex) {
		return calcMeanBVal(sectBVals, sectIndex);
	}
	
	public double getParentMeanBVal(int parentID) {
		return calcMeanBVal(parentBVals, parentIndex(parentID));
	}
	
	public double getSectTargetMeanBVal(int sectIndex) {
		return calcMeanBVal(sectTargetBVals, sectIndex);
	}
	
	public double getParentTargetMeanBVal(int parentID) {
		return calcMeanBVal(parentTargetBVals, parentIndex(parentID));
	}
	
	private double calcMeanBVal(float[][] bVals, int sectIndex) {
		double ret = 0d;
		double sumWeight = 0d;
		
		for (int i=0; i<bVals.length; i++) {
			ret += bVals[i][sectIndex]*weights[i];
			sumWeight += weights[i];
		}
		
		return ret/sumWeight;
	}
	
	public static void main(String[] args) throws IOException {
		File dir = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2022_09_28-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR");
		File resultsFile = new File(dir, "results.zip");
		SolutionLogicTree slt = SolutionLogicTree.load(resultsFile);
		LogicTree<?> tree = slt.getLogicTree();
		File inputBA = new File(dir, "results_NSHM23_v2_CoulombRupSet_branch_averaged.zip");
		File outputFile = new File(dir, "results_NSHM23_v2_CoulombRupSet_branch_averaged_sect_b_vals.zip");
		Builder builder = new Builder();
		int count = 0;
		CompletableFuture<Void> processingLoadedFuture = null;
		for (LogicTreeBranch<?> branch : tree) {
			Stopwatch watch = Stopwatch.createStarted();
			System.out.println("Loading solution for branch "+count);
			FaultSystemSolution sol = slt.forBranch(branch, false);
			if (processingLoadedFuture != null) {
				try {
					processingLoadedFuture.get();
				} catch (InterruptedException | ExecutionException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
			processingLoadedFuture = CompletableFuture.runAsync(new Runnable() {
				
				@Override
				public void run() {
					builder.process(sol, branch, tree.getBranchWeight(branch));
				}
			});
			watch.stop();
			double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
			System.out.println("DONE branch "+count+" in "+bDF.format(secs)+" s");
			count++;
		}
		try {
			processingLoadedFuture.get();
		} catch (InterruptedException | ExecutionException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		BranchSectBVals bVals = builder.build();
		FaultSystemSolution ba = FaultSystemSolution.load(inputBA);
		ba.addModule(bVals);
		ba.write(outputFile);
	}

}
