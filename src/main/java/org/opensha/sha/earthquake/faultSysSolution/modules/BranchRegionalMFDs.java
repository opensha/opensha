package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVReader;
import org.opensha.commons.data.CSVReader.Row;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class BranchRegionalMFDs implements SubModule<ModuleContainer<?>>, ArchivableModule {
	
	private FaultSystemSolution sol;
	
	public enum MFDType {
		SUPRA_ONLY,
		GRID_ONLY,
		SUM
	}
	
	private IncrementalMagFreqDist[] supraTotalBranchMFDs;
	private List<IncrementalMagFreqDist[]> supraRegionalBranchMFDs;
	
	private IncrementalMagFreqDist[] gridTotalBranchMFDs;
	private List<IncrementalMagFreqDist[]> gridRegionalBranchMFDs;
	
	private IncrementalMagFreqDist[] sumTotalBranchMFDs;
	private List<IncrementalMagFreqDist[]> sumRegionalBranchMFDs;
	
	private double[] weights;
	
	@SuppressWarnings("unused") // for deserialization
	private BranchRegionalMFDs() {};
	
	public static class Builder implements BranchModuleBuilder<FaultSystemSolution, BranchRegionalMFDs> {
		
		// oversized reference MFD that starts at M=0 and goes to M=12
		// will be reduced to the actual magnitude range when the final module is built
		private static final EvenlyDiscretizedFunc refMFD = new EvenlyDiscretizedFunc(
				0.05, 120, 0.1);

		private List<Double> weights;
		
		private List<IncrementalMagFreqDist> supraTotalMFDs;
		private List<List<IncrementalMagFreqDist>> supraRegionalMFDs;
		
		private List<IncrementalMagFreqDist> gridTotalMFDs;
		private List<List<IncrementalMagFreqDist>> gridRegionalMFDs;
		
		private List<IncrementalMagFreqDist> sumTotalMFDs;
		private List<List<IncrementalMagFreqDist>> sumRegionalMFDs;

		private int minMagIndex = Integer.MAX_VALUE;
		private int minSupraMagIndex = Integer.MAX_VALUE;
		private int maxMagIndex = 0;

		public synchronized void process(FaultSystemSolution sol, LogicTreeBranch<?> branch, double weight) {
			process(sol, sol.getGridSourceProvider(), branch, weight);
		}

		public synchronized void process(FaultSystemSolution sol, GridSourceProvider gridProv,
				LogicTreeBranch<?> branch, double weight) {
			RegionsOfInterest roi = sol.getRupSet().getModule(RegionsOfInterest.class);
			processInitCheck(roi == null ? 0 : roi.getRegions().size(), gridProv != null);
			
			int numROI = this.supraRegionalMFDs == null ? 0 : this.supraRegionalMFDs.size();
			
			weights.add(weight);
			
			for (int r=-1; r<numROI; r++) {
				Region region = r < 0 ? null : roi.getRegions().get(r);
				
				TectonicRegionType trt = r < 0 || roi.getTRTs() == null ? null : roi.getTRTs().get(r);
				
				IncrementalMagFreqDist supraMFD = sol.calcNucleationMFD_forRegion(
						region, refMFD.getMinX(), refMFD.getMaxX(), refMFD.getDelta(), false, trt);
				if (r < 0)
					addProcess(supraTotalMFDs, supraMFD, true);
				else
					addProcess(supraRegionalMFDs.get(r), supraMFD, true);
				
				if (gridTotalMFDs != null) {
					IncrementalMagFreqDist gridMFD = calcGridMFD(gridProv, region, trt);
					IncrementalMagFreqDist sumMFD = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
					
					for (int i=0; i<sumMFD.size(); i++)
						sumMFD.set(i, supraMFD.getY(i)+gridMFD.getY(i));
					
					if (r < 0) {
						addProcess(gridTotalMFDs, gridMFD);
						addProcess(sumTotalMFDs, sumMFD);
					} else {
						addProcess(gridRegionalMFDs.get(r), gridMFD);
						addProcess(sumRegionalMFDs.get(r), sumMFD);
					}
				}
			}
		}
		
		private void processInitCheck(int numROI, boolean hasGridded) {
			if (weights == null) {
				weights = new ArrayList<>();
				
				supraTotalMFDs = new ArrayList<>();
				if (numROI > 0) {
					supraRegionalMFDs = new ArrayList<>();
					for (int r=0; r<numROI; r++)
						supraRegionalMFDs.add(new ArrayList<>());
				}
				
				if (hasGridded) {
					gridTotalMFDs = new ArrayList<>();
					if (numROI > 0) {
						gridRegionalMFDs = new ArrayList<>();
						for (int r=0; r<numROI; r++)
							gridRegionalMFDs.add(new ArrayList<>());
					}
					
					sumTotalMFDs = new ArrayList<>();
					if (numROI > 0) {
						sumRegionalMFDs = new ArrayList<>();
						for (int r=0; r<numROI; r++)
							sumRegionalMFDs.add(new ArrayList<>());
					}
				}
			} else {
				if ((numROI == 0 && supraRegionalMFDs != null) ||
						(supraRegionalMFDs != null && supraRegionalMFDs.size() != numROI)) {
					// we previously had ROI but not all do, or sizes differ
					supraRegionalMFDs = null;
					gridRegionalMFDs = null;
					sumRegionalMFDs = null;
				}
				if (!hasGridded && gridTotalMFDs != null) {
					// we previously had a grid prov, but not all do
					gridTotalMFDs = null;
					gridRegionalMFDs = null;
					sumTotalMFDs = null;
					sumRegionalMFDs = null;
				}
			}
		}
		
		public int getNumBranches() {
			return weights == null ? 0 : weights.size();
		}

		public synchronized void process(BranchRegionalMFDs mfds) {
			int numROI = mfds.supraRegionalBranchMFDs == null ? 0 : mfds.supraRegionalBranchMFDs.size();
			processInitCheck(numROI, mfds.hasGridded());
			
			// these will have already been trimmed, need to instead use the max minimum magnitude
			int prevMinMagIndex = minMagIndex;
			// reset for processing just this one
			minMagIndex = Integer.MAX_VALUE;
			minSupraMagIndex = Integer.MAX_VALUE;
			
			for (int b=0; b<mfds.weights.length; b++) {
				weights.add(mfds.weights[b]);
				
				addProcess(supraTotalMFDs, unTrim(mfds.supraTotalBranchMFDs[b]), true);
				if (supraRegionalMFDs != null)
					for (int r=0; r<supraRegionalMFDs.size(); r++)
						addProcess(supraRegionalMFDs.get(r), unTrim(mfds.supraRegionalBranchMFDs.get(r)[b]), true);
				
				if (gridTotalMFDs != null) {
					addProcess(gridTotalMFDs, unTrim(mfds.gridTotalBranchMFDs[b]));
					if (gridRegionalMFDs != null)
						for (int r=0; r<gridRegionalMFDs.size(); r++)
							addProcess(gridRegionalMFDs.get(r), unTrim(mfds.gridRegionalBranchMFDs.get(r)[b]));
					
					addProcess(sumTotalMFDs, unTrim(mfds.sumTotalBranchMFDs[b]));
					if (sumRegionalMFDs != null)
						for (int r=0; r<sumRegionalMFDs.size(); r++)
							addProcess(sumRegionalMFDs.get(r), unTrim(mfds.sumRegionalBranchMFDs.get(r)[b]));
				}
			}
			
			Preconditions.checkState(minMagIndex < Integer.MAX_VALUE);
			if (prevMinMagIndex < Integer.MAX_VALUE) {
				// set to the max min
				minMagIndex = Integer.max(prevMinMagIndex, minMagIndex);
				minSupraMagIndex = minMagIndex;
			}
		}
		
		private IncrementalMagFreqDist calcGridMFD(GridSourceProvider prov, Region region, TectonicRegionType trt) {
			SummedMagFreqDist gridMFD = new SummedMagFreqDist(refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
			for (int i=0; i<prov.getNumLocations(); i++) {
				if (region != null && !region.contains(prov.getLocation(i)))
					continue;
				IncrementalMagFreqDist nodeMFD = prov.getMFD(trt, i);
				if (nodeMFD == null)
					continue;
				gridMFD.addIncrementalMagFreqDist(nodeMFD);
			}
			return gridMFD;
		}
		
		private void addProcess(List<IncrementalMagFreqDist> list, IncrementalMagFreqDist mfd) {
			addProcess(list, mfd, false);
		}
		
		private void addProcess(List<IncrementalMagFreqDist> list, IncrementalMagFreqDist mfd, boolean supra) {
			list.add(mfd);
			for (int i=0; i<mfd.size(); i++) {
				if (mfd.getY(i) > 0) {
					minMagIndex = Integer.min(minMagIndex, i);
					if (supra)
						minSupraMagIndex = Integer.min(minSupraMagIndex, i);
					maxMagIndex = Integer.max(maxMagIndex, i);
				}
			}
		}
		
		public BranchRegionalMFDs build() {
			BranchRegionalMFDs ret = new BranchRegionalMFDs();
			
			ret.weights = Doubles.toArray(weights);
			
			System.out.println("Building branch regional MFDs with "+supraTotalMFDs.size()+" solutions");
			if (supraRegionalMFDs == null)
				System.out.println("\tno ROI");
			else
				System.out.println("\t"+supraRegionalMFDs.size()+" regions");
			
			if (minMagIndex < minSupraMagIndex) {
				// reset lower magnitude to something more sensible, don't need to go down to zero
				// set this to the lesser of the floor of the minimum supra magnitude and M5
				double minSupraMag = refMFD.getX(minSupraMagIndex);
				minMagIndex = Integer.max(minMagIndex, refMFD.getClosestXIndex(
						Math.min(5d, Math.floor(minSupraMag))+0.01));
				System.out.println("\tWill store down to M="+(float)refMFD.getX(minMagIndex));
			}
			
			ret.supraTotalBranchMFDs = mfdListToTrimmedArray(supraTotalMFDs);
			ret.supraRegionalBranchMFDs = mfdRegListToTrimmedArray(supraRegionalMFDs);
			
			if (gridTotalMFDs != null)
				System.out.println("\thave gridded MFDs");
			ret.gridTotalBranchMFDs = mfdListToTrimmedArray(gridTotalMFDs);
			ret.gridRegionalBranchMFDs = mfdRegListToTrimmedArray(gridRegionalMFDs);
			
			ret.sumTotalBranchMFDs = mfdListToTrimmedArray(sumTotalMFDs);
			ret.sumRegionalBranchMFDs = mfdRegListToTrimmedArray(sumRegionalMFDs);
			
			return ret;
		}
		
		private IncrementalMagFreqDist[] mfdListToTrimmedArray(List<IncrementalMagFreqDist> list) {
			if (list == null)
				return null;
			IncrementalMagFreqDist[] ret = new IncrementalMagFreqDist[list.size()];
			for (int i=0; i<ret.length; i++)
				ret[i] = trimToMinMax(list.get(i));
			return ret;
		}
		
		private List<IncrementalMagFreqDist[]> mfdRegListToTrimmedArray(List<List<IncrementalMagFreqDist>> list) {
			if (list == null)
				return null;
			List<IncrementalMagFreqDist[]> ret = new ArrayList<>();
			for (int r=0; r<list.size(); r++)
				ret.add(mfdListToTrimmedArray(list.get(r)));
			return ret;
		}
		
		private IncrementalMagFreqDist trimToMinMax(IncrementalMagFreqDist mfd) {
			IncrementalMagFreqDist trimmed = new IncrementalMagFreqDist(
					refMFD.getX(minMagIndex), 1+maxMagIndex-minMagIndex, refMFD.getDelta());
			for (int i=minMagIndex; i<=maxMagIndex; i++)
				trimmed.set(i-minMagIndex, mfd.getY(i));
			return trimmed;
		}
		
		private IncrementalMagFreqDist unTrim(IncrementalMagFreqDist mfd) {
			IncrementalMagFreqDist ret = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
			Preconditions.checkState((float)ret.getDelta() == (float)mfd.getDelta());
			int offset = ret.getClosestXIndex(mfd.getX(0));
			for (int i=0; i<mfd.size(); i++)
				ret.set(i+offset, mfd.getY(i));
			return ret;
		}
	}

	@Override
	public String getName() {
		return "Branch Regional MFDs";
	}

	@Override
	public void setParent(ModuleContainer<?> parent) throws IllegalStateException {
		Preconditions.checkNotNull(parent);
		if (parent instanceof FaultSystemSolution) {
			// it's a solution
			FaultSystemSolution sol = (FaultSystemSolution)parent;
			RegionsOfInterest roi = sol.getRupSet().getModule(RegionsOfInterest.class);
			if (this.supraRegionalBranchMFDs != null) {
				Preconditions.checkState(roi != null, "New solution doesn't have regions of interest");
				Preconditions.checkState(this.supraRegionalBranchMFDs.size() == roi.getRegions().size(),
						"Solution has different region count");
			}
			this.sol = sol;
		} else {
			// likely just an empty archive for standalone storage, do nothing
			this.sol = null;
		}
	}

	@Override
	public FaultSystemSolution getParent() {
		return sol;
	}

	@Override
	public BranchRegionalMFDs copy(ModuleContainer<?> newParent) throws IllegalStateException {
		BranchRegionalMFDs ret = new BranchRegionalMFDs();
		ret.gridTotalBranchMFDs = gridTotalBranchMFDs;
		ret.gridRegionalBranchMFDs = gridRegionalBranchMFDs;
		ret.supraTotalBranchMFDs = supraTotalBranchMFDs;
		ret.supraRegionalBranchMFDs = supraRegionalBranchMFDs;
		ret.sumTotalBranchMFDs = sumTotalBranchMFDs;
		ret.sumRegionalBranchMFDs = sumRegionalBranchMFDs;
		ret.weights = weights;
		ret.setParent(newParent);
		return ret;
	}
	
	public int getNumRegions() {
		return supraRegionalBranchMFDs.size();
	}
	
	public double[] getBranchWeights() {
		return weights;
	}
	
	public IncrementalMagFreqDist[] getSupraTotalBranchMFDs() {
		return supraTotalBranchMFDs;
	}
	
	public IncrementalMagFreqDist[] getSupraRegionalBranchMFDs(int regionIndex) {
		return supraRegionalBranchMFDs.get(regionIndex);
	}
	
	public IncrementalMagFreqDist[] getGriddedTotalBranchMFDs() {
		return gridTotalBranchMFDs;
	}
	
	public IncrementalMagFreqDist[] getGriddedRegionalBranchMFDs(int regionIndex) {
		return gridRegionalBranchMFDs.get(regionIndex);
	}
	
	public IncrementalMagFreqDist[] getSumTotalBranchMFDs() {
		return sumTotalBranchMFDs;
	}
	
	public IncrementalMagFreqDist[] getSumRegionalBranchMFDs(int regionIndex) {
		return sumRegionalBranchMFDs.get(regionIndex);
	}
	
	public boolean hasMFDs(MFDType type) {
		switch (type) {
		case SUPRA_ONLY:
			return supraTotalBranchMFDs != null;
		case GRID_ONLY:
			return gridTotalBranchMFDs != null;
		case SUM:
			return sumTotalBranchMFDs != null;

		default:
			return false;
		}
	}
	
	public IncrementalMagFreqDist[] getTotalBranchMFDs(MFDType type) {
		switch (type) {
		case SUPRA_ONLY:
			return supraTotalBranchMFDs;
		case GRID_ONLY:
			return gridTotalBranchMFDs;
		case SUM:
			return sumTotalBranchMFDs;

		default:
			throw new IllegalStateException();
		}
	}
	
	public boolean hasRegionalBranchMFDs(MFDType type) {
		switch (type) {
		case SUPRA_ONLY:
			return supraRegionalBranchMFDs != null;
		case GRID_ONLY:
			return gridRegionalBranchMFDs != null;
		case SUM:
			return sumRegionalBranchMFDs != null;

		default:
			return false;
		}
	}
	
	public IncrementalMagFreqDist[] getRegionalBranchMFDs(MFDType type, int regionIndex) {
		List<IncrementalMagFreqDist[]> list;
		switch (type) {
		case SUPRA_ONLY:
			list = supraRegionalBranchMFDs;
			break;
		case GRID_ONLY:
			list = gridRegionalBranchMFDs;
			break;
		case SUM:
			list = sumRegionalBranchMFDs;
			break;

		default:
			throw new IllegalStateException();
		}
		Preconditions.checkNotNull(list, "No regional MFDs available for type %s (regIndex = %s)", type, regionIndex);
		Preconditions.checkState(list.size() > regionIndex, "Bad regIndex=%s with size %s for type %s",
				regionIndex, list.size(), type);
		return list.get(regionIndex);
	}
	
	public boolean hasGridded() {
		return gridTotalBranchMFDs != null;
	}
	
	public boolean hasRegionalMFDs() {
		return supraRegionalBranchMFDs != null;
	}
	
	private static final String SUPRA_FILE_NAME = "branch_regional_supra_nucl_mfds.csv";
	private static final String GRID_FILE_NAME = "branch_regional_grid_nucl_mfds.csv";
	private static final String SUM_FILE_NAME = "branch_regional_sum_nucl_mfds.csv";
	private static final String TOTAL_REG_FLAG = "total";

	@Override
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		Preconditions.checkNotNull(supraTotalBranchMFDs);
		writeCSV(FileBackedModule.initOutputStream(output, entryPrefix, SUPRA_FILE_NAME), supraTotalBranchMFDs, supraRegionalBranchMFDs);
		output.closeEntry();
		
		if (hasGridded()) {
			Preconditions.checkNotNull(gridTotalBranchMFDs);
			writeCSV(FileBackedModule.initOutputStream(output, entryPrefix, GRID_FILE_NAME), gridTotalBranchMFDs, gridRegionalBranchMFDs);
			output.closeEntry();
			
			Preconditions.checkNotNull(sumTotalBranchMFDs);
			writeCSV(FileBackedModule.initOutputStream(output, entryPrefix, SUM_FILE_NAME), sumTotalBranchMFDs, sumRegionalBranchMFDs);
			output.closeEntry();
		}
	}
	
	/*
	 * this assumes the entry has already been created externally, and will be closed externally after
	 */
	private void writeCSV(OutputStream zout, IncrementalMagFreqDist[] totalMFDs,
			List<IncrementalMagFreqDist[]> regionalMFDs) throws IOException {
		CSVWriter csv = new CSVWriter(zout, true);
		List<String> header = new ArrayList<>();
		header.add("Region Index");
		header.add("Branch Index");
		header.add("Branch Weight");
		IncrementalMagFreqDist refMFD = totalMFDs[0];
		for (Point2D pt : refMFD)
			header.add((float)pt.getX()+"");
		csv.write(header);
		int numReg = regionalMFDs == null ? 0 : regionalMFDs.size();
		for (int r=-1; r<numReg; r++) {
			IncrementalMagFreqDist[] mfds = r < 0 ? totalMFDs : regionalMFDs.get(r);
			Preconditions.checkState(mfds.length == weights.length, "Have %s weights but %s mfds for region %s",
					weights.length, mfds.length, r);
			for (int b=0; b<mfds.length; b++) {
				List<String> line = new ArrayList<>(header.size());
				if (r < 0)
					line.add(TOTAL_REG_FLAG);
				else
					line.add(r+"");
				line.add(b+"");
				line.add(weights[b]+"");
				IncrementalMagFreqDist mfd = mfds[b];
				Preconditions.checkState(mfd.size() == refMFD.size());
				Preconditions.checkState((float)mfd.getMinX() == (float)refMFD.getMinX());
				Preconditions.checkState((float)mfd.getDelta() == (float)refMFD.getDelta());
				for (Point2D pt : mfd)
					line.add((float)pt.getY()+"");
				csv.write(line);
			}
		}
		csv.flush();
		zout.flush();
	}

	@Override
	public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		// always have supra
		CSVReader csv = CSV_BackedModule.loadLargeFileFromArchive(input, entryPrefix, SUPRA_FILE_NAME);
		readCSV(csv, false, false);
		
		// see if we have gridded
		if (FileBackedModule.hasEntry(input, entryPrefix, GRID_FILE_NAME)) {
			csv = CSV_BackedModule.loadLargeFileFromArchive(input, entryPrefix, GRID_FILE_NAME);
			readCSV(csv, true, false);
			
			// should also have sum
			csv = CSV_BackedModule.loadLargeFileFromArchive(input, entryPrefix, SUM_FILE_NAME);
			readCSV(csv, true, true);
		}
	}
	
	private void readCSV(CSVReader csv, boolean gridded, boolean sum) {
		int expectedNum = weights == null ? 100 : weights.length;
		List<IncrementalMagFreqDist> totalMFDs = new ArrayList<>(expectedNum);
		List<List<IncrementalMagFreqDist>> regionalMFDs = new ArrayList<>(10);
		List<Double> myWeights = new ArrayList<>(expectedNum);
		
		Row header = csv.read();
		int mfdSize = header.columns()-3;
		EvenlyDiscretizedFunc refMFD = new EvenlyDiscretizedFunc(Double.parseDouble(header.get(3)),
				Double.parseDouble(header.get(header.columns()-1)), mfdSize);
		
		for (Row row : csv) {
			Preconditions.checkState(row.columns() == header.columns(),
					"Row BranchRegionalMFDs csv file has %s columns but header has %s", row.columns(), header.columns());
			String regStr = row.get(0);
			int branchIndex = row.getInt(1);
			while (myWeights.size() <= branchIndex)
				myWeights.add(null);
			
			double weight = row.getDouble(2);
			Double prevWeight = myWeights.get(branchIndex);
			if (prevWeight != null)
				Preconditions.checkState((float)weight == prevWeight.floatValue());
			else
				myWeights.set(branchIndex, weight);
			
			IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
			for (int i=0; i<mfd.size(); i++)
				mfd.set(i, row.getDouble(i+3));
			
			List<IncrementalMagFreqDist> mfdList;
			if (regStr.equals(TOTAL_REG_FLAG)) {
				mfdList = totalMFDs;
			} else {
				int regIndex = Integer.parseInt(regStr);
				while (regionalMFDs.size() <= regIndex)
					regionalMFDs.add(new ArrayList<>(expectedNum));
				mfdList = regionalMFDs.get(regIndex);
			}
			while (mfdList.size() <= branchIndex)
				mfdList.add(null);
			Preconditions.checkState(mfdList.get(branchIndex) == null);
			mfdList.set(branchIndex, mfd);
		}
		
		// make sure they're all full
		if (this.weights == null)
			this.weights = Doubles.toArray(myWeights);
		Preconditions.checkState(totalMFDs.size() == weights.length);
		if (regionalMFDs.isEmpty()) {
			regionalMFDs = null;
		} else {
			for (int r=0; r<regionalMFDs.size(); r++)
				Preconditions.checkState(regionalMFDs.get(r).size() == weights.length);
		}
		
		if (gridded) {
			if (sum) {
				sumTotalBranchMFDs = totalMFDs.toArray(new IncrementalMagFreqDist[0]);
				if (regionalMFDs != null) {
					sumRegionalBranchMFDs = new ArrayList<>();
					for (List<IncrementalMagFreqDist> regional : regionalMFDs)
						sumRegionalBranchMFDs.add(regional.toArray(new IncrementalMagFreqDist[0]));
				}
			} else {
				gridTotalBranchMFDs = totalMFDs.toArray(new IncrementalMagFreqDist[0]);
				if (regionalMFDs != null) {
					gridRegionalBranchMFDs = new ArrayList<>();
					for (List<IncrementalMagFreqDist> regional : regionalMFDs)
						gridRegionalBranchMFDs.add(regional.toArray(new IncrementalMagFreqDist[0]));
				}
			}
		} else {
			Preconditions.checkState(!sum);
			supraTotalBranchMFDs = totalMFDs.toArray(new IncrementalMagFreqDist[0]);
			if (regionalMFDs != null) {
				supraRegionalBranchMFDs = new ArrayList<>();
				for (List<IncrementalMagFreqDist> regional : regionalMFDs)
					supraRegionalBranchMFDs.add(regional.toArray(new IncrementalMagFreqDist[0]));
			}
		}
	}
	
	public IncrementalMagFreqDist[] calcTotalIncrementalFractiles(MFDType type, double... fractiles) {
		return (IncrementalMagFreqDist[])calcFractiles(getTotalBranchMFDs(type), fractiles, false);
	}
	
	public EvenlyDiscretizedFunc[] calcTotalCumulativeFractiles(MFDType type, double... fractiles) {
		return calcFractiles(getTotalBranchMFDs(type), fractiles, true);
	}
	
	public IncrementalMagFreqDist[] calcRegionalIncrementalFractiles(MFDType type, int regionIndex, double... fractiles) {
		return (IncrementalMagFreqDist[])calcFractiles(getRegionalBranchMFDs(type, regionIndex), fractiles, false);
	}
	
	public EvenlyDiscretizedFunc[] calcRegionalCumulativeFractiles(MFDType type, int regionIndex, double... fractiles) {
		return calcFractiles(getRegionalBranchMFDs(type, regionIndex), fractiles, true);
	}
	
	private EvenlyDiscretizedFunc[] calcFractiles(IncrementalMagFreqDist[] mfds, double[] fractiles, boolean cumulative) {
		EvenlyDiscretizedFunc refMFD = cumulative ? mfds[0].getCumRateDistWithOffset() : mfds[0];
		 
		double[][] branchVals = new double[refMFD.size()][mfds.length];
		
		for (int b=0; b<mfds.length; b++) {
			IncrementalMagFreqDist branchMFD = mfds[b];
			if (cumulative) {
				EvenlyDiscretizedFunc branchCmlMFD = branchMFD.getCumRateDistWithOffset();
				for (int i=0; i<branchCmlMFD.size(); i++)
					branchVals[i][b] = branchCmlMFD.getY(i);
			} else {
				for (int i=0; i<branchMFD.size(); i++)
					branchVals[i][b] = branchMFD.getY(i);
			}
		}
		
		LightFixedXFunc[] normCDFs = new LightFixedXFunc[refMFD.size()];
		
		for (int i=0; i<normCDFs.length; i++)
			normCDFs[i] = ArbDiscrEmpiricalDistFunc.calcQuickNormCDF(branchVals[i], weights);
		
		EvenlyDiscretizedFunc[] ret = cumulative ?
				new EvenlyDiscretizedFunc[fractiles.length] : new IncrementalMagFreqDist[fractiles.length];
		
		for (int f=0; f<ret.length; f++) {
			Preconditions.checkState(fractiles[f] >= 0d && fractiles[f] <= 1d);
			if (cumulative)
				ret[f] = new EvenlyDiscretizedFunc(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
			else
				ret[f] = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
			for (int i=0; i<normCDFs.length; i++) {
				LightFixedXFunc ncdf = normCDFs[i];
				if ((float)fractiles[f] <= (float)ncdf.getMinY())
					ret[f].set(i, ncdf.getX(0));
				else if (fractiles[f] == 1d)
					ret[f].set(i, ncdf.getX(ncdf.size()-1));
				else
					ret[f].set(i, ncdf.getFirstInterpolatedX(fractiles[f]));
			}
		}
		
		return ret;
	}

}
