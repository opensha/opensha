package org.opensha.nshmp2.erf.source;

import static org.opensha.nshmp2.util.NSHMP_Utils.*;
import static org.opensha.nshmp2.util.SourceRegion.*;
import static org.opensha.nshmp2.util.SourceType.*;

import java.io.File;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensha.nshmp2.util.FaultType;
import org.opensha.nshmp2.util.FocalMech;
import org.opensha.nshmp2.util.NSHMP_Utils;
import org.opensha.nshmp2.util.SourceIMR;
import org.opensha.nshmp2.util.SourceRegion;
import org.opensha.nshmp2.util.SourceType;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * 2008 NSHMP cluster source parser.
 */
public class ClusterParser {

	private Logger log;

	ClusterParser(Logger log) {
		this.log = log;
	}

	ClusterERF parse(SourceFile sf) {
		String name = sf.getName();
		SourceRegion srcRegion = sf.getRegion();
		SourceType srcType = sf.getType();
		SourceIMR srcIMR = SourceIMR.imrForSource(srcType, srcRegion, name, null);
		double srcWt = sf.getWeight();

		List<String> dat = sf.readLines();

		Iterator<String> srcLines = dat.iterator();
		FaultParser.skipHeader1(srcLines);
		double rMax = readDouble(srcLines.next(), 1);
		FaultParser.skipHeader2(srcLines);

		// load magnitude uncertainty data
		MagData md = new MagData(readLines(srcLines, 4));
		md.toLog(log);
		
		Map<Integer, ClusterSource> srcMap = Maps.newHashMap();

		while (srcLines.hasNext()) {
			String[] fltDat = StringUtils.split(srcLines.next());

			// For NMSZ NSHMP uses a group id to identify fault variants, in
			// this case 5 arrayed west to east, and a segment or section id to
			// identify north, central and southern cluster model faults
			int groupNum = readInt(fltDat, 3);
			int sectionNum = readInt(fltDat, 4);
			
			// Loop over the various faults that are all the geometric variants
			// contained in the cluster model. Init a new ClusterSource for each
			// geometry and populate it with the apppropriate FaultSources
			ClusterSource cs = srcMap.get(groupNum);
			if (cs == null) {
				cs = new ClusterSource();
				cs.name = "NMSZ Cluster: " + createGroupName(groupNum);
				cs.file = sf;
				cs.rate = readReturnPeriod(sf.getName());
				cs.weight = SourceMgr.getClusterWeight(sf.getName(),
					groupNum);
				srcMap.put(groupNum, cs);
			}
						
			FaultSource fs = new FaultSource();
			fs.name = createSegmentName(groupNum, sectionNum);
			fs.file = sf;
			fs.type = FaultType.typeForID(readInt(fltDat, 0));
			fs.mech = FocalMech.typeForID(readInt(fltDat, 1));
			fs.nMag = readInt(fltDat, 2);
			fs.mfds = Lists.newArrayList();

			/*
			 * For cluster sources, we need to preserve the weight associated
			 * with each magnitude variant and don't need the rate until doing
			 * the cluster analysis. Because the same cluster rate is applied to
			 * each M variant (e.g. 1/500), rather than change the behavior of a
			 * FaultSource, we store the scaled rate in each mfd. We also store
			 * the cluster rate in each ClusterSource and can then back out the
			 * weight associated with each magnitude in the NSHMP calculator.
			 */
			
			List<String> mfdSrcDat = readLines(srcLines, fs.nMag);
			generateMFDs(fs, mfdSrcDat);
			FaultParser.generateFaultTrace(srcLines, fs, log);
			FaultParser.toLog(log, fs);
			cs.sources.add(fs);
		}
		List<ClusterSource> sources = ImmutableList.copyOf(srcMap.values());
		for (ClusterSource cs : sources) {
			toLog(log, cs);
		}
		return new ClusterERF(name, sources, srcRegion, srcIMR, srcWt, rMax);
	}

	private void generateMFDs(FaultSource fs, List<String> lines) {
		// for 2008 NSHMP all cluster sources are entered as characteristic
		// and fill all the supplied geometries
		fs.floats = false;
		for (String line : lines) {
			CH_Data ch = new CH_Data(line);
			IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(ch.mag,
				ch.mag, 1);
			mfd.set(ch.mag, ch.weight * ch.rate);
			fs.mfds.add(mfd);
			if (log.isLoggable(Level.FINE)) {
				log.fine(new StringBuilder().append(IOUtils.LINE_SEPARATOR)
					.append("CH MFD: ").append(fs.name)
					.append(IOUtils.LINE_SEPARATOR)
					.append(mfd.getMetadataString()).toString());
			}
		}
	}
	
	private static String createSegmentName(int grp, int sec) {
		StringBuffer sb = new StringBuffer("NMSZ (");
		sb.append(createGroupName(grp)).append(", ");
		String secName = (sec == 1) ? "north" : (sec == 2) ? "center"
			: (sec == 3) ? "south" : "unknown";
		sb.append(secName).append(" section)");
		return sb.toString();
	}

	private static String createGroupName(int grp) {
		return ((grp == 1) ? "west" : (grp == 2) ? "mid-west" : (grp == 3)
			? "center" : (grp == 4) ? "mid-east" : (grp == 5) ? "east"
				: "unknown") +
			" model";
	}
	
	private static double readReturnPeriod(String fname) {
		String[] parts = StringUtils.split(fname, '.');
		return new Double(parts[1]);
	}
	
	private static void toLog(Logger log, ClusterSource cs) {
		if (log.isLoggable(Level.INFO)) {
			log.info(IOUtils.LINE_SEPARATOR + cs.toString());
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
		log.info((new Date()) + " " + ClusterParser.class.getName());

		ClusterParser dev = new ClusterParser(log);

//		SourceFile sf = SourceFileMgr.get(WUS, FAULT, "orwa_n.3dip.gr.in").get(0);
//		SourceFile sf = SourceFileMgr.get(CA, FAULT, "bFault.gr.in").get(0);
//		SourceFile sf = SourceFileMgr.get(CEUS, FAULT, "NMSZnocl.500yr.5branch.in").get(0);
		SourceFile sf = SourceMgr.get(CEUS, CLUSTER, "newmad.750.cluster.in").get(0);

//		File f = FileUtils.toFile(CEUSdev.class.getResource(srcPath));

		log.info("Source: " + sf.getName());
		ClusterERF erf = dev.parse(sf);

//		System.out.println("NumSrcs: " + erf.getNumSources());
//		int count = 0;
//		for (ProbEqkSource source : erf) {
//			((ClusterSource) source).init();
//			System.out.println("Source: " + source.getName());
//			System.out.println("  size: " + source.getNumRuptures());
//			count += source.getNumRuptures();
//			if (source.getName().equals("Sierra Madre Connected")) {
//				List<IncrementalMagFreqDist> list = ((FaultSource) source).mfds;
//				for (IncrementalMagFreqDist mfd : list) {
//					System.out.println(mfd);
//				}
//			}
//		}
//		System.out.println(" Count: " + count);
	}

}
