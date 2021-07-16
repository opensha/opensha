package scratch.UCERF3.utils;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.dom4j.DocumentException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;

public class LoadInversionSolutionIntegrationTest {

	private static URL alpineVernonRupturesUrl;

	@SuppressWarnings("deprecation")
	@BeforeClass
	public static void setUp() throws IOException, DocumentException, URISyntaxException {
		/*
		 * TODO: we should be using java resources here, but some global gradle/eclipse configuration needs agreement
		 *
		 * alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("testAlpineVernonInversion.zip");
		*/ 
		alpineVernonRupturesUrl = new File("test/resources/scratch/UCERF3/utils/testAlpineVernonInversion.zip").toURL();
	}	
	
	/**
	 * FaultSystemIO.loadSol will failing unless mags are set first in super.init()
	 * 
	 * @throws DocumentException
	 * @throws IOException
	 * @throws URISyntaxException 
	 */
	@Test
	public void testLoadInversionSolutionHasClusterRuptures() throws IOException, DocumentException, URISyntaxException {
		FaultSystemSolution loadedSolution =
				U3FaultSystemIO.loadSol(new File(alpineVernonRupturesUrl.toURI()));
		assertEquals(3101, loadedSolution.getRupSet().getModule(ClusterRuptures.class).getAll().size());
	}

}
