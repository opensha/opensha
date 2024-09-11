package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.report.RupturePlotPageGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class MultiRuptureStiffnessPlot extends AbstractRupSetPlot {

    // FIXME: get this from the rupset
    final static double STIFFNESS_THRESHOLD = 0.75;

    final static String PREFIX = "jointStiffness";
    DecimalFormat o3DF = new DecimalFormat("0.###");

    AggregatedStiffnessCalculator[] stiffnessCalcs;
    SectionDistanceAzimuthCalculator disAzCalc;
    FaultSystemRupSet rupSet;

    public void setStiffnessCalc(SubSectStiffnessCalculator stiffnessCalc) {
        this.stiffnessCalcs = new AggregatedStiffnessCalculator[] {
                new AggregatedStiffnessCalculator(
                        SubSectStiffnessCalculator.StiffnessType.CFF,
                        stiffnessCalc,
                        true,
                        AggregatedStiffnessCalculator.AggregationMethod.FLATTEN,
                        AggregatedStiffnessCalculator.AggregationMethod.SUM,
                        AggregatedStiffnessCalculator.AggregationMethod.SUM,
                        AggregatedStiffnessCalculator.AggregationMethod.SUM),
                new AggregatedStiffnessCalculator(
                        SubSectStiffnessCalculator.StiffnessType.CFF,
                        stiffnessCalc,
                        true,
                        AggregatedStiffnessCalculator.AggregationMethod.FLATTEN,
                        AggregatedStiffnessCalculator.AggregationMethod.NUM_POSITIVE,
                        AggregatedStiffnessCalculator.AggregationMethod.SUM,
                        AggregatedStiffnessCalculator.AggregationMethod.NORM_BY_COUNT)
        };

    }

    public static class RuptureProperties {
        public ClusterRupture rupture;
        public int index;
        public List<FaultSection> subduction;
        public List<FaultSection> crustal;
        public double[] subToCrustalStiffness;
        public double[] crustalToSubStiffness;
        public Map<String, String> plots = new HashMap<>();

        public RuptureProperties(
                ClusterRupture rupture,
                int index) {
            this.rupture = rupture;
            this.index = index;
        }
    }

    /**
     * For a given value, finds the most representative rupture from a list of rupture.
     * This is for example used to get ruptures representing percentiles.
     *
     * @param properties
     * @param valueFn
     * @param value
     * @return
     */
    public RuptureProperties findBestMatch(List<RuptureProperties> properties, ToDoubleFunction<RuptureProperties> valueFn, double value) {
        RuptureProperties prop = properties.get(0);
        for (RuptureProperties candidate : properties) {
            double actualValue = valueFn.applyAsDouble(candidate);
            if (actualValue < value) {
                prop = candidate;
            } else if (actualValue == value) {
                return candidate;
            } else {
                if (Math.abs(actualValue - value) < Math.abs(valueFn.applyAsDouble(prop) - value)) {
                    return candidate;
                } else {
                    return prop;
                }
            }
        }
        return prop;
    }

    @Override
    public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources, String topLink) throws IOException {
        this.rupSet = rupSet;
        this.disAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
        RupCartoonGenerator.SectionCharacteristicsFunction rupToonFun = RupCartoonGenerator.sectCharFun;
        List<String> lines = new ArrayList<>();

        ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
        StiffnessCalcModule stiffness = rupSet.requireModule(StiffnessCalcModule.class);
        setStiffnessCalc(stiffness.getStiffnessCalculator());

        List<RuptureProperties> properties = new ArrayList<>();
        for (int i = 0; i < cRups.size(); i++) {
            properties.add(new RuptureProperties(cRups.get(i), i));
        }

        properties.parallelStream().forEach(property -> {
            property.subduction = property.rupture.clusters[0].subSects;
            property.crustal = property.rupture.splays.values().asList().get(0).buildOrderedSectionList();
            property.subToCrustalStiffness = Arrays.stream(stiffnessCalcs).mapToDouble(stiffnessCalc -> stiffnessCalc.calc(property.subduction, property.crustal)).toArray();
            property.crustalToSubStiffness = Arrays.stream(stiffnessCalcs).mapToDouble(stiffnessCalc -> stiffnessCalc.calc(property.crustal, property.subduction)).toArray();
        });

//        lines.add("Stiffness measure in this section: " + stiffnessCalc.toString());
//        lines.add("");

        lines.add("### Subduction to Crustal Stiffness");
        lines.add("");
        lines.addAll(plotForValue(properties, p -> p.subToCrustalStiffness[0], resourcesDir, relPathToResources, "subToCru0"));

        lines.add("### Crustal to Subduction Stiffness");
        lines.add("");
        lines.addAll(plotForValue(properties, p -> p.crustalToSubStiffness[0], resourcesDir, relPathToResources, "cruToSub0"));

        lines.add("### Difference in Stiffness based on source");
        lines.add("");
        lines.addAll(plotForValue(properties, p -> Math.abs(p.crustalToSubStiffness[0] - p.subToCrustalStiffness[0]), resourcesDir, relPathToResources, "subToCru0"));

        lines.add("### Number of sections");
        lines.add("");
        lines.addAll(plotForValue(properties, p -> p.crustal.size() + p.subduction.size(), resourcesDir, relPathToResources, "subToCru0"));

        List<RuptureProperties> subductionOnly = properties.stream().
                filter(p -> p.subToCrustalStiffness[0] >= STIFFNESS_THRESHOLD && p.crustalToSubStiffness[0] < STIFFNESS_THRESHOLD).
                collect(Collectors.toList());

        if (!subductionOnly.isEmpty()) {
            lines.add("### Only Subduction As Source Above Stiffness Threshold of " + o3DF.format(STIFFNESS_THRESHOLD));
            lines.add("Ruptures where subduction as source is above threshold and crustal as source is below threshold.");
            lines.add("");
            lines.addAll(plotForValue(subductionOnly, p -> p.crustal.size() + p.subduction.size(), resourcesDir, relPathToResources, "subToCru0"));

        }

        List<RuptureProperties> crustalOnly = properties.stream().
                filter(p -> p.subToCrustalStiffness[0] < STIFFNESS_THRESHOLD && p.crustalToSubStiffness[0] >= STIFFNESS_THRESHOLD).
                collect(Collectors.toList());

        if (!crustalOnly.isEmpty()) {
            lines.add("### Only Crustal As Source Above Stiffness Threshold of " + o3DF.format(STIFFNESS_THRESHOLD));
            lines.add("Ruptures where crustal as source is above threshold and subduction as source is below threshold.");
            lines.add("");
            lines.addAll(plotForValue(crustalOnly, p -> p.crustal.size() + p.subduction.size(), resourcesDir, relPathToResources, "cruToSub0"));
        }

        List<RuptureProperties> both = properties.stream().
                filter(p -> p.subToCrustalStiffness[0] >= STIFFNESS_THRESHOLD && p.crustalToSubStiffness[0] >= STIFFNESS_THRESHOLD).
                collect(Collectors.toList());

        if (!both.isEmpty()) {
            lines.add("### Both Crustal and Subduction As Source Are Above Stiffness Threshold of " + o3DF.format(STIFFNESS_THRESHOLD));
            lines.add("Ruptures where both crustal as source is above threshold and subduction as source is above threshold.");
            lines.add("");
            lines.addAll(plotForValue(both, p -> p.crustal.size() + p.subduction.size(), resourcesDir, relPathToResources, "subToCru0"));
        }
        RupCartoonGenerator.sectCharFun = rupToonFun;
        return lines;
    }

    static String formatPercentile(double value) {
        if (value == 0) {
            return "Min: ";
        }
        if (value == 1) {
            return "Max: ";
        }
        return "P" + (int) value + ": ";
    }

    protected List<String> plotForValue(List<RuptureProperties> properties,
                                        ToDoubleFunction<RuptureProperties> valueFn,
                                        File resourcesDir,
                                        String relPathToResources,
                                        String thumbnail) throws IOException {

        double[] values = properties.stream().mapToDouble(valueFn).toArray();

        List<Double> percentiles = List.of(0.0, 10.0, 25.0, 50.0, 75.0, 90.0, 95.0, 97.5, 99.0, 1.0);
        List<Double> percentileValues = percentiles.stream().map(p -> {
            if (p == 0) {
                return StatUtils.min(values);
            }
            if (p == 1) {
                return StatUtils.max(values);
            }
            return StatUtils.percentile(values, p);

        }).collect(Collectors.toList());
        properties.sort(Comparator.comparing(valueFn::applyAsDouble));

        MarkdownUtils.TableBuilder table = MarkdownUtils.tableBuilder();

        table.initNewLine();
        for (int i = 0; i < percentiles.size(); i++) {
            table.addColumn(formatPercentile(percentiles.get(i)) + o3DF.format(percentileValues.get(i)));
        }
        table.finalizeLine();

        table.initNewLine();
        RupturePlotPageGenerator pageGenerator = new RupturePlotPageGenerator(stiffnessCalcs, rupSet);

        for (double value : percentileValues) {
            RuptureProperties prop = findBestMatch(properties, valueFn, value);
            String relOutputDir = PREFIX + prop.index;
            File outputDir = new File(resourcesDir, relOutputDir);
            outputDir.mkdirs();
            pageGenerator.makeRupturePage(prop, outputDir);
            File relPageDir = new File(relPathToResources, relOutputDir);
            table.addColumn("[<img src=\"" + new File(relPageDir, prop.plots.get(thumbnail)) + "\" />](" + new File(relPageDir, "index.html") + ")");
        }
        table.finalizeLine();

        return table.wrap(5, 0).build();
    }


    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return List.of();
    }

    @Override
    public String getName() {
        return "MultiRuptureStiffnessPlot";
    }

    public static void main(String[] args) throws IOException {
        File file = new File("C:\\tmp\\rupSetBruceRundir5883.zip");
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(file);
        ReportMetadata meta = new ReportMetadata(new RupSetMetadata(file.getName(), rupSet));
        List<AbstractRupSetPlot> plots = List.of(new MultiRuptureStiffnessPlot());
        ReportPageGen report = new ReportPageGen(meta, new File("/tmp/reports/bruceRundior5883"), plots);
        report.generatePage();
    }
}
