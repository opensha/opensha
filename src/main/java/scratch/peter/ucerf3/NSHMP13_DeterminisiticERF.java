package scratch.peter.ucerf3;

import static com.google.common.base.Preconditions.checkArgument;
import static scratch.UCERF3.enumTreeBranches.FaultModels.*;
import static scratch.UCERF3.enumTreeBranches.DeformationModels.*;
import static scratch.UCERF3.enumTreeBranches.ScalingRelationships.*;
import static java.lang.Math.abs;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;

/**
 * This ERF provides sources derived from UCERF3 data in manner consistent with
 * UCERF2 for the purpose of deriving deterministic ground motions for the
 * building code. That is, for all the equal and/or maximum weighted branches of
 * the UCERF3 logic tree that influence fault attributes, fault sources are
 * constructed that map to the named parent faults in the UCEF3 fault models
 * with the magnitudes spanning the equivalently weighted magnitude scaling
 * relations used in UCERF3. This is roughly equivalent to the UCERF2
 * 'characteristic' representation.
 * 
 * Stepovers require special treatment. They only exist on the Elsinore and San
 * Jacinto faults. We do not want them being treated as their own rupture,
 * however, they should always be included when adjacent fault section rupture.
 * We eliminate them, and their adjacents, from the the primary catalogue of
 * ruptures and include them when building UCERF2-style a-fault sources.
 * 
 * NOTE: not to be used for probabilisitic calcs. Only for use with custom
 * NSHMP calculator that outputs determinisitic ground motion data.
 * 
 * @author Peter Powers
 */
public class NSHMP13_DeterminisiticERF extends AbstractERF {

	private static final String NAME = "NSHMP 2013 Deterministic ERF";
	
	private static final double SPACING = 1.0; // km
	private static final Set<FaultModels> FMs = EnumSet.of(FM3_1, FM3_2);
	private static final Set<DeformationModels> DMs = EnumSet.of(GEOLOGIC,
		ZENGBB, NEOKINEMA);
	// only these three mag-scaling relations affect magnitude
	private static final Set<ScalingRelationships> MSs = EnumSet.of(
		SHAW_2009_MOD, HANKS_BAKUN_08, ELLSWORTH_B);
	
	// reference data map
	private static Map<DeformationModels, Map<Integer, List<FaultSection>>> faultSectionMaps;

	// source data maps
	private static Map<Integer, String> faultNameMap;
	private static Map<Integer, RuptureSurface> surfaceMap;
	private static Map<DeformationModels, Map<Integer, Double>> rakeMap;
	private static Map<Integer, Double> slipMap; // GEOLOGIC slip rates
	private static Map<Integer, Double> areaMap; // GEOLOGIC reduced widths * trace lengths
	private static Map<ScalingRelationships, Map<Integer, Double>> magMap;
	
	
	private List<ProbEqkSource> sources;
	private boolean aleatory = false;
	
	
	private NSHMP13_DeterminisiticERF(boolean aleatory) {
		this.aleatory = aleatory;
		timeSpan = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
		timeSpan.setDuration(1.0);
	}
	
	/**
	 * Create a new instance of the 2013 NSHMP determinisitic ERF.
	 * @param aleatory use magnitudes adjusted upwards for aleatory uncertainty
	 * @return the determinisitic ERF
	 */
	public static NSHMP13_DeterminisiticERF create(boolean aleatory) {
		return new NSHMP13_DeterminisiticERF(aleatory);
	}
	
	@Override
	public int getNumSources() {
		return sources.size();
	}

	@Override
	public ProbEqkSource getSource(int idx) {
		return sources.get(idx);
	}

	@Override
	public void updateForecast() {
		sources = Lists.newArrayList();
		for (Integer id : faultNameMap.keySet()) {
			String name = faultNameMap.get(id);
			
			// skip sources with slip < 0.2 mm/yr
			//if (id < 1000 && slipMap.get(id) < 0.2) continue;
			
			// skip sources with slip <= 0.1 mm/yr but include slow Holocene faults
			if (id < 1000 && slipMap.get(id) <= 0.100001 && !isHolocene(id)) {
//				System.out.println("skipped: " + id + " " + faultNameMap.get(id));
				continue;
			}
			
			double mag = 0.0;
			ScalingRelationships msForMag = null;
			for (ScalingRelationships ms : magMap.keySet()) {
				double msMag = magMap.get(ms).get(id);
				if (msMag > mag) {
					mag = msMag;
					msForMag = ms;
				}
			}
			
			// use GEOLOGIC rake
			double rake = rakeMap.get(GEOLOGIC).get(id);
			FaultRuptureSource source = new FaultRuptureSource(
				aleatory ? mag + 0.24 : mag, 
				surfaceMap.get(id), rake, 1e-4, true);
			source.setName(name +  " " + msForMag.getShortName() + " " + 
				GEOLOGIC.getShortName());
			sources.add(source);
			
			// old way 1/30/14
//			for (ScalingRelationships ms : magMap.keySet()) {
//				// don't repeat redundant rakes
//				List<Double> rakes = Lists.newArrayList();
//				for (DeformationModels dm : rakeMap.keySet()) {
//					double rake = rakeMap.get(dm).get(id);
//					if (rakes.contains(rake)) continue; 
//					rakes.add(rake);
//					// increase mag by generic 2-sigma if aleatory is on
//					FaultRuptureSource source = new FaultRuptureSource(
//						aleatory ? mag + 0.24 : mag, 
//						surfaceMap.get(id), rake, 1e-4, true);
//					source.setName(name +  " " + ms.getShortName() + " " + dm.getShortName());
//					sources.add(source);
//				}
//			}
		}
	}
	
	// section ids with slip rate <=0.1 mmm/yr but listed as being Holocene
	//	561 -- Butano 2011 CFM
	//	168 -- Joshua Tree (Seismicity)
	//	207 -- Lake Isabella (Seismicity)
	//	164 -- Manix-Afton Hills
	//	161 -- Red Pass
	//	31 -- San Juan
	//	209 -- Scodie Lineament
	//	882 -- Bullion Mountains
	//	65 -- Independence rev 2011
	//	83 -- North Frontal  (East)
	//	82 -- North Frontal  (West)
	//	689 -- Rocky Ledge 2011 CFM
	//	53 -- Zayante-Vergeles
	//	715 -- Zayante-Vergeles 2011 CFM
	

	private static final int[] SLOW_AND_HOLOCENE = { 
		561, 168, 207, 164, 161, 31, 209, 882, 65, 83, 82, 689, 53, 715 };

	private static boolean isHolocene(int id) {
		for (int listId : SLOW_AND_HOLOCENE) {
			if (listId == id) return true;
		}
		return false;
	}

	@Override
	public String getName() {
		return NAME;
	}
	
	
	static {
		initFaultSectionAndSlipMaps();
		initNamesMap();
		initBigRups();
		removeFaults();
		initSurfaceMap();
		initRakesAndMags();
	}
	
	// init maps of fault section data for each def model; we need multiple so
	// that we can properly average rake information, but all other section data
	// is the same for all deformation models so usually we can just use
	// GEOLOGIC alone; here we also gather original geologi slip rates
	private static void initFaultSectionAndSlipMaps() {
		faultSectionMaps = Maps.newEnumMap(DeformationModels.class);
		slipMap = Maps.newTreeMap();
		areaMap = Maps.newHashMap();
		for (DeformationModels dm : DMs) {
			
			Map<Integer, List<FaultSection>> sectMap = Maps.newHashMap();
			for (FaultModels fm : FMs) {
				Map<Integer, List<FaultSection>> fmSectMap = Maps.newHashMap();
				DeformationModelFetcher defFetch = new DeformationModelFetcher(
					fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR,
					InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
				
				
				for (FaultSection data : defFetch.getSubSectionList()) {
					int id = data.getParentSectionId();
					List<FaultSection> sects = fmSectMap.get(id);
					if (sects == null) {
						sects = Lists.newArrayList();
						fmSectMap.put(id, sects);
					}
					sects.add(data);
				}
				// merge FM31 and FM32
				sectMap.putAll(fmSectMap);
				
				// mine slips from GEOLOGIC DM, merging FM31 and FM32
				if (dm == GEOLOGIC) {
					List<? extends FaultSection> fspds = defFetch.getParentSectionList();
					for (FaultSection fspd : fspds) {
						slipMap.put(fspd.getSectionId(), fspd.getOrigAveSlipRate());
						areaMap.put(fspd.getSectionId(),
							fspd.getReducedDownDipWidth() *
								fspd.getFaultTrace().getTraceLength());
					}
				}
			}
			faultSectionMaps.put(dm, sectMap);
		}
	}
	
	// init source names from GEOLOGIC def model sections
	private static void initNamesMap() {
		faultNameMap = Maps.newTreeMap();
		for (List<FaultSection> sections : faultSectionMaps.get(
			GEOLOGIC).values()) {
			FaultSection sect0 = sections.get(0);
			faultNameMap.put(sect0.getParentSectionId(),
				sect0.getParentSectionName());
		}
	}
	
	// once big rups are built, explicitely remove stepovers and fault segments
	// adjacent to them; the adjacent segments with their respective stepovers
	// are included as part of the big rup build. Also removes Whittier alt 1
	// which is ont represented in UCERF2 model, as well as Carson-Genoa
	private static void removeFaults() {
		// Elsinore (1-3); San Jacinto (4-6); Whittier alt 1 (7)
		int[] removals = {
			296, 402, 299, // Elsinore
			289, 401, 293, // San Jacinto
			236,           // Whittier Alt 1
			721,           // Carson Range - Genoa
			719            // Klamath East
		};
		for (int idx : removals) {
			faultNameMap.remove(idx);
			for (DeformationModels dm : DMs) {
				faultSectionMaps.get(dm).remove(idx);
			}
		}
	}
	
	// init surfaces from GEOLOGIC def model
	private static void initSurfaceMap() {
		surfaceMap = Maps.newHashMap();
		for (Integer id : faultSectionMaps.get(GEOLOGIC).keySet()) {
			List<FaultSection> sects = faultSectionMaps.get(GEOLOGIC).get(id);
			List<RuptureSurface> surfs = Lists.newArrayList();
			for (FaultSection sect : sects) {
				surfs.add(sect.getFaultSurface(SPACING, false, true));
			}
			surfaceMap.put(id, (surfs.size() == 1) ? surfs.get(0)
				: new CompoundSurface(surfs));
		}
	}
	
	// init rakes and mags
	private static void initRakesAndMags() {
		rakeMap = Maps.newEnumMap(DeformationModels.class);
		magMap = Maps.newEnumMap(ScalingRelationships.class);
		for (ScalingRelationships ms : MSs) {
			Map<Integer, Double> idMagMap = Maps.newHashMap();
			magMap.put(ms, idMagMap);
		}
		for (DeformationModels dm : DMs) {
			Map<Integer, Double> idRakeMap = Maps.newHashMap();
			rakeMap.put(dm, idRakeMap);
			for (Integer id : faultSectionMaps.get(dm).keySet()) {
				List<Double> areas = Lists.newArrayList(); // reduced areas
				List<Double> rakes = Lists.newArrayList();
				double totalLength = 0.0;
				double totalOriginalArea = 0.0;
				double totalReducedArea = 0.0;
				List<FaultSection> sects = faultSectionMaps.get(dm).get(id);
				for (FaultSection sect : sects) {
					double length = sect.getTraceLength() * 1e3;
					totalLength += length;
					double reducedArea = length * sect.getReducedDownDipWidth() * 1e3;
					areas.add(reducedArea);
					totalReducedArea += reducedArea;
					double originalArea = length * sect.getOrigDownDipWidth() * 1e3;
					totalOriginalArea += originalArea;
					rakes.add(sect.getAveRake());
				}
				double originalWidth = totalOriginalArea / totalLength;
				double rake = FaultUtils.getInRakeRange(
					FaultUtils.getScaledAngleAverage(areas, rakes));
				idRakeMap.put(id, rake);
				
				// compute magnitudes on GEOLOGIC pass
				if (dm == GEOLOGIC) {
					for (ScalingRelationships ms : MSs) {
						Map<Integer, Double> idMagMap = magMap.get(ms);
						double mag = ms.getMag(totalReducedArea, originalWidth);
						idMagMap.put(id, mag);
					}
				}
			}
		}
	}
		
	/*
	 * Initialize large multi-fault ruptures. Negative fault indices indicate
	 * the order of the fault section data should be reversed.
	 */
	private static void initBigRups() {
		Map<Integer, String> bigRupDat;
		List<List<Integer>> perms;
		int id = 10000;
		int count = 1;
		
		// Elsinor section IDs -- south to north
		bigRupDat = toLinkedMap(
			new int[] {103, 102, -299, -402, -296, 237},
			new String[] {"CM","J","T","s","GI","W"});
		perms = permutations(bigRupDat.keySet());
		filterStepover(perms, -402);
		addBigRups("Elsinore", id * count++, bigRupDat, perms);

		// Garlock section IDs -- west to east
		bigRupDat = toLinkedMap(
			new int[] {49, 341, 48}, 
			new String[] {"GW", "GC", "GE"});
		perms = permutations(bigRupDat.keySet());
		addBigRups("Garlock", id * count++, bigRupDat, perms);

		// San Jacinto section IDs -- north to south -- San Jacinto has options
		// that terminate on Clark (C) segment so need to merge two permutations
		bigRupDat = toLinkedMap(
			new int[] {119, 289, 401, 293, 101, 99, 28},
			new String[] {"SBV", "SJV", "s", "A", "CC", "B", "SM"});
		perms = permutations(bigRupDat.keySet());
		filterStepover(perms, 401);
		addBigRups("San Jacinto", id * count, bigRupDat, perms);
		bigRupDat = toLinkedMap(
			new int[] {119, 289, 401, 293, 292},
			new String[] {"SBV", "SJV", "s", "A", "C"});
		List<List<Integer>> permsAlt = permutations(bigRupDat.keySet());
		filterStepover(permsAlt, 401);
		cleanPerms(permsAlt, perms);
		addBigRups("San Jacinto", (id * count++) + perms.size(), bigRupDat, permsAlt);

		// Southern San Andreas section IDs -- north to south
		bigRupDat = toLinkedMap(
			new int[] {-32, -285, 300, 287, 286, 301, 282, 283, -284, 295}, 
			new String[] {"PK","CH","CC","BB","NM","SM","NSB","SSB","BG","CO"});
		perms = permutations(bigRupDat.keySet());
		addBigRups("S. San Andreas", id * count++, bigRupDat, perms);
		
		// Northern San Andreas section IDs -- north to south
		bigRupDat = toLinkedMap(
			new int[] {-653, -654, -655, 657}, 
			new String[] {"SAO","SAN","SAP","SAS"});
		perms = permutations(bigRupDat.keySet());
		addBigRups("N. San Andreas", id * count++, bigRupDat, perms);
		
		// Hayward - Rodgers Ck section IDs -- north to south
		bigRupDat = toLinkedMap(
			new int[] {-651, -639, -638, -637}, 
			new String[] {"RC","HN","HS","HE"});
		perms = permutations(bigRupDat.keySet());
		addBigRups("Hayward", id * count++, bigRupDat, perms);

		// Calaveras section IDs -- north to south
		bigRupDat = toLinkedMap(
			new int[] {601, -602, -603, 621}, 
			new String[] {"CN","CC","CS","CE"});
		perms = permutations(bigRupDat.keySet());
		addBigRups("Calaveras", id * count++, bigRupDat, perms);

		
//		printNames(bigRupDat.keySet());
//		printPermutations(perms);
		
		// print traces of last permutation to make sure
		//sections are in correct order
//		printSectionTraces(id + perms.size() - 1);
//		printSectionTraces(id +  perms.size() + permsAlt.size() - 1);
		
	}
	
	// for each set of big rup data, update faultSection data and name maps
	private static void addBigRups(String name, int id, Map<Integer, String> sectIDs,
			List<List<Integer>> permutations) {
		
		for (List<Integer> permutation : permutations) {
			
			// name
			String rupName = buildBigRupName(name, sectIDs, permutation);
			faultNameMap.put(id,rupName);
			
			// update reference data map with big rup fault data lists
			for (DeformationModels dm : DMs) {
				Map<Integer, List<FaultSection>> dmFaultData = faultSectionMaps.get(dm);
				List<FaultSection> sects = Lists.newArrayList();
				for (Integer sectID : permutation) {
					sects.addAll(sectID < 0 ?
						Lists.reverse(dmFaultData.get(abs(sectID))) :
						dmFaultData.get(abs(sectID)));
				}
				dmFaultData.put(id, sects);
			}
			id++;
		}
	}
	
	private static String buildBigRupName(String name, Map<Integer, 
			String> sectIDs, List<Integer> permutation) {
		StringBuilder nameOut = new StringBuilder(name).append(": ");
		List<String> sectNames = Lists.newArrayList();
		for (Integer sect : permutation) {
			sectNames.add(sectIDs.get(sect));
		}
		Joiner.on('+').appendTo(nameOut, sectNames);
		return nameOut.toString();
	}
	

	private static Map<Integer, String> toLinkedMap(int[] keys, String[] values) {
		checkArgument(keys.length == values.length);
		Map<Integer, String> map = Maps.newLinkedHashMap();
		for (int i = 0; i < keys.length; i++) {
			map.put(keys[i], values[i]);
		}
		return map;
	}
	
	/*
	 * Returns a List of Lists that are ordered permutations of the supplied
	 * array with a length > 2.
	 */
	private static List<List<Integer>> permutations(Iterable<Integer> vals) {
		List<List<Integer>> permutations = Lists.newArrayList();
		List<Integer> valList = Lists.newArrayList(vals);
		// length
		for (int i = 2; i <= valList.size(); i++) {
			// start
			for (int j = 0; j < valList.size() - i + 1; j++) {
				permutations.add(valList.subList(j, j + i));
			}
		}
		return permutations;
	}
	
	/*
	 * Removes the elements of p2 that are contained in p1.
	 */
	private static void cleanPerms(List<List<Integer>> p1, List<List<Integer>> p2) {
		Iterator<List<Integer>> it = p1.iterator();
		while (it.hasNext()) {
			int[] p1array = Ints.toArray(it.next());
			for (List<Integer> pCheck : p2) {
				int[] pCheckArray = Ints.toArray(pCheck);
				if (Arrays.equals(p1array, pCheckArray)) {
					it.remove();
					break;
				}
			}
		}
	}
	
	/*
	 * Removes permutations where segments adjacent to a stepover do not include
	 * the stepover. Operates on permutation list in place
	 */
	private static void filterStepover(List<List<Integer>> permutations, int idx) {
		// last permutation contains all indices in order
		List<Integer> totalList = permutations.get(permutations.size() - 1);
		int stepPos = totalList.indexOf(idx);
		checkArgument(stepPos > 0 && stepPos < totalList.size() - 1);
		int idxBefore = totalList.get(stepPos - 1);
		int idxAfter = totalList.get(stepPos + 1);
		Iterator<List<Integer>> permIt = permutations.iterator();
		while (permIt.hasNext()) {
			List<Integer> permutation = permIt.next();
			int last = permutation.size() - 1;
			if (permutation.get(0) == idxAfter ||
				permutation.get(last) == idxBefore) permIt.remove();
		}
	}
	
	private static void printPermutations(List<List<Integer>> lists) {
		for (List<Integer> s : lists) {
			System.out.println(s);
		}
	}
	
	private static void printNames(Iterable<Integer> IDs) {
		for (Integer id : IDs) {
			System.out.println(Strings.padEnd(Integer.toString(abs(id)), 4, ' ') +
				faultNameMap.get(abs(id)));
		}
	}
	

	private static void printSectionTraces(int id) {
		
		List<FaultSection> sects = faultSectionMaps.get(GEOLOGIC).get(id);
		System.out.println(faultNameMap.get(abs(id)));
		for (FaultSection sect : sects) {
			System.out.println(sect.getFaultTrace());
		}
	}

	private static void printRakeMagSummary() {
		System.out.println("[rakes by DM] [mags by MS] ID Name");
		// prints summary of static fields
		for (Integer id : faultNameMap.keySet()) {
			List<String> rakes = Lists.newArrayList();
			for (DeformationModels dm : rakeMap.keySet()) {
				rakes.add(Strings.padStart(String.format("%.2f", rakeMap.get(dm).get(id)), 7, ' '));
			}
			List<String> mags = Lists.newArrayList();
			for (ScalingRelationships ms : magMap.keySet()) {
				mags.add(String.format("%.2f", magMap.get(ms).get(id)));
			}
			System.out.println(rakes + " " + mags +
				Strings.padStart(Integer.toString(id), 6, ' ') + " " +
				faultNameMap.get(id));
		}
	}
	
	private static void printSlipSummary() {
		Map<String, Integer> nameIdMap = ImmutableSortedMap.copyOf(
			HashBiMap.create(faultNameMap).inverse());
		
		System.out.println("ID, Mag, Slip, Recurrence, Name");
		// prints summary of static fields
		for (Map.Entry<String, Integer> entry : nameIdMap.entrySet()) {
			
			// skip multi-faults with null slips
			int id = entry.getValue();
			//if (id > 1000) continue;
			
			StringBuffer sb = new StringBuffer();
			sb.append(Strings.padStart(Integer.toString(id), 5, ' '));
			sb.append(", ");

			List<Double> mags = Lists.newArrayList();
			for (ScalingRelationships ms : magMap.keySet()) {
				mags.add(magMap.get(ms).get(id));
			}
			double mag = Doubles.max(Doubles.toArray(mags));
			sb.append(String.format("%.2f", mag));
			sb.append(", ");

			double slip = (id > 1000) ? Double.NaN : slipMap.get(id);
			double area = (id > 1000) ? Double.NaN : areaMap.get(id);
			
			double mu = 3e10; // dyne/cm2
			double moRate = area * mu * slip * 1000.0;
			double recurrence = MagUtils.magToMoment(mag) / moRate;
			
			sb.append(slip); //Strings.padStart(String.format("%.2f", slip), 5, ' '));
			sb.append(", ");
			sb.append(Strings.padStart(String.format("%d", (int) recurrence), 10, ' '));
			sb.append(", ");
			sb.append(entry.getKey());
			System.out.println(sb.toString());
		}
	}


	public static void main(String[] args) {
//		printRakeMagSummary();
//		printSlipSummary();
		NSHMP13_DeterminisiticERF erf = NSHMP13_DeterminisiticERF.create(true);
		erf.updateForecast();
		for (ProbEqkSource src : erf) {
			System.out.println(
				src.getNumRuptures() + " " + 
				String.format("%.1f", src.getRupture(0).getAveRake()) + " " + 
				String.format("%.2f", src.getRupture(0).getMag()) + " " + 
				src.getName());
		}
		System.out.println(erf.getNumSources());
	}

	
}
