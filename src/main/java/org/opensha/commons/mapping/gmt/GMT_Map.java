/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.mapping.gmt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.elements.CoastAttributes;
import org.opensha.commons.mapping.gmt.elements.PSText;
import org.opensha.commons.mapping.gmt.elements.PSXYPolygon;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbol;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbolSet;
import org.opensha.commons.mapping.gmt.elements.TopographicSlopeFile;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.cybershake.maps.GMT_InterpolationSettings;

public class GMT_Map implements Serializable {
	
	/**
	 * default serial version UID
	 */
	private static final long serialVersionUID = 1l;

	private Region region;
	
	private String cptFile = null;
	private String cptCustomFileName = "cptFile.cpt";
	private CPT cpt = null;
	private boolean rescaleCPT = true;
	private double griddedDataInc;
	private GeoDataSet griddedData = null;
	
	private String customGRDPath;
	private String customIntenPath;
	
	public enum HighwayFile {
		ALL			("CA All", "ca_hiwys.all.xy"),
		MAIN		("CA Main", "ca_hiwys.main.xy"),
		OTHER		("CA Other", "ca_hiwys.other.xy");
		
		private final String name;
		private final String fileName;
		HighwayFile(String name, String fileName) {
			this.name = name;
			this.fileName = fileName;
		}
		
		public String fileName() { return fileName; }
		public String description() { return name; }
	}
	private HighwayFile highwayFile = null;
	
	public static Region ca_topo_region  = new Region(
					new Location(32, -126),
					new Location(43, -115));
	
	public static Region us_topo_region  = new Region(
					new Location(20, -128),
					new Location(52, -60));
	private TopographicSlopeFile topoResolution = null;
	
	private CoastAttributes coast = new CoastAttributes();
	
	private double imageWidth = 6.5;
	
	private boolean hideColorbar = false;
	private String customLabel = null;
	private Integer labelSize = null;
	private Integer labelTickSize = null;
	
	private Double customScaleMin = null;
	private Double customScaleMax = null;
	
	private boolean cptEqualSpacing = false;
	private Double cptCustomInterval = null;
	
	private int dpi = 72;
	
	private boolean useGMTSmoothing = true;
	private boolean useGRDView = false;
	
	private boolean blackBackground = true;
	
	private boolean logPlot = false;
	
	private boolean drawScaleKM = true;
	
	private String xyzFileName = GMT_MapGenerator.DEFAULT_XYZ_FILE_NAME;
	private String psFileName = GMT_MapGenerator.DEFAULT_PS_FILE_NAME;
	private String pdfFileName = GMT_MapGenerator.DEFAULT_PDF_FILE_NAME;
	private String pngFileName = GMT_MapGenerator.DEFAULT_PNG_FILE_NAME;
	private String jpgFileName = GMT_MapGenerator.DEFAULT_JPG_FILE_NAME;
	
	private String gmtScriptFileName = GMT_MapGenerator.DEFAULT_GMT_SCRIPT_NAME;
	
	private ArrayList<PSXYSymbol> xySymbols = new ArrayList<PSXYSymbol>();
	private ArrayList<PSXYPolygon> xyLines = new ArrayList<PSXYPolygon>();
	private ArrayList<PSText> xyText = new ArrayList<PSText>();
	private PSXYSymbolSet xySymbolSet = null;
	
	private boolean generateKML = false;
	
	// scatter support
	private GMT_InterpolationSettings interpSettings;
	
	private boolean maskIfNotRectangular = false;
	
	// if non zero, will draw contour lines
	private double contourIncrement = 0d;
	private boolean contourOnly = false;
	
	private Map<String, String> gmtSetVals;
	
	public GMT_Map(Region region, GeoDataSet griddedData,
			double griddedDataInc, String cptFile) {
		this.region = region;
		setGriddedData(griddedData, griddedDataInc, cptFile);
	}
	
	public GMT_Map(Region region, GeoDataSet griddedData,
			double griddedDataInc, CPT cpt) {
		this.region = region;
		setGriddedData(griddedData, griddedDataInc, cpt);
	}
	
	/**
	 * Set the gridded XYZ dataset for this map
	 * 
	 * @param griddedData - XYZ dataset
	 * @param griddedDataInc - Degree spacing of dataset
	 * @param cptFile - CPT file
	 */
	public void setGriddedData(GeoDataSet griddedData, double griddedDataInc, String cptFile) {
		this.griddedData = griddedData;
		this.griddedDataInc = griddedDataInc;
		this.cptFile = cptFile;
		this.cpt = null;
	}
	
	/**
	 * Set the gridded XYZ dataset for this map
	 * 
	 * @param griddedData - XYZ dataset
	 * @param griddedDataInc - Degree spacing of dataset
	 * @param cpt - CPT object
	 */
	public void setGriddedData(GeoDataSet griddedData, double griddedDataInc, CPT cpt) {
		this.griddedData = griddedData;
		this.griddedDataInc = griddedDataInc;
		this.cptFile = null;
		this.cpt = cpt;
	}

	public Region getRegion() {
		return region;
	}

	public void setRegion(Region region) {
		this.region = region;
	}

	public String getCptFile() {
		return cptFile;
	}

	public void setCptFile(String cptFile) {
		this.cptFile = cptFile;
	}
	
	public void setCustomCptFileName(String cptCustomFileName) {
		this.cptCustomFileName = cptCustomFileName;
	}
	
	public String getCustomCptFileName() {
		return cptCustomFileName;
	}

	public CPT getCpt() {
		return cpt;
	}

	public void setCpt(CPT cpt) {
		this.cpt = cpt;
	}
	
	public boolean isRescaleCPT() {
		return rescaleCPT;
	}
	
	public void setRescaleCPT(boolean rescaleCPT) {
		this.rescaleCPT = rescaleCPT;
	}
	
	public boolean isCPTEqualSpacing() {
		return cptEqualSpacing;
	}
	
	public void setCPTEqualSpacing(boolean cptEqualSpacing) {
		this.cptEqualSpacing = cptEqualSpacing;
	}
	
	public Double getCPTCustomInterval() {
		return cptCustomInterval;
	}
	
	public void setCPTCustomInterval(Double cptCustomInterval) {
		this.cptCustomInterval = cptCustomInterval;
	}

	public double getGriddedDataInc() {
		return griddedDataInc;
	}

	public void setGriddedDataInc(double griddedDataInc) {
		this.griddedDataInc = griddedDataInc;
	}

	public GeoDataSet getGriddedData() {
		return griddedData;
	}

	public void setGriddedData(GeoDataSet griddedData) {
		this.griddedData = griddedData;
	}

	public HighwayFile getHighwayFile() {
		return highwayFile;
	}

	public void setHighwayFile(HighwayFile highwayFile) {
		this.highwayFile = highwayFile;
	}

	public TopographicSlopeFile getTopoResolution() {
		return topoResolution;
	}

	public void setTopoResolution(TopographicSlopeFile topoResolution) {
		this.topoResolution = topoResolution;
	}

	public CoastAttributes getCoast() {
		return coast;
	}

	public void setCoast(CoastAttributes coast) {
		this.coast = coast;
	}

	public double getImageWidth() {
		return imageWidth;
	}

	public void setImageWidth(double imageWidth) {
		this.imageWidth = imageWidth;
	}

	public String getCustomLabel() {
		return customLabel;
	}

	public void setCustomLabel(String customLabel) {
		this.customLabel = customLabel;
	}
	
	public void setHideColorbar(boolean hideColorbar) {
		this.hideColorbar = hideColorbar;
	}
	
	public boolean isHideColorbar() {
		return hideColorbar;
	}
	
	public void setLabelSize(Integer labelSize) {
		this.labelSize = labelSize;
	}
	
	public Integer getLabelSize() {
		return labelSize;
	}
	
	public void setLabelTickSize(Integer labelTickSize) {
		this.labelTickSize = labelTickSize;
	}
	
	public Integer getLabelTickSize() {
		return labelTickSize;
	}
	
	public boolean isCustomScale() {
		return customScaleMin != null && customScaleMax != null && customScaleMin < customScaleMax;
	}
	
	public void clearCustomScale() {
		customScaleMin = null;
		customScaleMax = null;
	}

	public Double getCustomScaleMin() {
		return customScaleMin;
	}

	public void setCustomScaleMin(Double customScaleMin) {
		this.customScaleMin = customScaleMin;
	}

	public Double getCustomScaleMax() {
		return customScaleMax;
	}

	public void setCustomScaleMax(Double customScaleMax) {
		this.customScaleMax = customScaleMax;
	}

	public int getDpi() {
		return dpi;
	}

	public void setDpi(int dpi) {
		this.dpi = dpi;
	}

	public boolean isUseGMTSmoothing() {
		return useGMTSmoothing;
	}

	public void setUseGMTSmoothing(boolean useGMTSmoothing) {
		this.useGMTSmoothing = useGMTSmoothing;
	}
	
	public boolean isBlackBackground() {
		return blackBackground;
	}

	public void setBlackBackground(boolean blackBackground) {
		this.blackBackground = blackBackground;
	}

	public boolean isLogPlot() {
		return logPlot;
	}

	public void setLogPlot(boolean logPlot) {
		this.logPlot = logPlot;
	}

	public String getXyzFileName() {
		return xyzFileName;
	}

	public void setXyzFileName(String xyzFileName) {
		this.xyzFileName = xyzFileName;
	}
	
	public String getPSFileName() {
		return psFileName;
	}

	public void setPSFileName(String psFileName) {
		this.psFileName = psFileName;
	}
	
	public String getPDFFileName() {
		return pdfFileName;
	}

	public void setPDFFileName(String pdfFileName) {
		this.pdfFileName = pdfFileName;
	}
	
	public String getPNGFileName() {
		return pngFileName;
	}

	public void setPNGFileName(String pngFileName) {
		this.pngFileName = pngFileName;
	}
	
	public String getJPGFileName() {
		return jpgFileName;
	}

	public void setJPGFileName(String jpgFileName) {
		this.jpgFileName = jpgFileName;
	}

	public String getGmtScriptFileName() {
		return gmtScriptFileName;
	}

	public void setGmtScriptFileName(String gmtScriptFileName) {
		this.gmtScriptFileName = gmtScriptFileName;
	}

	public ArrayList<PSXYSymbol> getSymbols() {
		return xySymbols;
	}

	public void setSymbols(ArrayList<PSXYSymbol> xySymbols) {
		this.xySymbols = xySymbols;
	}
	
	public void addSymbol(PSXYSymbol symbol) {
		this.xySymbols.add(symbol);
	}

	public ArrayList<PSXYPolygon> getPolys() {
		return xyLines;
	}

	public void setPolys(ArrayList<PSXYPolygon> xyLines) {
		this.xyLines = xyLines;
	}
	
	public void addPolys(PSXYPolygon line) {
		this.xyLines.add(line);
	}

	public PSXYSymbolSet getSymbolSet() {
		return xySymbolSet;
	}

	public void setSymbolSet(PSXYSymbolSet xySymbolSet) {
		this.xySymbolSet = xySymbolSet;
	}

	public ArrayList<PSText> getText() {
		return xyText;
	}

	public void setText(ArrayList<PSText> xyText) {
		this.xyText = xyText;
	}
	
	public void addText(PSText text) {
		this.xyText.add(text);
	}

	public boolean isGenerateKML() {
		return generateKML;
	}

	public void setGenerateKML(boolean generateKML) {
		this.generateKML = generateKML;
	}
	
	public GMT_InterpolationSettings getInterpSettings() {
		return interpSettings;
	}

	/**
	 * If non null, dataset is considered to be scattered and will be interpolated with the given settings
	 * @param interpSettings
	 */
	public void setInterpSettings(GMT_InterpolationSettings interpSettings) {
		this.interpSettings = interpSettings;
	}

	public boolean isMaskIfNotRectangular() {
		return maskIfNotRectangular;
	}

	public void setMaskIfNotRectangular(boolean maskIfNotRectangular) {
		this.maskIfNotRectangular = maskIfNotRectangular;
	}

	public double getContourIncrement() {
		return contourIncrement;
	}

	public void setContourIncrement(double contourIncrement) {
		this.contourIncrement = contourIncrement;
	}

	public boolean isContourOnly() {
		return contourOnly;
	}

	public void setContourOnly(boolean contourOnly) {
		this.contourOnly = contourOnly;
	}
	
	public boolean isDrawScaleKM() {
		return drawScaleKM;
	}
	
	public void setDrawScaleKM(boolean drawScaleKM) {
		this.drawScaleKM = drawScaleKM;
	}

	public String getCustomGRDPath() {
		return customGRDPath;
	}

	public void setCustomGRDPath(String customGRDPath) {
		this.customGRDPath = customGRDPath;
	}

	public String getCustomIntenPath() {
		return customIntenPath;
	}

	public void setCustomIntenPath(String customIntenPath) {
		this.customIntenPath = customIntenPath;
	}
	
	public void setGMT_Param(String name, String value) {
		if (gmtSetVals == null)
			gmtSetVals = new HashMap<>();
		gmtSetVals.put(name, value);
	}
	
	public Map<String, String> getGMT_Params() {
		return gmtSetVals;
	}

	public boolean isUseGRDView() {
		return useGRDView;
	}

	/**
	 * If true, will use grdview instead of grdimage when smoohting and topography are disabled. This is slower but
	 * more accurate for very finely spaced maps
	 * @param useGRDView
	 */
	public void setUseGRDView(boolean useGRDView) {
		this.useGRDView = useGRDView;
	}

}
