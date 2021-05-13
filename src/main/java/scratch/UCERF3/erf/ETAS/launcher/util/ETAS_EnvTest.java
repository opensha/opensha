package scratch.UCERF3.erf.ETAS.launcher.util;

import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public class ETAS_EnvTest {

	public static void main(String[] args) {
		System.out.println("Running ETAS_EnvTest java class");
		System.out.println("\tMaximum available memory to java (after overhead): "+ETAS_Launcher.getMaxMemMB()+" MB");
		String launcherVar = System.getenv("ETAS_LAUNCHER");
		System.out.println("\t$ETAS_LAUNCHER defined? "+(launcherVar != null)+", Value: "+launcherVar);
		System.out.println("ETAS_EnvTest DONE");
	}

}
