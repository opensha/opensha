package org.opensha.sha.imr.attenRelImpl.nshmp;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GroundMotion;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.tree.Branch;
import gov.usgs.earthquake.nshmp.tree.LogicTree;

public class InitialTests {

	public static void main(String[] args) {
		Gmm[] gmms = Gmm.values();
//		GroundMotionModel gmm = Gmm.ASK_14.instance(Imt.PGA);
//		GroundMotionModel gmm = Gmm.ASK_14_BASE.instance(Imt.PGA);
		
		GmmInput input = GmmInput.builder().withDefaults().build();
		
		for (Gmm gmmRef : gmms) {
			System.out.println("GMM: "+gmmRef);
			System.out.print("\tBuilding instance...");
			GroundMotionModel gmm;
			try {
				gmm = gmmRef.instance(Imt.PGA);
				System.out.println("Success!");
			} catch (Exception e) {
				System.out.println("FAILED: "+e.getMessage());
				continue;
			}
			
			System.out.print("\tCalculating for PGA defaults...");
			LogicTree<GroundMotion> result;
			try {
				result = gmm.calc(input);
				System.out.println("Success!");
			} catch (Exception e) {
				System.out.println("FAILED: "+e.getMessage());
				continue;
			}
			
			System.out.println("\tReturned LogicTree:");
			for (Branch<GroundMotion> gmBranch : result) {
				double weight = gmBranch.weight();
				GroundMotion value = gmBranch.value();
				String id = gmBranch.id();
				System.out.println("\t\t"+id+" (weight="+(float)weight+"): "+value);
			}
			
			System.out.println();
		}
	}

}
