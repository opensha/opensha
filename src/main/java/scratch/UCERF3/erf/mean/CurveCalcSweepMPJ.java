package scratch.UCERF3.erf.mean;

import java.io.File;
import java.util.List;

import mpi.MPI;
import net.kevinmilner.mpj.taskDispatch.MPJTaskCalculator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.analysis.MPJDistributedCompoundFSSPlots;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.simulatedAnnealing.hpc.MPJInversionDistributor;

public class CurveCalcSweepMPJ extends MPJTaskCalculator {
	
	private static class CalcParams {
		double upperDepthTol;
		boolean rakeCombine;
		double magTol;
		DeformationModels rakeBasis;
		public CalcParams(double upperDepthTol, boolean rakeCombine,
				double magTol, DeformationModels rakeBasis) {
			super();
			this.upperDepthTol = upperDepthTol;
			this.rakeCombine = rakeCombine;
			this.magTol = magTol;
			this.rakeBasis = rakeBasis;
		}
	}
	
	private List<CalcParams> calcs;
	private File meanSolFile;
	private File remoteDir;
	
	static List<CalcParams> buildCalcsList() {
		List<CalcParams> calcs = Lists.newArrayList();
		
//		double[] upperDepthTols = { 0, 1d, 5d, Double.POSITIVE_INFINITY };
		double[] upperDepthTols = { 1d, 5d, Double.POSITIVE_INFINITY };
		boolean[] rakeCombines = { false, true };
		double[] magTols = { 0, 0.1, 0.5, 1 };
		DeformationModels[] rakeBasisDMs = { null, DeformationModels.GEOLOGIC };
		DeformationModels[] noRakeBasisDMs = { null };
		
		for (double upperDepthTol : upperDepthTols) {
			for (boolean rakeCombine : rakeCombines) {
				DeformationModels[] myRakeBasisDMs;
				if (rakeCombine)
					myRakeBasisDMs = rakeBasisDMs;
				else
					myRakeBasisDMs = noRakeBasisDMs;
				for (double magTol : magTols) {
					for (DeformationModels rakeBasis : myRakeBasisDMs) {
						calcs.add(new CalcParams(upperDepthTol, rakeCombine, magTol, rakeBasis));
//						String jobArgs = meanSolFile.getAbsolutePath()+" "+remoteDir.getAbsolutePath()
//								+" "+upperDepthTol+" "+magTol+" "+rakeCombine+" "+rakeBasisStr;
					}
				}
			}
		}
		return calcs;
	}

	public CurveCalcSweepMPJ(CommandLine cmd) {
		super(cmd);
		
		String[] args = cmd.getArgs();
		meanSolFile = new File(args[0]);
		remoteDir = new File(args[1]);
		
		calcs = buildCalcsList();
		
		int dispatchNum = Integer.parseInt(cmd.getOptionValue("exact"));
		
		int numNodes = MPI.COMM_WORLD.Size();
		int numInversions = calcs.size();
		
		// make sure that the dispatch amount agrees with the number of inversions/nodes
		int numSlots = numNodes * dispatchNum;
		Preconditions.checkState(numInversions <= numSlots, "Too few slots! slots="
				+numSlots+", inversions="+numInversions);
	}

	@Override
	protected int getNumTasks() {
		return calcs.size();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		for (int index : batch) {
			CalcParams calc = calcs.get(index);
			String rakeBasisStr;
			if (calc.rakeBasis == null)
				rakeBasisStr = "null";
			else
				rakeBasisStr = calc.rakeBasis.name();
			String[] args = {meanSolFile.getAbsolutePath(), remoteDir.getAbsolutePath(), calc.upperDepthTol+"",
					calc.magTol+"", calc.rakeCombine+"", rakeBasisStr};
			CurveCalcTest.doCalc(args);
		}
	}

	@Override
	protected void doFinalAssembly() throws Exception {}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("ARGS: "+Joiner.on(" ").join(args));
		args = MPJTaskCalculator.initMPJ(args);
		
		try {
			Options options = createOptions();

			CommandLine cmd = parse(options, args, CurveCalcSweepMPJ.class);

			args = cmd.getArgs();
			
			// make sure that exact dispatch was selected
			Preconditions.checkArgument(cmd.hasOption("exact"), "Must specify exact dispatch!");
			
			System.out.println("Launching!");
			CurveCalcSweepMPJ driver = new CurveCalcSweepMPJ(cmd);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
