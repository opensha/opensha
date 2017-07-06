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

public class CVMHBasinDepth extends AbstractBinarySiteDataLoader implements ParameterChangeListener {
	
	public static final String NAME = "SCEC/Harvard Community Velocity Model Version 11.9.0 Basin Depth"; // TODO
	public static final String SHORT_NAME = "CVMH";
	
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
	
	public static final String DEFAULT_DATA_DIR = "src"+s+"resources"+s+"data"+s+"site"+s+"CVMH"+s;
	public static final String DEPTH_2_5_FILE_PREFIX = "depth_2.5";
	public static final String DEPTH_1_0_FILE_PREFIX = "depth_1.0";
	
	public static final String SERVLET_2_5_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/CVMH_2_5";
	public static final String SERVLET_1_0_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/CVMH_1_0";
	
	// versions
	public enum Version {
		VER_11_9_1("11.9.1 accessed 1/17/14 with UCVM 13.9.0", "11.9.1", new boolean[] { true, false}),
		VER_11_9_0("11.9.0 accessed 5/29/12 with UCVM 12.2.0", "11.9.0", new boolean[] { true });
		
		private String name, dirName;
		private boolean[] gtls;
		private Version(String name, String dirName, boolean[] gtls) {
			this.name = name;
			this.dirName = dirName;
			this.gtls = gtls;
		}
		
		@Override
		public String toString() {
			return name;
		}
		
		boolean isGTLModeSupported(boolean includeGTL) {
			return Booleans.contains(gtls, includeGTL);
		}
	}
	
	private File dataDir;
	
	public static final String VERSION_PARAM_NAME = "Version";
	private EnumParameter<Version> versionParam;
	public static final Version VERSION_DEFAULT = Version.VER_11_9_1;
	
	public static final String GTL_PARAM_NAME = "Include Geotechnical Layer (GTL)";
	private BooleanParameter gtlParam;
	public static final Boolean GTL_PARAM_DEFAULT = true;
	
	public CVMHBasinDepth(String type) throws IOException {
		this(type, null, true);
	}

	public CVMHBasinDepth(String type, File dataDir, boolean useServlet) throws IOException {
		super(nx, ny, minLat, minLon, grid_spacing, true, true, type,
				getDataFile(type, dataDir, VERSION_DEFAULT, GTL_PARAM_DEFAULT), useServlet);
		
		this.dataDir = dataDir;
		
		versionParam = new EnumParameter<CVMHBasinDepth.Version>(VERSION_PARAM_NAME,
				EnumSet.allOf(Version.class), VERSION_DEFAULT, null);
		versionParam.addParameterChangeListener(this);
		paramList.addParameter(versionParam);
		serverParamsList.addParameter(versionParam);
		
		gtlParam = new BooleanParameter(GTL_PARAM_NAME, GTL_PARAM_DEFAULT);
		gtlParam.addParameterChangeListener(this);
		paramList.addParameter(gtlParam);
		serverParamsList.addParameter(gtlParam);
		
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
		return getDataType()+", CVMH 11.9.0 extracted with UCVM 12.2.0 on May 21 2012 by Patrick Small";
	}

	@Override
	protected File getDefaultFile(String type) {
		return getDataFile(type, dataDir, VERSION_DEFAULT, GTL_PARAM_DEFAULT);
	}
	
	private void updateFile() {
		if (useServlet)
			return;
		String type = getDataType();
		File dataFile = getDataFile(type, dataDir, versionParam.getValue(), gtlParam.getValue());
		try {
			setDataFile(dataFile);
		} catch (FileNotFoundException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
	}
	
	private static File getDataFile(String type, File dataDir, Version version, boolean includeGTL) {
		if (dataDir == null)
			// servlet mode
			return null;
		Preconditions.checkState(dataDir.exists(), "CVMH Data Dir Not Found: "+dataDir.getAbsolutePath());
		File subDir = new File(dataDir, version.dirName);
		Preconditions.checkState(subDir.exists(), "CVMH Sub Dir Not Found: "+subDir.getAbsolutePath());
		
		String prefix;
		if (type.equals(TYPE_DEPTH_TO_1_0))
			prefix = DEPTH_1_0_FILE_PREFIX;
		else if (type.equals(TYPE_DEPTH_TO_2_5))
			prefix = DEPTH_2_5_FILE_PREFIX;
		else
			throw new IllegalStateException("Unsupported type: "+type);
		
		File dataFile;
		if (includeGTL)
			dataFile = new File(subDir, prefix+"_with_gtl.bin");
		else
			dataFile = new File(subDir, prefix+"_no_gtl.bin");
		Preconditions.checkState(dataFile.exists(), "CVMH Data File Not Found: "+dataFile.getAbsolutePath());
		return dataFile;
	}

	@Override
	protected String getServletURL(String type) {
		if (type.equals(TYPE_DEPTH_TO_1_0))
			return SERVLET_1_0_URL;
		return SERVLET_2_5_URL;
	}
	
	public static void main(String[] args) throws IOException {
		CVMHBasinDepth cvmh = new CVMHBasinDepth(TYPE_DEPTH_TO_2_5, null, false);
		
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
		if (event.getParameter() == versionParam) {
			Version newVersion = versionParam.getValue();
			boolean gtlVal = gtlParam.getValue();
			if (!newVersion.isGTLModeSupported(gtlVal)) {
				// we need to swap the gtl setting and disable the parameter
				gtlParam.removeParameterChangeListener(this);
				gtlParam.setValue(!gtlVal);
				try {
					// always disable param if it's not supported
					gtlParam.getEditor().setEnabled(false);
					gtlParam.getEditor().refreshParamEditor();
				} catch (Exception e) {
					// do nothing, this will prevent nastyness in headless environments
				}
				gtlParam.addParameterChangeListener(this);
			} else {
				// it is supported, make sure it's enabled but only if both modes supported
				try {
					gtlParam.getEditor().setEnabled(newVersion.isGTLModeSupported(!gtlVal));
					gtlParam.getEditor().refreshParamEditor();
				} catch (Exception e) {
					// do nothing, this will prevent nastyness in headless environments
				}
			}
		} else if (event.getParameter() == gtlParam) {
			updateFile();
		}
		updateFile();
	}

}
