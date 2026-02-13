package org.opensha.sra.calc.parallel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.calc.sourceFilters.params.MagDistCutoffParam;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.imr.AbstractIMR;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sra.gui.portfolioeal.Asset;
import org.opensha.sra.gui.portfolioeal.CalculationExceptionHandler;
import org.opensha.sra.gui.portfolioeal.Portfolio;
import org.opensha.sra.vulnerability.VulnerabilityFetcher;

import com.google.common.base.Preconditions;

import mpi.MPI;
import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

public class MPJ_EAL_Calc extends MPJTaskCalculator implements CalculationExceptionHandler {
	
	public static final String BATCH_ELEMENT_NAME = "BatchCalculation";
	
	private Portfolio portfolio;
	protected List<Asset> assets;
	
	
	private ArrayList<Integer> indexes = new ArrayList<Integer>();
	private ArrayList<Double> eals = new ArrayList<Double>();
	
	protected ThreadedEALCalc calc;
	
	private File outputFile;
	
	public MPJ_EAL_Calc(CommandLine cmd, Portfolio portfolio, Element el) throws IOException, DocumentException, InvocationTargetException {
		this(cmd, portfolio, el, null);
	}
	
	public MPJ_EAL_Calc(CommandLine cmd, Portfolio portfolio, Element el, File outputFile) throws IOException, DocumentException, InvocationTargetException {
		super(cmd);
		
		this.portfolio = portfolio;
		assets = portfolio.getAssetList();
		System.gc();
		
		if (outputFile == null)
			outputFile = new File(el.attributeValue("outputFile"));
		this.outputFile = outputFile;
		
		int numThreads = getNumThreads();
		
		int numERFs;
		if (cmd.hasOption("mult-erfs"))
			numERFs = numThreads; // could set to 1 for single instance
		else
			numERFs = 1;
		
		ERF[] erfs = new ERF[numERFs];
		for (int i=0; i<numERFs; i++) {
			erfs[i] = loadERF(el);
			erfs[i].updateForecast();
		}
		
//		ERF erf = loadERF(el);
//		erf.updateForecast();
		
		ScalarIMR[] imrs = new ScalarIMR[numThreads];
		for (int i=0; i<imrs.length; i++) {
			imrs[i] = (ScalarIMR)AbstractIMR.fromXMLMetadata(el.element(AbstractIMR.XML_METADATA_NAME), null);
		}
		
		if (cmd.hasOption("vuln-file")) {
			File vulnFile = new File(cmd.getOptionValue("vuln-file"));
			System.out.println("trying to load vulnerabilities from: "+vulnFile.getAbsolutePath());
			VulnerabilityFetcher.getVulnerabilities(vulnFile);
			System.out.println("DONE loading vulns.");
		}
		
		ArbitrarilyDiscretizedFunc magThreshFunc;
		if (cmd.hasOption("dist-func"))
			magThreshFunc = new MagDistCutoffParam().getDefaultValue();
		else
			magThreshFunc = null;
		calc = new ThreadedEALCalc(assets, erfs, imrs, this, 200d, magThreshFunc);
	}
	
	private ERF loadERF(Element root) throws InvocationTargetException {
		Element epistemicEl = root.element(AbstractEpistemicListERF.XML_METADATA_NAME);
		if (epistemicEl != null)
			return (ERF) AbstractEpistemicListERF.fromXMLMetadata(epistemicEl);
		else
			return AbstractERF.fromXMLMetadata(root.element(AbstractERF.XML_METADATA_NAME));
	}

	@Override
	protected int getNumTasks() {
		return assets.size();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		double[] results = calc.calculateBatch(batch);
		
		for (int i=0; i<batch.length; i++)
			registerResult(batch[i], results[i]);
	}
	
	protected synchronized void registerResult(int index, double eal) {
		indexes.add(index);
		eals.add(eal);
	}
	
	@Override
	protected void doFinalAssembly() throws Exception {
		// gather the loss
		
		int[] myIndexes = new int[indexes.size()];
		double[] myEALs = new double[indexes.size()];
		for (int i=0; i<indexes.size(); i++) {
			myIndexes[i] = indexes.get(i);
			myEALs[i] = eals.get(i);
		}
		
		int TAG_GET_NUM = 0;
		int TAG_GET_INDEXES = 1;
		int TAG_GET_EALS = 2;
		
		if (rank == 0) {
			double[] eal_vals = new double[assets.size()];
			for (int i=0; i<eal_vals.length; i++)
				eal_vals[i] = Double.NaN;
			
			for (int source=0; source<size; source++) {
				int[] srcIndexes;
				double[] srcEALs;
				
				if (source == rank) {
					srcIndexes = myIndexes;
					srcEALs = myEALs;
				} else {
					// ask for size
					int[] size = new int[1];
					MPI.COMM_WORLD.Recv(size, 0, 1, MPI.INT, source, TAG_GET_NUM);
					
					// get indices
					srcIndexes = new int[size[0]];
					MPI.COMM_WORLD.Recv(srcIndexes, 0, srcIndexes.length, MPI.INT, source, TAG_GET_INDEXES);
					
					// get eals
					srcEALs = new double[size[0]];
					MPI.COMM_WORLD.Recv(srcEALs, 0, srcEALs.length, MPI.DOUBLE, source, TAG_GET_EALS);
				}
				
				for (int i=0; i<srcIndexes.length; i++) {
					eal_vals[srcIndexes[i]] = srcEALs[i];
				}
			}
			
			for (double eal_val : eal_vals)
				Preconditions.checkState(!Double.isNaN(eal_val));
			
			writeOutputFile(eal_vals);
		} else {
			int[] size = { indexes.size() };
			MPI.COMM_WORLD.Send(size, 0, 1, MPI.INT, 0, TAG_GET_NUM);
			
			// get indices
			MPI.COMM_WORLD.Send(myIndexes, 0, myIndexes.length, MPI.INT, 0, TAG_GET_INDEXES);
			
			// get eals
			MPI.COMM_WORLD.Send(myEALs, 0, myEALs.length, MPI.DOUBLE, 0, TAG_GET_EALS);
		}
	}
	
	private void writeOutputFile(double[] eal_vals) throws IOException {
		FileWriter fw = new FileWriter(outputFile);
		
		double portfolioEAL = StatUtils.sum(eal_vals);
		
		// TODO add metadata
		fw.write("Portfolio EAL: "+portfolioEAL+"\n");
		fw.write("\n");
		for (int i=0; i<eal_vals.length; i++) {
			int id = (Integer)assets.get(i).getParameterList().getParameter("AssetID").getValue();
			fw.write(id+","+eal_vals[i]+"\n");
		}
		fw.close();
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		Option vulnOp = new Option("v", "vuln-file", true, "VUL06 file");
		vulnOp.setRequired(false);
		ops.addOption(vulnOp);
		
		Option erfOp = new Option("e", "mult-erfs", false, "If set, a copy of the ERF will be instantiated for each thread.");
		erfOp.setRequired(false);
		ops.addOption(erfOp);
		
		Option distFuncOp = new Option("df", "dist-func", false, "If set, the default distance/mag function will be used instead of 200 km");
		distFuncOp.setRequired(false);
		ops.addOption(distFuncOp);
		
		return ops;
	}
	
	public static void main(String[] args) {
		args = MPJTaskCalculator.initMPJ(args);
		
		try {
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_EAL_Calc.class);
			
			args = cmd.getArgs();
			
			if (args.length < 2 || args.length > 3) {
				System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(MPJ_EAL_Calc.class)
						+" [options] <portfolio_file> <calculation_params_file> [<output_file>]");
				abortAndExit(2);
			}

			Portfolio portfolio = Portfolio.createPortfolio(new File(args[0]));

			Document doc = XMLUtils.loadDocument(new File(args[1]));
			Element root = doc.getRootElement();
			
			if (args.length == 2) {
				// batch mode
				
				Iterator<Element> it = root.elementIterator(BATCH_ELEMENT_NAME);
				
				while (it.hasNext()) {
					MPJ_EAL_Calc driver = new MPJ_EAL_Calc(cmd, portfolio, it.next());
					
					driver.run();
				}
			} else {
				File outputFile = new File(args[2]);
				
				MPJ_EAL_Calc driver = new MPJ_EAL_Calc(cmd, portfolio, root, outputFile);
				
				driver.run();
			}
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

	@Override
	public void calculationException(String errorMessage) {
		abortAndExit(new RuntimeException(errorMessage));
	}

}
