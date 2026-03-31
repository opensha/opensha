package gov.usgs.earthquake.nshmp.erf.nshm27.util;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM26_InterfaceDeformationModels;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM26_InterfaceFaultModels;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM26_LogicTree;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;

public class InterfaceGridAssociations implements FaultGridAssociations, ArchivableModule {
	
//	public static FaultGridAssociations buildInterfaceAssociations(List<? extends FaultSection> sects, GriddedRegion gridReg) {
//		return new Fau
//	}

	public static void main(String[] args) throws IOException {
//		LogicTreeBranch<LogicTreeNode> branch = NSHM26_LogicTree.buildDefault(
//				NSHM26_SeismicityRegions.AMSAM, TectonicRegionType.SUBDUCTION_INTERFACE, false);
		LogicTreeBranch<LogicTreeNode> branch = NSHM26_LogicTree.buildDefault(
				NSHM26_SeismicityRegions.GNMI, TectonicRegionType.SUBDUCTION_INTERFACE, false);
		NSHM26_InterfaceFaultModels fm = branch.requireValue(NSHM26_InterfaceFaultModels.class);
		NSHM26_InterfaceDeformationModels dm = branch.requireValue(NSHM26_InterfaceDeformationModels.class);

		NSHM26_SeismicityRegions reg = fm.getSeisReg();
		
		System.out.println("Branch: "+branch+"; reg="+reg);
		
		List<? extends FaultSection> sects = dm.build(branch);
		
		GriddedRegion gridReg = new GriddedRegion(reg.load(), 0.1, GriddedRegion.ANCHOR_0_0);
		
		InterfaceGridAssociations assoc = new InterfaceGridAssociations(sects, gridReg);
		
		GeographicMapMaker mapMaker = new GeographicMapMaker(sects);
		
		GriddedGeoDataSet mapped = new GriddedGeoDataSet(gridReg, Arrays.copyOf(assoc.nodeFractMapped, gridReg.getNodeCount()));
		for (int i=0; i<mapped.size(); i++)
			if (mapped.get(i) == 0d)
				mapped.set(i, Double.NaN);
		
		CPT mappedCPT = GMT_CPT_Files.SEQUENTIAL_LAJOLLA_UNIFORM.instance().reverse().rescale(0d, 1d);
		Color transColor = new Color(255, 255, 255, 0);
		mappedCPT.setNanColor(transColor);
		mapMaker.plotXYZData(mapped, mappedCPT, "Node fraction mapped");
		List<Color> transColors = new ArrayList<>(sects.size());
		for (int i=0; i<sects.size(); i++)
			transColors.add(transColor);
		mapMaker.plotSectColors(transColors);
		
		mapMaker.plot(new File("/tmp"), "assoc_"+reg.name()+"_nodes", " ");
		
		mapMaker.clearXYZData();
		
		mapMaker.plotSectScalars(assoc.sectFractMapped, mappedCPT, "Section fraction mapped");
		
		mapMaker.plot(new File("/tmp"), "assoc_"+reg.name()+"_sects", " ");
	}

	private List<Map<Integer, Double>> scaledNodesToSects;
	private double[] nodeFractMapped;
	private List<Map<Integer, Double>> scaledSectsToNodes;
	private List<Map<Integer, Double>> sectsToNodes;
	private List<Map<Integer, Double>> nodesToSectFracts;
	private double[] sectFractMapped;
	private Set<Integer> sectsMapped;
	private GriddedRegion gridReg;
	
	public InterfaceGridAssociations(List<? extends FaultSection> sects, GriddedRegion gridReg) {
		this.gridReg = gridReg;
		RuptureSurface[] surfs = new RuptureSurface[sects.size()];
		Region[] surfOutlines = new Region[sects.size()];
		Location[] surfCenters = new Location[sects.size()];
		
		double cellWidth = 111d*gridReg.getSpacing();
		
		double maxLen = 0d;
		double[] sectExtents = new double[sects.size()];
		for (int i=0; i<sects.size(); i++) {
			surfs[i] = sects.get(i).getFaultSurface(2d);
			surfOutlines[i] = new Region(surfs[i].getPerimeter(), BorderType.MERCATOR_LINEAR);
			maxLen = Math.max(maxLen, surfs[i].getAveLength());
			Preconditions.checkState(surfs[i] instanceof EvenlyGriddedSurface);
			EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surfs[i];
			surfCenters[i] = gridSurf.get(gridSurf.getNumRows()/2, gridSurf.getNumCols()/2);
			sectExtents[i] = surfOutlines[i].getExtent();
		}
		
		// distance to do real intersection tests
		double minCheckDist = 2*(cellWidth+maxLen);
		
		double halfSpacing = gridReg.getSpacing()*0.5;
		MinMaxAveTracker mappedExtentFractTrack = new MinMaxAveTracker();
		int numMapped = 0;
		scaledNodesToSects = new ArrayList<>(gridReg.getNodeCount());
		nodeFractMapped = new double[gridReg.getNodeCount()];
		scaledSectsToNodes = new ArrayList<>(sects.size());
		sectsToNodes = new ArrayList<>(sects.size());
		sectsMapped = new HashSet<>(sects.size());
		for (int i=0; i<sects.size(); i++) {
			scaledSectsToNodes.add(new HashMap<>());
			sectsToNodes.add(new HashMap<>());
		}
		for (int n=0; n<gridReg.getNodeCount(); n++) {
			Location center = gridReg.getLocation(n);
			Region cell = new Region(new Location(center.lat-halfSpacing, center.lon-halfSpacing),
					new Location(center.lat+halfSpacing, center.lon+halfSpacing));
//			cell.
//			Region.inte
			double fullExtent = cell.getExtent();
			double mappedExtentSum = 0;
			Map<Integer, Double> mappedExtents = null;
			for (int s=0; s<sects.size(); s++) {
				double centerDist = LocationUtils.horzDistanceFast(center, surfCenters[s]);
				if (centerDist < minCheckDist) {
					Region intersection = Region.intersect(cell, surfOutlines[s]);
					if (intersection != null) {
						if (mappedExtents == null)
							mappedExtents = new HashMap<>();
						double extent = intersection.getExtent();
						double fract = extent/fullExtent;
						Preconditions.checkState(fract < 1.02,
								"Bad intersection? %s > %s, f=%s", extent, fullExtent, fract);
						if (fract > 1)
							extent = fullExtent;
						mappedExtentSum += extent;
						double scaledNodeFract = extent/fullExtent;
						mappedExtents.put(s, scaledNodeFract);
						scaledSectsToNodes.get(s).put(n, scaledNodeFract);
						double sectFract = extent/sectExtents[s];
						Preconditions.checkState(sectFract < 1.02,
								"Bad intersection? %s > %s, f=%s", extent, sectExtents[s], sectFract);
						if (sectFract > 1)
							sectFract = 1;
						sectsToNodes.get(s).put(n, sectFract);
						sectsMapped.add(s);
					}
				}
			}
			double mappedFract = mappedExtentSum/fullExtent;
			mappedExtentFractTrack.addValue(mappedFract);
			if (mappedFract > 1) {
				Preconditions.checkState(mappedFract < 1.02);
				for (Integer id : List.copyOf(mappedExtents.keySet())) {
					double scaledNodeFract = mappedExtents.get(id)/mappedFract;
					mappedExtents.put(id, scaledNodeFract);
					scaledSectsToNodes.get(id).put(n, scaledNodeFract);
				}
				mappedFract = 1d;
			}
			if (mappedExtents != null)
				numMapped++;
			scaledNodesToSects.add(mappedExtents);
			nodeFractMapped[n] = mappedFract;
		}
		System.out.println("Mapped "+numMapped+"/"+gridReg.getNodeCount()+" grid nodes");
		System.out.println("Raw node intersection mapping stats (of those mapped):\n\t"+mappedExtentFractTrack);
		mappedExtentFractTrack = new MinMaxAveTracker();
		sectFractMapped = new double[sects.size()];
		for (int s=0; s<sects.size(); s++) {
			Map<Integer, Double> mappings = sectsToNodes.get(s);
			if (!mappings.isEmpty()) {
				double sum = mappings.values().stream().mapToDouble(D->D).sum();
				mappedExtentFractTrack.addValue(sum);
				if (sum > 1d) {
					Preconditions.checkState(sum < 1.02);
					// rounding error, rescale down to one
					for (Integer key : List.copyOf(mappings.keySet()))
						mappings.put(key, mappings.get(key)/sum);
					sum = 1d;
				} else if (sum > 0.99) {
					// rounding error, rescale up to one
					for (Integer key : List.copyOf(mappings.keySet()))
						mappings.put(key, mappings.get(key)/sum);
					sum = 1d;
				}
				sectFractMapped[s] = sum;
			} else {
				mappedExtentFractTrack.addValue(0d);
			}
		}
		System.out.println("Raw section intersection mapping stats:\n\t"+mappedExtentFractTrack);
		
		nodesToSectFracts = new ArrayList<>(gridReg.getNodeCount());
		for (int n=0; n<gridReg.getNodeCount(); n++)
			nodesToSectFracts.add(null);
		for (int s=0; s<sects.size(); s++) {
			for (Map.Entry<Integer, Double> entry : sectsToNodes.get(s).entrySet()) {
				int nodeIdx = entry.getKey();
				Map<Integer, Double> nodeMappings = nodesToSectFracts.get(nodeIdx);
				if (nodeMappings == null) {
					nodeMappings = new HashMap<>();
					nodesToSectFracts.set(nodeIdx, nodeMappings);
				}
				nodeMappings.put(s, entry.getValue());
			}
		}
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Interface Grid Associations";
	}

	@Override
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		Precomputed precomputed = new Precomputed(this);
		precomputed.writeToArchive(output, entryPrefix);
	}

	@Override
	public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		throw new IllegalStateException("Should be loaded as Precomputed");
	}

	@Override
	public Map<Integer, Double> getNodeExtents() {
		Map<Integer, Double> ret = new HashMap<>();
		for (int i=0; i<nodeFractMapped.length; i++)
			if (nodeFractMapped[i] > 0)
				ret.put(i, nodeFractMapped[i]);
		return ret;
	}

	@Override
	public double getNodeFraction(int nodeIdx) {
		return nodeFractMapped[nodeIdx];
	}

	@Override
	public Map<Integer, Double> getScaledNodeFractions(int sectIdx) {
		return scaledSectsToNodes.get(sectIdx);
	}

	@Override
	public Map<Integer, Double> getScaledSectFracsOnNode(int nodeIdx) {
		Map<Integer, Double> ret = scaledNodesToSects.get(nodeIdx);
		if (ret == null)
			return Map.of();
		return ret;
	}

	@Override
	public Map<Integer, Double> getNodeFractions(int sectIdx) {
		return sectsToNodes.get(sectIdx);
	}

	@Override
	public Map<Integer, Double> getSectionFracsOnNode(int nodeIdx) {
		Map<Integer, Double> ret = nodesToSectFracts.get(nodeIdx);
		if (ret == null)
			return Map.of();
		return ret;
	}

	@Override
	public GriddedRegion getRegion() {
		return gridReg;
	}

	@Override
	public Collection<Integer> sectIndices() {
		return sectsMapped;
	}

	@Override
	public Class<? extends ArchivableModule> getLoadingClass() {
		return FaultGridAssociations.Precomputed.class;
	}

}
