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
	 * lajolla from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * Dark red through to light yellow
	 */
	SEQUENTIAL_LAJOLLA_UNIFORM("lajolla.cpt"),
	/**
	 * categorical version of batlow from https://www.fabiocrameri.ch/colourmaps/ and GMT 6
	 * Grab the first color from each CPT value
	 */
	CATEGORICAL_BATLOW_UNIFORM("batlowS.cpt");
	
	private String fname;
	
	private GMT_CPT_Files(String fname) {
		this.fname = fname;
	}
	
	public String getFileName() {
		return fname;
	}
	
	public CPT instance() throws IOException {
		CPT cpt = CPT.loadFromStream(this.getClass().getResourceAsStream("/cpt/"+fname));
		cpt.setName(fname);
		return cpt;
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
