package org.opensha.sha.calc.hazardMap;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;

import org.dom4j.Document;
import org.opensha.commons.data.Site;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.calc.hazardMap.components.CalculationInputsXMLFile;
import org.opensha.sha.calc.hazardMap.components.CalculationSettings;
import org.opensha.sha.calc.hazardMap.components.CurveResultsArchiver;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * This is a command line hazard curve calculator. It will be called by the Condor submit
 * scripts to calculate a set of hazard curves. It sets up the inputs to {@link HazardCurveSetCalculator}
 * and initiates the calculation.
 * 
 * Initially this will take an XML file which describes the inputs. This is the XML format that
 * I (Kevin Milner) used for a previous implementation. For GEM purposes, this could be changed
 * to some other input, as long as all of the inputs are specified.
 * 
 * @author kevin
 *
 */
public class HazardCurveDriver {
	
	private HazardCurveSetCalculator[] calcs;
	private List<Site> sites;
	
	public HazardCurveDriver(Document doc, int threads) throws InvocationTargetException, IOException {
		this(CalculationInputsXMLFile.loadXML(doc, threads, true), threads);
	}
	
	private static CalculationInputsXMLFile[] toArray(CalculationInputsXMLFile inputs) {
		CalculationInputsXMLFile[] array = { inputs };
		return array;
	}
	
	public HazardCurveDriver(CalculationInputsXMLFile inputs) throws InvocationTargetException, IOException {
		this(toArray(inputs), 1);
	}
	
	public HazardCurveDriver(CalculationInputsXMLFile[] inputs, int threads) throws InvocationTargetException, IOException {
		Preconditions.checkArgument(inputs.length == threads, "incompatible number of threads/inputs");
		calcs = new HazardCurveSetCalculator[threads];
		
		for (int i=0; i<inputs.length; i++)
			calcs[i] = new HazardCurveSetCalculator(inputs[i]);
		
		sites = inputs[0].getSites();
	}
	
	public void startCalculation() throws IOException, InterruptedException {
		if (calcs.length > 1) {
			ThreadedHazardCurveSetCalculator calc = new ThreadedHazardCurveSetCalculator(calcs);
			calc.calculateCurves(sites);
		} else {
			calcs[0].calculateCurves(sites, null);
		}
		calcs[0].close();
	}
	
	/**
	 * Command line hazard curve calculator
	 * 
	 * @param args
	 */
	public static void main(String args[]) {
		System.out.println(HazardCurveDriver.class.getName() + ": starting up");
		try {
			if (args.length != 1 && args.length != 2) {
				System.err.println("USAGE: HazardCurveDriver [--threaded] <XML File>");
				System.exit(2);
			}
			
			String filePath;
			
			int threads = 1;
			if (args.length == 2) {
				Preconditions.checkArgument(args[0].equals("--threaded"), "Unknown argument: "+args[0]);
				threads = Runtime.getRuntime().availableProcessors();
				filePath = args[1];
			} else {
				filePath = args[0];
			}

			File xmlFile = new File(filePath);
			if (!xmlFile.exists()) {
				throw new IOException("XML Input file '"+filePath+"' not found!");
			}
			
			Document doc = XMLUtils.loadDocument(xmlFile.getAbsolutePath());
			HazardCurveDriver driver = new HazardCurveDriver(doc, threads);
			
			driver.startCalculation();
			System.exit(0);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}

}
