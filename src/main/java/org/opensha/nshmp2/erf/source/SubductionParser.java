package org.opensha.nshmp2.erf.source;

import static org.opensha.nshmp2.util.NSHMP_Utils.*;
import static org.opensha.nshmp2.util.SourceRegion.*;
import static org.opensha.nshmp2.util.SourceType.*;

import java.io.File;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.geo.Location;
import org.opensha.nshmp2.util.FaultType;
import org.opensha.nshmp2.util.FocalMech;
import org.opensha.nshmp2.util.NSHMP_Utils;
import org.opensha.nshmp2.util.SourceIMR;
import org.opensha.nshmp2.util.SourceRegion;
import org.opensha.nshmp2.util.SourceType;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 * 2008 NSHMP subduction source parser.
 */
public class SubductionParser {

	private Logger log;

	SubductionParser(Logger log) {
		this.log = log;
	}

	SubductionERF parse(SourceFile sf) {
		List<SubductionSource> srcList = Lists.newArrayList();

		String name = sf.getName();
		SourceRegion srcRegion = sf.getRegion();
		SourceType srcType = sf.getType();
		SourceIMR srcIMR = SourceIMR.imrForSource(srcType, srcRegion, name, null);
		double srcWt = sf.getWeight();
		
		List<String> dat = sf.readLines();

		Iterator<String> srcLines = dat.iterator();
		skipHeader(srcLines);
		
		double rMax = readDouble(srcLines.next(), 1);

		while (srcLines.hasNext()) {
			// NSHMP subduction sources are processed by two differnt versions
			// of hazSUBXnga; one assumes a single mfd, the other reads the
			// number of mfd models
			String[] fltDat = StringUtils.split(srcLines.next());
			SubductionSource ss = new SubductionSource();
			ss.file = sf;
			ss.mfds = Lists.newArrayList();
			ss.type = FaultType.typeForID(readInt(fltDat, 0));
			ss.mech = FocalMech.typeForID(readInt(fltDat, 1));
			try {
				// hazSUBXngatest: read a 3rd value for mfd count
				ss.nMag = readInt(fltDat, 2);
				ss.name = StringUtils.join(fltDat, ' ', 3, fltDat.length);
			} catch (NumberFormatException nfe) {
				// hazSUBXnga: if can't read 3rd int, set name and nMag to 1
				ss.nMag = 1;
				ss.name = StringUtils.join(fltDat, ' ', 2, fltDat.length);
			}

			List<String> mfdSrcDat = readLines(srcLines, ss.nMag);
			generateMFDs(ss, mfdSrcDat);
			generateTraces(srcLines, ss);
			toLog(log, ss);
			srcList.add(ss);
		}
		return new SubductionERF(name, srcList, srcRegion, srcIMR, srcWt, rMax);
	}

	private void generateMFDs(SubductionSource ss, List<String> lines) {
		// for 2008 NSHMP all sub sources are entered as floating GR, however
		// any M8.8 or greater events are rupture filling, pseudo-char
		for (String line : lines) {
			GR_Data gr = new GR_Data(line, SUBDUCTION);
			if (gr.nMag > 1 && gr.mMin < 8.8) ss.floats = true;

			// TODO clean
//			double mMax = gr.mMin + (gr.nMag - 1) * gr.dMag;
//			GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(
//				gr.bVal, 1.0, gr.mMin, mMax, gr.nMag);
//			
//			double mMinRate = gr.weight * Math.pow(10, gr.aVal - gr.bVal * gr.mMin);
//			System.out.println(gr.aVal+" "+gr.bVal+" "+gr.mMin+" ");
//			System.out.println(mMinRate);
//			mfd.scaleToIncrRate(0, mMinRate);

			double tmr = totalMoRate(gr.mMin, gr.nMag, gr.dMag, gr.aVal, gr.bVal);
			GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(
				gr.mMin, gr.nMag, gr.dMag);
			// set total moment rate
			mfd.setAllButTotCumRate(gr.mMin, gr.mMin + (gr.nMag - 1) * gr.dMag,
				gr.weight * tmr, gr.bVal);

			ss.mfds.add(mfd);
			if (log.isLoggable(Level.FINE)) {
				log.fine(new StringBuilder().append(IOUtils.LINE_SEPARATOR)
					.append("GR MFD: ").append(IOUtils.LINE_SEPARATOR)
					.append(mfd.getMetadataString()).toString());
			}
		}
	}
	
	private void generateTraces(Iterator<String> it, SubductionSource ss) {
		int upperTraceLen = readInt(it.next(), 0);
		ss.trace = generateTrace(it, upperTraceLen, ss.name + " Upper Trace");
		int lowerTraceLen = readInt(it.next(), 0);
		ss.lowerTrace = generateTrace(it, lowerTraceLen, ss.name + " Lower Trace");
		if (log.isLoggable(Level.FINE)) {
			log.fine(IOUtils.LINE_SEPARATOR + ss.trace.toString());
			log.fine(IOUtils.LINE_SEPARATOR + ss.lowerTrace.toString());
		}
	}
	
	private static FaultTrace generateTrace(Iterator<String> it, int traceCount,
			String name) {
		FaultTrace trace = new FaultTrace(name);
		List<String> traceDat = readLines(it, traceCount);
		for (String ptDat : traceDat) {
			String[] locDat = StringUtils.split(ptDat);
			trace.add(new Location(readDouble(locDat, 0),
				readDouble(locDat, 1), readDouble(locDat, 2)));
		}
		return trace;
	}

	private static void skipHeader(Iterator<String> it) {
		int numSta = readInt(it.next(), 0); // grid of sites or station list
		// skip num station lines or lat lon bounds (2 lines)
		Iterators.advance(it, (numSta > 0) ? numSta : 2);
		it.next(); // site data (Vs30)
		int nP = readInt(it.next(), 0); // num periods
		for (int i = 0; i < nP; i++) {
			it.next(); // period
			it.next(); // out file
			int nAR = readInt(it.next(), 0); // num atten. rel.
			Iterators.advance(it, nAR); // atten rel
			it.next(); // num ground motion values
			it.next(); // ground motion values
		}
		it.next(); // discretization
//		it.next(); // distance sampling to fault and max distance
	}
	
	
	
	static void toLog(Logger log, SubductionSource ss) {
		if (log.isLoggable(Level.INFO)) {
			log.info(IOUtils.LINE_SEPARATOR + ss.toString());
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Logger log = NSHMP_Utils.logger();
		Level level = Level.FINE;
		log.setLevel(level);
		for (Handler h : NSHMP_Utils.logger().getHandlers()) {
			h.setLevel(level);
		}

		// log.setLevel(Level.FINE);
		// log date and class as these are suppressed by custom formatter
		log.info((new Date()) + " " + FaultParser.class.getName());

		SubductionParser dev = new SubductionParser(log);

//		SourceFile sf = SourceFileMgr.get(CA, FAULT, "bFault.gr.in").get(0);
		SourceFile sf = SourceMgr.get(CASC, SUBDUCTION, "cascadia.bot.8082.in").get(0);
//		SourceFile sf = SourceFileMgr.get(CASC, SUBDUCTION, "cascadia.bot.9pm.in").get(0);
//		SourceFile sf = SourceFileMgr.get(CA, FAULT, "aFault_unseg.in").get(0);

//		File f = FileUtils.toFile(CEUSdev.class.getResource(srcPath));

		log.info("Source: " + sf.getName());
		SubductionERF erf = dev.parse(sf);

//		System.out.println("NumSrcs: " + erf.getNumSources());
		for (ProbEqkSource source : erf) {
			((SubductionSource) source).init();
			System.out.println("Source: " + source.getName());
			System.out.println("  size: " + source.getNumRuptures());
		}
	}


}
