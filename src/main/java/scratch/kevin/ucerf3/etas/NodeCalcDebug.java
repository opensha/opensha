package scratch.kevin.ucerf3.etas;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskLogStatsGen;

public class NodeCalcDebug {

	public static void main(String[] args) throws IOException {
		File logFile = new File(args[0]);
		int node = Integer.parseInt(args[1]);
		
		BufferedReader read = new BufferedReader(new FileReader(logFile));
		
		Date prevDate = null;
		Map<Integer, Long> calcStartTimes = new HashMap<>();
		for (String line : new MPJTaskLogStatsGen.LogFileIterable(read)) {
			if (!line.contains("Process "+node+"]"))
				continue;
			if (line.contains("batch") || line.contains("binary") || line.contains("output"))
				continue;
			if (line.contains("]: calculating ")) {
				Date date = MPJTaskLogStatsGen.parseDate(line, prevDate);
				String split[] = line.trim().split(" ");
				int index = Integer.parseInt(split[split.length-1]);
				calcStartTimes.put(index, date.getTime());
				
				prevDate = date;
			} else if (line.contains("]: completed ")) {
				Date date = MPJTaskLogStatsGen.parseDate(line, prevDate);
				line = line.substring(line.indexOf("]: completed ")+("]: completed ").length()).trim();
				String split[] = line.split(" ");
				int index = Integer.parseInt(split[0]);
				Long startTime = calcStartTimes.get(index);
				if (startTime == null) {
					System.out.println(index+" ended but no start time");
				} else {
					double time = (double)(date.getTime() - startTime)/1000d;
					calcStartTimes.remove(index);
					String rupStr = line.substring(line.indexOf(index+"")+(index+"").length()).trim();
					System.out.println("index "+index+" took "+(float)time+" "+rupStr);
				}
				
				prevDate = date;
			}
		}
		
		read.close();
	}

}
