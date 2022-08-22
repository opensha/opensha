package org.opensha.sha.earthquake.rupForecastImpl.nshm23.data;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

public class NSHM23_PaleoProbabilityModel extends PaleoProbabilityModel {
	
	private static final Region U3_MODEL_REGION = new CaliforniaRegions.RELM_TESTING();
	
	private static PaleoProbabilityModel u3Model;
	private static PaleoProbabilityModel wasatchModel;
	
	private Map<FaultSection, PaleoProbabilityModel> modelCache;

	@Override
	public double getProbPaleoVisible(double mag, List<FaultSection> rupSections, int sectIndex) {
		// first check this section
		for (FaultSection sect : rupSections) {
			if (sect.getSectionId() == sectIndex) {
				PaleoProbabilityModel model = getModel(sect);
				if (model != null)
					return model.getProbPaleoVisible(mag, rupSections, sectIndex);
				break;
			}
		}
		// this particular section doesn't have a match, check other sections in the rupture
		for (FaultSection sect : rupSections) {
			PaleoProbabilityModel model = getModel(sect);
			if (model != null)
				return model.getProbPaleoVisible(mag, rupSections, sectIndex);
		}
		throw new IllegalStateException(
				"No suitable paleo probability model found for rupture including section "+sectIndex);
	}

	@Override
	public double getProbPaleoVisible(double mag, double distAlongRup) {
		throw new UnsupportedOperationException("Not supported");
	}
	
	private static void checkInitModels() {
		if (u3Model == null) {
			synchronized (NSHM23_PaleoDataLoader.class) {
				if (u3Model == null) {
					try {
						wasatchModel = new WasatchPaleoProbabilityModel();
						u3Model = UCERF3_PaleoProbabilityModel.load();
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			}
		}
	}
	
	private PaleoProbabilityModel getModel(FaultSection sect) {
		if (modelCache == null) {
			synchronized (this) {
				if (modelCache == null)
					modelCache = new ConcurrentHashMap<>();
			}
		}
		PaleoProbabilityModel cached = modelCache.get(sect);
		if (cached == null) {
			cached = doGetModel(sect);
			synchronized (this) {
				modelCache.put(sect, cached);
			}
		}
		return cached;
	}
	
	private static PaleoProbabilityModel doGetModel(FaultSection sect) {
		checkInitModels();
		
		boolean wasatch = sect.getSectionName().toLowerCase().contains("wasatch");
		boolean u3 = false;
		for (Location loc : sect.getFaultTrace()) {
			 if (U3_MODEL_REGION.contains(loc)) {
				 u3 = true;
				 break;
			 }
		}
		
		if (wasatch && u3)
			throw new IllegalStateException("Section "+sect.getSectionId()+". "
					+sect.getSectionName()+" maps to both Wasatch and UCERF3");
		
		if (wasatch)
			return wasatchModel;
		else if (u3)
			return u3Model;
		return null;
	}
	
	/**
	 * This is based on equation 4 of Youngs et al. [2003, A Methodology for Probabilistic Fault
	 * Displacement Hazard Analysis (PFDHA), Earthquake Spectra 19, 191-219] using 
	 * the coefficients they list in their appendix for "Data from Wells and 
	 * Coppersmith (1993) 276 worldwide earthquakes".  Their function has the following
	 * probabilities:
	 * 
	 * mag	prob
	 * 5		0.10
	 * 6		0.45
	 * 7		0.87
	 * 8		0.98
	 * 9		1.00
	 * 
	 * @return
	 */
	public static class WasatchPaleoProbabilityModel extends PaleoProbabilityModel {

		@Override
		public double getProbPaleoVisible(double mag, List<FaultSection> rupSections, int sectIndex) {
			return getProbPaleoVisible(mag, Double.NaN);
		}

		@Override
		public double getProbPaleoVisible(double mag, double distAlongRup) {
			return Math.exp(-12.51+mag*2.053)/(1.0 + Math.exp(-12.51+mag*2.053));
		}
		
	}

}
