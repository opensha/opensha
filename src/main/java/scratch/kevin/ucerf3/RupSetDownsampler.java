package scratch.kevin.ucerf3;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.erf.mean.RuptureCombiner;
import scratch.UCERF3.utils.FaultSystemIO;

public class RupSetDownsampler {
	
	private FaultSystemRupSet rupSet;
	private double deltaMag;
	
	private Map<String, List<Integer>> parentSectsToRupsMap;
	private HashSet<Integer> rupsToKeep;

	public RupSetDownsampler(FaultSystemRupSet rupSet, double deltaMag) {
		this.rupSet = rupSet;
		this.deltaMag = deltaMag;
	}
	
	private void initParentSectsToRupsMap() {
		parentSectsToRupsMap = Maps.newHashMap();
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			String key = getParentsKey(r);
			List<Integer> rups = parentSectsToRupsMap.get(key);
			if (rups == null) {
				rups = Lists.newArrayList();
				parentSectsToRupsMap.put(key, rups);
			}
			rups.add(r);
		}
	}
	
	private String getParentsKey(int rupIndex) {
		List<Integer> parents = Lists.newArrayList(rupSet.getParentSectionsForRup(rupIndex));
		Collections.sort(parents);
		return keyJoin.join(parents);
	}
	
	private static Joiner keyJoin = Joiner.on("_");
	
	public void calculate() {
		initParentSectsToRupsMap();
		int numMag = (int)((9d-5d)/deltaMag + 0.5);
		EvenlyDiscretizedFunc magFunc = new EvenlyDiscretizedFunc(5d, numMag, deltaMag);
		rupsToKeep = new HashSet<Integer>();
		for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++) {
			if (sectIndex % 100 == 0)
				System.out.println("Processing section "+sectIndex+"/"+rupSet.getNumSections()+". "
						+rupsToKeep.size()+"/"+rupSet.getNumRuptures()+" rups so far.");
			int parentID = rupSet.getFaultSectionData(sectIndex).getParentSectionId();
			List<Integer> rups = rupSet.getRupturesForSection(sectIndex);
			for (int r : rups) {
				if (rupsToKeep.contains(r))
					continue;
				List<Integer> sects = rupSet.getSectionsIndicesForRup(r);
				if (sects.get(sects.size()-1) == sectIndex) {
					// switch it so that my section is first
					sects = Lists.newArrayList(sects);
					Collections.reverse(sects);
				}
				if (sects.get(0) != sectIndex)
					// only consider ruptures that start at this section
					continue;
				int lastParent = rupSet.getFaultSectionData(sects.get(sects.size()-1)).getParentSectionId();
				if (lastParent == parentID) {
					// keep all ruptures on my parent section
					rupsToKeep.add(r);
					continue;
				}
				int sectsOnLastParent = getNumSectsOnParent(r, lastParent);
				int maxSectsOnLastParent = 0;
				double myMag = rupSet.getMagForRup(r);
				int myMagIndex = magFunc.getClosestXIndex(myMag);
				double myMagDelta = Math.abs(myMag - magFunc.getX(myMagIndex));
				double minOtherMagDelta = Double.POSITIVE_INFINITY;
				int maxMagIndex = -1;
				List<Integer> otherRupsSameParents = parentSectsToRupsMap.get(getParentsKey(r));
				for (int otherRup : otherRupsSameParents) {
					int count = getNumSectsOnParent(otherRup, lastParent);
					if (count > maxSectsOnLastParent)
						maxSectsOnLastParent = count;
					if (otherRup != r) {
						double otherMag = rupSet.getMagForRup(otherRup);
						int otherMagIndex = magFunc.getClosestXIndex(otherMag);
						if (otherMagIndex > maxMagIndex)
							maxMagIndex = otherMagIndex;
						double otherMagDelta = Math.abs(otherMag - magFunc.getX(myMagIndex));
						minOtherMagDelta = Math.min(minOtherMagDelta, otherMagDelta);
					}
				}
				if (sectsOnLastParent == maxSectsOnLastParent) {
					rupsToKeep.add(r);
					continue;
				}
				if (myMagDelta <= minOtherMagDelta && myMagIndex < maxMagIndex) {
					rupsToKeep.add(r);
					continue;
				}
			}
		}
	}
	
	private int getNumSectsOnParent(int rupIndex, int parentID) {
		int count = 0;
		for (FaultSectionPrefData sect : rupSet.getFaultSectionDataForRupture(rupIndex))
			if (sect.getParentSectionId() == parentID)
				count++;
		return count;
	}
	
	public synchronized List<Integer> getRuptures() {
		if (rupsToKeep == null)
			calculate();
		List<Integer> rups = Lists.newArrayList(rupsToKeep);
		Collections.sort(rups);
		return rups;
	}
	
	public FaultSystemRupSet getDownsampled() {
		List<Integer> rups = getRuptures();
		return new RuptureCombiner.SubsetRupSet(rupSet, rups);
	}

	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/"
				+ "InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		RupSetDownsampler sampler = new RupSetDownsampler(rupSet, 0.05);
		sampler.calculate();
		int orig = rupSet.getNumRuptures();
		int sampled = sampler.rupsToKeep.size();
		double percent = 100d*sampled/orig;
		System.out.println("Retained "+sampled+"/"+orig+" ruptures ("+(float)percent+" %)");
		FaultSystemRupSet sampledRupSet = sampler.getDownsampled();
		FaultSystemIO.writeRupSet(sampledRupSet, new File("/tmp/downsampled.zip"));
	}

}
