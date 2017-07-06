package org.opensha.commons.hpc.mpj.taskDispatch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Class to read in the log file from a MPJTaskCalculator run and print stats on task runtime
 * @author kevin
 *
 */
public class MPJTaskLogStatsGen {

	public static void main(String[] args) throws IOException {
		Preconditions.checkArgument(args.length == 1,
				"USAGE: "+ClassUtils.getClassNameWithoutPackage(MPJTaskLogStatsGen.class)+" <log-file>");
		
		File logFile = new File(args[0]);
		Preconditions.checkArgument(logFile.exists(), "Log file doesn't exist: %s", logFile.getAbsolutePath());
		
		if (logFile.isDirectory()) {
			// look for log file
			File[] files = logFile.listFiles();
			Arrays.sort(files, new FileNameComparator());
			File match = null;
			for (int i=files.length; --i>=0;) {
				if (files[i].getName().contains(".pbs.o")) {
					match = files[i];
					break;
				}
			}
			Preconditions.checkState(match != null, "No *.pbs.o* output file found in directory: %s", logFile.getAbsolutePath());
			System.out.println("Found match in directory: "+match.getAbsolutePath());
			logFile = match;
		}
		
		BufferedReader read = new BufferedReader(new FileReader(logFile), 81920);
		
		Map<Node, Node> nodeSet = Maps.newHashMap();
		Map<Node, Date> lastHeardFromMap = Maps.newHashMap();
		Map<Node, List<CalcBatch>> nodeBatches = Maps.newHashMap();
		
		
		// used to detect midnight transitions
		Date prevDate = null;
		
		Date firstDate = null;
		
		int numTasks = -1;
		int numLeft = -1;
		int prevDispatch = 0;
		int numDone = 0;
		
		boolean nodeZeroDirect = false;
		
//		String line;
//		while ((line = read.readLine()) != null) {
		for (String line : new LogFileIterable(read)) {
			if (line.contains("DispatcherThread]:")) {
				if (line.contains("getting batch with ")) {
					line = line.substring(line.indexOf("]:")+2, line.indexOf(" left"));
					String[] split = line.split(" ");
					try {
						numLeft = Integer.parseInt(split[split.length-1]);
					} catch (NumberFormatException e) {
						System.err.println("Bad num left parse: "+e.getMessage());
					}
					if (numTasks < 0)
						numTasks = numLeft; 
				} else if (line.contains("returning batch of size:")) {
					String[] split = line.trim().split(" ");
					try {
						prevDispatch = Integer.parseInt(split[split.length-1]);
						// first states num left, then removes and returns, so this updates to actual post dispatch count
						numLeft -= prevDispatch;
					} catch (NumberFormatException e) {
						System.err.println("Bad num dispatch size parse: "+e.getMessage());
					}
				}
			}
			Node node = parseNodeLine(line);
			if (node == null)
				continue;
			
			if (nodeSet.containsKey(node)) {
				// don't want duplicates in memory, use the one we already have
				node = nodeSet.get(node);
			} else {
				nodeSet.put(node, node);
				nodeBatches.put(node, new ArrayList<MPJTaskLogStatsGen.CalcBatch>());
			}
			
			Date date = parseDate(line, prevDate);
			if (date == null)
				continue;
			if (firstDate == null)
				firstDate = date;
			
			lastHeardFromMap.put(node, date);
			
			List<CalcBatch> batches = nodeBatches.get(node);
			
			if (line.contains("receiving batch of length")) {
				// new batch
				batches.add(parseBatchLine(line, node, date));
//			} else if (!nodeZeroDirect && line.contains("getting next batch directly")) {
//				nodeZeroDirect = true;
//			} else if (nodeZeroDirect && node.getProcessNum() == 0) {
//				boolean newCalc = line.contains("calculating batch");
//				boolean done = line.contains("DONE!");
//				if ((newCalc && prevDispatch > 0) || done) {
//					if (!batches.isEmpty()) {
//						CalcBatch batch = getLastInProgress(batches);
//						if (batch != null) {
//							batch.setEndDate(date);
//							numDone += batch.size;
//						}
//					}
//				}
//				if (newCalc)
//					batches.add(new CalcBatch(prevDispatch, node, date));
			} else if (line.contains("sending READY message") || line.contains("getting next batch directly")) {
				if (!batches.isEmpty()) {
					// finished a batch
					CalcBatch batch = getLastInProgress(batches);
					if (batch != null) {
						batch.setEndDate(date);
						numDone += batch.size;
					}
				}
			}
			
			prevDate = date;
		}
		
		read.close();
		
		System.out.println("Done parsing log");
		
		List<Node> nodes = Lists.newArrayList(nodeSet.keySet());
		Collections.sort(nodes, new Comparator<Node>() {

			@Override
			public int compare(Node o1, Node o2) {
				return new Integer(o1.getProcessNum()).compareTo(o2.getProcessNum());
			}
		});
		
		MinMaxAveTracker allDurationTrack = new MinMaxAveTracker();
		MinMaxAveTracker allBatchDurationTrack = new MinMaxAveTracker();
		
		int[] numBatches = new int[nodes.size()];
		int[] numBatchesCompleted = new int[nodes.size()];
		int[] numTasksAssigned = new int[nodes.size()];
		int[] numTasksCompleted = new int[nodes.size()];
		double[] averages = new double[nodes.size()];
		
		int numNodesRunning = 0;
		MinMaxAveTracker runningBatchSizeTrack = new MinMaxAveTracker();
		
		System.out.println();
		for (int i=0; i<nodes.size(); i++) {
			Node node = nodes.get(i);
			List<CalcBatch> batches = nodeBatches.get(node);
			
			numBatches[i] = batches.size();
			
			MinMaxAveTracker track = new MinMaxAveTracker();
			
			int runningBatchSize = 0;
			
			for (CalcBatch batch : batches) {
				if (batch.isCompleted()) {
					numBatchesCompleted[i]++;
					numTasksCompleted[i] += batch.getSize();
					if (batch.getSize() > 0) {
						double each = batch.getDurationMillisEach();
						track.addValue(each);
						allDurationTrack.addValue(each);
						allBatchDurationTrack.addValue(batch.getDurationMillis());
					}
				} else {
					runningBatchSize += batch.size;
				}
				numTasksAssigned[i] += batch.getSize();
			}
			
			if (runningBatchSize > 0) {
				numNodesRunning++;
				runningBatchSizeTrack.addValue(runningBatchSize);
			}
			
			if (numTasksCompleted[i] > 0)
				averages[i] = track.getAverage();
			else
				averages[i] = Double.NaN;
		}
		
		String countStr = "";
		for (int i=0; i<(max(numTasksAssigned)+"").length(); i++)
			countStr += "0";
		DecimalFormat nodeTaskDF = new DecimalFormat(countStr);
		countStr = "";
		for (int i=0; i<(max(numBatches)+"").length(); i++)
			countStr += "0";
		DecimalFormat nodeBatchDF = new DecimalFormat(countStr);
		
		Date curDate;
		try {
			curDate = MPJTaskCalculator.df.parse(MPJTaskCalculator.df.format(new Date()));
		} catch (ParseException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		while (curDate.getTime() < prevDate.getTime())
			curDate = new Date(curDate.getTime() + MILLISEC_PER_DAY);

		double longestPeriod = 0d;
		Node longestPeriodNode = null;
		double longestPeriod2 = 0d;
		Node shortestPeriodNode = null;
		double shortestPeriod = Double.POSITIVE_INFINITY;
		
		for (int i=0; i<nodes.size(); i++) {
			Node node = nodes.get(i);
			double lastContactMillis = timeDeltaMillis(lastHeardFromMap.get(node), prevDate);
			if (lastContactMillis > longestPeriod) {
				longestPeriod = lastContactMillis;
				longestPeriodNode = node;
			}
			double lastContactMillis2 = timeDeltaMillis(lastHeardFromMap.get(node), curDate);
			if (lastContactMillis2 > longestPeriod2)
				longestPeriod2 = lastContactMillis2;
			
			if (lastContactMillis2 < shortestPeriod) {
				shortestPeriod = lastContactMillis2;
				shortestPeriodNode = node;
			}
			
			String runningAdd = "";
			if (numTasksCompleted[i] < numTasksAssigned[i])
				runningAdd = "\tRUNNING: "+(numTasksAssigned[i] - numTasksCompleted[i]);
			System.out.println(node+":\tlastContact: "+smartTimePrint(lastContactMillis)+"\t("+smartTimePrint(lastContactMillis2)+")"
					+"\tbatches: "+nodeBatchDF.format(numBatchesCompleted[i])+"/"+nodeBatchDF.format(numBatches[i])
					+"\ttasks: "+nodeTaskDF.format(numTasksCompleted[i])+"/"+nodeTaskDF.format(numTasksAssigned[i])
					+"\tavg: "+smartTimePrint(averages[i])+runningAdd);
		}
		
		System.out.println();
		System.out.println("Longest current time without contact: "+longestPeriodNode+": "
				+smartTimePrint(longestPeriod)+" ("+smartTimePrint(longestPeriod2)+")");
		System.out.println("Most recent contact from current date: "+shortestPeriodNode+": "
				+smartTimePrint(shortestPeriod));
		System.out.println();
		
		int numDispatched = numTasks - numLeft;
		double percentDispatched = (double)numDispatched/(double)numTasks;
		double percentDone = (double)numDone/(double)numTasks;
		System.out.println(numDispatched+"/"+numTasks+" ("+percentDF.format(percentDispatched)+") "
				+"dispatched ("+(numTasks-numDispatched)+" left)");
		System.out.println(numDone+"/"+numTasks+" ("+percentDF.format(percentDone)+") "
				+"completed ("+(numTasks-numDone)+" left)");
		int inProcess = numDispatched - numDone;
		System.out.println(inProcess+" in process on "+numNodesRunning+"/"+nodes.size()
			+" nodes, batch sizes ["+(int)runningBatchSizeTrack.getMin()+" "+(int)runningBatchSizeTrack.getMax()+"]");
		
		System.out.println("Calc durations (note: threading effects ignored):");
		System.out.println("\tRange: ["+smartTimePrint(allDurationTrack.getMin())
				+" "+smartTimePrint(allDurationTrack.getMax())+"]");
		System.out.println("\tAverage: "+smartTimePrint(allDurationTrack.getAverage()));
		System.out.println("\tTotal: "+smartTimePrint(allDurationTrack.getAverage()*allDurationTrack.getNum()));
		
		System.out.println("Batch durations:");
		System.out.println("\tRange: ["+smartTimePrint(allBatchDurationTrack.getMin())
				+" "+smartTimePrint(allBatchDurationTrack.getMax())+"]");
		System.out.println("\tAverage: "+smartTimePrint(allBatchDurationTrack.getAverage()));
		System.out.println("\tTotal: "+smartTimePrint(allBatchDurationTrack.getAverage()*allBatchDurationTrack.getNum()));
		
		System.out.println();
		boolean done = numDone == numTasks;
		System.out.println("DONE? "+done);
		
		long avgMillis = (long)allDurationTrack.getAverage();
		
		double totDuration = timeDeltaMillis(firstDate, prevDate);
		double nowDuration = timeDeltaMillis(firstDate, curDate);
		System.out.println();
		System.out.println("Current duration: "+smartTimePrint(totDuration)+" ("+smartTimePrint(nowDuration)+")");
		System.out.println("Total rate: "+smartRatePrint(numDone, totDuration));
		
		if (numDone == 0 && numDispatched > 0) {
			// assume that all running end right now
			double tasksPerMilli = numDispatched/nowDuration;
			double millisLeft = (numTasks - numDispatched)/tasksPerMilli;
			System.out.println();
			System.out.println("None done, estimates if all currently dispatched completed now:");
			System.out.println("\tTotal rate: <"+smartRatePrint(numDispatched, nowDuration));
			System.out.println("\tTime left: >"+smartTimePrint(millisLeft));
			System.out.println("\tTot duration: >"+smartTimePrint(millisLeft+nowDuration));
		}
		
		if (!done && numDone > 0 && avgMillis > 10 && numTasks > 0 && numDispatched > 0) {
			System.out.println();
			System.out.println("Estimating time left, assuming average task runtime & ideal dispatching.");
			
			// estimate time left
			
			// simulation based approach using actual time slots
			// assume each task takes the average time

			long maxSimMillis = 10*MILLISEC_PER_DAY;
			
			System.out.println("Estimating time left from last known date ("+MPJTaskCalculator.df.format(prevDate)+"):");
			double timeLeftFromLast = estimateTimeLeft(prevDate, numDone, numDispatched, numTasks, nodeBatches,
					avgMillis, maxSimMillis);
			if (timeLeftFromLast >= maxSimMillis)
				System.out.println("\tTime left: >= "+smartTimePrint(timeLeftFromLast));
			else
				System.out.println("\tTime left: "+smartTimePrint(timeLeftFromLast));
			System.out.println("Estimating time left from current date ("+MPJTaskCalculator.df.format(curDate)+"):");
			double timeLeftFromCur = estimateTimeLeft(curDate, numDone, numDispatched, numTasks, nodeBatches,
					avgMillis, maxSimMillis);
			if (timeLeftFromLast >= maxSimMillis)
				System.out.println("\tTime left: >= "+smartTimePrint(timeLeftFromCur));
			else
				System.out.println("\tTime left: "+smartTimePrint(timeLeftFromCur));
			System.out.println("Estimated total duration: "+smartTimePrint(totDuration+timeLeftFromLast));
		}
	}
	
	private static class LogFileIterable implements Iterable<String> {
		
		private BufferedReader read;
		
		public LogFileIterable(BufferedReader read) {
			this.read = read;
		}

		@Override
		public Iterator<String> iterator() {
			return new LogFileIterator(read);
		}
		
	}
	
	private static class LogFileIterator implements Iterator<String> {
		
		private BufferedReader read;
		private LinkedList<String> queue;
		
		final int buffer_size = 10;
		
		public LogFileIterator(BufferedReader read) {
			this.read = read;
			
			queue = new LinkedList<String>();
		}
		
		private void checkFillQueue() {
			if (queue.isEmpty()) {
				while (queue.size() < buffer_size) {
					try {
						String line = read.readLine();
						if (line == null)
							// we're done
							break;
						List<String> extraLines = null;
						while (line.lastIndexOf('[') > 0) {
							// have an extra line that was merged in with the previous
							int ind = line.lastIndexOf('[');
							String subLine = line.substring(ind);
							if (subLine.contains("]:")) {
								// it's a valid extra log line
								
								// trim away the extra part
								line = line.substring(0, ind);
								if (extraLines == null)
									extraLines = Lists.newArrayList();
								extraLines.add(0, subLine);
							} else {
								break;
							}
						}
						queue.add(line);
						if (extraLines != null)
							queue.addAll(extraLines);
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			}
		}

		@Override
		public boolean hasNext() {
			checkFillQueue();
			return !queue.isEmpty();
		}

		@Override
		public String next() {
			checkFillQueue();
			return queue.pop();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
		
	}
	
	private static int max(int[] vals) {
		int max = 0;
		for (int val : vals)
			if (val > max)
				max = val;
		return max;
	}
	
	private static double estimateTimeLeft(Date simStartDate, int numDone, int numDispatched, int numTasks,
			Map<Node, List<CalcBatch>> nodeBatches, long avgMillis, long maxSimMillis) {
		
		// clone batches lists since we'll be messing with them
		Map<Node, List<CalcBatch>> newNodeBatches = Maps.newHashMap();
		List<Node> nodes = Lists.newArrayList();
		for (Node node : nodeBatches.keySet()) {
			nodes.add(node);
			List<CalcBatch> newBatches = Lists.newArrayList();
			List<CalcBatch> batches = nodeBatches.get(node);
			
			for (CalcBatch batch : batches) {
				CalcBatch newBatch = new CalcBatch(batch.size, batch.node, batch.startDate);
				if (batch.isCompleted())
					newBatch.setEndDate(batch.endDate);
				newBatches.add(newBatch);
			}
			newNodeBatches.put(node, newBatches);
		}
		
		nodeBatches = newNodeBatches;
		
		long timeStepMillis = 1000l*60l; // step forward in minutes
		if (timeStepMillis > avgMillis)
			timeStepMillis = avgMillis;
		
		Date curDate = new Date(simStartDate.getTime());
		
		while (numDone < numTasks) {
			if (timeDeltaMillis(simStartDate, curDate) > maxSimMillis) {
				System.out.println("Bailing on time estimate, too long. Dispatched: "
						+numDispatched+"/"+numTasks+", completed "+numDone+"/"+numTasks);
				break;
			}
			// set end date on any in progress
			for (Node node : nodes) {
				List<CalcBatch> batches = nodeBatches.get(node);
				CalcBatch curBatch = getLastInProgress(batches);
				if (curBatch != null) {
					Date start = curBatch.startDate;
					Date end = new Date(start.getTime() + avgMillis*curBatch.size);
					if (end.getTime() <= curDate.getTime()) {
						numDone += curBatch.size;
						curBatch.setEndDate(end);
					}
				}
			}
			
			// now see if we can send any new batches out
			for (Node node : nodes) {
				List<CalcBatch> batches = nodeBatches.get(node);
				CalcBatch curBatch = getLastInProgress(batches);
				if (curBatch == null && numDispatched < numTasks) {
					Date startDate;
					if (batches.isEmpty())
						startDate = curDate;
					else
						startDate = batches.get(batches.size()-1).endDate;
					// we can assign a new one
					batches.add(new CalcBatch(1, node, startDate));
					numDispatched++;
				}
			}
			
			curDate = new Date(curDate.getTime()+timeStepMillis);
		}
		
		double deltaMillis = timeDeltaMillis(simStartDate, curDate);
		return deltaMillis;
	}
	
	private static CalcBatch getLastInProgress(List<CalcBatch> batches) {
		if (batches.isEmpty())
			return null;
		CalcBatch batch = batches.get(batches.size()-1);
		if (batch.isCompleted())
			return null;
		return batch;
	}
	
	private static double timeDeltaMillis(Date start, Date end) {
		return end.getTime() - start.getTime();
	}
	
	private static class Node {
		private final int processNum;
		private final String hostName;
		
		private Node(int processNum, String hostName) {
			this.processNum = processNum;
			this.hostName = hostName;
		}

		public int getProcessNum() {
			return processNum;
		}

		public String getHostName() {
			return hostName;
		}
		
		@Override
		public String toString() {
			String str = "Process "+getProcessNum();
			if (getHostName() != null)
				str += " ("+getHostName()+")";
			return str;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((hostName == null) ? 0 : hostName.hashCode());
			result = prime * result + processNum;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Node other = (Node) obj;
			if (hostName == null) {
				if (other.hostName != null)
					return false;
			} else if (!hostName.equals(other.hostName))
				return false;
			if (processNum != other.processNum)
				return false;
			return true;
		}
	}
	
	public static Node parseNodeLine(String line) {
		if (!line.startsWith("["))
			return null;
		int endBracketIndex = line.indexOf("]:");
		if (endBracketIndex < 0)
			return null;
		line = line.substring(0, endBracketIndex);
		if (!line.contains("Process"))
			return null;
		String[] split = line.split(" ");
		if (split.length < 2)
			return null;
		// last token is process num
		int processNum;
		try {
			processNum = Integer.parseInt(split[split.length-1]);
		} catch (NumberFormatException e) {
			return null;
		}
		
		String hostName = null;
		if (split[1].startsWith("(") && split[1].endsWith(")")) {
			// we have hostNames
			hostName = split[1].substring(1);
			hostName = hostName.substring(0, hostName.length()-1);
		}
		
		return new Node(processNum, hostName);
	}
	
	private static class CalcBatch {
		private final int size;
		private final Node node;
		private final Date startDate;
		private Date endDate;
		
		private CalcBatch(int size, Node node, Date startDate) {
			super();
			this.size = size;
			this.node = node;
			this.startDate = startDate;
		}
		
		public void setEndDate(Date endDate) {
			this.endDate = endDate;
		}
		
		public long getDurationMillis() {
			long dur = endDate.getTime() - startDate.getTime();
			Preconditions.checkState(dur >= 0);
			return dur;
		}
		
		public double getDurationMillisEach() {
			return (double)getDurationMillis() / (double)size;
		}
		
		public boolean isCompleted() {
			return endDate != null;
		}
		
		public int getSize() {
			return size;
		}
	}
	
	private static final DecimalFormat timeDF = new DecimalFormat("0.00");
	private static final DecimalFormat percentDF = new DecimalFormat("0.00 %");
	
	public static String smartTimePrint(double millis) {
		if (Double.isNaN(millis))
			return "N/A";
		double secs = millis / 1000d;
		if (secs < 1d)
			return timeDF.format(millis)+" ms";
		double mins = secs / 60d;
		if (mins < 1d)
			return timeDF.format(secs)+" s";
		double hours = mins / 60d;
		if (hours < 1d)
			return timeDF.format(mins)+" m";
		double days = hours / 24;
		if (days < 1)
			return timeDF.format(hours)+" h";
		return timeDF.format(days)+" d";
	}
	
	public static String smartRatePrint(double numDone, double millis) {
		if (Double.isNaN(millis))
			return "N/A";
		double rate = numDone/millis;
		if (rate > 1d)
			return timeDF.format(rate)+" tasks/ms";
		double secs = millis / 1000d;
		rate = numDone/secs;
		if (rate > 1d)
			return timeDF.format(rate)+" tasks/s";
		double mins = secs / 60d;
		rate = numDone/mins;
		if (rate > 1d)
			return timeDF.format(rate)+" tasks/m";
		double hours = mins / 60d;
		rate = numDone/hours;
		if (rate > 1d)
			return timeDF.format(rate)+" tasks/h";
		double days = hours / 24;
		rate = numDone/days;
		return timeDF.format(rate)+" task/d";
	}
	
	private static final String receive_message = "receiving batch of length";
	
	private static CalcBatch parseBatchLine(String line, Node node, Date date) {
		Preconditions.checkState(line.contains(receive_message));
		
		int size;
		try {
			size = Integer.parseInt(line.substring(line.indexOf(receive_message)+receive_message.length()).trim());
		} catch (NumberFormatException e) {
			System.err.println("Couldn't parse node size: "+e.getMessage());
			return null;
		}
		
		return new CalcBatch(size,  node, date);
	}
	
	private final static long MILLISEC_PER_DAY = 1000*60*60*24;
	private static final long HALF_DAY_MILLIS = MILLISEC_PER_DAY/2;
	
	private static Date parseDate(String line, Date prevDate) {
		line = line.substring(1).split(" ")[0];
		try {
			Date date =  MPJTaskCalculator.df.parse(line);
			if (prevDate != null) {
				// properly handle midnight, if this date is before, then add 24 hours (but make sure it's at least 12 hours before)
				
				while (date.getTime() < (prevDate.getTime() - HALF_DAY_MILLIS))
					date = new Date(date.getTime()+MILLISEC_PER_DAY);
			}
			return date;
		} catch (ParseException e) {
			return null;
		}
	}

}
