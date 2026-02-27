package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.IntegerSampler;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint.Adapter;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ColumnOrganizedAnnealingData;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ReweightEvenFitSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SerialSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompoundCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.EnergyCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationsPerVariableCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.MisfitStdDevCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.CoolingScheduleType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.Quantity;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.JsonAdapter;

/**
 * This class contains the constraints and inversion parameters needed to configure and run an inversion.
 * 
 * @author kevin
 *
 */
public class InversionConfiguration implements SubModule<ModuleContainer<?>>, JSON_TypeAdapterBackedModule<InversionConfiguration> {
	
	private transient ModuleContainer<?> parent;
	
	// default values
	private static final GenerationFunctionType PERTURB_DEFAULT = GenerationFunctionType.VARIABLE_EXPONENTIAL_SCALE;
	private static final NonnegativityConstraintType NON_NEG_DEFAULT = NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN;
	private static final CoolingScheduleType COOL_DEFAULT = CoolingScheduleType.FAST_SA;
	private static final CompletionCriteria SUB_COMPLETION_DEFAULT = TimeCompletionCriteria.getInSeconds(1l);
	
	// inputs
	private List<InversionConstraint> constraints;
	private double[] waterLevel;
	private double[] initial;
	
	// annealing params
	private double[] variablePertubationBasis;
	@JsonAdapter(IntegerSampler.Adapter.class)
	private IntegerSampler sampler;
	private GenerationFunctionType perturb = PERTURB_DEFAULT;
	private NonnegativityConstraintType nonneg = NON_NEG_DEFAULT;
	private CoolingScheduleType cool = COOL_DEFAULT;
	private CompletionCriteria completion;
	private Quantity reweightTargetQuantity = null;
	
	// for threaded inversions
	private int threads = 1;
	private CompletionCriteria subCompletion;
	private Integer avgThreads;
	private CompletionCriteria avgCompletion;
	
	/**
	 * Initializes a configuration builder with the given constraints and completion criteria
	 * 
	 * @param constraints
	 * @param completion
	 * @return
	 */
	public static Builder builder(List<InversionConstraint> constraints, CompletionCriteria completion) {
		return new Builder(constraints, completion);
	}
	
	/**
	 * Initializes a configuration builder from an existing configuration, which can then be modified
	 * 
	 * @param config
	 * @return
	 */
	public static Builder builder(InversionConfiguration config) {
		return new Builder(config);
	}
	
	/**
	 * Initializes a configuration builder with the given constraints, and annealing parameters from a command line
	 * 
	 * @param constraints
	 * @param cmd
	 * @return
	 */
	public static Builder builder(List<InversionConstraint> constraints, CommandLine cmd) {
		return new Builder(constraints, cmd);
	}
	
	public static class Builder {
		
		private InversionConfiguration config;
		
		private Builder(InversionConfiguration config) {
			this.config = config.copy();
		}
		
		private Builder(List<InversionConstraint> constraints, CompletionCriteria completion) {
			config = new InversionConfiguration();
			config.constraints = ImmutableList.copyOf(constraints);
			config.completion = completion;
		}
		
		private Builder(List<InversionConstraint> constraints, CommandLine cmd) {
			config = new InversionConfiguration();
			config.constraints = ImmutableList.copyOf(constraints);
			
			if (!cmd.hasOption("threads"))
				config.threads = FaultSysTools.defaultNumThreads();
			
			forCommandLine(cmd);
		}
		
		public Builder forCommandLine(CommandLine cmd) {
			if (cmd == null)
				return this;
			if (cmd.hasOption("threads"))
				config.threads = FaultSysTools.getNumThreads(cmd);
			
			if (cmd.hasOption("completion"))
				config.completion = parseCompletionArg(cmd.getOptionValue("completion"));
			
			if (cmd.hasOption("completion-sd")) {
				if (config.completion != null && !cmd.hasOption("completion"))
					// we're overriding one that wasn't just supplied, clear out the old one
					config.completion = null;
				double sd = Double.parseDouble(cmd.getOptionValue("completion-sd"));
				ConstraintWeightingType type = null;
				if (cmd.hasOption("completion-sd-type"))
					type = ConstraintWeightingType.valueOf(cmd.getOptionValue("completion-sd-type"));
				MisfitStdDevCompletionCriteria completion = new MisfitStdDevCompletionCriteria(type, sd);
				if (config.completion == null)
					config.completion = completion;
				else
					config.completion = new CompoundCompletionCriteria(List.of(config.completion, completion));
			}
			
			if (cmd.hasOption("avg-threads"))
				config.avgThreads = Integer.parseInt(cmd.getOptionValue("avg-threads"));
			
			if (config.avgThreads != null && config.avgThreads > 0) {
				if (cmd.hasOption("avg-completion"))
					config.avgCompletion = parseCompletionArg(cmd.getOptionValue("avg-completion"));
				else
					Preconditions.checkArgument(config.avgCompletion != null,
							"Averaging enabled but --avg-completion <value> not specified");
			}
			
			if (cmd.hasOption("sub-completion"))
				config.subCompletion = parseCompletionArg(cmd.getOptionValue("sub-completion"));
			
			if (cmd.hasOption("perturb"))
				config.perturb = GenerationFunctionType.valueOf(cmd.getOptionValue("perturb"));
			
			if (cmd.hasOption("non-negativity"))
				config.nonneg = NonnegativityConstraintType.valueOf(cmd.getOptionValue("non-negativity"));
			
			if (cmd.hasOption("cooling-schedule"))
				config.cool = CoolingScheduleType.valueOf(cmd.getOptionValue("cooling-schedule"));
			
			if (cmd.hasOption("reweight-quantity"))
				config.reweightTargetQuantity = Quantity.valueOf(cmd.getOptionValue("reweight-quantity"));
			else if (cmd.hasOption("reweight"))
				config.reweightTargetQuantity = ReweightEvenFitSimulatedAnnealing.QUANTITY_DEFAULT;
			
			return this;
		}
		
		public Builder waterLevel(double[] waterLevel) {
			config.waterLevel = waterLevel;
			return this;
		}
		
		public Builder initialSolution(double[] initial) {
			config.initial = initial;
			return this;
		}
		
		public Builder perturbation(GenerationFunctionType perturb) {
			config.perturb = perturb;
			return this;
		}
		
		public Builder variablePertubationBasis(double[] variablePertubationBasis) {
			config.variablePertubationBasis = variablePertubationBasis;
			return this;
		}
		
		public Builder nonNegativity(NonnegativityConstraintType nonneg) {
			config.nonneg = nonneg;
			return this;
		}
		
		public Builder cooling(CoolingScheduleType cool) {
			config.cool = cool;
			return this;
		}
		
		public Builder sampler(double[] samplerBasis) {
			if (samplerBasis == null) {
				config.sampler = null;
				return this;
			}
			return sampler(new IntegerPDF_FunctionSampler(samplerBasis));
		}
		
		public Builder clearSampler() {
			config.sampler = null;
			return this;
		}
		
		public Builder sampler(IntegerSampler sampler) {
			config.sampler = sampler;
			return this;
		}
		
		public Builder threads(int threads) {
			config.threads = threads;
			if (config.subCompletion == null)
				config.subCompletion = SUB_COMPLETION_DEFAULT;
			return this;
		}
		
		/**
		 * This normalizes the weights of all constraints by their row counts. This effectively weights each constraint
		 * type equally, despite varying numbers of rows.
		 * 
		 * @return
		 */
		public Builder normalizeConstraintWeightsByRowCount() {
			return normalizeConstraintWeightsByRowCount(null);
		}
		
		/**
		 * This normalizes the weights of all constraints with the given weighting type by their row counts.
		 * This effectively weights each constraint type equally, despite varying numbers of rows.
		 * 
		 * @param weightingType
		 * @return
		 */
		public Builder normalizeConstraintWeightsByRowCount(ConstraintWeightingType weightingType) {
			for (InversionConstraint constraint : config.getConstraints()) {
				if (weightingType == null || constraint.getWeightingType() == weightingType) {
					int rows = constraint.getNumRows();
					constraint.setWeight(constraint.getWeight()/(double)rows);
				}
			}
			return this;
		}
		
		public Builder subCompletion(CompletionCriteria subCompletion) {
			config.subCompletion = subCompletion;
			return this;
		}
		
		public Builder completion(CompletionCriteria completion) {
			config.completion = completion;
			return this;
		}
		
		public Builder avgThreads(int avgThreads, CompletionCriteria avgCompletion) {
			config.avgThreads = avgThreads;
			config.avgCompletion = avgCompletion;
			return this;
		}
		
		public Builder noAvg() {
			config.avgThreads = null;
			config.avgCompletion = null;
			return this;
		}
		
		public Builder reweight() {
			return reweight(ReweightEvenFitSimulatedAnnealing.QUANTITY_DEFAULT);
		}
		
		public Builder reweight(Quantity reweightTargetQuantity) {
			config.reweightTargetQuantity = reweightTargetQuantity;
			return this;
		}
		
		public Builder except(Class<? extends InversionConstraint> type) {
			return except(type, true);
		}
		
		public Builder except(Class<? extends InversionConstraint> type, boolean failIfNotFound) {
			List<InversionConstraint> constraints = new ArrayList<>(config.constraints);
			for (int i=constraints.size(); --i>=0;)
				if (type.isAssignableFrom(constraints.get(i).getClass()))
					constraints.remove(i);
			Preconditions.checkState(!constraints.isEmpty(), "No constraints left!");
			Preconditions.checkState(!failIfNotFound || constraints.size() < config.constraints.size(), "No constraints removed!");
			config.constraints = constraints;
			return this;
		}
		
		public Builder add(InversionConstraint constraint) {
			List<InversionConstraint> constraints = new ArrayList<>(config.constraints);
			constraints.add(constraint);
			config.constraints = constraints;
			return this;
		}
		
		public InversionConfiguration build() {
			Preconditions.checkNotNull(config.completion, "No completion criteria specified");
			Preconditions.checkNotNull(config.constraints, "No comstraints supplied");
			Preconditions.checkState(!config.constraints.isEmpty(), "No comstraints supplied");
			
			Preconditions.checkState(config.threads >= 1, "Threads must be positive, supplied: %s", config.threads);
			if (config.subCompletion == null && (config.threads > 1	|| config.reweightTargetQuantity != null))
				config.subCompletion = SUB_COMPLETION_DEFAULT;
			
			if (config.avgThreads != null) {
				Preconditions.checkState(config.avgThreads >= 1,
						"Averaging threads (if enabled) must be >=1: %s", config.avgThreads);
				Preconditions.checkState(config.avgThreads <= config.threads,
						"The number of averaging threads (%s) must be less than the number of total threads (%s)",
						config.avgThreads, config.threads);
				Preconditions.checkNotNull(config.avgCompletion,
						"Averaging enabled but average completion criteria not specified");
			}
			
			// copy it so that this builder can be modified and reused
			return config.copy();
		}
	}
	
	private static CompletionCriteria parseCompletionArg(String value) {
		value = value.trim().toLowerCase();
		if (value.endsWith("h"))
			return TimeCompletionCriteria.getInHours(Long.parseLong(value.substring(0, value.length()-1)));
		if (value.endsWith("m"))
			return TimeCompletionCriteria.getInMinutes(Long.parseLong(value.substring(0, value.length()-1)));
		if (value.endsWith("s"))
			return TimeCompletionCriteria.getInSeconds(Long.parseLong(value.substring(0, value.length()-1)));
		if (value.endsWith("e")) // TODO add to docs
			return new EnergyCompletionCriteria(Double.parseDouble(value.substring(0, value.length()-1)));
		if (value.endsWith("ip")) // TODO add to docs
			return new IterationsPerVariableCompletionCriteria(Double.parseDouble(value.substring(0, value.length()-2)));
		if (value.endsWith("i"))
			value = value.substring(0, value.length()-1);
		return new IterationCompletionCriteria(Long.parseLong(value));
	}
	
	public static Options createSAOptions() {
		Options ops = new Options();

		ops.addOption(FaultSysTools.threadsOption());

		/*
		 *  Simulated Annealing parameters
		 */

		String complText = "If either no suffix or 'i' is appended, then it is assumed to be an iteration count. "
				+ "Specify times in hours, minutes, or seconds by appending 'h', 'm', or 's' respecively. Specify total "
				+ "energy by appending 'e'. Fractions are not allowed.";

		Option completionOption = new Option("c", "completion", true, "Total inversion completion criteria. "+complText);
		completionOption.setRequired(false);
		ops.addOption(completionOption);
		
		// TODO add to docs
		Option completionSDOption = new Option("csd", "completion-sd", true, "Total inversion completion criteria where "
				+ "misfits for each constraint must have no more than the given standard deviation. This can apply "
				+ "to only a given constraint weighting type if you supply the --completion-sd-type <type> argument.");
		completionSDOption.setRequired(false);
		ops.addOption(completionSDOption);
		
		// TODO add to docs
		Option completionSDTypeOption = new Option("csd", "completion-sd-type", true, "Constraint wieghting type for the"
				+ " --completion-sd option. Default is all constraints, options are: "
				+FaultSysTools.enumOptions(ConstraintWeightingType.class));
		completionSDTypeOption.setRequired(false);
		ops.addOption(completionSDTypeOption);

		Option avgOption = new Option("at", "avg-threads", true, "Enables a top layer of threads that average results "
				+ "of worker threads at fixed intervals. Supply the number of averaging threads, which must be < threads. "
				+ "Default is no averaging, if enabled you must also supply --avg-completion <value>.");
		avgOption.setRequired(false);
		ops.addOption(avgOption);

		Option avgCompletionOption = new Option("ac", "avg-completion", true,
				"Interval between across-thread averaging. "+complText);
		avgCompletionOption.setRequired(false);
		ops.addOption(avgCompletionOption);

		Option subCompletionOption = new Option("sc", "sub-completion", true,
				"Interval between across-thread synchronization. "+complText+" Default: 1s");
		subCompletionOption.setRequired(false);
		ops.addOption(subCompletionOption);

		Option perturbOption = new Option("pt", "perturb", true, "Perturbation function. One of "
				+FaultSysTools.enumOptions(GenerationFunctionType.class)+". Default: "+PERTURB_DEFAULT.name());
		perturbOption.setRequired(false);
		ops.addOption(perturbOption);

		Option nonNegOption = new Option("nn", "non-negativity", true, "Non-negativity constraint. One of "
				+FaultSysTools.enumOptions(NonnegativityConstraintType.class)+". Default: "+NON_NEG_DEFAULT.name());
		nonNegOption.setRequired(false);
		ops.addOption(nonNegOption);

		Option coolOption = new Option("cool", "cooling-schedule", true, "Cooling schedule. One of "
				+FaultSysTools.enumOptions(CoolingScheduleType.class)+". Default: "+COOL_DEFAULT.name());
		coolOption.setRequired(false);
		ops.addOption(coolOption);

		Option reweightQuantity = new Option("rwq", "reweight-quantity", true, "Enables dynamic constraint reweighting, "
				+ "targeting the given quantity. Note that this only applies to uncertainty-weighted constraints. "
				+ "If you simply want enable re-weighting using the default quantity ("
				+ReweightEvenFitSimulatedAnnealing.QUANTITY_DEFAULT.name()+"), use --reweight instead.");
		reweightQuantity.setRequired(false);
		ops.addOption(reweightQuantity);

		Option reweight = new Option("rw", "reweight", false, "Enables dynamic constraint reweighting, "
				+ "targeting "+ReweightEvenFitSimulatedAnnealing.QUANTITY_DEFAULT.name()+". Note that this only applies "
				+ "to uncertainty-weighted constraints. If you want to target a different quantity, use "
				+ "--reweight-quantity <quantity> instead.");
		reweight.setRequired(false);
		ops.addOption(reweight);
		
		return ops;
	}
	
	private InversionConfiguration() {}

	@Override
	public String getName() {
		return "Inversion Configuration";
	}
	
	public SimulatedAnnealing buildSA(InversionInputGenerator inputs) {
		ColumnOrganizedAnnealingData equalityData = new ColumnOrganizedAnnealingData(inputs.getA(), inputs.getD());
		ColumnOrganizedAnnealingData inequalityData = null;
		if (inputs.getA_ineq() != null)
			inequalityData = new ColumnOrganizedAnnealingData(inputs.getA_ineq(), inputs.getD_ineq());
		SimulatedAnnealing sa;
		if (threads > 1) {
			if (avgThreads != null && avgThreads > 0) {
				int threadsPerAvg = (int)Math.ceil((double)threads/(double)avgThreads);
				Preconditions.checkState(threadsPerAvg <= threads);
				Preconditions.checkState(threadsPerAvg > 0);
				
				int threadsLeft = threads;
				
				// arrange lower-level (actual worker) SAs
				List<SimulatedAnnealing> tsas = new ArrayList<>();
				while (threadsLeft > 0) {
					int myThreads = Integer.min(threadsLeft, threadsPerAvg);
					if (myThreads > 1)
						tsas.add(new ThreadedSimulatedAnnealing(equalityData, inequalityData,
								inputs.getInitialSolution(), 0d, myThreads, subCompletion));
					else
						tsas.add(new SerialSimulatedAnnealing(equalityData, inequalityData,
								inputs.getInitialSolution(), 0d));
					threadsLeft -= myThreads;
				}
				sa = new ThreadedSimulatedAnnealing(tsas, avgCompletion, true);
			} else {
				sa = new ThreadedSimulatedAnnealing(equalityData, inequalityData,
						inputs.getInitialSolution(), 0d, threads, subCompletion);
			}
		} else {
			sa = new SerialSimulatedAnnealing(equalityData, inequalityData, inputs.getInitialSolution(), 0d);
		}
		sa.setConstraintRanges(inputs.getConstraintRowRanges());
		if (reweightTargetQuantity != null) {
			if (sa instanceof ThreadedSimulatedAnnealing) {
				sa = new ReweightEvenFitSimulatedAnnealing((ThreadedSimulatedAnnealing)sa, reweightTargetQuantity);
			} else {
				sa.setConstraintRanges(null);
				sa = new ReweightEvenFitSimulatedAnnealing(sa, subCompletion, reweightTargetQuantity);
				sa.setConstraintRanges(inputs.getConstraintRowRanges());
			}
		}
		
		if (perturb.isVariable()) {
			double[] basis = variablePertubationBasis;
			if (basis == null)
				basis = Inversions.getDefaultVariablePerturbationBasis(inputs.getRuptureSet());
			
			sa.setVariablePerturbationBasis(basis);
		}
		sa.setPerturbationFunc(perturb);
		sa.setNonnegativeityConstraintAlgorithm(nonneg);
		sa.setCoolingFunc(cool);
		if (sampler != null)
			sa.setRuptureSampler(sampler);
		
		return sa;
	}
	
	public InversionConfiguration copy() {
		InversionConfiguration copy = new InversionConfiguration();
		
		copy.set(this);
		
		return copy;
	}

	public ImmutableList<InversionConstraint> getConstraints() {
		return ImmutableList.copyOf(constraints);
	}

	public CompletionCriteria getCompletionCriteria() {
		return completion;
	}

	public double[] getWaterLevel() {
		return waterLevel;
	}

	public double[] getInitialSolution() {
		return initial;
	}

	public GenerationFunctionType getPerturbationFunc() {
		return perturb;
	}

	public NonnegativityConstraintType getNonnegConstraint() {
		return nonneg;
	}

	public CoolingScheduleType getCoolingSchedule() {
		return cool;
	}

	public double[] getVariablePertubationBasis() {
		return variablePertubationBasis;
	}

	public IntegerSampler getSampler() {
		return sampler;
	}

	public int getThreads() {
		return threads;
	}

	public CompletionCriteria getSubCompletionCriteria() {
		return subCompletion;
	}

	public Integer getAvgThreads() {
		return avgThreads;
	}

	public CompletionCriteria getAvgCompletionCriteria() {
		return avgCompletion;
	}

	public Quantity getReweightTargetQuantity() {
		return reweightTargetQuantity;
	}

	@Override
	public void setParent(ModuleContainer<?> parent) throws IllegalStateException {
		this.parent = parent;
		FaultSystemRupSet rupSet = null;
		if (parent instanceof FaultSystemRupSet)
			rupSet = (FaultSystemRupSet)parent;
		else if (parent instanceof FaultSystemSolution)
			rupSet = ((FaultSystemSolution)parent).getRupSet();
		if (rupSet != null && constraints != null)
			for (InversionConstraint constraint : constraints)
				constraint.setRuptureSet(rupSet);
	}

	@Override
	public ModuleContainer<?> getParent() {
		return parent;
	}

	@Override
	public SubModule<ModuleContainer<?>> copy(ModuleContainer<?> newParent) throws IllegalStateException {
		InversionConfiguration copy = copy();
		if (this.parent != null && this.parent != newParent)
			copy.setParent(newParent);
		return copy;
	}

	@Override
	public String getFileName() {
		return "inversion_config.json";
	}

	@Override
	public Type getType() {
		return InversionConfiguration.class;
	}

	@Override
	public InversionConfiguration get() {
		return this;
	}

	@Override
	public void set(InversionConfiguration source) {
		constraints = source.constraints;
		waterLevel = source.waterLevel;
		initial = source.initial;
		
		perturb = source.perturb;
		nonneg = source.nonneg;
		cool = source.cool;
		completion = source.completion;
		variablePertubationBasis = source.variablePertubationBasis;
		sampler = source.sampler;
		
		threads = source.threads;
		subCompletion = source.subCompletion;
		avgThreads = source.avgThreads;
		avgCompletion = source.avgCompletion;
		reweightTargetQuantity = source.reweightTargetQuantity;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(initial);
		result = prime * result + Arrays.hashCode(variablePertubationBasis);
		result = prime * result + Arrays.hashCode(waterLevel);
		result = prime * result + Objects.hash(complStr(avgCompletion), avgThreads, complStr(completion),
				constrStr(constraints), cool, nonneg, perturb, sampler, complStr(subCompletion), threads);
		return result;
	}
	
	private static String complStr(CompletionCriteria crit) {
		if (crit == null)
			return null;
		return crit.getClass().getName()+" "+crit.toString();
	}
	
	private static List<String> constrStr(List<InversionConstraint> constraints) {
		if (constraints == null || constraints.isEmpty())
			return null;
		List<String> ret = new ArrayList<>(constraints.size());
		for (InversionConstraint constr : constraints)
			ret.add(constr.getClass().getName()+" "+constr.getName()+" "+(float)constr.getWeight());
		return ret;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		InversionConfiguration other = (InversionConfiguration) obj;
		return Objects.equals(complStr(avgCompletion), complStr(other.avgCompletion))
				&& Objects.equals(avgThreads, other.avgThreads)
				&& Objects.equals(complStr(completion), complStr(other.completion))
				&& Objects.equals(constrStr(constraints), constrStr(other.constraints))
				&& cool == other.cool && Arrays.equals(initial, other.initial) && nonneg == other.nonneg
				&& perturb == other.perturb && Objects.equals(sampler, other.sampler)
				&& Objects.equals(complStr(subCompletion), complStr(other.subCompletion)) && threads == other.threads
				&& Arrays.equals(variablePertubationBasis, other.variablePertubationBasis)
				&& Arrays.equals(waterLevel, other.waterLevel);
	}

	@Override
	public void registerTypeAdapters(GsonBuilder builder) {
		builder.serializeSpecialFloatingPointValues();
		// no extra adapters, (default serialization works fine)
	}
	
	public static void writeJSON(InversionConfiguration config, File jsonFile) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile));
		Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
		gson.toJson(config, InversionConfiguration.class, writer);
		writer.flush();
		writer.close();
	}
	
	public static InversionConfiguration readJSON(File jsonFile, FaultSystemRupSet rupSet) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
		Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
				.registerTypeAdapter(InversionConstraint.class, new Adapter(rupSet)).create();
		InversionConfiguration config = gson.fromJson(reader, InversionConfiguration.class);
//		System.out.println("Read configuration. Completion: "+config.completion);
		return config;
	}

}
