package gov.usgs.earthquake.nshmp.erf.mpj;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.imr.AttenRelRef;

final class HazardScriptUtil {

	private HazardScriptUtil() {}

	static HazardArgs buildLogicTreeHazardArgs(File localDir, HazardConfig hazard, LogicTree<LogicTreeNode> logicTree,
			String analysisTreePath, String resultsPath) throws IOException {
		StringBuilder args = new StringBuilder();
		appendArg(args, "--input-file", resultsPath+".zip");
		if (analysisTreePath != null)
			appendArg(args, "--analysis-logic-tree", analysisTreePath);
		appendArg(args, "--output-dir", resultsPath);
		appendArg(args, "--gridded-seis", hazard.backgroundOption().name());
		HazardRegion region = resolveHazardRegion(localDir, hazard, logicTree);
		args.append(region.arg);
		String sharedArgs = buildSharedArgs(hazard);
		args.append(sharedArgs);
		return new HazardArgs(args.toString(), region.arg, sharedArgs);
	}

	static HazardRegion resolveHazardRegion(File localDir, HazardConfig hazard, LogicTree<LogicTreeNode> logicTree)
			throws IOException {
		if (hazard.region() != null) {
			File regionFile = new File(localDir, "gridded_region.geojson");
			Feature.write(hazard.region().toFeature(), regionFile);
			return new HazardRegion(" --region $DIR/"+regionFile.getName());
		}
		double gridSpacing = logicTree != null && logicTree.size() > 1000 ? 0.2 : 0.1;
		if (hazard.gridSpacing() != null)
			gridSpacing = hazard.gridSpacing();
		return new HazardRegion(" --grid-spacing "+(float)gridSpacing);
	}

	static String buildSharedArgs(HazardConfig hazard) {
		StringBuilder args = new StringBuilder();
		appendSharedArgs(args, hazard);
		return args.toString();
	}

	static void appendSharedArgs(StringBuilder args, HazardConfig hazard) {
		appendSharedArgs(args, hazard, true);
	}

	static void appendSharedArgs(StringBuilder args, HazardConfig hazard, boolean includeSupersampling) {
		for (AttenRelRef gmpe : hazard.gmpes())
			appendArg(args, "--gmpe", gmpe.name());
		if (hazard.periods() != null && hazard.periods().length > 0) {
			StringBuilder periods = new StringBuilder();
			for (int i=0; i<hazard.periods().length; i++) {
				if (i > 0)
					periods.append(",");
				periods.append((float)hazard.periods()[i]);
			}
			appendArg(args, "--periods", periods.toString());
		}
		if (hazard.vs30() != null)
			appendArg(args, "--vs30", hazard.vs30().floatValue());
		if (includeSupersampling && hazard.supersample())
			appendFlag(args, "--supersample-quick");
		if (hazard.sigmaTruncation() != null)
			appendArg(args, "--gmm-sigma-trunc-one-sided", hazard.sigmaTruncation().floatValue());
	}

	static void appendArg(StringBuilder args, String name, Object value) {
		args.append(" ").append(name).append(" ").append(value);
	}

	static void appendFlag(StringBuilder args, String name) {
		args.append(" ").append(name);
	}

	static int capWeek(int mins) {
		return Integer.min(mins, 60*24*7 - 1);
	}

	record HazardRegion(String arg) {}

	record HazardArgs(String baseArgs, String regionArg, String sharedArgs) {}
}
