package scratch.kevin.ucerf3.etas.weeklyRuns;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.util.FileNameComparator;
import org.opensha.commons.util.FileUtils;

import com.google.common.base.Stopwatch;
import com.google.common.io.Files;

import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher.DebugLevel;

public class WeeklySpeedTests {

	public static void main(String[] args) throws IOException {
		File baseDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
				+ "2020_05_14-weekly-1986-present-full_td-kCOV1.5");
		
		int curBatch = 0;
		int delta = 5;
		DecimalFormat df = new DecimalFormat("000");
		
		List<String> batchNames = new ArrayList<>();
		List<String> dirNames = new ArrayList<>();
		List<Double> configSecs = new ArrayList<>();
		List<Double> launcherSecs = new ArrayList<>();
		List<Double> calcFirstSecs = new ArrayList<>();
		List<Double> calcSecondSecs = new ArrayList<>();
		
		while (true) {
			String batchName = "batch_"+df.format(curBatch);
			File batchDir = new File(baseDir, batchName);
			if (!batchDir.exists())
				break;
			System.gc();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
			System.out.println(batchName);
			File[] subDirs = batchDir.listFiles();
			Arrays.sort(subDirs, new FileNameComparator());
			batchNames.add(batchName);
			for (File dir : subDirs) {
				if (!dir.getName().startsWith("Start"))
					continue;
				System.out.println("\t"+dir.getName());
				dirNames.add(dir.getName());
				File configFile = new File(dir, "config.json");
				Stopwatch watch = Stopwatch.createStarted();
				ETAS_Config config = ETAS_Config.readJSON(configFile);
				watch.stop();
				File tempDir = Files.createTempDir();
				config.setOutputDir(tempDir);
				double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
				System.out.println("\t\tload config: "+(float)secs+" s");
				configSecs.add(secs);
				watch = Stopwatch.createStarted();
				ETAS_Launcher launcher = new ETAS_Launcher(config, true);
				launcher.getCombinedTriggers();
				watch.stop();
				secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
				System.out.println("\t\tbuild launcher: "+(float)secs+" s");
				launcherSecs.add(secs);
				launcher.setDebugLevel(DebugLevel.ERROR);
				watch = Stopwatch.createStarted();
				long[] seeds = new long[config.getNumSimulations()];
				for (int i=0; i<seeds.length; i++)
					seeds[i] = 123456789l;
				launcher.setRandomSeeds(seeds);
				launcher.calculateBatch(1, new int[] { 0 });
				watch.stop();
				secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
				System.out.println("\t\tcalc 0: "+(float)secs+" s");
				calcFirstSecs.add(secs);
				watch = Stopwatch.createStarted();
				launcher.calculateBatch(1, new int[] { 1 });
				watch.stop();
				secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
				System.out.println("\t\tcalc 1: "+(float)secs+" s");
				calcSecondSecs.add(secs);
				FileUtils.deleteRecursive(tempDir);
				break;
			}
			
			curBatch += delta;
		}
		
		for (int i=0; i<batchNames.size(); i++) {
			System.out.println(batchNames.get(i)+": "+dirNames.get(i));
			System.out.println("\tconfig: "+configSecs.get(i).floatValue());
			System.out.println("\tlauncher: "+launcherSecs.get(i).floatValue());
			System.out.println("\tcalc 0: "+calcFirstSecs.get(i).floatValue());
			System.out.println("\tcalc 1: "+calcSecondSecs.get(i).floatValue());
		}
	}

}
