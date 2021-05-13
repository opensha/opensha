package scratch.kevin.ucerf3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.dom4j.DocumentException;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

public class SubSectCoruptureRateCalc {

	public static void main(String[] args) throws IOException, DocumentException {
		int[] indexes = null;
		String[] names = { "San Andreas (Mojave S), Subsection 14",
				"San Andreas (San Bernardino N), Subsection 2", "San Jacinto (San Bernardino), Subsection 1" };
		
		double[] minMags = {0d, 7d};
		
		File fssFile = new File("/home/kevin/workspace/opensha-ucerf3/src/scratch/UCERF3/data/scratch/"
				+ "InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip");
		FaultSystemSolution fss = FaultSystemIO.loadSol(fssFile);
		FaultSystemRupSet rupSet = fss.getRupSet();
		
		if (indexes == null) {
			System.out.println("Determining indexes from names");
			indexes = new int[names.length];
			for (int i=0; i<names.length; i++) {
				indexes[i] = -1;
				for (int s=0; s<rupSet.getNumSections(); s++) {
					if (rupSet.getFaultSectionData(s).getName().equals(names[i])) {
						indexes[i] = s;
						break;
					}
				}
				Preconditions.checkState(indexes[i] >= 0, "No match found for: "+names[i]);
			}
		} else {
			names = new String[indexes.length];
			for (int i=0; i<indexes.length; i++)
				names[i] = rupSet.getFaultSectionData(indexes[i]).getName();
		}
		
		List<HashSet<Integer>> rupLists = new ArrayList<>();
		for (int s : indexes)
			rupLists.add(new HashSet<>(rupSet.getRupturesForSection(s)));
		
		for (double minMag : minMags) {
			System.out.println();
			if (minMag > 0)
				System.out.println("Minimum magnitude: "+(float)minMag);
			else
				System.out.println("All supra-seismogenic magnitudes");
			for (int i=0; i<indexes.length; i++) {
				for (int j=i+1; j<indexes.length; j++) {
					HashSet<Integer> rups = new HashSet<>(rupLists.get(i));
					rups.retainAll(rupLists.get(j));
					double rate = 0;
					for (int r : rups)
						if (rupSet.getMagForRup(r) >= minMag)
							rate += fss.getRateForRup(r);
					System.out.println("Corupture rate, '"+names[i]+"' and '"+names[j]+"': "+(float)rate);
				}
			}
		}
	}

}
