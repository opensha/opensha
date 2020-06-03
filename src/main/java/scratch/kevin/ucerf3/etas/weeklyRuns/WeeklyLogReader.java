package scratch.kevin.ucerf3.etas.weeklyRuns;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.util.DataUtils;

import com.google.common.primitives.Doubles;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskLogStatsGen;

public class WeeklyLogReader {

	public static void main(String[] args) throws IOException {
		File baseDir = new File(args[0]);
		
		int curBatch = args.length > 1 ? Integer.parseInt(args[1]) : 0;
		int delta = args.length > 2 ? Integer.parseInt(args[2]) : 1;
		DecimalFormat df = new DecimalFormat("000");
		
		while (true) {
			String batchName = "batch_"+df.format(curBatch);
			File batchDir = new File(baseDir, batchName);
			if (!batchDir.exists())
				break;
			System.out.println(batchDir.getName());
			List<Double> times = new ArrayList<>();
			for (File file : batchDir.listFiles()) {
				if (file.getName().startsWith("batch_") && file.getName().contains(".slurm.o")) {
					System.out.println("parsing "+file.getName());
					times.addAll(loadDurations(file));
				}
			}
			if (times.isEmpty()) {
				System.out.println("\tno times parsed");
			} else {
				double[] timeArray = Doubles.toArray(times);
				double min = StatUtils.min(timeArray);
				double max = StatUtils.max(timeArray);
				double mean = StatUtils.mean(timeArray);
				double median = DataUtils.median(timeArray);
				System.out.println("\tparsed "+times.size());
				System.out.println("\t\tmean: "+(float)mean);
				System.out.println("\t\tmedian: "+(float)median);
				System.out.println("\t\trange: ["+(float)min+" "+(float)max+"]");
			}
			curBatch += delta;
		}
	}
	
	private static List<Double> loadDurations(File logFile) throws IOException {
		Date prevDate = null;
		Map<Integer, Long> calcStartTimes = new HashMap<>();
		BufferedReader read = new BufferedReader(new FileReader(logFile));
		int numDuplicates = 0;
		int numNoStarts = 0;
		List<Double> ret = new ArrayList<>();
		Map<Integer, List<Double>> nodeTimes = new HashMap<>();
		for (String line : new MPJTaskLogStatsGen.LogFileIterable(read)) {
			if (line.contains("batch") || line.contains("binary") || line.contains("output"))
				continue;
			if (line.contains("]: completed ") || line.contains("]: calculating ")) {
				Date date = MPJTaskLogStatsGen.parseDate(line, prevDate);
				String indexStr;
				if (line.contains("completed"))
					indexStr = line.substring(line.indexOf("completed")+("completed").length()).trim();
				else
					indexStr = line.substring(line.indexOf("calculating")+("calculating").length()).trim();
				int batchIndex = Integer.parseInt(indexStr.split(" ")[0]);
				int process = -1;
				if (line.contains("Process ")) {
					String pStr = line.substring(line.indexOf("Process ")+"Process ".length());
					pStr = pStr.substring(0, pStr.indexOf("]"));
					process = Integer.parseInt(pStr.trim());
				}
				if (line.contains("calculating")) {
					if (calcStartTimes.containsKey(batchIndex))
						numDuplicates++;
					calcStartTimes.put(batchIndex, date.getTime());
				} else {
					Long startTime = calcStartTimes.get(batchIndex);
					if (startTime == null) {
						numNoStarts++;
					} else {
						double time = (double)(date.getTime() - startTime)/1000d;
						ret.add(time);
						calcStartTimes.remove(batchIndex);
						if (process > 0) {
							List<Double> processTimes = nodeTimes.get(process);
							if (processTimes == null) {
								processTimes = new ArrayList<>();
								nodeTimes.put(process, processTimes);
							}
							processTimes.add(time);
						}
					}
				}
				
				prevDate = date;
			}
		}
		read.close();
		System.out.println("\tParsed "+ret.size()+" calculations ("
				+numDuplicates+" dups, "+calcStartTimes.size()+" orphans, "
				+numNoStarts+" outta-nowheres)");
		if (!nodeTimes.isEmpty()) {
			List<Integer> ids = new ArrayList<>(nodeTimes.keySet());
			Collections.sort(ids);
			System.out.println("\tNode times:");
			DecimalFormat df = new DecimalFormat("0.0");
			for (int process : ids) {
				List<Double> times = nodeTimes.get(process);
				double mean = StatUtils.mean(Doubles.toArray(times));
				System.out.println("\t\t"+process+":\t"+df.format(mean)+"\t("+times.size()+" sims)");
			}
		}
		return ret;
	}

}
