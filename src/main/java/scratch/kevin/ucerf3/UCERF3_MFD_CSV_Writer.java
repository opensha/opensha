package scratch.kevin.ucerf3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipException;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Region;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.logicTree.APrioriBranchWeightProvider;
import scratch.UCERF3.logicTree.LogicTreeBranch;

public class UCERF3_MFD_CSV_Writer {

	public static void main(String[] args) throws ZipException, IOException {
		CompoundFaultSystemSolution cfss = CompoundFaultSystemSolution.fromZipFile(
				new File("/home/kevin/workspace/opensha-ucerf3/src/scratch/UCERF3/data/scratch/InversionSolutions/"
						+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL.zip"));
		
		FaultModels fm = FaultModels.FM3_1;
		DeformationModels dm = DeformationModels.GEOLOGIC;

//		Region region = new CaliforniaRegions.RELM_TESTING();
//		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/mfd_csv_files");
//		Region region = new CaliforniaRegions.RELM_SOCAL();
//		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/mfd_csv_files/socal");
		Region region = new CaliforniaRegions.RELM_NOCAL();
		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/mfd_csv_files/nocal");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		APrioriBranchWeightProvider weightProv = new APrioriBranchWeightProvider();
		
		List<IncrementalMagFreqDist> offMFDs = new ArrayList<>();
		List<IncrementalMagFreqDist> subMFDs = new ArrayList<>();
		List<IncrementalMagFreqDist> supraMFDs = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		
		double totWeight = 0d;
		
		for (LogicTreeBranch branch : cfss.getBranches()) {
			if (fm != null && branch.getValue(FaultModels.class) != fm)
				continue;
			if (dm != null && branch.getValue(DeformationModels.class) != dm)
				continue;
			InversionFaultSystemSolution sol = cfss.getSolution(branch);
			double weight = weightProv.getWeight(branch);
			totWeight += weight;
			weights.add(weight);
			IncrementalMagFreqDist offMFD = sol.getFinalTrulyOffFaultMFD();
			IncrementalMagFreqDist subMFD = sol.getFinalTotalSubSeismoOnFaultMFD();
			IncrementalMagFreqDist supraMFD = sol.calcNucleationMFD_forRegion(region, InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.MAX_MAG,
					InversionTargetMFDs.NUM_MAG, true);
			Preconditions.checkState(offMFD.size() == subMFD.size(), "%s != %s", offMFD.size(), subMFD.size());
			Preconditions.checkState(offMFD.size() == supraMFD.size(), "%s != %s", offMFD.size(), supraMFD.size());
			Preconditions.checkState(offMFD.getMinX() == subMFD.getMinX());
			Preconditions.checkState(offMFD.getMinX() == supraMFD.getMinX());
			
			for (int i=0; i<offMFD.size(); i++) {
				double x = offMFD.getX(i);
				Preconditions.checkState(Double.isFinite(offMFD.getY(i)), "Non-finite value for x=%s: %s", x, offMFD.getY(i));
				Preconditions.checkState(Double.isFinite(subMFD.getY(i)), "Non-finite value for x=%s: %s", x, subMFD.getY(i));
				Preconditions.checkState(Double.isFinite(supraMFD.getY(i)), "Non-finite value for x=%s: %s", x, supraMFD.getY(i));
			}
			
			offMFDs.add(offMFD);
			subMFDs.add(subMFD);
			supraMFDs.add(supraMFD);
			
			if (offMFDs.size() % 10 == 0)
				System.out.println("Done with "+offMFDs.size()+" branches");
		}
		
		System.out.println("Loaded MFDs for "+supraMFDs.size()+" branches");
		
		String fm_dm_prefix = "";
		if (fm != null)
			fm_dm_prefix = fm.encodeChoiceString()+"_";
		if (dm != null)
			fm_dm_prefix += dm.encodeChoiceString()+"_";
		
		String[] prefixes = { fm_dm_prefix+"supra_seis", fm_dm_prefix+"supra_plus_sub_seis", fm_dm_prefix+"total" };
		List<SummedMagFreqDist> subPlusSupra = sumMFDs(supraMFDs, subMFDs);
		List<SummedMagFreqDist> total = sumMFDs(supraMFDs, subMFDs);
		
		List<List<? extends IncrementalMagFreqDist>> mfdLists = new ArrayList<>();
		mfdLists.add(supraMFDs);
		mfdLists.add(subPlusSupra);
		mfdLists.add(total);
		
		for (int i=0; i<prefixes.length; i++) {
			for (boolean cumulative : new boolean[] { false, true }) {
				EvenlyDiscretizedFunc[] array = getMeanMinMax(mfdLists.get(i), weights, totWeight, cumulative);
				
				EvenlyDiscretizedFunc meanFunc = array[0];
				EvenlyDiscretizedFunc minFunc = array[1];
				EvenlyDiscretizedFunc maxFunc = array[2];
				
				CSVFile<String> csv = new CSVFile<>(true);
				csv.addLine("Magnitude", "Mean", "Min", "Max");
				for (int j=0; j<meanFunc.size(); j++)
					csv.addLine(meanFunc.getX(j)+"", meanFunc.getY(j)+"", minFunc.getY(j)+"", maxFunc.getY(j)+"");
				
				String myPrefix = prefixes[i];
				if (cumulative)
					myPrefix += "_cumulative";
				else
					myPrefix += "_incremental";
				csv.writeToFile(new File(outputDir, myPrefix+".csv"));
			}
		}
	}
	
	private static List<SummedMagFreqDist> sumMFDs(List<IncrementalMagFreqDist> list1, List<IncrementalMagFreqDist> list2) {
		List<SummedMagFreqDist> ret = new ArrayList<>();
		for (int i=0; i<list1.size(); i++) {
			IncrementalMagFreqDist mfd1 = list1.get(i);
			IncrementalMagFreqDist mfd2 = list2.get(i);
			SummedMagFreqDist summedMFD = new SummedMagFreqDist(mfd1.getMinX(), mfd1.getMaxX(), mfd1.size());
			summedMFD.addIncrementalMagFreqDist(mfd1);
			summedMFD.addIncrementalMagFreqDist(mfd2);
			ret.add(summedMFD);
		}
		return ret;
	}
	
	private static EvenlyDiscretizedFunc[] getMeanMinMax(List<? extends IncrementalMagFreqDist> mfds, List<Double> weights, double totWeight,
			boolean cumulative) {
		EvenlyDiscretizedFunc minFunc = null;
		EvenlyDiscretizedFunc maxFunc = null;
		EvenlyDiscretizedFunc meanFunc = null;
		
		for (int i=0; i<mfds.size(); i++) {
			EvenlyDiscretizedFunc mfd = cumulative ? mfds.get(i).getCumRateDistWithOffset() : mfds.get(i);
			double weight = weights.get(i)/totWeight;
			if (minFunc == null) {
				minFunc = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
				for (int j=0; j<minFunc.size(); j++)
					minFunc.set(j, Double.POSITIVE_INFINITY);
				maxFunc = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
				meanFunc = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
			}
			Preconditions.checkState(mfd.size() == minFunc.size());
			for (int j=0; j<mfd.size(); j++) {
				double val = mfd.getY(j);
				minFunc.set(j, Math.min(val, minFunc.getY(j)));
				maxFunc.set(j, Math.max(val, maxFunc.getY(j)));
				meanFunc.add(j, val*weight);
			}
		}
		
		for (int j=0; j<minFunc.size(); j++)
			if (Double.isInfinite(minFunc.getY(j)))
				minFunc.set(j, 0d);
		
		return new EvenlyDiscretizedFunc[] { meanFunc, minFunc, maxFunc };
	}

}
