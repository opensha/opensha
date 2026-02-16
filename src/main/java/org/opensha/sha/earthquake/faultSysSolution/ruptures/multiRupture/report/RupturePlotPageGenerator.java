package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.report;

import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureStiffnessPlot;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;

/**
 * Creates a page of plots for a rupture
 */
public class RupturePlotPageGenerator {

    static final DecimalFormat o3DF = new DecimalFormat("0.###");
    static final DecimalFormat o1DF = new DecimalFormat("0.#");

    AggregatedStiffnessCalculator[] stiffnessCalcs;
    FaultSystemRupSet rupSet;

    public RupturePlotPageGenerator(AggregatedStiffnessCalculator[] stiffnessCalcs, FaultSystemRupSet rupSet) {
        this.stiffnessCalcs = stiffnessCalcs;
        this.rupSet = rupSet;
    }


    protected List<String> getParentNames(ClusterRupture rupture) {
        List<String> results = new ArrayList<>();
        rupture.getClustersIterable().forEach(cluster -> results.add(cluster.parentSectionName));
        return results;
    }

    /**
     * Writes the specified plots to file, making sure that all use the same CPT to make them easily comparable.
     * @param outputDir
     * @param plots
     * @throws IOException
     */
    public void plotWithSameCPT(File outputDir, MultiRupturePlot... plots) throws IOException {

        // work out CPT to be able to use the same one across all plots
        double[] values = Arrays.stream(plots).flatMapToDouble(p -> DoubleStream.of(p.min, p.max)).toArray();

        double min = Arrays.stream(values).min().getAsDouble();
        double max = Arrays.stream(values).max().getAsDouble();
        if (min >= 0 && min <= 1 && max >= 0 && max <= 1) {
            min = 0;
            max = 1;
        }
        if (min < 0 && max > 0) {
            double dist = Math.max(-min, max);
            min = -dist;
            max = dist;
        }
        if(min >=0) {
            min = -max;
        }
        CPT cpt = GMT_CPT_Files.DIVERGENT_RYB.instance().reverse().rescale(min, max);
//        CPT cpt = min >= 0 ?
//                GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(min, max) :
//                GMT_CPT_Files.DIVERGENT_RYB.instance().reverse().rescale(min, max);

        // create all plots
        for (MultiRupturePlot plot : plots) {
            try {
                plot.plot(outputDir, cpt);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void plotWithFixedCPT(File outputDir, double min, double max, MultiRupturePlot... plots) throws IOException {

        CPT cpt = GMT_CPT_Files.DIVERGENT_RYB.instance().reverse().rescale(min, max);
        // create all plots
        for (MultiRupturePlot plot : plots) {
            try {
                plot.plot(outputDir, cpt);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void makeRupturePage(MultiRuptureStiffnessPlot.RuptureProperties prop, File outputDir) throws IOException {
        String rupTitle = "Rupture " + prop.index + " ";

        try {
            // set up plots

            MultiRupturePlot subToCruPlot0 = new MultiRupturePlot("SubAsSource0", prop.rupture, rupTitle + "(subduction as source)", MultiRupturePlot.subductionAsSource(stiffnessCalcs[0], prop), stiffnessCalcs[0].toString());
            MultiRupturePlot cruToSubPlot0 = new MultiRupturePlot("CruAsSource0", prop.rupture, rupTitle + "(crustal as source)", MultiRupturePlot.crustalAsSource(stiffnessCalcs[0], prop), stiffnessCalcs[0].toString());
            MultiRupturePlot subToCruPlot1 = new MultiRupturePlot("SubAsSource1", prop.rupture, rupTitle + "(subduction as source)", MultiRupturePlot.subductionAsSource(stiffnessCalcs[1], prop), stiffnessCalcs[1].toString());
            MultiRupturePlot cruToSubPlot1 = new MultiRupturePlot("CruAsSource1", prop.rupture, rupTitle + "(crustal as source)", MultiRupturePlot.crustalAsSource(stiffnessCalcs[1], prop), stiffnessCalcs[1].toString());
            prop.plots.put("subToCru0", subToCruPlot0.getFileName());
            prop.plots.put("cruToSub0", cruToSubPlot0.getFileName());
            prop.plots.put("subToCru1", subToCruPlot1.getFileName());
            prop.plots.put("cruToSub1", cruToSubPlot1.getFileName());

            plotWithSameCPT(outputDir, subToCruPlot0, cruToSubPlot0);
            plotWithFixedCPT(outputDir, 0, 1, subToCruPlot1, cruToSubPlot1);

            // create markdown

            List<String> lines = new ArrayList<>();

            lines.add("## Rupture " + prop.index);
            lines.add("");
//            lines.add("Stiffness data: " + stiffnessCalc.toString());
//            lines.add("");

            MarkdownUtils.TableBuilder table = MarkdownUtils.tableBuilder();
            table.addLine("Subduction to crustal stiffness", "Crustal to subduction stiffness");
            table.initNewLine();
            table.addColumn("Subduction sections in this column show the stiffness of the section as source and all crustal sections as receivers. Crustal sections show the stiffness of the section as sole receiver with all subduction sections as source.");
            table.addColumn("Subduction sections in this column show the stiffness of the section as receiver and all crustal sections as source. Crustal sections show the stiffness of the section as sole source with all subduction sections as receivers.");
            table.finalizeLine();
            table.addLine("![subToCruPlot](" + subToCruPlot0.getFileName() + ")", "![cruToSubPlot](" + cruToSubPlot0.getFileName() + ")");
            table.addLine(
                    subToCruPlot0.getStats() + "Total: " + o3DF.format(prop.subToCrustalStiffness[0]),
                    cruToSubPlot0.getStats() + "Total: " + o3DF.format(prop.crustalToSubStiffness[0]));
            table.addLine("![subToCruPlot](" + subToCruPlot1.getFileName() + ")", "![cruToSubPlot](" + cruToSubPlot1.getFileName() + ")");
            table.addLine(
                    subToCruPlot1.getStats() + "Total: " + o3DF.format(prop.subToCrustalStiffness[1]),
                    cruToSubPlot1.getStats() + "Total: " + o3DF.format(prop.crustalToSubStiffness[1]));
            lines.addAll(table.wrap(2, 1).build());

            lines.add("");
            lines.add("- Crustal sections: " + prop.crustal.size());
            lines.add("- Subduction sections: " + prop.subduction.size());
            lines.add("- Rake: " + o1DF.format(rupSet.getAveRakeForRup(prop.index)));
            lines.add("");
            lines.add("#### Parent Faults");
            for (String parent : getParentNames(prop.rupture)) {
                lines.add("- " + parent);
            }
            lines.add("");

            MarkdownUtils.writeHTML(lines, new File(outputDir, "index.html"));
        } catch (Exception x) {
            System.err.println("Could not generate page for " + rupTitle);
            x.printStackTrace();
        }
    }

}
