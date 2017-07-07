package org.opensha.nshmp2.erf.source;

import static org.opensha.nshmp2.util.SourceRegion.*;
import static org.opensha.nshmp2.util.SourceType.*;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensha.nshmp2.util.SourceRegion;
import org.opensha.nshmp2.util.SourceType;
import org.opensha.nshmp2.util.Utils;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;

/*
 * Container class that manages NSHHMP source file references. Source weights
 * are hard coded in this class and supplied to each SourceFile generated. This
 * class also provides the weights used for NMSZ fault models, which can be
 * retreived by group index [1,2,3,4,5] and recurrence [500,750,1000,1500]; 
 * 
 * @author Peter Powers
 * @version $Id:$
 */
class SourceMgr {

	// source path relative to nshmp resource directory
	private static String datPath = "/sources/";
	private static List<SourceFile> srcFiles;

	// no instantiation
	private SourceMgr() {}

	static {
		initSources();
	}

	/**
	 * Returns an immutable list of all NSHHMP source files.
	 * @return a list of all source files
	 */
	public static List<SourceFile> getAll() {
		return ImmutableList.copyOf(srcFiles);
	}

	/**
	 * Returns an immutable list of the source files that match the supplied
	 * <code>SourceRegion</code> and <code>SourceType</code>. If either argument
	 * is <code>null</code> or empty, method will match all elements of the
	 * argument class.
	 * @param region to filter by
	 * @param type to filter by
	 * @return the list of matching source files
	 */
	public static List<SourceFile> get(SourceRegion region, SourceType type) {
		return get(region, type, null);
	}

	/**
	 * Returns an immutable list of the source files that match the supplied
	 * <code>SourceRegion</code>, <code>SourceType</code>, and file name. If
	 * either argument is <code>null</code>, method will match all elements of
	 * the argument class.
	 * @param region to filter by
	 * @param type to filter by
	 * @param name to filter by
	 * @return the list of matching source files
	 */
	public static List<SourceFile> get(SourceRegion region, SourceType type,
			String name) {
		return get((region != null) ? EnumSet.of(region) : null, (type != null)
			? EnumSet.of(type) : null, (StringUtils.isNotBlank(name))
			? ImmutableSet.of(name) : null);
	}

	/**
	 * Returns an immutable list of the source files that match the supplied
	 * <code>SourceRegion</code>s, <code>SourceType</code>s, and file names. If
	 * either argument is <code>null</code> or empty, method will match all
	 * elements of the argument class.
	 * @param regions 
	 * @param types 
	 * @param names 
	 * @return  the list of matching source files
	 */
	public static List<SourceFile> get(EnumSet<SourceRegion> regions,
			EnumSet<SourceType> types, Set<String> names) {
		List<Predicate<SourceFile>> pList = generateFilters(regions, types,
			names);
		Predicate<SourceFile> p = Predicates.and(pList);
		return ImmutableList.copyOf(Collections2.filter(srcFiles, p));
	}
	
	private static List<Predicate<SourceFile>> generateFilters(
			EnumSet<SourceRegion> regions, EnumSet<SourceType> types,
			Set<String> names) {
		Predicate<SourceFile> rp = Predicates.alwaysTrue();
		if (regions != null && !regions.isEmpty()) {
			rp = Predicates.alwaysFalse();
			for (SourceRegion region : regions) {
				rp = Predicates.or(rp, new SourceFileRegion(region));
			}
		}
		Predicate<SourceFile> tp = Predicates.alwaysTrue();
		if (types != null && !types.isEmpty()) {
			tp = Predicates.alwaysFalse();
			for (SourceType type : types) {
				tp = Predicates.or(tp, new SourceFileType(type));
			}
		}
		Predicate<SourceFile> np = Predicates.alwaysTrue();
		if (names != null && !names.isEmpty()) {
			np = Predicates.alwaysFalse();
			for (String name : names) {
				np = Predicates.or(np, new SourceFileName(name));
			}
		}
		return ImmutableList.of(rp, tp, np);
	}

	// @formatter:off

	private static class SourceFileType implements Predicate<SourceFile> {
		SourceType type;
		SourceFileType(SourceType type) { this.type = type; }
		@Override public boolean apply(SourceFile input) {
			return input.getType().equals(type);
		}
		@Override public String toString() { return type.toString(); }
	}
	
	private static class SourceFileRegion implements Predicate<SourceFile> {
		SourceRegion region;
		SourceFileRegion(SourceRegion region) { this.region = region; }
		@Override public boolean apply(SourceFile input) {
			return input.getRegion().equals(region);
		}
		@Override public String toString() { return region.toString(); }
	}

	private static class SourceFileName implements Predicate<SourceFile> {
		String name;
		SourceFileName(String name) { this.name = name; }
		@Override public boolean apply(SourceFile input) {
			return input.getName().equals(name);
		}
		@Override public String toString() { return name; }
	}

	// @formatter:on
	
	private static SourceFile create(String resource, double weight) {
		String[] parts = StringUtils.split(resource, '/');
		SourceRegion region = SourceRegion.valueOf(parts[0]);
		SourceType type = null;
		if (region == CASC) {
			type = SUBDUCTION;
		} else if (resource.contains("cluster")) {
			type = CLUSTER;
		} else {
			type = (parts[1].equals("gridded")) ? GRIDDED : FAULT;
		}
		URL url = Utils.getResource(datPath + resource);
		return new SourceFile(url, region, type, weight);
		
	}
	private static void initSources() {
		srcFiles = Lists.newArrayList();
		
		// CA faults
		srcFiles.add(create("CA/faults/bFault.ch.in", 0.6667));
		srcFiles.add(create("CA/faults/bFault.gr.in", 0.1667));
		srcFiles.add(create("CA/faults/aFault_aPriori_D2.1.in", 0.4500));
		srcFiles.add(create("CA/faults/aFault_unseg.in", 0.0500));
		srcFiles.add(create("CA/faults/aFault_MoBal.in", 0.2250));

		// CA grid and fixed strike
		srcFiles.add(create("CA/gridded/CAdeep.in", 1.0));
		srcFiles.add(create("CA/gridded/CAmap.24.ch.in", 0.3333));
		srcFiles.add(create("CA/gridded/CAmap.21.ch.in", 0.3334));
		srcFiles.add(create("CA/gridded/CAmap.21.gr.in", 0.1666));
		srcFiles.add(create("CA/gridded/CAmap.24.gr.in", 0.1667));
		srcFiles.add(create("CA/gridded/brawmap.in", 1.0));
		srcFiles.add(create("CA/gridded/creepmap.in", 1.0));
		srcFiles.add(create("CA/gridded/sangorg.in", 1.0));
		srcFiles.add(create("CA/gridded/mendo.in", 1.0));
		srcFiles.add(create("CA/gridded/mojave.in", 1.0));
		srcFiles.add(create("CA/gridded/impext.ch.in", 0.6667));
		srcFiles.add(create("CA/gridded/impext.gr.in", 0.3333));
		srcFiles.add(create("CA/gridded/shear1.in", 1.0));
		srcFiles.add(create("CA/gridded/shear2.in", 1.0));
		srcFiles.add(create("CA/gridded/shear3.in", 1.0));
		srcFiles.add(create("CA/gridded/shear4.in", 1.0));
		
		// WUS faults
		srcFiles.add(create("WUS/faults/brange.3dip.ch.in", 0.6666));
		srcFiles.add(create("WUS/faults/brange.3dip.gr.in", 0.3334));
		srcFiles.add(create("WUS/faults/brange.3dip.65.in", 1.0000));
		srcFiles.add(create("WUS/faults/nv.3dip.ch.in", 0.6666));
		srcFiles.add(create("WUS/faults/nv.3dip.gr.in", 0.3334));
		srcFiles.add(create("WUS/faults/nvut.3dip.65.in", 1.0000));
		srcFiles.add(create("WUS/faults/ut.3dip.ch.in", 0.6666));
		srcFiles.add(create("WUS/faults/ut.3dip.gr.in", 0.3334));
		srcFiles.add(create("WUS/faults/orwa_c.in", 0.5000));
		srcFiles.add(create("WUS/faults/orwa_n.3dip.ch.in", 0.6667));
		srcFiles.add(create("WUS/faults/orwa_n.3dip.gr.in", 0.3333));
		srcFiles.add(create("WUS/faults/wasatch.3dip.ch.in", 1.0000));
		srcFiles.add(create("WUS/faults/wasatch.3dip.gr.in", 1.0000));
		srcFiles.add(create("WUS/faults/wasatch.3dip.74.in", 1.0000));

		// WUS grid and fixed strike
		srcFiles.add(create("WUS/gridded/WUSmap.ch.in", 0.25));
		srcFiles.add(create("WUS/gridded/WUSmap.gr.in", 0.25));
		srcFiles.add(create("WUS/gridded/EXTmap.ch.in", 0.6667));
		srcFiles.add(create("WUS/gridded/EXTmap.gr.in", 0.333));
		srcFiles.add(create("WUS/gridded/nopuget.ch.in", 0.25));
		srcFiles.add(create("WUS/gridded/nopuget.gr.in", 0.25));
		srcFiles.add(create("WUS/gridded/puget.ch.in", 0.25));
		srcFiles.add(create("WUS/gridded/puget.gr.in", 0.25));
		srcFiles.add(create("WUS/gridded/pnwdeep.in", 1.0));
		srcFiles.add(create("WUS/gridded/portdeep.in", 1.0));

		// CASC
		srcFiles.add(create("CASC/cascadia.top.8387.in", 0.0051282));
		srcFiles.add(create("CASC/cascadia.mid.8387.in", 0.0102564));
		srcFiles.add(create("CASC/cascadia.bot.8387.in", 0.0102564));
		srcFiles.add(create("CASC/cascadia.older2.8387.in", 0.025644));
		srcFiles.add(create("CASC/cascadia.top.8082.in", 0.0025641));
		srcFiles.add(create("CASC/cascadia.mid.8082.in", 0.0051282));
		srcFiles.add(create("CASC/cascadia.bot.8082.in", 0.0051282));
		srcFiles.add(create("CASC/cascadia.older2.8082.in", 0.012833));
		srcFiles.add(create("CASC/cascadia.top.9pm.in", 0.06666667));
		srcFiles.add(create("CASC/cascadia.mid.9pm.in", 0.13333));
		srcFiles.add(create("CASC/cascadia.bot.9pm.in", 0.13333));
		srcFiles.add(create("CASC/cascadia.older2.9pm.in", 0.3333));

		// CEUS faults
		srcFiles.add(create("CEUS/faults/NMSZnocl.500yr.5branch.in", 0.45));
		srcFiles.add(create("CEUS/faults/NMSZnocl.1000yr.5branch.in", 0.05));
		srcFiles.add(create("CEUS/faults/CEUScm.in", 1.0));

		// CEUS gridded
		srcFiles.add(create("CEUS/gridded/CEUS.2007all8.AB.in", 0.5));
		srcFiles.add(create("CEUS/gridded/CEUS.2007all8.J.in", 0.5));
		srcFiles.add(create("CEUS/gridded/CEUSchar.73.in", 0.45));
		srcFiles.add(create("CEUS/gridded/CEUSchar.71.in", 0.2));
		srcFiles.add(create("CEUS/gridded/CEUSchar.75.in", 0.15));
		srcFiles.add(create("CEUS/gridded/CEUSchar.68.in", 0.2));

		// CLUSTER NOTE: the NNMSZ cluster model is the one case in the 2008
		// NSHMP where there is not a 1:1 correspondence between input and
		// output files (there are actually 5 output files per input
		// representing the different fault geometry models). As such we supply
		// the weights to the ClusterSource and have the NSHMP hazard calcuator
		// apply them appropriately; see getClusterWeight(). The inputs are
		// therefore given full weight.
		srcFiles.add(create("CEUS/faults/newmad.500.cluster.in", 1.0));
		srcFiles.add(create("CEUS/faults/newmad.750.cluster.in", 1.0));
		srcFiles.add(create("CEUS/faults/newmad.1000.cluster.in", 1.0));
		srcFiles.add(create("CEUS/faults/newmad.1500.cluster.in", 1.0));
		
		// Original cluster wieghts from NSHMP combine phase
		// newmad-500-clu.pga.g1 0.01125
		// newmad-500-clu.pga.g2 0.0225
		// newmad-500-clu.pga.g3 0.1575
		// newmad-500-clu.pga.g4 0.0225
		// newmad-500-clu.pga.g5 0.01125
		
		// newmad-750-clu.pga.g1 0.01125
		// newmad-750-clu.pga.g2 0.0225
		// newmad-750-clu.pga.g3 0.1575
		// newmad-750-clu.pga.g4 0.0225
		// newmad-750-clu.pga.g5 0.01125
		
		// newmad-1000-clu.pga.g1 0.0025
		// newmad-1000-clu.pga.g2 0.005
		// newmad-1000-clu.pga.g3 0.035
		// newmad-1000-clu.pga.g4 0.005
		// newmad-1000-clu.pga.g5 0.0025
		
		// newmad-1500-clu.pga.g1 0.01125
		// newmad-1500-clu.pga.g2 0.0225
		// newmad-1500-clu.pga.g3 0.1575
		// newmad-1500-clu.pga.g4 0.0225
		// newmad-1500-clu.pga.g5 0.01125
		
//		srcWts.put("CarsonKingsDipVar.in", 1.0000);
//		srcWts.put("CarsonKingsDipVarWt.in", 1.0000);
//		srcWts.put("CarsonKingsDipVarMagAlt.in", 1.0000);
//		srcWts.put("CarsonKingsDipVarSlipAlt.in", 1.0000);
//		
//		srcWts.put("cascadia.bot.88.in", 0.0051282);
//		srcWts.put("puente.ch.in", 0.6667);

	}
	
	// These are combined the weights of: (un)clustered * location * recurrence
	// e.g. 0.5 * 0.05 * 0.9 * 0.05 = 0.01125
	private static final double[] CL_WTS_BASE = { 0.01125, 0.0225, 0.1575, 0.0225, 0.01125 };
	private static final double[] CL_WTS_1000 = { 0.0025, 0.005, 0.035, 0.005, 0.0025 };

	/*
	 * Returns the weight for the supplied NMSZ cluster model file name and
	 * fault model group index (1-5, west to east)
	 */
	static double getClusterWeight(String name, int group) {
		double[] wts = (name.contains(".1000.")) ? CL_WTS_1000 : CL_WTS_BASE;
		return wts[group-1];
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		List<SourceFile> files = SourceMgr.getAll();
//		List<SourceFile> files = SourceMgr.get(WUS, FAULT);
		for (SourceFile f : files) {
			System.out.println(f);
		}
	}

}
