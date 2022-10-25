package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.RakeType;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.DistDependentJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Class for various Biasi & Wesnousky (2016, 2017) probability implementations
 * 
 * @author kevin
 *
 */
public class BiasiWesnouskyJumpProb {

	public static RuptureProbabilityCalc[] getPrefferedBWCalcs(SectionDistanceAzimuthCalculator distAzCalc) {
		return new RuptureProbabilityCalc[] {
				new BiasiWesnousky2016CombJumpDistProb(),
				new BiasiWesnousky2017JumpAzChangeProb(distAzCalc),
				new BiasiWesnousky2017MechChangeProb()
		};
	}
	
	private static class HardcodedMechDistIndepJumpProb implements DistDependentJumpProbabilityCalc {
		
		private double minJumpDist;
		private RakeType type;

		public HardcodedMechDistIndepJumpProb(double minJumpDist, RakeType type) {
			this.minJumpDist = minJumpDist;
			this.type = type;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "BW16 DistIndep"+type.name;
		}

		@Override
		public double calcJumpProbability(double distance) {
			if (distance < minJumpDist)
				return 1d;
			return BiasiWesnouskyDistIndepJumpProb.getDistanceIndepentProb(type);
		}
		
	}
	
	public static class BiasiWesnouskyDistIndepJumpProb implements JumpProbabilityCalc {
		
		private double minJumpDist;

		public BiasiWesnouskyDistIndepJumpProb(double minJumpDist) {
			this.minJumpDist = minJumpDist;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "BW16 DistIndep";
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			if (jump.distance < minJumpDist)
				return 1d;
			RakeType type1 = RakeType.getType(jump.fromSection.getAveRake());
			RakeType type2 = RakeType.getType(jump.toSection.getAveRake());
			// average probabilities from each mechanism
			// TODO is this right? should we take the minimum or maximum? check with Biasi 
			return 0.5*(getDistanceIndepentProb(type1)+getDistanceIndepentProb(type2));
		}

		public static double getDistanceIndepentProb(RakeType type) {
			// Table 4 of Biasi & Wesnousky (2016)
			if (type == RakeType.REVERSE)
				return 0.62;
			if (type == RakeType.NORMAL)
				return 0.37;
			// generic dip slip or SS
			return 0.46;
		}
		
	}

	public static class BiasiWesnousky2016CombJumpDistProb implements JumpProbabilityCalc {

		private double minJumpDist;
		private BiasiWesnousky2016SSJumpProb ssJumpProb;
		private BiasiWesnouskyDistIndepJumpProb indepJumpProb;

		public BiasiWesnousky2016CombJumpDistProb() {
			this(1d);
		}

		public BiasiWesnousky2016CombJumpDistProb(double minJumpDist) {
			this.minJumpDist = minJumpDist;
			ssJumpProb = new BiasiWesnousky2016SSJumpProb(minJumpDist);
			indepJumpProb = new BiasiWesnouskyDistIndepJumpProb(minJumpDist);
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			if (jump.distance < minJumpDist)
				return 1d;
			RakeType type1 = RakeType.getType(jump.fromSection.getAveRake());
			RakeType type2 = RakeType.getType(jump.toSection.getAveRake());
			if (type1 == type2 && (type1 == RakeType.LEFT_LATERAL || type1 == RakeType.RIGHT_LATERAL))
				// only use distance-dependent model if both are SS
				return ssJumpProb.calcJumpProbability(fullRupture, jump, verbose);
			return indepJumpProb.calcJumpProbability(fullRupture, jump, verbose);
		}

		@Override
		public String getName() {
			return "BW16 JumpDist";
		}

		public boolean isDirectional(boolean splayed) {
			return false;
		}

	}

	public static double passingRatioToProb(double passingRatio) {
		return passingRatio/(passingRatio + 1);
	}

	public static double probToPassingRatio(double prob) {
		return -prob/(prob-1d);
	}

	public static class BiasiWesnousky2016SSJumpProb implements DistDependentJumpProbabilityCalc{

		private double minJumpDist;

		public BiasiWesnousky2016SSJumpProb() {
			this(1d);
		}

		public BiasiWesnousky2016SSJumpProb(double minJumpDist) {
			this.minJumpDist = minJumpDist;
		}

		public double calcPassingRatio(double distance) {
			// this is the ratio of of times the rupture passes through a jump of this size
			// relative to the number of times it stops there
			return Math.max(0, 1.89 - 0.31*distance);
		}

		public double calcPassingProb(double distance) {
			return passingRatioToProb(calcPassingRatio(distance));
		}

		@Override
		public double calcJumpProbability(double distance) {
			if (distance < minJumpDist)
				return 1d;
			return calcPassingProb(distance);
		}

		@Override
		public String getName() {
			return "BW16 SS JumpDist";
		}

		public boolean isDirectional(boolean splayed) {
			return false;
		}

	}

	public static final EvenlyDiscretizedFunc bw2017_ss_passRatio;
	static {
		// tabulated by eye from Figure 8c (10km bin size)
		// TODO: get exact values from Biasi
		bw2017_ss_passRatio = new EvenlyDiscretizedFunc(5d, 45d, 5);
		bw2017_ss_passRatio.set(5d,		2.7d);
		bw2017_ss_passRatio.set(15d,	1.35d);
		bw2017_ss_passRatio.set(25d,	1.3d);
		bw2017_ss_passRatio.set(35d,	0.1d);
		bw2017_ss_passRatio.set(45d,	0.08d);
	}

	public static class BiasiWesnousky2017JumpAzChangeProb implements JumpProbabilityCalc {

		private SectionDistanceAzimuthCalculator distAzCalc;

		public BiasiWesnousky2017JumpAzChangeProb(SectionDistanceAzimuthCalculator distAzCalc) {
			this.distAzCalc = distAzCalc;
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			RuptureTreeNavigator nav = fullRupture.getTreeNavigator();

			RakeType type1 = RakeType.getType(jump.fromSection.getAveRake());
			RakeType type2 = RakeType.getType(jump.toSection.getAveRake());
			if (type1 != type2)
				// only evaluate within mechanism
				// rely on other models for mechanism change probabilities
				return 1d;

			FaultSection before2 = jump.fromSection;
			FaultSection before1 = nav.getPredecessor(before2);
			if (before1 == null)
				return 1d;
			double beforeAz = distAzCalc.getAzimuth(before1, before2);
			FaultSection after1 = jump.toSection;
			double prob = 1d;
			for (FaultSection after2 : nav.getDescendants(after1)) {
				double afterAz = distAzCalc.getAzimuth(after1, after2);
				double diff = Math.abs(JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz));
				double passingRatio;
				if (type1 == RakeType.LEFT_LATERAL || type1 == RakeType.RIGHT_LATERAL) {
					// strike-slip case
					// will just grab the closest binned value
					// this extends the last bin uniformly to 180 degree differences, TODO confer with Biasi
					passingRatio = bw2017_ss_passRatio.getY(bw2017_ss_passRatio.getClosestXIndex(diff));
				} else {
					// not well defined, arbitrary choice here based loosely on Figure 8d
					// TODO: confer with Biasi
					if (diff < 60d)
						passingRatio = 2d;
					else
						passingRatio = 0.5d;
				}
				prob = Math.min(prob, passingRatioToProb(passingRatio));
			}
			return prob;
		}

		@Override
		public String getName() {
			return "BW17 AzChange";
		}

		public boolean isDirectional(boolean splayed) {
			return false;
		}

	}

	public static class BiasiWesnousky2017_SSJumpAzChangeProb implements JumpProbabilityCalc {

		private SectionDistanceAzimuthCalculator distAzCalc;

		public BiasiWesnousky2017_SSJumpAzChangeProb(SectionDistanceAzimuthCalculator distAzCalc) {
			this.distAzCalc = distAzCalc;
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			RuptureTreeNavigator nav = fullRupture.getTreeNavigator();

			RakeType type1 = RakeType.getType(jump.fromSection.getAveRake());
			RakeType type2 = RakeType.getType(jump.toSection.getAveRake());
			if (type1 != type2)
				// only evaluate within mechanism
				// rely on other models for mechanism change probabilities
				return 1d;
			if (type1 != RakeType.RIGHT_LATERAL && type1 != RakeType.LEFT_LATERAL)
				// SS only
				return 1d;

			FaultSection before2 = jump.fromSection;
			FaultSection before1 = nav.getPredecessor(before2);
			if (before1 == null)
				return 1d;
			double beforeAz = distAzCalc.getAzimuth(before1, before2);
			FaultSection after1 = jump.toSection;
			double prob = 1d;
			for (FaultSection after2 : nav.getDescendants(after1)) {
				double afterAz = distAzCalc.getAzimuth(after1, after2);
				double diff = Math.abs(JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz));
				// will just grab the closest binned value
				// this extends the last bin uniformly to 180 degree differences, TODO confer with Biasi
				double passingRatio = bw2017_ss_passRatio.getY(bw2017_ss_passRatio.getClosestXIndex(diff));
				prob = Math.min(prob, passingRatioToProb(passingRatio));
			}
			return prob;
		}

		@Override
		public String getName() {
			return "BW17 SS AzChange";
		}

		public boolean isDirectional(boolean splayed) {
			return false;
		}

	}

	public static final double bw2017_mech_change_prob = 4d/75d;

	public static class BiasiWesnousky2017MechChangeProb implements JumpProbabilityCalc {

		public BiasiWesnousky2017MechChangeProb() {
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			double rake1 = jump.fromSection.getAveRake();
			double rake2 = jump.toSection.getAveRake();
			if ((float)rake1 == (float)rake2)
				// no mechanism change
				return 1d;
			RakeType type1 = RakeType.getType(rake1);
			RakeType type2 = RakeType.getType(rake2);
			if (type1 == type2)
				// no mechanism change
				return 1d;
			// only 4 of 75 ruptures had a mechanism change of any type
			// TODO consult Biasi
			return bw2017_mech_change_prob;
		}

		@Override
		public String getName() {
			return "BW17 MechChange";
		}

		public boolean isDirectional(boolean splayed) {
			return false;
		}
		
		public static void main(String[] args) throws IOException {
			List<DiscretizedFunc> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			EvenlyDiscretizedFunc distFunc = new EvenlyDiscretizedFunc(0d, 15d, 100);
			
			List<DistDependentJumpProbabilityCalc> jumpProbs = new ArrayList<>();
			jumpProbs.add(new HardcodedMechDistIndepJumpProb(1d, RakeType.REVERSE));
			jumpProbs.add(new HardcodedMechDistIndepJumpProb(1d, RakeType.NORMAL));
			jumpProbs.add(new HardcodedMechDistIndepJumpProb(1d, RakeType.RIGHT_LATERAL));
			jumpProbs.add(new BiasiWesnousky2016SSJumpProb());
			jumpProbs.add(new Shaw07JumpDistProb(1d, 1d));
			jumpProbs.add(new Shaw07JumpDistProb(1d, 2d));
			jumpProbs.add(new Shaw07JumpDistProb(1d, 3d));
			jumpProbs.add(new Shaw07JumpDistProb(1d, 4d));
			
			CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, jumpProbs.size()-1);
			
			for (int i=0; i<jumpProbs.size(); i++) {
				DistDependentJumpProbabilityCalc calc = jumpProbs.get(i);
				EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(distFunc.getMinX(), distFunc.getMaxX(), distFunc.size());
				func.setName(calc.getName());
				
				for (int j=0; j<func.size(); j++)
					func.set(j, calc.calcJumpProbability(func.getX(j)));
				
				funcs.add(func);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, cpt.getColor((float)i)));
			}
			
			PlotSpec spec = new PlotSpec(funcs, chars, "Jump Distance Models", "Jump Distance (km)", "Jump Probability");
			spec.setLegendInset(true);
			
			Range xRange = new Range(distFunc.getMinX(), distFunc.getMaxX());
			Range yRange = new Range(0d, 1d);
			
			HeadlessGraphPanel gp = PlotUtils.initHeadless();
			
			gp.drawGraphPanel(spec, false, false, xRange, yRange);
			
			File outputDir = new File("/tmp");
			String prefix = "jump_dist_models";
			
			PlotUtils.writePlots(outputDir, prefix, gp, 1000, 850, true, true, false);
			
			yRange = new Range(1e-4, 1);
			
			gp.drawGraphPanel(spec, false, true, xRange, yRange);
			
			PlotUtils.writePlots(outputDir, prefix+"_log", gp, 1000, 850, true, true, false);
		}

	}

}
