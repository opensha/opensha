package org.opensha.sha.imr.attenRelImpl.nshmp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.opensha.commons.param.Parameter;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.attenRelImpl.nshmp.GroundMotionLogicTreeFilter;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Field;
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
		
//		Gmm[] gmms = {
//				Gmm.COMBINED_ACTIVE_CRUST_2023,
//				Gmm.COMBINED_ACTIVE_CRUST_2023_LOS_ANGELES,
//				Gmm.COMBINED_ACTIVE_CRUST_2023_SAN_FRANCISCO,
//		};
		
//		Gmm[] gmms = {
//				Gmm.COMBINED_INTERFACE_2023_SEATTLE
//		};
		
		Gmm[] gmms = {
				Gmm.PRVI_2025_ACTIVE_CRUST,
				Gmm.PRVI_2025_ACTIVE_CRUST_ADJUSTED,
				Gmm.PRVI_2025_INTERFACE,
				Gmm.PRVI_2025_INTERFACE_ADJUSTED,
				Gmm.PRVI_2025_INTRASLAB,
				Gmm.PRVI_2025_INTRASLAB_ADJUSTED,
				Gmm.TOTAL_TREE_PRVI_ACTIVE_CRUST_2025,
				Gmm.TOTAL_TREE_PRVI_INTERFACE_2025,
				Gmm.TOTAL_TREE_PRVI_INTRASLAB_2025,
				Gmm.COMBINED_PRVI_ACTIVE_CRUST_2025,
				Gmm.COMBINED_PRVI_INTERFACE_2025,
				Gmm.COMBINED_PRVI_INTRASLAB_2025,
		};
//		filter = new GroundMotionLogicTreeFilter.StringMatching(GroundMotions.EPI_LO, UsgsPrviBackbone2025.SIGMA_PRVI_ID);
		
//		GroundMotionModel gmm = Gmm.ASK_14.instance(Imt.PGA);
//		GroundMotionModel gmm = Gmm.ASK_14_BASE.instance(Imt.PGA);
		
		List<NSHMP_GMM_Wrapper> wrappers = new ArrayList<>();
		for (Gmm gmm : gmms) {
			try {
				wrappers.add(new NSHMP_GMM_Wrapper.Single(gmm));
				
				System.out.println("GMM: "+gmm);
				Constraints constraints = gmm.constraints();
				
				for (Field field : Field.values()) {
					Optional<?> fieldConstr = constraints.get(field);
					if (fieldConstr.isPresent())
						System.out.println("\t"+field+":\t"+fieldConstr.get());
					else
						System.out.println("\t"+field+":\t(missing)");
				}
			} catch (Exception e) {
				System.out.flush();
				System.err.println("FAILED for "+gmm+": "+e.getMessage());
				System.err.flush();
				System.out.println();
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {}
				continue;
			}
		}
		System.exit(0);
		
		wrappers.add((NSHMP_GMM_Wrapper)AttenRelRef.USGS_NSHM23_ACTIVE.get());
		
		GmmInput input = GmmInput.builder().withDefaults().build();
		
		for (NSHMP_GMM_Wrapper wrapper : wrappers) {
			System.out.println("GMM: "+wrapper.getName());
			wrapper.setParamDefaults();
			
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
//			
//			System.out.print("\tBuilding wrapped instance...");
//			NSHMP_GMM_Wrapper.Single wrapper;
//			try {
//				wrapper = new NSHMP_GMM_Wrapper.Single(gmmRef);
//				System.out.println("Success!");
//			} catch (Exception e) {
//				System.out.flush();
//				System.err.println("FAILED: "+e.getMessage());
//				System.err.flush();
//				System.out.println();
//				try {
//					Thread.sleep(100);
//				} catch (InterruptedException e1) {}
//				continue;
//			}
			
			if (filter != null)
				wrapper.setGroundMotionTreeFilter(filter);
			
			if (wrapper instanceof NSHMP_GMM_Wrapper.Single) {
				GroundMotionModel instance = ((NSHMP_GMM_Wrapper.Single)wrapper).getCurrentGMM_Instance();
				System.out.println("\tInstance class: "+instance.getClass().getName());
				Class<?> superclass = instance.getClass().getSuperclass();
				while (getAllInterfaces(superclass).contains(GroundMotionModel.class)) {
//				while (true) {
					System.out.println("\t\tSuperclass:\t"+superclass.getName());
					superclass = superclass.getSuperclass();
					if (superclass == null)
						break;
				}
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
		
		NSHMP_GMM_Wrapper listGMM = (NSHMP_GMM_Wrapper)AttenRelRef.USGS_NSHM23_ACTIVE.get();
		listGMM.setParamDefaults();
		System.out.println("Testing list GMM: "+listGMM.getName());
		LogicTree<GroundMotion> result = listGMM.getGroundMotionTree();
		System.out.println("\tReturned LogicTree:");
		for (Branch<GroundMotion> gmBranch : result) {
			double weight = gmBranch.weight();
			GroundMotion value = gmBranch.value();
			String id = gmBranch.id();
			System.out.println("\t\t"+id+" (weight="+(float)weight+"): "+value);
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
