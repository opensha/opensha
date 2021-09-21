package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class SubSeismoOnFaultMFDs implements CSV_BackedModule {
	
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
		
		List<IncrementalMagFreqDist> mfds = new ArrayList<>();
		for (int row=1; row<csv.getNumRows(); row++) {
			List<String> line = csv.getLine(row);
			Preconditions.checkState(Integer.parseInt(line.get(0)) == row-1, "File out of order or not 0-based");
			IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(min, max, num);
			for (int i=0; i<mfd.size(); i++)
				mfd.set(i, Double.parseDouble(line.get(i+1)));
			mfds.add(mfd);
		}
		this.subSeismoOnFaultMFDs = ImmutableList.copyOf(mfds);
	}
	
	public static SubSeismoOnFaultMFDs fromCSV(CSVFile<String> csv) {
		SubSeismoOnFaultMFDs mfds = new SubSeismoOnFaultMFDs();
		mfds.initFromCSV(csv);
		return mfds;
	}

}
