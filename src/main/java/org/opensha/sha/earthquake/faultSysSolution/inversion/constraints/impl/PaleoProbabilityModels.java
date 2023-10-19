package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.data.NSHM23_PaleoProbabilityModel;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

public enum PaleoProbabilityModels implements ShortNamed, Supplier<PaleoProbabilityModel> {
	
	UCERF3("UCERF3", "UCERF3") {
		
		@Override
		public PaleoProbabilityModel doGet() throws IOException {
			return UCERF3_PaleoProbabilityModel.load();
		}
	},
	YOUNGS_2003("Youngs (2003)", "Youngs03") {
		
		@Override
		protected PaleoProbabilityModel doGet() {
			return new NSHM23_PaleoProbabilityModel.WasatchPaleoProbabilityModel();
		}
	},
	NSHM23("NSHM23 (UCERF3 for CA, Youngs03 for Wasatch)", "NSHM23") {
		@Override
		protected PaleoProbabilityModel doGet() {
			return new NSHM23_PaleoProbabilityModel();
		}
	},
	NONE("None (all ruptures fully observed)", "None)") {
		@Override
		protected PaleoProbabilityModel doGet() throws IOException {
			return new NoneModel();
		}
	};
	
	public static PaleoProbabilityModels DEFAULT = UCERF3;
	
	private String name;
	private String shortName;
	
	private PaleoProbabilityModel model;
	
	private PaleoProbabilityModels(String name, String shortName) {
		this.name = name;
		this.shortName = shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getShortName() {
		return shortName;
	}
	
	protected abstract PaleoProbabilityModel doGet() throws IOException;

	@Override
	public PaleoProbabilityModel get() {
		if (model == null) {
			synchronized (this) {
				if (model == null) {
					try {
						model = doGet();
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			}
		}
		return model;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public static class NoneModel extends PaleoProbabilityModel {
		
		@Override
		public double getProbPaleoVisible(FaultSystemRupSet rupSet, int rupIndex, int sectIndex) {
			return 1d;
		}

		@Override
		public double getProbPaleoVisible(double mag, List<? extends FaultSection> rupSections, int sectIndex) {
			return 1d;
		}

		@Override
		public double getProbPaleoVisible(double mag, double distAlongRup) {
			return 1d;
		}
		
	}

}
