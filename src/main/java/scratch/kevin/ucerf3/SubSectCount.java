package scratch.kevin.ucerf3;

import java.util.HashSet;
import java.util.List;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.utils.RSQSimUtils;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;

public class SubSectCount {

	public static void main(String[] args) {
		FaultModels[] fms = { FaultModels.FM3_1, FaultModels.FM3_2 };
		for (FaultModels fm : fms) {
			List<? extends FaultSection> subSects = RSQSimUtils.getUCERF3SubSectsForComparison(fm, DeformationModels.GEOLOGIC);
			System.out.println(fm.getName());
			System.out.println("\t"+subSects.size()+" sub sections");
			HashSet<String> parentSects = new HashSet<>();
			for (FaultSection subSect : subSects)
				parentSects.add(subSect.getParentSectionName());
			System.out.println("\t"+parentSects.size()+" parent sections");
		}
	}

}
;