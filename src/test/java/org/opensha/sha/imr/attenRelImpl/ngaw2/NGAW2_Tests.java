package org.opensha.sha.imr.attenRelImpl.ngaw2;

import static com.google.common.base.Charsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.NORMAL;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.REVERSE;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.STRIKE_SLIP;
import static org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle.UNKNOWN;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.io.Resources;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class NGAW2_Tests {

	private static final Splitter COMMA_SPLIT = Splitter.on(',').omitEmptyStrings().trimResults();
	private static final Splitter DASH_SPLIT = Splitter.on('-').omitEmptyStrings().trimResults();

	private static String D_DIR = "data/";
	static String GMM_INPUTS = "NGAW2_inputs.csv";
	private static String GMM_RESULTS = "NGAW2_results.csv";
	private static double TOL = 0.000001; // results precision = 1e-6
	private static List<GMM_Input> inputsList;
		
	static {
		try {
			inputsList = loadInputs(GMM_INPUTS);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}
	}
	
    @Parameters(name = "{index}: {0} {2} {1}")
    public static Collection<Object[]> data() throws IOException {
    	return loadResults(GMM_RESULTS);
    }

    private int idx;
    private String gmmId;
    private IMT imt;
    private double exMedian;
    private double exSigma;
	
	public NGAW2_Tests(int idx, String gmmId, IMT imt, double exMedian,
		double exSigma) {

		this.idx = idx;
		this.gmmId = gmmId;
		this.imt = imt;
		this.exMedian = exMedian;
		this.exSigma = exSigma;
	}

    @Test
    public void test() {
    	ScalarGroundMotion sgm = computeGM(gmmId, imt, idx);
        assertEquals(exMedian, Math.exp(sgm.mean()), TOL);
        assertEquals(exSigma, sgm.stdDev(), TOL);
    }
    
    private static ScalarGroundMotion computeGM(String gmmId, IMT imt, int idx) {
    	NGAW2_GMM gmm = getGMM(gmmId);
    	initGMM(gmm, imt, idx);
    	return gmm.calc();
    }
    
    private static void initGMM(NGAW2_GMM gmm, IMT imt, int idx) {
    	GMM_Input gmmIn = inputsList.get(idx);
    	gmm.set_IMT(imt);
    	gmm.set_Mw(gmmIn.Mw);
    	gmm.set_rJB(gmmIn.rJB);
    	gmm.set_rRup(gmmIn.rRup);
    	gmm.set_rX(gmmIn.rX);
    	gmm.set_dip(gmmIn.dip);
    	gmm.set_width(gmmIn.width);
    	gmm.set_zTop(gmmIn.zTop);
    	gmm.set_zHyp(gmmIn.zHyp);
    	gmm.set_vs30(gmmIn.vs30);
    	gmm.set_vsInf(gmmIn.vsInf);
    	gmm.set_z2p5(gmmIn.z2p5);
    	gmm.set_z1p0(gmmIn.z1p0);
    	gmm.set_fault(getFaultStyleForRake(gmmIn.rake));
    }

    private static FaultStyle getFaultStyleForRake(double rake) {
    	if (Double.isNaN(rake)) return UNKNOWN;
		return (rake >= 45 && rake <= 135) ? REVERSE :
			   (rake >= -135 && rake <= -45) ? NORMAL : STRIKE_SLIP;
	}

    
    private static NGAW2_GMM getGMM(String gmmId) {
    	NGAW2_GMM gmm = null;
    	if (gmmId.equals("ASK_14")) {
    		gmm = new ASK_2014();
    	} else if (gmmId.equals("BSSA_14")) {
    		gmm = new BSSA_2014();
    	} else if (gmmId.equals("CB_14")) {
    		gmm = new CB_2014();
    	} else if (gmmId.equals("CY_14")) {
    		gmm = new CY_2014();
    	} else if (gmmId.equals("IDRISS_14")) {
    		gmm = new Idriss_2014();
    	} else {
    		throw new IllegalStateException("Bad gmm identifier: " + gmmId);
    	}
    	return gmm;
    }

	
    public static void main(String[] args) {
    	System.out.println(COMMA_SPLIT);
		for (GMM_Input gmmIn : inputsList) {
			System.out.println(gmmIn);
		}
	}
	// @formatter:off
	
	private static List<Object[]> loadResults(String resource) throws IOException {
		URL url = Resources.getResource(NGAW2_Tests.class, D_DIR + resource);
		return FluentIterable
				.from(Resources.readLines(url, US_ASCII))
				.transform(ResultsToObjectsFunction.INSTANCE)
				.toList();
	}
	
	private enum ResultsToObjectsFunction implements Function<String, Object[]> {
		INSTANCE;
		@Override
		public Object[] apply(String line) {
			Iterator<String> lineIt = COMMA_SPLIT.split(line).iterator();
			Iterator<String> idIt = DASH_SPLIT.split(lineIt.next()).iterator();
			return new Object[] {
				Integer.valueOf(idIt.next()),	// inputs index
				idIt.next(),					// GMM
				IMT.valueOf(idIt.next()),		// IMT
				Double.valueOf(lineIt.next()),	// median
				Double.valueOf(lineIt.next())	// sigma
			};
		}
	}
	
	static List<GMM_Input> loadInputs(String resource) throws IOException {
		URL url = Resources.getResource(NGAW2_Tests.class, D_DIR + resource);
		return FluentIterable
				.from(Resources.readLines(url, US_ASCII))
				.skip(1)
				.transform(ArgsToInputFunction.INSTANCE)
				.toList();
	}
	
	private static enum ArgsToInputFunction implements Function<String, GMM_Input> {
		INSTANCE;
		@Override
		public GMM_Input apply(String line) {

			Iterator<Double> it = FluentIterable
				.from(COMMA_SPLIT.split(line))
				.transform(DoubleValueOfFunction.INSTANCE)
				.iterator();
			
			GMM_Input gmmIn = new GMM_Input();
			gmmIn.Mw = it.next();
			gmmIn.rJB = it.next();
			gmmIn.rRup = it.next();
			gmmIn.rX = it.next();
			gmmIn.dip = it.next();
			gmmIn.width = it.next();
			gmmIn.zTop = it.next();
			gmmIn.zHyp = it.next();
			gmmIn.rake = it.next();
			gmmIn.vs30 = it.next();
			gmmIn.vsInf = it.next() > 0.0;
			gmmIn.z2p5 = it.next();
			gmmIn.z1p0 = it.next();

			return gmmIn;
		}
	}
	
	// @formatter:off
	private static enum DoubleValueOfFunction implements Function<String, Double> {
		INSTANCE;
		@Override public Double apply(String s) {
			return Double.valueOf(s.trim());
		}
	}	
	
	private static class GMM_Input {
		double Mw;
		double rJB;
		double rRup;
		double rX;
		double dip;
		double width;
		double zTop;
		double zHyp;
		double rake;
		double vs30;
		boolean vsInf;
		double z2p5;
		double z1p0;
	}
	
}
