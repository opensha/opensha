package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.helpers.JSON_BackedModule;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class LogicTreeRateStatistics implements JSON_BackedModule {
	
	public static final double[] RATE_THRESHOLDS_DEFAULT = { 0d, 1e-16, 1e-14, 1e-12, 1e-10, 1e-8 };
	
	private double[] thresholds;
	private FractAboveStats[] overallStats;
	private List<String> logicTreeLevels;
	private List<List<String>> logicTreeValues;
	private List<List<FractAboveStats[]>> logicTreeValueStats;
	
	public static LogicTreeRateStatistics forSolutionLogicTree(SolutionLogicTree slt) throws IOException {
		return forSolutionLogicTree(RATE_THRESHOLDS_DEFAULT, slt);
	}
	
	public static LogicTreeRateStatistics forSolutionLogicTree(double[] thresholds, SolutionLogicTree slt) throws IOException {
		Builder builder = new Builder();
		
		for (LogicTreeBranch<?> branch : slt.getLogicTree()) {
			double[] rates = slt.loadRatesForBranch(branch);
			
			builder.process(branch, rates);
		}
		
		return builder.build();
	}
	
	public static class Builder {
		
		private double[] thresholds;
		private MinMaxAveTracker[] overallStats;
		private Table<String, String, MinMaxAveTracker[]> branchStats;
		private List<String> levelNames = new ArrayList<>();
		private List<List<String>> levelValues = new ArrayList<>();
		
		public Builder() {
			this(RATE_THRESHOLDS_DEFAULT);
		}
		
		public Builder(double[] thresholds) {
			Preconditions.checkState(thresholds.length > 0);
			this.thresholds = thresholds;
			this.overallStats = initStats(thresholds);
			branchStats = HashBasedTable.create();
		}
		
		private MinMaxAveTracker[] initStats(double[] statLevels) {
			MinMaxAveTracker[] ret = new MinMaxAveTracker[statLevels.length];
			for (int i=0; i<ret.length; i++)
				ret[i] = new MinMaxAveTracker();
			return ret;
		}
		
		public void process(LogicTreeBranch<?> branch, double[] rates) {
			List<MinMaxAveTracker[]> tracks = new ArrayList<>(branch.size()+1);
			tracks.add(overallStats);
			for (int i=0; i<branch.size(); i++) {
				String level = branch.getLevel(i).getName();
				if (i >= levelNames.size()) {
					levelNames.add(level);
					levelValues.add(new ArrayList<>());
				} else {
					Preconditions.checkState(level.equals(levelNames.get(i)));
				}
				LogicTreeNode choice = branch.getValue(i);
				if (choice == null)
					continue;
				String value = choice.getName();
				MinMaxAveTracker[] choiceStats = branchStats.get(level, value);
				if (choiceStats == null) {
					choiceStats = initStats(thresholds);
					branchStats.put(level, value, choiceStats);
					levelValues.get(i).add(value);
				}
				tracks.add(choiceStats);
			}
			
			for (int t=0; t<thresholds.length; t++) {
				int numAbove = 0;
				for (double rate : rates)
					if (rate > thresholds[t])
						numAbove++;
				double fract = (double)numAbove/(double)rates.length;
				for (MinMaxAveTracker[] track : tracks)
					track[t].addValue(fract);
			}
		}
		
		public LogicTreeRateStatistics build() {
			Preconditions.checkState(overallStats[0].getNum() > 0);
			LogicTreeRateStatistics ret = new LogicTreeRateStatistics();
			ret.thresholds = thresholds;
			ret.overallStats = tracksToStats(overallStats);
			ret.logicTreeLevels = new ArrayList<>();
			ret.logicTreeValues = new ArrayList<>();;
			ret.logicTreeValueStats = new ArrayList<>();
			for (int l=0; l<levelNames.size(); l++) {
				String level = levelNames.get(l);
				List<String> values = levelValues.get(l);
				if (values.size() == 1)
					continue;
				ret.logicTreeLevels.add(level);
				ret.logicTreeValues.add(values);
				List<FractAboveStats[]> stats = new ArrayList<>();
				ret.logicTreeValueStats.add(stats);
				for (String value : levelValues.get(l))
					stats.add(tracksToStats(branchStats.get(level, value)));
			}
			return ret;
		}
		
		private FractAboveStats[] tracksToStats(MinMaxAveTracker[] tracks) {
			FractAboveStats[] stats = new FractAboveStats[tracks.length];
			for (int i=0; i<stats.length; i++)
				stats[i] = new FractAboveStats(thresholds[i], tracks[i].getAverage(), tracks[i].getMin(), tracks[i].getMax());
			return stats;
		}
	}
	
	private LogicTreeRateStatistics() {}

	@Override
	public String getName() {
		return "Logic Tree Rupture Rate Statistics";
	}

	@Override
	public String getFileName() {
		return "logic_tree_rate_stats.json";
	}
	
	public static class FractAboveStats {
		public final double threshold;
		public final double avg;
		public final double min;
		public final double max;
		
		public FractAboveStats(double threshold, double avg, double min, double max) {
			this.threshold = threshold;
			this.avg = avg;
			this.min = min;
			this.max = max;
		}
	}
	
	public FractAboveStats[] getOverallStats() {
		return Arrays.copyOf(overallStats, overallStats.length);
	}
	
	public List<String> getLevels() {
		return ImmutableList.copyOf(logicTreeLevels);
	}
	
	public List<String> getLevelValues(int levelIndex) {
		return ImmutableList.copyOf(logicTreeValues.get(levelIndex));
	}
	
	public FractAboveStats[] getValueStats(int levelIndex, int valueIndex) {
		return Arrays.copyOf(logicTreeValueStats.get(levelIndex).get(valueIndex), thresholds.length);
	}
	
	public TableBuilder buildTable() {
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		table.initNewLine().addColumn("");
		for (double threshold : thresholds)
			table.addColumn("% &ge; "+(float)threshold);
		table.finalizeLine();
		
		statLine(table, "**Overall**", overallStats);
		
		for (int l=0; l<logicTreeLevels.size(); l++) {
			table.initNewLine();
			table.addColumn("**"+logicTreeLevels.get(l)+"**");
			for (int i=0; i<thresholds.length; i++)
				table.addColumn("");
			table.finalizeLine();
			
			List<String> values = logicTreeValues.get(l);
			List<FractAboveStats[]> valueStats = logicTreeValueStats.get(l);
			for (int v=0; v<values.size(); v++)
				statLine(table, values.get(v), valueStats.get(v));
		}
		
		return table;
	}
	
	private static DecimalFormat pDF = new DecimalFormat("0.00%");
	private void statLine(TableBuilder table, String heading, FractAboveStats[] stats) {
		table.initNewLine();
		table.addColumn(heading);
		for (FractAboveStats stat : stats)
			table.addColumn(pDF.format(stat.avg)+", ["+pDF.format(stat.min)+","+pDF.format(stat.max)+"]");
		table.finalizeLine();
	}

	@Override
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		out.beginObject();
		
		out.name("thresholds").beginArray();
		for (double threshold : thresholds)
			out.value(threshold);
		out.endArray();
		
		out.name("overallStats").beginArray();
		for (FractAboveStats stats : overallStats)
			gson.toJson(stats, stats.getClass(), out);
		out.endArray();
		
		out.name("logicTreeStats").beginArray();
		for (int l=0; l<logicTreeLevels.size(); l++) {
			out.beginObject();
			out.name("level").value(logicTreeLevels.get(l));
			out.name("values").beginArray();
			List<String> values = logicTreeValues.get(l);
			for (int v=0; v<values.size(); v++) {
				out.beginObject();
				out.name("value").value(values.get(v));
				out.name("stats").beginArray();
				for (FractAboveStats stats : logicTreeValueStats.get(l).get(v))
					gson.toJson(stats, stats.getClass(), out);
				out.endArray();
				out.endObject();
			}
			out.endArray();
			out.endObject();
		}
		out.endArray();
		
		out.endObject();
	}

	@Override
	public void initFromJSON(JsonReader in, Gson gson) throws IOException {
		in.beginObject();
		
		while (in.hasNext()) {
			switch (in.nextName()) {
			case "thresholds":
				List<Double> thresholds = new ArrayList<>();
				in.beginArray();
				while (in.hasNext())
					thresholds.add(in.nextDouble());
				in.endArray();
				Preconditions.checkState(!thresholds.isEmpty(), "no thresholds supplied");
				this.thresholds = Doubles.toArray(thresholds);
				break;
			case "overallStats":
				this.overallStats = loadStats(in, gson, this.thresholds);
				break;
			case "logicTreeStats":
				logicTreeLevels = new ArrayList<>();
				logicTreeValues = new ArrayList<>();
				logicTreeValueStats = new ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					in.beginObject();
					String level = null;
					List<String> values = new ArrayList<>();
					List<FractAboveStats[]> valueStats = new ArrayList<>();
					while (in.hasNext()) {
						switch (in.nextName()) {
						case "level":
							level = in.nextString();
							break;
						case "values":
							in.beginArray();
							while (in.hasNext()) {
								in.beginObject();
								String value = null;
								FractAboveStats[] stats = null;
								while (in.hasNext()) {
									switch (in.nextName()) {
									case "value":
										value = in.nextString();
										break;
									case "stats":
										stats = loadStats(in, gson, this.thresholds);
										break;

									default:
										System.err.println("WARNING: skipping value at "+in.getPath());
										in.skipValue();
										break;
									}
								}
								Preconditions.checkNotNull(stats);
								Preconditions.checkNotNull(value);
								values.add(value);
								valueStats.add(stats);
								in.endObject();
							}
							in.endArray();
							break;

						default:
							System.err.println("WARNING: skipping unexpected value at "+in.getPath());
							in.skipValue();
							break;
						}
					}
					Preconditions.checkNotNull(level, "level name was not supplied");
					Preconditions.checkState(!values.isEmpty(), "no values for level %s", level);
					Preconditions.checkState(!valueStats.isEmpty(), "no value stats for level %s", level);
					Preconditions.checkState(values.size() == valueStats.size(),
							"value and stats sizes inconsistent for %s", level);
					logicTreeLevels.add(level);
					logicTreeValues.add(values);
					logicTreeValueStats.add(valueStats);
					in.endObject();
				}
				in.endArray();
				break;

			default:
				System.err.println("WARNING: skipping unexpected value at "+in.getPath());
				in.skipValue();
				break;
			}
		}
		
		in.endObject();
	}
	
	private static FractAboveStats[] loadStats(JsonReader in, Gson gson, double[] thresholds) throws IOException {
		Preconditions.checkNotNull(thresholds, "thresholds muust be specified before stats in json");
		List<FractAboveStats> stats = new ArrayList<>();
		in.beginArray();
		while (in.hasNext())
			stats.add(gson.fromJson(in, FractAboveStats.class));
		in.endArray();
		Preconditions.checkState(stats.size() == thresholds.length);
		return stats.toArray(new FractAboveStats[0]);
	}
	
	public static void main(String[] args) throws IOException {
		File jsonFile = new File("/tmp/logic_tree_rate_stats.json");
		Reader reader = new BufferedReader(new FileReader(jsonFile));
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonReader in = gson.newJsonReader(reader);
		LogicTreeRateStatistics stats = new LogicTreeRateStatistics();
		stats.initFromJSON(in, gson);
		System.out.println(stats.buildTable());
	}

}
