package org.opensha.nshmp2.calc;

import java.io.IOException;

/**
 * Manages the writing of {@code HazardCalcResult}s . Implementations of this
 * interface are not necessarily {@code java.io.Writer}s, although they may make
 * use of one internally.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public interface HazardResultWriter  {

	/**
	 * Writes a {@code HazardResult} throwing any exceptions encountered
	 * @param result to write
	 * @throws IOException if error encountered
	 */
	public void write(HazardResult result) throws IOException;
	
	/**
	 * Flushes and closes any resources that may have been used, throwing any
	 * exceptions encountered
	 * @throws IOException
	 */
	public void close() throws IOException;
	
	
//	private static final Joiner J = Joiner.on(',').useForNull(" ");
//	private BlockingQueue<HazardCalcResult> queue;
//	private BufferedWriter writer;
//	
//	/**
//	 * Creates a new writer of {@code HazardCalcResult}s. 
//	 * 
//	 * @param queue to take results from 
//	 * @param out file
//	 * @param period for hazard data; used to retrieve IMLs
//	 * @throws IOException if {@code out} file initialization fails
//	 */
//	public HazardCalcWriter(BlockingQueue<HazardCalcResult> queue, File out, Period period) 
//			throws IOException {
//		checkNotNull(queue);
//		checkNotNull(out);
//		this.queue = queue;
//		Files.createParentDirs(out);
//		writer = Files.newWriter(out, Charsets.US_ASCII);
//		writeHeader(period);
//	}
//	
//	/**
//	 * Closes the streams used by this class.
//	 */
//	public void close() {
//		Flushables.flushQuietly(writer);
//		Closeables.closeQuietly(writer);
//	}
//	
//	@Override
//	public Void call() throws Exception {
//		while (true) write(queue.take());
//	}
//
//	/*
//	 * Write a result.
//	 */
//	private void write(HazardCalcResult result) throws IOException {
//		String resultStr = formatResult(result);
//		writer.write(resultStr);
//		writer.newLine();
//	}
//	
//	/*
//	 * Format a result.
//	 */
//	private static String formatResult(HazardCalcResult result) {
//		List<String> dat = Lists.newArrayList();
//		Location loc = result.location();
//		dat.add(Double.toString(loc.getLatitude()));
//		dat.add(Double.toString(loc.getLongitude()));
//		for (Point2D p : result.curve()) {
//			dat.add(Double.toString(p.getY()));
//		}
//		return J.join(dat);
//	}
//	
//	private void writeHeader(Period p) throws IOException {
//		List<String> headerVals = Lists.newArrayList();
//		headerVals.add("lat");
//		headerVals.add("lon");
//		for (Double d : p.getIMLs()) {
//			headerVals.add(d.toString());
//		}
//		writer.write(J.join(headerVals));
//		writer.newLine();
//	}
//
}
