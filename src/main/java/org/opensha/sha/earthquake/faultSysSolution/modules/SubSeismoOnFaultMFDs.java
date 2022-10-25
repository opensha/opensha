package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class SubSeismoOnFaultMFDs implements CSV_BackedModule, BranchAverageableModule<SubSeismoOnFaultMFDs> {
	
	private ImmutableList<IncrementalMagFreqDist> subSeismoOnFaultMFDs;
	
	private SubSeismoOnFaultMFDs() {
		// used for deserialization
	}
	
	public SubSeismoOnFaultMFDs(List<? extends IncrementalMagFreqDist> subSeismoOnFaultMFDs) {
		this.subSeismoOnFaultMFDs = ImmutableList.copyOf(subSeismoOnFaultMFDs);
	}
	
	public IncrementalMagFreqDist get(int sectIndex) {
		return subSeismoOnFaultMFDs.get(sectIndex);
	}
	
	public int size() {
		return subSeismoOnFaultMFDs.size();
	}
	
	public ImmutableList<IncrementalMagFreqDist> getAll() {
		return subSeismoOnFaultMFDs;
	}
	
	public SummedMagFreqDist getTotal() {
		SummedMagFreqDist sum = null;
		for (int i=0; i<size(); i++) {
			IncrementalMagFreqDist mfd = get(i);
			if (mfd == null)
				continue;
			if (sum == null)
				sum = new SummedMagFreqDist(mfd.getMinX(), mfd.getMaxX(), mfd.size());
			sum.addIncrementalMagFreqDist(mfd);
		}
		return sum;
	}
	
	public static final String DATA_FILE_NAME = "sub_seismo_on_fault_mfds.csv";

	@Override
	public String getFileName() {
		return DATA_FILE_NAME;
	}

	@Override
	public String getName() {
		return "Sub-Seismogenic On-Fault MFDs";
	}

	@Override
	public CSVFile<?> getCSV() {
		Preconditions.checkNotNull(subSeismoOnFaultMFDs);
		CSVFile<String> csv = new CSVFile<>(false);
		
		IncrementalMagFreqDist maxMagFunc = null;
		for (IncrementalMagFreqDist mfd : subSeismoOnFaultMFDs) {
			if (maxMagFunc == null) {
				maxMagFunc = mfd;
			} else {
				Preconditions.checkState((float)mfd.getMinX() == (float)maxMagFunc.getMinX());
				Preconditions.checkState((float)mfd.getDelta() == (float)maxMagFunc.getDelta());
				if (mfd.size() > maxMagFunc.size())
					maxMagFunc = mfd;
			}
		}
		List<String> header = new ArrayList<>();
		header.add("Section Index");
		for (int i=0; i<maxMagFunc.size(); i++)
			header.add(maxMagFunc.getX(i)+"");
		csv.addLine(header); // will replace letter
		for (int s=0; s<subSeismoOnFaultMFDs.size(); s++) {
			IncrementalMagFreqDist mfd = subSeismoOnFaultMFDs.get(s);
			List<String> line = new ArrayList<>(mfd.size()+1);
			line.add(s+"");
			for (int i=0; i<mfd.size(); i++)
				line.add(mfd.getY(i)+"");
			csv.addLine(line);
		}
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		List<String> header = csv.getLine(0);
		double min = Double.parseDouble(header.get(1));
		double max = Double.parseDouble(header.get(header.size()-1));
		int num = header.size()-1;
		IncrementalMagFreqDist refMFD = new IncrementalMagFreqDist(min, max, num);
		for (int i=0; i<num; i++) {
			double myX = refMFD.getX(i);
			double csvX = Double.parseDouble(header.get(i+1));
			Preconditions.checkState((float)myX == (float)csvX, "Expected M=%s as value %s in the MFD x-values, got %s",
					(float)myX, i, (float)csvX);
		}
		
		List<IncrementalMagFreqDist> mfds = new ArrayList<>();
		for (int row=1; row<csv.getNumRows(); row++) {
			List<String> line = csv.getLine(row);
			Preconditions.checkState(Integer.parseInt(line.get(0)) == row-1, "File out of order or not 0-based");
			int myNum = 0;
			for (int i=1; i<line.size(); i++) {
				if (line.get(i).isBlank())
					break;
				myNum++;
			}
			IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(min, refMFD.getX(myNum-1), myNum);
			for (int i=0; i<mfd.size(); i++) {
				Preconditions.checkState((float)mfd.getX(i) == (float)refMFD.getX(i));
				mfd.set(i, Double.parseDouble(line.get(i+1)));
			}
			mfds.add(mfd);
		}
		this.subSeismoOnFaultMFDs = ImmutableList.copyOf(mfds);
	}
	
	public static SubSeismoOnFaultMFDs fromCSV(CSVFile<String> csv) {
		SubSeismoOnFaultMFDs mfds = new SubSeismoOnFaultMFDs();
		mfds.initFromCSV(csv);
		return mfds;
	}

	@Override
	public AveragingAccumulator<SubSeismoOnFaultMFDs> averagingAccumulator() {
		return new AveragingAccumulator<SubSeismoOnFaultMFDs>() {
			
			private List<IncrementalMagFreqDist> avgMFDs;
			private double totWeight = 0d;

			@Override
			public void process(SubSeismoOnFaultMFDs module, double relWeight) {
				if (avgMFDs == null) {
					avgMFDs = new ArrayList<>();
					for (int i=0; i<module.size(); i++)
						avgMFDs.add(null);
				}
				for (int i=0; i<module.size(); i++) {
					IncrementalMagFreqDist avg = avgMFDs.get(i);
					IncrementalMagFreqDist mine = module.get(i);
					if (mine == null)
						continue;
					if (avg == null) {
						avg = new IncrementalMagFreqDist(mine.getMinX(), mine.size(), mine.getDelta());
						avgMFDs.set(i, avg);
					}
					Preconditions.checkState((float)mine.getMinX() == (float)avg.getMinX());
					Preconditions.checkState((float)mine.getDelta() == (float)avg.getDelta());
					if (mine.size() > avg.size()) {
						// need to grow it
						IncrementalMagFreqDist larger = new IncrementalMagFreqDist(mine.getMinX(), mine.size(), mine.getDelta());
						for (int j=0; j<avg.size(); j++)
							larger.set(j, avg.getY(j));
						avg = larger;
						avgMFDs.set(i, larger);
					}
					for (int j=0; j<mine.size(); j++)
						avg.add(j, mine.getY(j)*relWeight);
				}
				totWeight += relWeight;
			}

			@Override
			public SubSeismoOnFaultMFDs getAverage() {
				if (totWeight != 1d) {
					double scale = 1d/totWeight;
					for (IncrementalMagFreqDist mfd : avgMFDs)
						if (mfd != null)
							mfd.scale(scale);
				}
				return new SubSeismoOnFaultMFDs(avgMFDs);
			}

			@Override
			public Class<SubSeismoOnFaultMFDs> getType() {
				return SubSeismoOnFaultMFDs.class;
			}
		};
	}

}
