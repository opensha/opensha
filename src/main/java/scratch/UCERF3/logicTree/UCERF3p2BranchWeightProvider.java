package scratch.UCERF3.logicTree;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;

public class UCERF3p2BranchWeightProvider implements BranchWeightProvider {

	@Override
	public double getWeight(LogicTreeBranch branch) {
		double wt = 1;
		InversionModels im = branch.getValue(InversionModels.class);
		// special cases
		for (LogicTreeBranchNode<?> node : branch) {
			double subWt;
			if (node instanceof DeformationModels) {
				// special case for DMs
				switch ((DeformationModels)node) {
				case ABM:
					subWt = 0.2;
					break;
				case NEOKINEMA:
					subWt = 0.2;
					break;
				case ZENGBB:
					subWt = 0.3;
					break;
				case GEOLOGIC:
					subWt = 0.3;
					break;

				default:
					subWt = 0d;
					break;
				}
			} else if (node instanceof TotalMag5Rate) {
				switch ((TotalMag5Rate)node) {
				case RATE_7p6:
					subWt = 0.1;
					break;
				case RATE_8p7:
					subWt = 0.6;
					break;
				case RATE_10p0:
					subWt = 0.3;
					break;

				default:
					subWt = 0d;
					break;
				}
			} else if (node instanceof MaxMagOffFault) {
				switch ((MaxMagOffFault)node) {
				case MAG_7p2:
					subWt = 0.3;
					break;
				case MAG_7p6:
					subWt = 0.6;
					break;
				case MAG_8p0:
					subWt = 0.1;
					break;

				default:
					subWt = 0d;
					break;
				}
			} else {
				subWt = LogicTreeBranch.getNormalizedWt(node, im);
			}
			wt *= subWt;
		}
		return wt;
	}

}
