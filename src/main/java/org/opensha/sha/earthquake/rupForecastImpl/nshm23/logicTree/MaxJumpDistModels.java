package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.calc.nnls.NNLSWrapper;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.DistDependentJumpProbabilityCalc;

import com.google.common.base.Preconditions;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum MaxJumpDistModels implements LogicTreeNode {
	ONE(		1d),
	THREE(		3d),
	FIVE(		5d),
	SEVEN(		7d),
	NINE(		9d),
	ELEVEN(		11d),
	THIRTEEN(	13d),
	FIFTEEN(	15d);
	
	public static double WEIGHT_TARGET_R0 = 3d;
	
	private double weight;
	private final double maxDist;

	private MaxJumpDistModels(double maxDist) {
		this.maxDist = maxDist;
		this.weight = -1d;
	}
	
	public HardDistCutoffJumpProbCalc getModel(FaultSystemRupSet rupSet) {
		return new HardDistCutoffJumpProbCalc(getMaxDist());
	};

	@Override
	public String getShortName() {
		return "MaxDist"+oDF.format(maxDist)+"km";
	}

	@Override
	public String getName() {
		return "MaxDist="+oDF.format(maxDist)+"km";
	}
	
	public double getMaxDist() {
		return maxDist;
	}
	
	public static void setWeights(double[] weights) {
		MaxJumpDistModels[] values = values();
		Preconditions.checkState(weights.length == values.length);
		for (int i=0; i<values.length; i++)
			values[i].weight = weights[i];
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		if (fullBranch != null) {
			RupturePlausibilityModels model = fullBranch.getValue(RupturePlausibilityModels.class);
			if (maxDist > 5d && model == RupturePlausibilityModels.UCERF3)
				return 0d;
		}
		if (weight < 0) {
			synchronized (this) {
				if (weight < 0) {
					try {
						invertForWeights();
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			}
		}
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return getShortName();
	}
	
	/**
	 * 
	 * @param cutoffDistanceArray - the set of distance cutoffs applied in the fault-system-solution inversions
	 * @param dataList - the cutoff rate data (in same order as above)
	 * @param target_ro - the target decay rate parameter (typically 3 km).
	 * @return calculated A value
	 * @throws IOException 
	 */
	public static double invertForWeights() throws IOException {
		MaxJumpDistModels[] values = MaxJumpDistModels.values();
		List<CSVFile<String>> dataCSVs = new ArrayList<>();
		
		for (MaxJumpDistModels value : values) {
			String name = "/data/erf/nshm23/constraints/segmentation/hard_cutoff_csvs/"
					+ "passthrough_rates_"+oDF.format(value.maxDist)+"km.csv";
					
			dataCSVs.add(CSVFile.readStream(MaxJumpDistModels.class.getResourceAsStream(name), true));
		}
		
		return invertForWeights(values, dataCSVs, WEIGHT_TARGET_R0);
	}
	
	/**
	 * 
	 * @param cutoffDistanceArray - the set of distance cutoffs applied in the fault-system-solution inversions
	 * @param dataList - the cutoff rate data (in same order as above)
	 * @param target_ro - the target decay rate parameter (typically 3 km).
	 * @return calculated A value
	 */
	public static double invertForWeights(MaxJumpDistModels[] values, List<CSVFile<String>> dataCSVs, double target_ro) {
		Preconditions.checkState(values.length == dataCSVs.size());
		// one column for each hard cutoff value, plus 1 for A
		int numCols = values.length+1;
		
		// there's a header row, but we count that as we're adding an extra row for -exp(-r/ro) values
		int numRows = dataCSVs.get(0).getNumRows();

		double[] d = new double[numRows];
		d[d.length-1] = 1.0;  // sum of weights must equal 1.0; all other array elements are zero
		
		// matrix is [row][col]	
		double[][] C = new double[numRows][numCols];
		
		for(int col=0; col<values.length; col++) {
			CSVFile<String> csv = dataCSVs.get(col);
			for (int row=0; row<numRows-1; row++)
				C[row][col] = csv.getDouble(row+1, 1);
			// put "1.0 in last row (sum of wts must equal 1.0)
			C[numRows-1][col] = 1.0;
		}

		for(int row=0; row<numRows-1; row++) {
			double r = dataCSVs.get(0).getDouble(row+1, 0);	// get distance from first column of any of the data objects
			C[row][numCols-1] = -Math.exp(-r/target_ro);
		}
		C[numRows-1][numCols-1] = 0;
		
//		// write out matrices
//		for(int r=0;r<numRows;r++) {
//			System.out.print("\n");
//			for(int c=0;c<numCols;c++) {
//				System.out.print(C[r][c]+"\t");
//			}
//		}
//		System.out.print("\n");
//
//		for(int r=0;r<numRows;r++) {
//			System.out.println(d[r]);
//		}
		
		// NNLS inversion:
		NNLSWrapper nnls = new NNLSWrapper();

		int nRow = C.length;
		int nCol = C[0].length;
		
//		System.out.println("NNLS: nRow="+nRow+"; nCol="+nCol);
		
		double[] A = new double[nRow*nCol];
		double[] x = new double[nCol];
		
		int i,j,k=0;
			
		for(j=0;j<nCol;j++) 
			for(i=0; i<nRow;i++)	{
				A[k]=C[i][j];
				k+=1;
			}
		nnls.update(A,nRow,nCol);
		
		boolean converged = nnls.solve(d,x);
		if(!converged)
			throw new RuntimeException("ERROR:  NNLS Inversion Failed");
		
//		System.out.println("A value: "+x[x.length-1]);
		
		for (i=0; i<values.length; i++) {
			values[i].weight = x[i];
//			System.out.println(values[i].getShortName()+", weight="+(float)x[i]);
		}
		// return A
		return x[values.length];
	}
	
	private static final DecimalFormat oDF = new DecimalFormat("0.#");
	
	public static class HardDistCutoffJumpProbCalc implements BinaryJumpProbabilityCalc, DistDependentJumpProbabilityCalc {
		
		private double maxDist;

		public HardDistCutoffJumpProbCalc(double maxDist) {
			this.maxDist = maxDist;
			
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "MaxDist="+oDF.format(maxDist)+"km";
		}

		@Override
		public boolean isJumpAllowed(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			return (float)jump.distance <= (float)maxDist;
		}

		@Override
		public double calcJumpProbability(double distance) {
			if ((float)distance < (float)maxDist)
				return 1d;
			return 0;
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			return calcJumpProbability(jump.distance);
		}
		
	}
	
	public static void main(String[] args) throws IOException {
		double a = invertForWeights();
		System.out.println("a: "+a);
		for (MaxJumpDistModels model : values())
			System.out.println(model.getName()+", weight="+(float)model.getNodeWeight(null));
	}

}
