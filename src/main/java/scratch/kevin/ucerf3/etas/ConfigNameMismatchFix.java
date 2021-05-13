package scratch.kevin.ucerf3.etas;

import java.io.File;
import java.io.IOException;

import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;

public class ConfigNameMismatchFix {

	public static void main(String[] args) throws IOException {
//		File mainDir = new File("/home/kevin/git/ucerf3-etas-results");
		File mainDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations");
		if (args.length > 0)
			mainDir = new File(args[0]);
		String nameContains = null;
		if (args.length > 1)
			nameContains = args[1];
		
		for (File subDir : mainDir.listFiles()) {
			File confFile = new File(subDir, "config.json");
			if (!confFile.exists())
				continue;
			String dirName = subDir.getName();
			if (nameContains != null && !dirName.contains(nameContains))
				continue;
			ETAS_Config config = ETAS_Config.readJSON(confFile, false);
			String confDirName = config.getOutputDir().getName();
			if (!dirName.equals(confDirName)) {
				System.out.println("Found mismatch!");
				System.out.println("\tDir name: "+dirName);
				System.out.println("\tCof dir: "+confDirName);
				config.setOutputDir(new File(config.getOutputDir().getPath().replace(confDirName, dirName)));
				config.writeJSON(confFile);
			}
		}
	}

}
