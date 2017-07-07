package org.opensha.sha.calc.hazardMap.mpj;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.opensha.commons.data.Site;
import org.opensha.commons.hpc.mpj.taskDispatch.DispatcherThread;
import org.opensha.commons.hpc.mpj.taskDispatch.MPJTaskCalculator;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.calc.hazardMap.HazardCurveSetCalculator;
import org.opensha.sha.calc.hazardMap.ThreadedHazardCurveSetCalculator;
import org.opensha.sha.calc.hazardMap.components.BinaryCurveArchiver;
import org.opensha.sha.calc.hazardMap.components.CalculationInputsXMLFile;

import com.google.common.base.Preconditions;

public class MPJHazardCurveDriver extends MPJTaskCalculator {
	
	protected static final int TAG_READY_FOR_BATCH = 1;
	protected static final int TAG_NEW_BATCH_LENGH = 2;
	protected static final int TAG_NEW_BATCH = 3;
	
	private static final int MIN_DISPATCH_DEFAULT = 5;
	private static final int MAX_DISPATCH_DEFAULT = 100;
	
	public static final boolean D = true;
	protected static final SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");
	
	private ThreadedHazardCurveSetCalculator calc;
	private List<Site> sites;
	
//	private int rank;
	
	public MPJHazardCurveDriver(CommandLine cmd, String[] args) throws IOException, DocumentException, InvocationTargetException {
		super(cmd);
		if (args.length != 1) {
			System.err.println("USAGE: HazardCurveDriver [<options>] <XML input file>");
			abortAndExit(2);
		}
		
		File xmlFile = new File(args[0]);
		
		if (!xmlFile.exists()) {
			throw new IOException("XML Input file '" + args[0] + "' not found!");
		}
		
		Document doc = XMLUtils.loadDocument(xmlFile.getAbsolutePath());
		
		Preconditions.checkArgument(getNumThreads() >= 1, "threads must be >= 1. you supplied: "+getNumThreads());
		
		boolean multERFs = cmd.hasOption("mult-erfs");
		
		debug("loading inputs for "+getNumThreads()+" threads");
		CalculationInputsXMLFile[] inputs = CalculationInputsXMLFile.loadXML(doc, getNumThreads(), multERFs);
		// initialize binary curve writer if applicable
		if (rank == 0 && inputs[0].getArchiver() instanceof BinaryCurveArchiver) {
			((BinaryCurveArchiver)inputs[0].getArchiver()).initialize();
		}
		sites = inputs[0].getSites();
		HazardCurveSetCalculator[] calcs = new HazardCurveSetCalculator[getNumThreads()];
		for (int i=0; i<inputs.length; i++)
			calcs[i] = new HazardCurveSetCalculator(inputs[i]);
		
		Preconditions.checkNotNull(calcs, "calcs cannot be null!");
		Preconditions.checkArgument(calcs.length > 0, "calcs cannot be empty!");
		for (HazardCurveSetCalculator calc : calcs)
			Preconditions.checkNotNull(calc, "calc cannot be null!");
		Preconditions.checkNotNull(sites, "sites cannot be null!");
		Preconditions.checkArgument(!sites.isEmpty(), "sites cannot be empty!");
		
		calc = new ThreadedHazardCurveSetCalculator(calcs);
	}
	
	@Override
	public int getNumTasks() {
		return sites.size();
	}
	
	@Override
	public void calculateBatch(int[] batch) throws Exception, InterruptedException {
		calc.calculateCurves(sites, batch);
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		Option erfOp = new Option("e", "mult-erfs", false, "If set, a copy of the ERF will be instantiated for each thread.");
		erfOp.setRequired(false);
		ops.addOption(erfOp);
		
		return ops;
	}
	
	public static void main(String[] args) {
		args = MPJTaskCalculator.initMPJ(args);
		
		try {
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJHazardCurveDriver.class);
			
			args = cmd.getArgs();
			
			MPJHazardCurveDriver driver = new MPJHazardCurveDriver(cmd, args);
			
			driver.run();
			
			driver.calc.close();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		// do nothing
	}

}
