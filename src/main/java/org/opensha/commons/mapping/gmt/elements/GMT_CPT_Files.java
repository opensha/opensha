package org.opensha.commons.mapping.gmt.elements;

import java.io.IOException;
import java.util.ArrayList;

import org.opensha.commons.util.cpt.CPT;

/**
 * Enum for GMT CPT files stored in the src/main/resources/cpt folder. These are used by the
 * GMT map plotter.
 * 
 * @author kevin
 *
 */
public enum GMT_CPT_Files {
	
	/*
	 * Original GMT set
	 */
	BLUE_YELLOW_RED("BlueYellowRed.cpt"),
	GMT_COOL("GMT_cool.cpt"),
	MAX_SPECTRUM("MaxSpectrum.cpt"),
	RELM("relm_color_map.cpt"),
	SHAKEMAP("Shakemap.cpt"),
	STEP("STEP.cpt"),
	UCERF2_FIGURE_35("ucerf2_fig_35.cpt"),
	UCERF3_RATIOS("UCERF3_RatiosCPT.cpt"),
	UCERF3_ETAS_TRIGGER("UCERF3_ETAS_Trigger.cpt"),
	UCERF3_ETAS_GAIN("UCERF3_ETAS_Gain_CPT.cpt"),
	UCERF3_HAZ_RATIO("UCERF3_hazRatio.cpt"),
	UCERF3_HAZ_RATIO_P3("UCERF3_hazRatioP3toP3.cpt"),
	NSHMP_1hz("nshmp_1hz.cpt"),
	NSHMP_5hz("nshmp_5hz.cpt"),
	NSHMP_RATIO("NSHMP_ratio.cpt"),
	NSHMP_DIFF("NSHMP_diff.cpt"),
	GMT_COPPER("GMT_copper.cpt"),
//	GMT_CYCLIC("GMT_cyclic.cpt"),
	GMT_DRYWET("GMT_drywet.cpt"),
	GMT_GEBCO("GMT_gebco.cpt"),
	GMT_GLOBE("GMT_globe.cpt"),
	GMT_GRAY("GMT_gray.cpt"),
	GMT_HAXBY("GMT_haxby.cpt"),
	GMT_HOT("GMT_hot.cpt"),
	GMT_JET("GMT_jet.cpt"),
	GMT_NO_GREEN("GMT_no_green.cpt"),
	GMT_OCEAN("GMT_ocean.cpt"),
	GMT_OCEAN2("GMT_ocean2.cpt"),
	GMT_PANOPLY("GMT_panoply.cpt"),
	GMT_POLAR("GMT_polar.cpt"),
//	GMT_RAINBOW("GMT_rainbow.cpt"),
	GMT_RED_2_GREEN("GMT_red2green.cpt"),
	GMT_RELIEF("GMT_relief.cpt"),
//	GMT_SEALAND("GMT_sealand.cpt"),
	GMT_SEIS("GMT_seis.cpt"),
	GMT_SPLIT("GMT_split.cpt"),
//	GMT_TOPO("GMT_topo.cpt"),
	GMT_WYSIWYG("GMT_wysiwyg.cpt"),
	
	/*
	 * Newer perceptually uniform
	 */
	
	/**
	 * CET-L4 from https://colorcet.com/
	 */
	BLACK_RED_YELLOW_UNIFORM("CET-L4.cpt"),
	/**
	 * CET-R1 from https://colorcet.com/
	 */
	RAINBOW_UNIFORM("CET-R1.cpt"),
	/**
	 * CET-D01 from https://colorcet.com/
	 * blue, light blue, light red, red
	 */
	DIVERGING_BLUE_RED_UNIFORM("CET-D01.cpt"),
	/**
	 * CET-D01A from https://colorcet.com/
	 * Dark blue, blue, light blue, light red, red, dark red
	 */
	DIVERGING_DARK_BLUE_RED_UNIFORM("CET-D01A.cpt"),
	/**
	 * vik from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * dark cyan-ish blue, light blue, light orange red, dark orange red
	 */
	DIVERGING_VIK_UNIFORM("vik.cpt"),
	/**
	 * bam from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * dark greens through white to dark magenta
	 */
	DIVERGING_BAM_UNIFORM("bam.cpt"),
	/**
	 * cork from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * dark blues through white to dark greens
	 */
	DIVERGING_CORK_UNIFORM("cork.cpt"),
	/**
	 * bam from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * dark blues through white to yellow/greenish browns
	 */
	DIVERGING_BROC_UNIFORM("broc.cpt"),

	DIVERGING_RAINBOW("CET-R3.cpt"),
	/**
	 * batlow from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * rainbow-ish, but perceptually uniform
	 */
	SEQUENTIAL_BATLOW_UNIFORM("batlow.cpt"),
	/**
	 * navia from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * Dark blue through green to light green-yellow
	 */
	SEQUENTIAL_NAVIA_UNIFORM("navia.cpt"),
	/**
	 * oslo from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * Dark blue through to light blue to white
	 */
	SEQUENTIAL_OSLO_UNIFORM("oslo.cpt"),
	/**
	 * lajolla from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * Dark red through to light yellow
	 */
	SEQUENTIAL_LAJOLLA_UNIFORM("lajolla.cpt"),
	/**
	 * categorical version of batlow from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * Grab the first color from each CPT value
	 */
	CATEGORICAL_BATLOW_UNIFORM("batlowS.cpt"),
	/**
	 * Categorical colormap from Tableau, commonly used in MatPlotLib (called TAB10), with 10 distinct colors.
	 * Grab the first color from each CPT value, or call <code>cpt.getColor(index % cpt.size())</code>
	 */
	CATEGORICAL_TAB10("tab10.cpt"),
	/**
	 * Light version of {@link GMT_CPT_Files#CATEGORICAL_TAB10}. This was created by taking the even numbered values from
	 * MatPlotLib's TAB20 (the light ones).
	 */
	CATEGORICAL_TAB10_LIGHT("tab10_light.cpt"),
	/**
	 * Same as {@link GMT_CPT_Files#CATEGORICAL_TAB10}, except omitting the gray color.
	 */
	CATEGORICAL_TAB10_NOGRAY("tab10_nogray.cpt"),
	/**
	 * Same as {@link GMT_CPT_Files#CATEGORICAL_TAB10_LIGHT}, except omitting the gray color.
	 */
	CATEGORICAL_TAB10_LIGHT_NOGRAY("tab10_light_nogray.cpt"),
	/**
	 * Categorical colormap from Tableau, commonly used in matplotlib (called TAB10), with 20 distinct colors.
	 * <p>
	 * This is similar to {@link GMT_CPT_Files#CATEGORICAL_TAB10}, except that it has light versions interspersed (see
	 * {@link GMT_CPT_Files#CATEGORICAL_TAB10_LIGHT}). The dark ones (originals from {@link GMT_CPT_Files#CATEGORICAL_TAB10}
	 * are accessible at integer values (e.g., <code>cpt.getColor(index % cpt.size())</code>), and the light colors
	 * (same as {@link GMT_CPT_Files#CATEGORICAL_TAB10_LIGHT}) are accessible at half fractions (e.g.,
	 * <code>cpt.getColor(index % cpt.size() + 0.5f)</code>).
	 * <p>
	 * If you iterate through CPT values (or access them via index), you will get dark then light of each color before
	 * moving on to the next color.
	 */
	CATEGORICAL_TAB20("tab20.cpt"),

	DIVERGENT_RYB("RdYlBu.cpt");

	private String fname;
	private CPT instance;

	private GMT_CPT_Files(String fname) {
		this.fname = fname;
	}
	
	public String getFileName() {
		return fname;
	}
	
	public CPT instance() throws IOException {
		if (instance == null) {
			CPT cpt = CPT.loadFromStream(this.getClass().getResourceAsStream("/cpt/"+fname));
			cpt.setName(fname);
			instance = cpt;
		}
		return (CPT)instance.clone();
	}
	
	public static ArrayList<CPT> instances() throws IOException {
		ArrayList<CPT> cpts = new ArrayList<CPT>();
		for (GMT_CPT_Files cptFile : values()) {
//			System.out.println("Instantiating CPT: "+cptFile.getFileName());
			cpts.add(cptFile.instance());
		}
		return cpts;
	}

}