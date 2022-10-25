package org.opensha.commons.util.modules.helpers;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.ModuleHelper;

import com.google.common.base.Preconditions;

@ModuleHelper
public abstract class AbstractDoubleArrayCSV_BackedModule implements CSV_BackedModule {
	
	protected double[] values;
	
	protected AbstractDoubleArrayCSV_BackedModule() {
		
	}
	
	public AbstractDoubleArrayCSV_BackedModule(double[] values) {
		this.values = values;
	}
	
	public double get(int index) {
		return values[index];
	}
	
	public double[] get() {
		return values;
	}

	/**
	 * Name for the first column of the CSV file, which describes to the array index
	 * 
	 * @return
	 */
	protected abstract String getIndexHeading();
	
	/**
	 * Name for the second column of the CSV file, which describes the data value
	 * 
	 * @return
	 */
	protected abstract String getValueHeading();
	
	@Override
	public CSVFile<String> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine(getIndexHeading(), getValueHeading());
		for (int r=0; r<values.length; r++)
			csv.addLine(r+"", values[r]+"");
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		double[] vals = new double[csv.getNumRows()-1];
		
		String heading = getIndexHeading();
		for (int row=1; row<csv.getNumRows(); row++) {
			int r = row-1;
			Preconditions.checkState(r == csv.getInt(row, 0),
					"%s must be 0-based and in order. Expected %s at row %s", heading, r, row);
			vals[r] = csv.getDouble(row, 1);
		}
		this.values = vals;
	}
	
	@ModuleHelper
	public static abstract class Averageable<E extends Averageable<E>> extends AbstractDoubleArrayCSV_BackedModule
	implements AverageableModule<E> {
		
		protected Averageable() {
			super();
		}
		
		public Averageable(double[] values) {
			super(values);
		}

		@Override
		public AveragingAccumulator<E> averagingAccumulator() {
			return new AveragingAccumulator<>() {
				
				private double[] avgValues = null;
				private double sumWeight = 0d;

				@Override
				public void process(E module, double relWeight) {
					if (avgValues == null)
						avgValues = new double[module.values.length];
					else
						Preconditions.checkState(module.values.length == avgValues.length);
					
					for (int i=0; i< avgValues.length; i++)
						avgValues[i] += module.values[i]*relWeight;
					sumWeight += relWeight;
				}

				@Override
				public E getAverage() {
					AverageableModule.scaleToTotalWeight(avgValues, sumWeight);
					return averageInstance(avgValues);
				}

				@SuppressWarnings("unchecked") // it's guaranteed by generics to be the correct type at runtime
				@Override
				public Class<E> getType() {
					return (Class<E>)Averageable.this.getClass();
				}
				
			};
		}
		
		protected abstract E averageInstance(double[] avgValues);
		
	}

}
