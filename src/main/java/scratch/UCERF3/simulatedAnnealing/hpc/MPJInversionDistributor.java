package scratch.UCERF3.simulatedAnnealing.hpc;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import mpi.MPI;
import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.util.XMLUtils;

import scratch.UCERF3.analysis.MPJDistributedCompoundFSSPlots;
import scratch.UCERF3.inversion.CommandLineInversionRunner;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class MPJInversionDistributor extends MPJTaskCalculator {
	
	private List<String[]> argsList;

	public MPJInversionDistributor(CommandLine cmd, List<String[]> argsList) {
		super(cmd);
		this.argsList = argsList;
	}

	@Override
	protected int getNumTasks() {
		return argsList.size();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		Thread[] threads = new Thread[batch.length];
		for (int i=0; i<batch.length; i++)
			threads[i] = new Thread(new CalculationThread(argsList.get(batch[i])));
		
		for (Thread thread : threads)
			thread.start();
		
		for (Thread thread : threads)
			thread.join();
	}
	
	private static class CalculationThread implements Runnable {
		private String[] args;
		
		public CalculationThread(String[] args) {
			this.args = args;
		}

		@Override
		public void run() {
			CommandLineInversionRunner.run(args, true);
		}
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		// do nothing
	}
	
	private static List<String[]> loadXMLInputFile(File file) throws IOException, DocumentException {
		List<String[]> argsList = Lists.newArrayList();
		
		Document doc = XMLUtils.loadDocument(file);
		Element root = doc.getRootElement();
		Element invConfEl = root.element("InversionConfigurations");
		
		int numArgs = Integer.parseInt(invConfEl.attributeValue("num"));
		while (argsList.size() < numArgs)
			argsList.add(null);
		
		Iterator<Element> it = invConfEl.elementIterator();
		while (it.hasNext()) {
			Element invEl = it.next();
			
			String command = invEl.attributeValue("args");
			String[] args = Iterables.toArray(Splitter.on(" ").split(command), String.class);
			int num = Integer.parseInt(invEl.attributeValue("num"));
			Preconditions.checkState(args.length == num, "Incorrect argument lengh on parse! " +
					"Expected="+num+", actual="+args.length+". Command: "+command);
			int index = Integer.parseInt(invEl.attributeValue("index"));
			argsList.set(index, args);
		}
		
		// make sure none are null
		for (String[] args : argsList)
			Preconditions.checkNotNull(args, "Missing args from XML file!");
		
		return argsList;
	}
	
	public static void writeXMLInputFile(List<String[]> argsList, File file)
			throws IOException {
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		Element invConfEl = root.addElement("InversionConfigurations");
		invConfEl.addAttribute("num", argsList.size()+"");
		for (int i=0; i<argsList.size(); i++) {
			String[] args = argsList.get(i);
			Element invEl = invConfEl.addElement("InversionConfiguration");
			for (int j=0; j<args.length; j++)
				args[j] = args[j].trim();
			String command = Joiner.on(" ").join(args);
			invEl.addAttribute("index", i+"");
			invEl.addAttribute("args", command);
			invEl.addAttribute("num", args.length+"");
		}
		XMLUtils.writeDocumentToFile(file, doc);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("ARGS: "+Joiner.on(" ").join(args));
		args = MPJTaskCalculator.initMPJ(args);
		
		try {
			Options options = createOptions();

			CommandLine cmd = parse(options, args, MPJDistributedCompoundFSSPlots.class);

			args = cmd.getArgs();
			
			// make sure that exact dispatch was selected
			Preconditions.checkArgument(cmd.hasOption("exact"), "Must specify exact dispatch!");
			int dispatchNum = Integer.parseInt(cmd.getOptionValue("exact"));
			
			Preconditions.checkArgument(args.length == 1, "Must specify input XML file!");
			File inputFile = new File(args[0]);
			System.out.println("Loading Input File: "+inputFile.getAbsolutePath());
			List<String[]> argsList = loadXMLInputFile(inputFile);
			System.out.println("Loaded "+argsList.size()+" inversion args");
			
			int numNodes = MPI.COMM_WORLD.Size();
			int numInversions = argsList.size();
			
			// make sure that the dispatch amount agrees with the number of inversions/nodes
			int numSlots = numNodes * dispatchNum;
			Preconditions.checkState(numInversions <= numSlots, "Too few slots! slots="
					+numSlots+", inversions="+numInversions);
			
			System.out.println("Launching!");
			MPJInversionDistributor driver = new MPJInversionDistributor(cmd, argsList);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
