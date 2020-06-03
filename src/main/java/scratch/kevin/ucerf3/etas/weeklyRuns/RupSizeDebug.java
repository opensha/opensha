package scratch.kevin.ucerf3.etas.weeklyRuns;

import java.io.File;
import java.io.IOException;

import org.opensha.sha.faultSurface.RuptureSurface;

import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public class RupSizeDebug {

	public static void main(String[] args) throws IOException {
		File configFile = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
				+ "2020_05_14-weekly-1986-present-full_td-kCOV1.5/batch_059/"
				+ "Start2020_05_13_kCOV1p5_ComCatStitch/config.json");
		System.out.println("Loading config:");
		ETAS_Config config = ETAS_Config.readJSON(configFile);
		System.out.println("Building launcher");
		ETAS_Launcher launcher = new ETAS_Launcher(config, false);
		System.out.println("looping over ruptures");
		ETAS_Utils utils = new ETAS_Utils();
		FaultSystemSolutionERF fssERF = (FaultSystemSolutionERF)launcher.checkOutERF();
		int maxSize = 0;
		for (ETAS_EqkRupture rup : launcher.getTriggerRuptures()) {
			RuptureSurface surf = rup.getRuptureSurface();
			if (rup.getFSSIndex() >= 0)
				surf = utils.getRuptureSurfaceWithNoCreepReduction(rup.getFSSIndex(), fssERF, 0.05);
			int mySize = surf.getEvenlyDiscretizedNumLocs();
			if (mySize > maxSize) {
				System.out.println("New max of "+mySize+" locations: M"+(float)rup.getMag());
				maxSize = mySize;
			}
		}
		
	}

}
