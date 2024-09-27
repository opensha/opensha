package org.opensha.sha.imr.attenRelImpl.nshmp.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.opensha.commons.param.Parameter;
import org.opensha.sha.imr.attenRelImpl.nshmp.GroundMotionLogicTreeFilter;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GroundMotion;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.gmm.GroundMotions;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.gmm.UsgsPrviBackbone2025;
import gov.usgs.earthquake.nshmp.tree.Branch;
import gov.usgs.earthquake.nshmp.tree.LogicTree;

class InitialTests {

	public static void main(String[] args) {
		GroundMotionLogicTreeFilter filter = null;
		
//		Gmm[] gmms = Gmm.values();
		
//		Gmm[] gmms = {
//				Gmm.ASK_14,
//				Gmm.ASK_14_BASE,
//				Gmm.ASK_14_BASIN,
//				Gmm.ASK_14_CYBERSHAKE,
//				Gmm.ASK_14_VS30_MEASURED
//		};
		
		Gmm[] gmms = {
				Gmm.COMBINED_ACTIVE_CRUST_2023,
				Gmm.COMBINED_ACTIVE_CRUST_2023_LOS_ANGELES,
				Gmm.COMBINED_ACTIVE_CRUST_2023_SAN_FRANCISCO,
		};
		
//		Gmm[] gmms = {
//				Gmm.COMBINED_INTERFACE_2023_SEATTLE
//		};
		
//		Gmm[] gmms = {
//				Gmm.USGS_PRVI_ACTIVE_CRUST,
//				Gmm.USGS_PRVI_ACTIVE_CRUST_ADJUSTED,
//				Gmm.USGS_PRVI_INTERFACE,
//				Gmm.USGS_PRVI_INTRASLAB,
//		};
//		filter = new GroundMotionLogicTreeFilter.StringMatching(GroundMotions.EPI_LO, UsgsPrviBackbone2025.SIGMA_PRVI_ID);
		
//		GroundMotionModel gmm = Gmm.ASK_14.instance(Imt.PGA);
//		GroundMotionModel gmm = Gmm.ASK_14_BASE.instance(Imt.PGA);
		
		GmmInput input = GmmInput.builder().withDefaults().build();
		
		for (Gmm gmmRef : gmms) {
			System.out.println("GMM: "+gmmRef.name()+": "+gmmRef);
			
			LogicTree<GroundMotion> result;
			
//			System.out.print("\tBuilding instance...");
//			GroundMotionModel gmm;
//			try {
//				gmm = gmmRef.instance(Imt.PGA);
//				System.out.println("Success!");
//			} catch (Exception e) {
//				System.out.println("FAILED: "+e.getMessage());
//				continue;
//			}
//			
//			System.out.print("\tCalculating for PGA defaults...");
//			try {
//				result = gmm.calc(input);
//				System.out.println("Success!");
//			} catch (Exception e) {
//				System.out.println("FAILED: "+e.getMessage());
//				continue;
//			}
//			
//			System.out.println("\tReturned LogicTree:");
//			for (Branch<GroundMotion> gmBranch : result) {
//				double weight = gmBranch.weight();
//				GroundMotion value = gmBranch.value();
//				String id = gmBranch.id();
//				System.out.println("\t\t"+id+" (weight="+(float)weight+"): "+value);
//			}
			
			System.out.print("\tBuilding wrapped instance...");
			NSHMP_GMM_Wrapper wrapper;
			try {
				wrapper = new NSHMP_GMM_Wrapper(gmmRef);
				wrapper.setParamDefaults();
				System.out.println("Success!");
			} catch (Exception e) {
				System.out.flush();
				System.err.println("FAILED: "+e.getMessage());
				System.err.flush();
				System.out.println();
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {}
				continue;
			}
			
			if (filter != null)
				wrapper.setGroundMotionTreeFilter(filter);;
			
			GroundMotionModel instance = wrapper.getCurrentGMM_Instance();
			System.out.println("\tInstance class: "+instance.getClass().getName());
			Class<?> superclass = instance.getClass().getSuperclass();
			while (getAllInterfaces(superclass).contains(GroundMotionModel.class)) {
//			while (true) {
				System.out.println("\t\tSuperclass:\t"+superclass.getName());
				superclass = superclass.getSuperclass();
				if (superclass == null)
					break;
			}
//			while (GroundMotionModel.class.instan)
//			instance.getClass().super
			
			System.out.println("\tParameter List:");
			System.out.println("\tRupture Parms:");
			for (Parameter<?> param : wrapper.getEqkRuptureParams())
				System.out.println("\t\t"+param.getName()+":\tdefault="+param.getValue());
			System.out.println("\tProp Effect Parms:");
			for (Parameter<?> param : wrapper.getPropagationEffectParams())
				System.out.println("\t\t"+param.getName()+":\tdefault="+param.getValue());
			System.out.println("\tOther Parms:");
			for (Parameter<?> param : wrapper.getOtherParams())
				System.out.println("\t\t"+param.getName()+":\tdefault="+param.getValue());
			System.out.println("\tSite Parms:");
			for (Parameter<?> param : wrapper.getSiteParams())
				System.out.println("\t\t"+param.getName()+":\tdefault="+param.getValue());
			
			System.out.print("\tCalculating for "+wrapper.getIntensityMeasure().getName()+" defaults...");
			try {
				result = wrapper.getGroundMotionTree();
				System.out.println("Success!");
			} catch (Exception e) {
				e.printStackTrace();
				System.out.flush();
				System.err.println("FAILED: "+e.getMessage());
				System.err.flush();
				System.out.println();
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {}
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
	
	static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
		Set<Class<?>> res = getAllDirectInterfaces(clazz);
		
		Class<?> superclass = clazz.getSuperclass();
		while (superclass != null) {
			res.addAll(getAllDirectInterfaces(superclass));
			superclass = superclass.getSuperclass();
		}
		return res;
	}

	static Set<Class<?>> getAllDirectInterfaces(Class<?> clazz) {
		Set<Class<?>> res = new HashSet<Class<?>>();
		Class<?>[] interfaces = clazz.getInterfaces();

		if (interfaces.length > 0) {
			res.addAll(Arrays.asList(interfaces));

			for (Class<?> interfaze : interfaces) {
				res.addAll(getAllDirectInterfaces(interfaze));
			}
		}

		return res;
	}

}
