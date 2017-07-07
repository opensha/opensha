package org.opensha.nshmp2.erf.source;

import static com.google.common.base.Preconditions.*;
import static org.opensha.nshmp2.util.NSHMP_Utils.*;
import static org.opensha.nshmp2.util.SourceRegion.*;
import static org.opensha.nshmp2.util.SourceType.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.Location;
import org.opensha.nshmp2.util.FaultType;
import org.opensha.nshmp2.util.FocalMech;
import org.opensha.nshmp2.util.NSHMP_Utils;
import org.opensha.nshmp2.util.SourceIMR;
import org.opensha.nshmp2.util.SourceRegion;
import org.opensha.nshmp2.util.SourceType;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.magdist.GaussianMagFreqDist;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.collect.Collections2;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 * 2008 NSHMP fault source parser.
 */
public class FaultParser {

	static final int TRUNC_TYPE = 2;
	static final int TRUNC_LEVEL = 2;

	private Logger log;

	FaultParser(Logger log) {
		this.log = log;
	}

	FaultERF parse(SourceFile sf) {
		List<FaultSource> srcList = Lists.newArrayList();

		String name = sf.getName();
		SourceRegion srcRegion = sf.getRegion();
		SourceType srcType = sf.getType();
		SourceIMR srcIMR = SourceIMR.imrForSource(srcType, srcRegion, name, null);
		double srcWt = sf.getWeight();
		
		List<String> dat = sf.readLines();

		// index from which fault source name starts; general WUS case is 4
		int srcNameIdx = (srcRegion == CA || srcRegion == CEUS) ? 3 : 4;

		Iterator<String> srcLines = dat.iterator();
		
		skipHeader1(srcLines);
		double rMax = readDouble(srcLines.next(), 1);
		skipHeader2(srcLines);

		// load magnitude uncertainty data
		MagData md = new MagData(readLines(srcLines, 4));
		md.toLog(log);

		while (srcLines.hasNext()) {
			FaultSource fs = createSource(srcLines.next(), sf, srcNameIdx);

			// read source magnitude data and build mfds; due to peculiarities
			// of how mfd's are handled with different uncertainty settings
			// under certain conditions (e.g. when mMax < 6.5), cloned magDat
			// are used so that any changes to magDat do not percolate to
			// other sources

			List<String> mfdSrcDat = readLines(srcLines, fs.nMag);

			generateMFDs(fs, mfdSrcDat, md.clone());
			generateFaultTrace(srcLines, fs, log);
			toLog(log, fs);

			if (fs.mfds.size() == 0) {
				StringBuilder sb = new StringBuilder()
					.append("Source with no mfds");
				appendFaultDat(sb, fs);
				log.warning(sb.toString());

				// TODO recheck; probably should 'continue' here
			}
			srcList.add(fs);
			//fs.init();
		}
		// KLUDGY
		if (name.contains("3dip")) cleanStrikeSlip(srcList);
		
		return new FaultERF(name, srcList, srcRegion, srcIMR, srcWt, rMax);
	}

	private void generateMFDs(FaultSource fs, List<String> lines,
			MagData md) {
		switch (fs.type) {
			case CH:
				buildMFDs_CH(lines, md, fs);
				break;
			case GR:
				buildMFDs_GR(lines, md, fs);
				break;
			case GRB0:
				buildMFDs_GRB0(lines, md, fs);
				break;
		}
	}

	private void buildMFDs_CH(List<String> lines, MagData md,
			FaultSource fs) {
		fs.floats = false;
		for (String line : lines) {
			CH_Data chData = new CH_Data(line);
			chBuilder(chData, md, fs);
		}
	}

	private void buildMFDs_GR(List<String> lines, MagData md,
			FaultSource fs) {
		fs.floats = true;
		List<GR_Data> grData = new ArrayList<GR_Data>();
		for (String line : lines) {
			grData.add(new GR_Data(line, fs, log));
		}

		// check mMax
		for (GR_Data gr : grData) {
			if (gr.hasMagExceptions(log, fs, md)) {
				md.suppressUncertainty();
			}
		}

		// build all GR_Data
		for (GR_Data gr : grData) {
			if (gr.nMag > 1) {
				grBuilder(gr, md, fs);
			} else {
				CH_Data ch = new CH_Data(gr.mMin, MagUtils.gr_rate(gr.aVal,
					gr.bVal, gr.mMin), gr.weight);
				fs.type = FaultType.CH;
				fs.floats = true;
				chBuilder(ch, md, fs);
			}
		}
	}

	private void buildMFDs_GRB0(List<String> lines, MagData md,
			FaultSource fs) {
		fs.floats = true;

		checkArgument(!md.hasAleatory,
			"Aleatory unc. [%s] is incompatible with GR b=0 branches",
			md.aleaSigma);

		List<GR_Data> grData = new ArrayList<GR_Data>();
		for (String line : lines) {
			GR_Data gr = new GR_Data(line, fs, log);
			checkArgument(gr.mMax > gr.mMin,
				"GR b=0 branch can't handle floating CH (mMin=mMax)");
			grData.add(gr);
		}

		for (GR_Data gr : grData) {
			gr.weight *= 0.5;
			grBuilder(gr, md, fs);

			// adjust for b=0, preserving cumulative moment rate
			double tmr = totalMoRate(gr.mMin, gr.nMag, gr.dMag, gr.aVal,
				gr.bVal);
			double tsm = totalMoRate(gr.mMin, gr.nMag, gr.dMag, 0, 0);
			gr.aVal = Math.log10(tmr / tsm);
			gr.bVal = 0;
			grBuilder(gr, md, fs);
		}
	}

	// TODO should be able to just use 1 rate field by checking md.momentBal
	private void chBuilder(CH_Data ch, MagData md, FaultSource fs) {

		// total moment rate
		double tmr = ch.rate * MagUtils.magToMoment(ch.mag);
		// total event rate
		double tcr = ch.rate;
		
		// loop over epistemic uncertainties
		if (md.hasEpistemic) {
			for (int i = 0; i < md.numEpiBranches; i++) {

				double epiMag = ch.mag + md.epiDeltas[i];

				if (md.hasAleatory) {
					GaussianMagFreqDist mfd = new GaussianMagFreqDist(
						md.aleaMinMag(epiMag), md.aleaMaxMag(epiMag),
						md.aleaMagCt);

					double mfdWeight = ch.weight * md.epiWeights[i];

					if (md.momentBalance) {
						mfd.setAllButCumRate(epiMag, md.aleaSigma, tmr *
							mfdWeight, TRUNC_LEVEL, TRUNC_TYPE);
					} else {
						mfd.setAllButTotMoRate(epiMag, md.aleaSigma, tcr *
							mfdWeight, TRUNC_LEVEL, TRUNC_TYPE);
					}
					fs.mfds.add(mfd);
					if (log.isLoggable(Level.FINE)) {
						log.fine(new StringBuilder()
							.append("CH MFD [+epi +ale], M-branch ")
							.append(i + 1).append(": ").append(fs.name)
							.append(IOUtils.LINE_SEPARATOR)
							.append(mfd.getMetadataString()).toString());
					}
				} else {
					// aleatory switch added to handle floating M7.4 CH ruptures
					// on Wasatch that do not have long aleatory tails, may
					// be used elsewhere

					IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(
						epiMag, epiMag, 1);

					double tmpMoRate = tmr * ch.weight * md.epiWeights[i];
					double tmpRate = tmpMoRate / MagUtils.magToMoment(epiMag);

					mfd.set(epiMag, tmpRate);
					fs.mfds.add(mfd);
					if (log.isLoggable(Level.FINE)) {
						log.fine(new StringBuilder()
							.append(IOUtils.LINE_SEPARATOR)
							.append("CH MFD [+epi -ale], M-branch ")
							.append(i + 1).append(": ").append(fs.name)
							.append(IOUtils.LINE_SEPARATOR)
							.append(mfd.getMetadataString()).toString());
					}
				}
			}
		} else {
			if (md.hasAleatory) {
				GaussianMagFreqDist mfd = new GaussianMagFreqDist(
					md.aleaMinMag(ch.mag), md.aleaMaxMag(ch.mag), md.aleaMagCt);
				if (md.momentBalance) {
					mfd.setAllButCumRate(ch.mag, md.aleaSigma, tmr * ch.weight,
						TRUNC_LEVEL, TRUNC_TYPE);
				} else {
					mfd.setAllButTotMoRate(ch.mag, md.aleaSigma, tcr *
						ch.weight, TRUNC_LEVEL, TRUNC_TYPE);
				}
				fs.mfds.add(mfd);
				if (log.isLoggable(Level.FINE)) {
					log.fine(new StringBuilder().append("CH MFD [-epi +ale]: ")
						.append(fs.name).append(IOUtils.LINE_SEPARATOR)
						.append(mfd.getMetadataString()).toString());
				}
			} else {
				IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(ch.mag,
					ch.mag, 1);
				mfd.set(ch.mag, ch.weight * ch.rate);
				fs.mfds.add(mfd);
				if (log.isLoggable(Level.FINE)) {
					log.fine(new StringBuilder().append(IOUtils.LINE_SEPARATOR)
						.append("CH MFD [-epi -ale]: ").append(fs.name)
						.append(IOUtils.LINE_SEPARATOR)
						.append(mfd.getMetadataString()).toString());
				}
			}
		}
	}

	private void grBuilder(GR_Data gr, MagData md, FaultSource fs) {

		double tmr = totalMoRate(gr.mMin, gr.nMag, gr.dMag, gr.aVal, gr.bVal);

		// loop over epistemic uncertainties
		GutenbergRichterMagFreqDist mfd;

		if (md.hasEpistemic) {
			double mMax = gr.mMax; // reference; gr.mMax will vary
			double weight = gr.weight; // reference; weight will vary
			for (int i = 0; i < md.numEpiBranches; i++) {
				// update mMax and nMag
				gr.mMax = mMax + md.epiDeltas[i];
				gr.updateMagCount();
				if (gr.nMag > 0) {
					gr.weight = weight * md.epiWeights[i];
					mfd = makeGR(gr, tmr);

					fs.mfds.add(mfd);
					if (log.isLoggable(Level.FINE)) {
						log.fine(new StringBuilder()
							.append(IOUtils.LINE_SEPARATOR)
							.append("GR MFD, M-branch ").append(i + 1)
							.append(": ").append(fs.name)
							.append(IOUtils.LINE_SEPARATOR)
							.append(mfd.getMetadataString()).toString());
					}

				} else {
					StringBuilder sb = new StringBuilder()
						.append("GR MFD epi branch with no mags");
					appendFaultDat(sb, fs);
					log.warning(sb.toString());
				}
			}
		} else {
			// TODO need nMag==0 check??
			mfd = makeGR(gr, tmr);
			fs.mfds.add(mfd);
			if (log.isLoggable(Level.FINE)) {
				log.fine(new StringBuilder().append(IOUtils.LINE_SEPARATOR)
					.append("GR MFD: ").append(IOUtils.LINE_SEPARATOR)
					.append(mfd.getMetadataString()).toString());
			}
		}
	}

	/* build (possible unc. adjusted) GR mfd's */
	private GutenbergRichterMagFreqDist makeGR(GR_Data gr, double totMoRate) {
		GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(
			gr.mMin, gr.nMag, gr.dMag);

		// epi branches preserve Mo between mMin and dMag(nMag-1),
		// not mMax to ensure that Mo is 'spent' on earthquakes
		// represented by the epi GR distribution with adj. mMax.

		// set total moment rate
		mfd.setAllButTotCumRate(gr.mMin, gr.mMin + (gr.nMag - 1) * gr.dMag,
			gr.weight * totMoRate, gr.bVal);
		return mfd;
	}

	static void generateFaultTrace(Iterator<String> it, FaultSource fs, Logger log) {
		readFaultGeom(it.next(), fs);

		int traceCount = readInt(it.next(), 0);
		List<String> traceDat = readLines(it, traceCount);
		FaultTrace trace = new FaultTrace(fs.name);
		for (String ptDat : traceDat) {
			String[] latlon = StringUtils.split(ptDat);
			trace.add(new Location(readDouble(latlon, 0),
				readDouble(latlon, 1), fs.top));
		}
		
		// catch negative dips; kludge in configs
		// used instead of reversing trace
		if (fs.dip < 0) {
			fs.dip = -fs.dip;
			trace.reverse();
		}
		
		fs.trace = trace;
		
		if (log.isLoggable(Level.FINE)) {
			log.fine(IOUtils.LINE_SEPARATOR + trace.toString());
		}
	}

	// KLUDGY nameIdx indicates the array index at which a fault
	// name begins; most NSHMP files define the fault name on a line such as:
	//
	//  2 3 1 1    805 Juniper Mountain fault
	//
	// where the starting index would be 4. (The identifying number is
	// necessary to distinguish some faults, e.g. Seattle Fault in orwa_c.in)
	// CA files generally start at idx=3
	private FaultSource createSource(String src, SourceFile file, int nameIdx) {
		FaultSource fs = new FaultSource();
		fs.file = file;
		String[] fltDat = StringUtils.split(src);
		fs.type = FaultType.typeForID(readInt(fltDat, 0));
		fs.mech = FocalMech.typeForID(readInt(fltDat, 1));
		fs.nMag = readInt(fltDat, 2);
		fs.name = StringUtils.join(fltDat, ' ', nameIdx, fltDat.length);
		fs.mfds = Lists.newArrayList();
		return fs;
	}
	
	private static void readFaultGeom(String line, FaultSource fs) {
		String[] fltDat = StringUtils.split(line);
		fs.dip = readDouble(fltDat, 0);
		fs.width = readDouble(fltDat, 1);
		fs.top = readDouble(fltDat, 2);
	}
	
	/*
	 * Convenience method to append to supplied <code>StringBuilder</code> fault
	 * and file information.
	 */
	static void appendFaultDat(StringBuilder b, FaultSource fs) {
		b.append(IOUtils.LINE_SEPARATOR).append(WARN_INDENT)
			.append(fs.name).append(IOUtils.LINE_SEPARATOR)
			.append(WARN_INDENT).append(fs.file);
	}
	
	static void toLog(Logger log, FaultSource fs) {
		if (log.isLoggable(Level.INFO)) {
			log.info(IOUtils.LINE_SEPARATOR + fs.toString());
		}
	}

	/*
	 * Strike slip faults with no dip-variation are included in the *.3dip.*
	 * config files. They are consolidated here for a total of 9 mfds in their
	 * mfd List
	 */
	private void cleanStrikeSlip(List<FaultSource> list) {

		Collection<FaultSource> ssSrcs = Collections2.filter(list,
			SourcePredicates.mech(FocalMech.STRIKE_SLIP));

		Map<String, FaultSource> cleanSrcs = new HashMap<String, FaultSource>();
		for (FaultSource fs : ssSrcs) {
			if (!cleanSrcs.containsKey(fs.name)) {
				cleanSrcs.put(fs.name, fs);
				continue;
			}
			cleanSrcs.get(fs.name).mfds.addAll(fs.mfds);
		}

		list.removeAll(ssSrcs);
		list.addAll(cleanSrcs.values());
	}

	static void skipHeader1(Iterator<String> it) {
		int numSta = readInt(it.next(), 0); // grid of sites or station list
		// skip num station lines or lat lon bounds (2 lines)
		Iterators.advance(it, (numSta > 0) ? numSta : 2);
		it.next(); // site data (Vs30) and Campbell basin depth
	}
	
	static void skipHeader2(Iterator<String> it) {
		int nP = readInt(it.next(), 0); // num periods
		for (int i = 0; i < nP; i++) {
			double epi = readDouble(it.next(), 1); // period w/ epi. unc. flag
			if (epi > 0) Iterators.advance(it, 3); 
			it.next(); // out file
			it.next(); // num ground motion values
			it.next(); // ground motion values
			int nAR = readInt(it.next(), 0); // num atten. rel.
			Iterators.advance(it, nAR); // atten rel
		}
		it.next(); // distance sampling on fault and dMove
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Logger log = NSHMP_Utils.logger();
		Level level = Level.ALL;
		log.setLevel(level);
		for (Handler h : NSHMP_Utils.logger().getHandlers()) {
			h.setLevel(level);
		}

		log.info((new Date()) + " " + FaultParser.class.getName());

		FaultParser dev = new FaultParser(log);
		// String srcPath = datPath + "WUS/faults/brange.3dip.gr.in";
		// String srcPath = datPath + "WUS/faults/brange.3dip.ch.in";
		// String srcPath = datPath + "WUS/faults/brange.3dip.65.in";
		//
//		 String srcPath = datPath + "WUS/faults/nv.3dip.gr.in";
//		 String srcPath = datPath + "WUS/faults/nv.3dip.ch.in";
		// String srcPath = datPath + "WUS/faults/nvut.3dip.65.in";
		// String srcPath = datPath + "WUS/faults/ut.3dip.gr.in";
		// String srcPath = datPath + "WUS/faults/ut.3dip.ch.in";
		//
		// String srcPath = datPath + "WUS/faults/orwa_n.3dip.gr.in";
		// String srcPath = datPath + "WUS/faults/orwa_n.3dip.ch.in";
		// String srcPath = datPath + "WUS/faults/orwa_c.in";
		//
		// String srcPath = datPath + "WUS/faults/wasatch.3dip.gr.in";
		// String srcPath = datPath + "WUS/faults/wasatch.3dip.ch.in";
		// String srcPath = datPath + "WUS/faults/wasatch.3dip.74.in";
		//
//		 String srcPath = datPath + "CA/faults/bFault.gr.in";
		// String srcPath = datPath + "CA/faults/bFault.ch.in";
//		 String srcPath = datPath + "CA/faults/aFault_aPriori_D2.1.in";
		// String srcPath = datPath + "CA/faults/aFault_MoBal.in";
		// String srcPath = datPath + "CA/faults/aFault_MoBal.in";
		// String srcPath = datPath + "CA/faults/aFault_unsegEll.in";
		// String srcPath = datPath + "CA/faults/aFault_unseg_HB.in";
		// String srcPath = datPath + "CA/faults/creepflt.in";

		// String srcPath = datPath + "CEUS/faults/CEUScm.in";
			// String srcPath = datPath + "CEUS/faults/NMSZnocl.1000yr.5branch.in";
			// String srcPath = datPath + "CEUS/faults/NMSZnocl.500yr.5branch.in";

		SourceFile sf = SourceMgr.get(WUS, FAULT, "brange.3dip.gr.in").get(0);
//		SourceFile sf = SourceMgr.get(CA, FAULT, "puente.ch.in").get(0);
//		SourceFile sf = SourceMgr.get(CA, FAULT, "aFault_unseg.in").get(0);
//		SourceFile sf = SourceMgr.get(CA, FAULT, "bFault.ch.in").get(0);
//		SourceFile sf = SourceFileMgr.get(CEUS, FAULT, "NMSZnocl.500yr.5branch.in").get(0);
//		NSHMP_ERF erf = Sources.get("newmad.500.cluster.in");
//		NSHMP_ERF erf = Sources.get("bFault.gr.in");
//		NSHMP_ERF erf = Sources.get("aFault_unseg.in");

//		File f = FileUtils.toFile(CEUSdev.class.getResource(srcPath));

//		log.info("Source: " + sf.getName());
		FaultERF erf = dev.parse(sf);

//		System.out.println("NumSrcs: " + erf.getNumSources());
		//int count = 0;
		
//		erf.updateForecast();
//		System.out.println("   ERF: " + erf.getName());
//		System.out.println("  srcs: " + erf.getNumSources());
//		System.out.println("  rups: " + erf.getRuptureCount());
		
//		for (ProbEqkSource source : erf) {
//			((FaultSource) source).init();
//			System.out.println("Source: " + source.getName());
//			System.out.println("  size: " + source.getNumRuptures());
//			count += source.getNumRuptures();
////			if (source.getName().equals("Sierra Madre Connected")) {
////				List<IncrementalMagFreqDist> list = ((FaultSource) source).mfds;
////				for (IncrementalMagFreqDist mfd : list) {
////					System.out.println(mfd);
////				}
////			}
//		}
//		System.out.println(" Count: " + count);
	}

}
