package scratch.peter.nshmp;

import java.util.List;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.BackgroundRupType;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;

import com.google.common.collect.Lists;

/**
 * This is a custom erf for the NSHMP that filters out 'Klamath Graben East' and
 * 'Carson Range (Genoa)' ruptures (sets their rates to zero to avoid double
 * counting with NSHMP. These two faults have different implementations in the
 * NSHMP WUS model and connot really be removed (such as others such as 'Antelope
 * Valley' can).
 * 
 * This implementation also filters out grid sources that are
 * outside California so that WUS and CA hazard curves can be combined using
 * the fortran process. This is done by remapping the indices of those source
 * nodes within the CA polygon to those in the CA RELM region used by UCERF3.
 * 
 * @author Peter Powers
 */
public class NSHMP_UCERF3_ERF extends FaultSystemSolutionERF {

	private static final Region CA_REGION;
	private GridSourceProvider gridSources;
	private List<Integer> indices;
	
	public NSHMP_UCERF3_ERF(FaultSystemSolution fss) {
		super(fss);
		
		// manipulate fss prior to updateForecast being called which will build
		// all the required fault sources
		double[] rates = fss.getRateForAllRups();
		FaultSystemRupSet fsrs =  fss.getRupSet();
		List<Integer> rupIDs = Lists.newArrayList();
		
		// Carson and Klamath IDs are the same across both fault models
		
		// Zero out Carson Range (parent section ID = 721)
		rupIDs.addAll(fsrs.getRupturesForParentSection(721));	
	
		// Zero out Klamath East (parent section ID = 719)
		rupIDs.addAll(fsrs.getRupturesForParentSection(719));	
		
		for (int rupID : rupIDs) {
			rates[rupID] = 0.0;
		}
	}
	
	@Override
	protected boolean initOtherSources() {
		gridSources = getSolution().getGridSourceProvider();
		
//		if (bgRupType.equals(BackgroundRupType.POINT)) {
//			// default is false; gridGen will create point sources for those
//			// with M<6 anyway; this forces those M>6 to be points as well
//			gridSources.setAsPointSources(true);
//		}
		
		// build grid source index map: CA_REGION to CA_RELM
		GriddedRegion gr = gridSources.getGriddedRegion();
		indices = Lists.newArrayList();
		for (Location loc: gr) {
			if (CA_REGION.contains(loc)) {
				indices.add(gr.indexForLocation(loc));
			}
		}
		
		// update grid source count
		numOtherSources = indices.size();
		return true;
	}
	
	@Override
	protected ProbEqkSource getOtherSource(int iSource) {
		return gridSources.getSource(indices.get(iSource), timeSpan.getDuration(),
			applyAftershockFilter, BackgroundRupType.POINT);
	}


	static {
		LocationList locs = new LocationList();
		locs.add(new Location(39.000, -119.999));
		locs.add(new Location(35.000, -114.635));
		locs.add(new Location(34.848, -114.616));
		locs.add(new Location(34.719, -114.482));
		locs.add(new Location(34.464, -114.371));
		locs.add(new Location(34.285, -114.122));
		locs.add(new Location(34.097, -114.413));
		locs.add(new Location(33.934, -114.519));
		locs.add(new Location(33.616, -114.511));
		locs.add(new Location(33.426, -114.636));
		locs.add(new Location(33.401, -114.710));
		locs.add(new Location(33.055, -114.676));
		locs.add(new Location(33.020, -114.501));
		locs.add(new Location(32.861, -114.455));
		locs.add(new Location(32.741, -114.575));
		locs.add(new Location(32.718, -114.719));
		locs.add(new Location(32.151, -120.861));
		locs.add(new Location(39.000, -126.000));
		locs.add(new Location(42.001, -126.000));
		locs.add(new Location(42.001, -119.999));
		locs.add(new Location(39.000, -119.999));
		CA_REGION = new Region(locs, null);
	}

	public static void main(String[] args) {
//		String f = "tmp/UC33/src/bravg/FM/UC33brAvg_FM31.zip";
//		File tf = new File(f);
//		System.out.println(tf.exists());
//		NSHMP_UCERF3_ERF erf = new NSHMP_UCERF3_ERF(UC3_CalcUtils.getSolution(f));
//		erf.updateForecast();
//		System.out.println(erf.numOtherSources);
		
		boolean contains = CA_REGION.contains(new Location(42.0, -121.0));
		System.out.println(contains);
	}


}
