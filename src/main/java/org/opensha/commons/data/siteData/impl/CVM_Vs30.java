package org.opensha.commons.data.siteData.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;

import org.opensha.commons.data.siteData.AbstractBinarySiteDataLoader;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.ServerPrefUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.primitives.Booleans;

public class CVM_Vs30 extends AbstractBinarySiteDataLoader implements ParameterChangeListener {
	
	public static final String NAME = "Vs30 from various Community Velocity Models";
	public static final String SHORT_NAME = "CVMVs30";
	
	// CVM4 region
	public static final double minLat = 31;
	public static final double minLon = -121;
	private static final int nx = 1701;
	private static final int ny = 1101;
	
//	public static final double minLat = 30.96;
//	public static final double minLon = -120.85;
//	private static final int nx = 1501;
//	private static final int ny = 1129;
	
	private static final double grid_spacing = 0.005;
	
	public enum CVM {
		CVMS4i26("SCEC Community Velocity Model Version 4, Iteration 26, Vs30", "CVM4i26", "vs30.bin");
		
		private String name;
		private String[] resourcePath;
		private CVM(String name, String... resourcePath) {
			this.name = name;
			this.resourcePath = resourcePath;
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
	
	public static final String DEFAULT_RESOURCE_DIR = "src"+s+"resources"+s+"data"+s+"site"+s;
	
	public static final String SERVLET_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/CVM_Vs30";
	
	private File dataDir;
	
	public static final String CVM_PARAM_NAME = "CVM";
	private EnumParameter<CVM> cvmParam;
	public static final CVM CVM_DEFAULT = CVM.CVMS4i26;
	
	public CVM_Vs30(CVM cvm) throws IOException {
		this(null, cvm, true);
	}

	public CVM_Vs30(CVM cvm, boolean useServlet) throws IOException {
		this(new File(DEFAULT_RESOURCE_DIR), cvm, useServlet);
	}

	public CVM_Vs30(File dataDir, CVM cvm, boolean useServlet) throws IOException {
		super(nx, ny, minLat, minLon, grid_spacing, true, true, TYPE_VS30,
				getDataFile(dataDir, cvm), useServlet);
		
		this.dataDir = dataDir;
		
		cvmParam = new EnumParameter<CVM>(CVM_PARAM_NAME,
				EnumSet.allOf(CVM.class), cvm, null);
		cvmParam.addParameterChangeListener(this);
		paramList.addParameter(cvmParam);
		serverParamsList.addParameter(cvmParam);
		
		updateFile();
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getDataMeasurementType() {
		return TYPE_FLAG_INFERRED;
	}

	@Override
	public String getMetadata() {
		return getDataType()+", Vs30 from various CVM models extracted with UCVM by David Gill";
	}

	@Override
	protected File getDefaultFile(String type) {
		return getDataFile(dataDir, CVM_DEFAULT);
	}
	
	private void updateFile() {
		if (useServlet)
			return;
		String type = getDataType();
		File dataFile = getDataFile(dataDir, cvmParam.getValue());
		try {
			setDataFile(dataFile);
		} catch (FileNotFoundException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
	}
	
	private static File getDataFile(File dataDir, CVM version) {
		if (dataDir == null)
			// servlet mode
			return null;
		Preconditions.checkState(dataDir.exists(), "CVM Data Dir Not Found: "+dataDir.getAbsolutePath());
		Preconditions.checkState(version.resourcePath.length>0);
		File dataFile = dataDir;
		for (String pathElement : version.resourcePath)
			dataFile = new File(dataFile, pathElement);
		
		Preconditions.checkState(dataFile.exists(), "CVM Data File Not Found: "+dataFile.getAbsolutePath());
		return dataFile;
	}

	@Override
	protected String getServletURL(String type) {
		return SERVLET_URL;
	}
	
	public static void main(String[] args) throws IOException {
		CVM_Vs30 cvmh = new CVM_Vs30(CVM.CVMS4i26, false);
		
		System.out.println(cvmh.getApplicableRegion());
		FileWriter fw = new FileWriter(new File("/tmp/cvmh_grid_locs.txt"));
		for (long pos=0; pos<=cvmh.maxFilePos; pos+=4) {
			Double val = cvmh.getValue(pos);
			long x = cvmh.calc.calcFileX(pos);
			long y = cvmh.calc.calcFileY(pos);
			Location loc = cvmh.calc.getLocationForPoint(x, y);
//			System.out.println(loc.getLatitude() + ", " + loc.getLongitude() + ": " + val);
			fw.write((float)loc.getLatitude()+"\t"+(float)loc.getLongitude()+"\t"+val.floatValue()+"\n");
		}
		fw.close();
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == cvmParam) {
			updateFile();
		}
	}

}
