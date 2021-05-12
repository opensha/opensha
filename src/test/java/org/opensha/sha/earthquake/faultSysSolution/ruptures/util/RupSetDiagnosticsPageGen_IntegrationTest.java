package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import static org.junit.Assert.*;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dom4j.DocumentException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.HistScalarValues;

//import nz.cri.gns.NZSHM22.util.NZSHM22_InversionDiagnosticsReportBuilder;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;

class ExampleSubclass extends RupSetDiagnosticsPageGen {

	public ExampleSubclass(CommandLine cmd) throws IOException, DocumentException {
		super(cmd);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void generatePage() throws IOException { // throws IOException
	}
	
}

public class RupSetDiagnosticsPageGen_IntegrationTest {

	private static URL alpineVernonRupturesUrl;
	private static File tempFolder;
	private static CommandLine cmd;
	private static String inputName = "Dummy name";

	@BeforeClass
	public static void setUpBeforeClass() throws IOException, DocumentException, URISyntaxException, ParseException {
		
		System.setProperty("java.awt.headless", "true"); //We don't want modal dialogs in our automated tests.
		
		/*
		 * TODO: we should be using java resources here, but some global gradle/eclipse configuration needs agreement
		 *
		 * alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("testAlpineVernonInversion.zip");
		*/ 
		alpineVernonRupturesUrl = new File("test/resources/scratch/UCERF3/utils/testAlpineVernonInversion.zip").toURL();
		tempFolder = Files.createTempDirectory("_testNew").toFile();
		
		Options options = RupSetDiagnosticsPageGen.createOptions();
		CommandLineParser parser = new DefaultParser();	
		
		String[] args = new String[] {
				"-rs", alpineVernonRupturesUrl.getPath(), 	//set input rupture set
				"-output-dir", tempFolder.toString(), 		//set the report destination
				"-name", inputName,
				};

		cmd = parser.parse(options, args);		
	
	}
	
	@AfterClass
	public static void tearDownAfterClass() throws IOException {
		
		File[] tmp_folders = {
				new File(tempFolder, "resources"),
				new File(tempFolder, "hist_rup_pages"),
				tempFolder}; //this one must be last
		
		for (File folder : tmp_folders) {
			File[] files = folder.listFiles();
			for (File f : files) {
				f.delete();
			}		
			Files.deleteIfExists(folder.toPath());
		}		
		
	}
	
	@Test
	public void testOptionsVisiblityIsPublic() {
		Options options = RupSetDiagnosticsPageGen.createOptions();
		
		//so we can extend the base class options as we wish ...
		Option dummyOption = new Option("fn", "fault-name", true,
				"fault name (or portion thereof) to filter");
		options.addOption(dummyOption);
		assertTrue(options.hasOption("fn"));
	}

	@Test
	public void testHistScalarClassVisibility() {
		HistScalar magscalar = null; 
		for (HistScalar scalar : HistScalar.values()) {
			if (scalar != HistScalar.MAG)
				continue;
			magscalar = scalar;
		}
		assertTrue(magscalar.getName() == HistScalar.MAG.getName());
	}

		
	@Test
	public void testHistScalarValuesClassVisibility() throws IOException, DocumentException {
		HistScalar scalar = HistScalar.MAG;
		ExampleSubclass report = new ExampleSubclass(cmd);
		HistScalarValues inputScalars = new HistScalarValues(scalar, 
				report.getInputRupSet(), report.getInputSol(), report.getInputRups(), report.getDistAzCalc());
		assertEquals(3101, inputScalars.getValues().size());
	}	
		
	@Test
	public void test_RupSetDiagnosticsPageGen_methods() throws ParseException, IOException, DocumentException {
		ExampleSubclass report = new ExampleSubclass(cmd);

		//these are the methods we need RupSetDiagnosticsPageGen to make available
		assertEquals(report.getInputName(), inputName);
		assertEquals(report.getOutputDir(), tempFolder);
		assertTrue(report.getInputSol() instanceof InversionFaultSystemSolution);
		assertEquals(3101, report.getInputRupSet().getClusterRuptures().size());
		assertEquals(3101, report.getInputRups().size());
		assertEquals(3101,report.getInputUniques().size());
		assertTrue(report.getDistAzCalc() instanceof SectionDistanceAzimuthCalculator);
		assertTrue(ExampleSubclass.getMainColor() instanceof Color);

	}	

	/**
	 * Build a diagnostics report from an InversionSolution.
	 * 
	 * This is actually a bit slow, perhaps could be built using a smaller inversion fixture.
	 * 
	 * @throws IOException
	 * @throws DocumentException
	 * @throws ParseException 
	 */
	@Test 
	public void testRunReportForInversionSolution() throws IOException, DocumentException, ParseException {
		new RupSetDiagnosticsPageGen(cmd).generatePage();
		File index_html = new File(tempFolder, "index.html");	
		assertTrue(index_html.exists());
	}
	
}
