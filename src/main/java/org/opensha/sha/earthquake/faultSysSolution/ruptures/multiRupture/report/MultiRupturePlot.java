package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.report;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureStiffnessPlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates a single colour-coded map plot for a multi-rupture, where each fault section is
 * shaded according to a computed stiffness value. Factory methods ({@link #subductionAsSource},
 * {@link #crustalAsSource}, {@link #everythingAsSource}, etc.) provide different perspectives
 * on source/receiver directionality.
 */
public class MultiRupturePlot {

    protected final DecimalFormat df;
    protected final String prefix;
    protected final ClusterRupture rup;
    protected final String title;
    protected final String valueUnit;
    protected final ValueForSection valueForSection;
    public final double[] values;
    public final double min;
    public final double max;

    /** Computes a stiffness (or other) value for a single fault section, or null if not applicable. */
    public interface ValueForSection {
        Double getValue(FaultSection section);
    }

    public MultiRupturePlot(String prefix, ClusterRupture rup, String title, ValueForSection valueForSection, String valueUnit) {
        this.prefix = prefix;
        this.rup = rup;
        this.title = title;
        this.valueForSection = valueForSection;
        this.valueUnit = valueUnit;
        this.values = rup.buildOrderedSectionList().stream().map(valueForSection::getValue).filter(Objects::nonNull).mapToDouble(s -> s).toArray();
        if(title.contains("Rupture 53") && title.contains("everything")) {
            System.out.println(Arrays.toString(this.values));
        }
        this.min = StatUtils.min(values);
        this.max = StatUtils.max(values);
        if (min >= 0 && max <= 1) {
            df = new DecimalFormat("0.###");
        } else {
            df = new DecimalFormat("0.#");
        }
    }

    public String getFileName() {
        return prefix + ".png";
    }

    public void plot(File outputDir, CPT cpt) throws IOException {
        RupCartoonGenerator.sectCharFun = (section, traceChar, outlineChar) -> {
            List<PlotCurveCharacterstics> chars = new ArrayList<>();
            chars.add(traceChar);
            if (section.getSectionName().contains("row:")) {
                chars.add(null);
            } else {
                chars.add(outlineChar);
            }
            Double stiffness = valueForSection.getValue(section);
            if (stiffness == null || stiffness == 0) {
                chars.add(null);
            } else {
                chars.add(new PlotCurveCharacterstics(PlotLineType.POLYGON_SOLID, 1, cpt.getColor(stiffness.floatValue())));
            }
            return chars;
        };
        PlotSpec spec = RupCartoonGenerator.buildRupturePlot(rup, title, false, true);

        double tickUnit = (cpt.getMaxValue() - cpt.getMinValue()) / 3.99;
        PaintScaleLegend cptLegend = GraphPanel.getLegendForCPT(cpt, valueUnit,
                22, 18, tickUnit, RectangleEdge.BOTTOM);

        spec.addSubtitle(cptLegend);

        RupCartoonGenerator.plotRupture(outputDir, prefix, spec, true);
    }

    public String getStats() {

        return "min: " + df.format(min) + "<br />" +
                "max: " + df.format(max) + "<br />" +
                "mean: " + df.format(StatUtils.mean(values)) + "<br />" +
                "p50: " + df.format(StatUtils.percentile(values, 50)) + "<br />";
    }

    public static List<FaultSection> filterNear(
            FaultSection origin, List<FaultSection> candidates,
            double maxDistance,
            SectionDistanceAzimuthCalculator disAzCalc) {
        return candidates.stream().filter(s -> disAzCalc.getDistance(origin, s) <= maxDistance).collect(Collectors.toList());
    }

    /**
     * Returns a value function where subduction sections act as source (stress onto crustal)
     * and crustal sections act as receiver (stress from subduction).
     */
    public static ValueForSection subductionAsSource(
            AggregatedStiffnessCalculator stiffnessCalculator,
            MultiRuptureStiffnessPlot.RuptureProperties prop) {
        return s -> s.getSectionName().contains("row:") ?
                stiffnessCalculator.calc(List.of(s), prop.crustal) :
                stiffnessCalculator.calc(prop.subduction, List.of(s));
    }

    /**
     * Returns a value function where all sections (crustal + subduction) act as source onto
     * each crustal section as receiver. Subduction sections return 0.
     */
    public static ValueForSection everythingAsSource(
            AggregatedStiffnessCalculator stiffnessCalculator,
            MultiRuptureStiffnessPlot.RuptureProperties prop) {
        List<FaultSection> source = new ArrayList<>(prop.crustal);
        source.addAll(prop.subduction);
        return s -> s.getSectionName().contains("row:") ?
                0 :
                stiffnessCalculator.calc(source, List.of(s));
    }

    public static ValueForSection subductionAsSource(
            AggregatedStiffnessCalculator stiffnessCalculator,
            MultiRuptureStiffnessPlot.RuptureProperties prop,
            double maxDist,
            SectionDistanceAzimuthCalculator disAzCalc) {
        return s -> {
            if (s.getSectionName().contains("row:")) {
                List<FaultSection> receivers = filterNear(s, prop.crustal, maxDist, disAzCalc);
                if (receivers.isEmpty()) {
                    return null;
                }
                return stiffnessCalculator.calc(List.of(s), receivers);
            }
            List<FaultSection> sources = filterNear(s, prop.subduction, maxDist, disAzCalc);
            if (sources.isEmpty()) {
                return null;
            }
            return stiffnessCalculator.calc(sources, List.of(s));
        };
    }

    /**
     * Returns a value function where crustal sections act as source (stress onto subduction)
     * and subduction sections act as receiver (stress from crustal).
     */
    public static ValueForSection crustalAsSource(
            AggregatedStiffnessCalculator stiffnessCalculator,
            MultiRuptureStiffnessPlot.RuptureProperties prop) {
        return s -> s.getSectionName().contains("row:") ?
                stiffnessCalculator.calc(prop.crustal, List.of(s)) :
                stiffnessCalculator.calc(List.of(s), prop.subduction);
    }

    /** Distance-limited variant of {@link #crustalAsSource} that only considers sections within maxDist. */
    public static ValueForSection crustalAsSource(
            AggregatedStiffnessCalculator stiffnessCalculator,
            MultiRuptureStiffnessPlot.RuptureProperties prop,
            double maxDist,
            SectionDistanceAzimuthCalculator disAzCalc) {
        return s -> {
            if (s.getSectionName().contains("row:")) {
                List<FaultSection> sources = filterNear(s, prop.crustal, maxDist, disAzCalc);
                if (sources.isEmpty()) {
                    return null;
                }
                return stiffnessCalculator.calc(sources, List.of(s));
            }
            List<FaultSection> receivers = filterNear(s, prop.subduction, maxDist, disAzCalc);
            if (receivers.isEmpty()) {
                return null;
            }
            return stiffnessCalculator.calc(List.of(s), receivers);
        };
    }

    /**
     * Returns a value function where each section acts as the sole source onto the opposite
     * component's sections as receivers.
     */
    public static ValueForSection sectionAsSource(
            AggregatedStiffnessCalculator stiffnessCalculator,
            MultiRuptureStiffnessPlot.RuptureProperties prop) {
        return s -> s.getSectionName().contains("row:") ?
                stiffnessCalculator.calc(List.of(s), prop.crustal) :
                stiffnessCalculator.calc(List.of(s), prop.subduction);
    }

    /**
     * Returns a value function where each section acts as the sole receiver of stress from the
     * opposite component's sections.
     */
    public static ValueForSection sectionAsReceiver(
            AggregatedStiffnessCalculator stiffnessCalculator,
            MultiRuptureStiffnessPlot.RuptureProperties prop) {
        return s -> s.getSectionName().contains("row:") ?
                stiffnessCalculator.calc(prop.crustal, List.of(s)) :
                stiffnessCalculator.calc(prop.subduction, List.of(s));
    }

}
