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

import org.opensha.nshmp.shaded.gmm.NshmpGmm;
import org.opensha.nshmp.shaded.gmm.NshmpGmmInput;
import org.opensha.nshmp.shaded.gmm.NshmpGmmInput.Constraints;
import org.opensha.nshmp.shaded.gmm.NshmpGmmInput.Field;
import org.opensha.nshmp.shaded.gmm.NshmpGroundMotion;
import org.opensha.nshmp.shaded.gmm.NshmpGroundMotionModel;
import org.opensha.nshmp.shaded.gmm.NshmpGroundMotions;
import org.opensha.nshmp.shaded.gmm.NshmpImt;
import org.opensha.nshmp.shaded.gmm.NshmpUsgsPrviBackbone2025;
import org.opensha.nshmp.shaded.tree.NshmpBranch;
import org.opensha.nshmp.shaded.tree.NshmpLogicTree;

class InitialTests {

	public static void main(String[] args) {
		GroundMotionLogicTreeFilter filter = null;
		
//		NshmpGmm[] gmms = NshmpGmm.values();
		
//		NshmpGmm[] gmms = {
//				NshmpGmm.ASK_14,
//				NshmpGmm.ASK_14_BASE,
//				NshmpGmm.ASK_14_BASIN,
//				NshmpGmm.ASK_14_CYBERSHAKE,
//				NshmpGmm.ASK_14_VS30_MEASURED
//		};
		
//		NshmpGmm[] gmms = {
//				NshmpGmm.COMBINED_ACTIVE_CRUST_2023,
//				NshmpGmm.COMBINED_ACTIVE_CRUST_2023_LOS_ANGELES,
//				NshmpGmm.COMBINED_ACTIVE_CRUST_2023_SAN_FRANCISCO,
//		};
		
//		NshmpGmm[] gmms = {
//				NshmpGmm.COMBINED_INTERFACE_2023_SEATTLE
//		};
		
//		NshmpGmm[] gmms = {
//				NshmpGmm.PRVI_2025_ACTIVE_CRUST,
//				NshmpGmm.PRVI_2025_ACTIVE_CRUST_ADJUSTED,
//				NshmpGmm.PRVI_2025_INTERFACE,
//				NshmpGmm.PRVI_2025_INTERFACE_ADJUSTED,
//				NshmpGmm.PRVI_2025_INTRASLAB,
//				NshmpGmm.PRVI_2025_INTRASLAB_ADJUSTED,
//				NshmpGmm.TOTAL_TREE_PRVI_ACTIVE_CRUST_2025,
//				NshmpGmm.TOTAL_TREE_PRVI_INTERFACE_2025,
//				NshmpGmm.TOTAL_TREE_PRVI_INTRASLAB_2025,
//				NshmpGmm.COMBINED_PRVI_ACTIVE_CRUST_2025,
//				NshmpGmm.COMBINED_PRVI_INTERFACE_2025,
//				NshmpGmm.COMBINED_PRVI_INTRASLAB_2025,
//		};
//		filter = new GroundMotionLogicTreeFilter.StringMatching(NshmpGroundMotions.EPI_LO, NshmpUsgsPrviBackbone2025.SIGMA_PRVI_ID);
		
		NshmpGmm[] gmms = { NshmpGmm.TOTAL_TREE_CONUS_STABLE_CRUST_2023 };
		
//		NshmpGroundMotionModel gmm = NshmpGmm.ASK_14.instance(NshmpImt.PGA);
//		NshmpGroundMotionModel gmm = NshmpGmm.ASK_14_BASE.instance(NshmpImt.PGA);
		
		List<NSHMP_GMM_Wrapper> wrappers = new ArrayList<>();
		for (NshmpGmm gmm : gmms) {
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
//		System.exit(0);
		
//		wrappers.add((NSHMP_GMM_Wrapper)AttenRelRef.USGS_NSHM23_ACTIVE.get());
//		
//		NshmpGmmInput input = NshmpGmmInput.builder().withDefaults().build();
		
		for (NSHMP_GMM_Wrapper wrapper : wrappers) {
			System.out.println("GMM: "+wrapper.getName());
			wrapper.setParamDefaults();
			
			NshmpLogicTree<NshmpGroundMotion> result;
			
//			System.out.print("\tBuilding instance...");
//			NshmpGroundMotionModel gmm;
//			try {
//				gmm = gmmRef.instance(NshmpImt.PGA);
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
//			System.out.println("\tReturned NshmpLogicTree:");
//			for (NshmpBranch<NshmpGroundMotion> gmBranch : result) {
//				double weight = gmBranch.weight();
//				NshmpGroundMotion value = gmBranch.value();
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
				NshmpGroundMotionModel instance = ((NSHMP_GMM_Wrapper.Single)wrapper).getCurrentGMM_Instance();
				System.out.println("\tInstance class: "+instance.getClass().getName());
				Class<?> superclass = instance.getClass().getSuperclass();
				while (getAllInterfaces(superclass).contains(NshmpGroundMotionModel.class)) {
//				while (true) {
					System.out.println("\t\tSuperclass:\t"+superclass.getName());
					superclass = superclass.getSuperclass();
					if (superclass == null)
						break;
				}
			}
//			while (NshmpGroundMotionModel.class.instan)
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
			
			System.out.println("\tCurrent input: "+wrapper.getCurrentGmmInput());
			
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
			
			System.out.println("\tReturned NshmpLogicTree:");
			for (NshmpBranch<NshmpGroundMotion> gmBranch : result) {
				double weight = gmBranch.weight();
				NshmpGroundMotion value = gmBranch.value();
				String id = gmBranch.id();
				System.out.println("\t\t"+id+" (weight="+(float)weight+"): "+value);
			}
			// use this if you want Peter's default formatting:
//			System.out.println(result);
			
			System.out.println();
		}
		
		NSHMP_GMM_Wrapper listGMM = (NSHMP_GMM_Wrapper)AttenRelRef.USGS_NSHM23_ACTIVE.get();
		listGMM.setParamDefaults();
		System.out.println("Testing list GMM: "+listGMM.getName());
		NshmpLogicTree<NshmpGroundMotion> result = listGMM.getGroundMotionTree();
		System.out.println("\tReturned NshmpLogicTree:");
		for (NshmpBranch<NshmpGroundMotion> gmBranch : result) {
			double weight = gmBranch.weight();
			NshmpGroundMotion value = gmBranch.value();
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
