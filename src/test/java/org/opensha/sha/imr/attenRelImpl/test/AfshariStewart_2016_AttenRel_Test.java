package org.opensha.sha.imr.attenRelImpl.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.imr.attenRelImpl.AfshariStewart_2016_AttenRel;
import org.opensha.sha.imr.attenRelImpl.AfshariStewart_2016_AttenRel.BasinDepthModel;
import org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DurationTimeInterval;
import org.opensha.sha.imr.param.IntensityMeasureParams.SignificantDurationParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

public class AfshariStewart_2016_AttenRel_Test {
	
	private static final double delta = 1e-6;
	
	private static class AS_TestCase {
		final double mag;
		final double rRup;
		final FaultStyle faultStyle;
		final double vs30;
		final Double z10;
		final BasinDepthModel basinModel;
		
		final String inputsString;
		
		final Result refResults;
		Result calcResults;
		
		public AS_TestCase(double mag, double rRup, FaultStyle faultStyle, double vs30, Double z10,
				BasinDepthModel basinModel, Result refResults) {
			super();
			this.mag = mag;
			this.rRup = rRup;
			this.faultStyle = faultStyle;
			this.vs30 = vs30;
			this.z10 = z10;
			this.basinModel = basinModel;
			
			inputsString = "M="+(float)mag+", R="+(float)rRup+", Style="+faultStyle.name()+", Vs30="+(float)vs30
					+", Z1.0="+(z10 == null ? "null" : z10.floatValue())+", BasinModel="+basinModel;
			
			this.refResults = refResults;
		}
		
		@SuppressWarnings("unchecked")
		public synchronized Result calcResult(AfshariStewart_2016_AttenRel gmpe) {
			if (calcResults == null) {
				synchronized (gmpe) {
					gmpe.getParameter(MagParam.NAME).setValue(mag);
					gmpe.getParameter(DistanceRupParameter.NAME).setValue(rRup);
					gmpe.getParameter(AfshariStewart_2016_AttenRel.FAULT_STYLE_PARAM_NAME).setValue(faultStyle);
					gmpe.getParameter(Vs30_Param.NAME).setValue(vs30);
					gmpe.getParameter(DepthTo1pt0kmPerSecParam.NAME).setValue(z10);
					gmpe.getParameter(AfshariStewart_2016_AttenRel.BASIN_DEPTH_MODEL_NAME).setValue(basinModel);
					
					Parameter<?> imt = gmpe.getIntensityMeasure();
					
					SignificantDurationParam.setTimeInterval(imt, DurationTimeInterval.INTERVAL_5_75);
					
					double dur575 = Math.exp(gmpe.getMean());
					
					gmpe.getParameter(StdDevTypeParam.NAME).setValue(StdDevTypeParam.STD_DEV_TYPE_INTER);
					double tau575 = gmpe.getStdDev();
					
					gmpe.getParameter(StdDevTypeParam.NAME).setValue(StdDevTypeParam.STD_DEV_TYPE_INTRA);
					double phi575 = gmpe.getStdDev();
					
					SignificantDurationParam.setTimeInterval(imt, DurationTimeInterval.INTERVAL_5_95);
					
					double dur595 = Math.exp(gmpe.getMean());
					
					gmpe.getParameter(StdDevTypeParam.NAME).setValue(StdDevTypeParam.STD_DEV_TYPE_INTER);
					double tau595 = gmpe.getStdDev();
					
					gmpe.getParameter(StdDevTypeParam.NAME).setValue(StdDevTypeParam.STD_DEV_TYPE_INTRA);
					double phi595 = gmpe.getStdDev();
					
					SignificantDurationParam.setTimeInterval(imt, DurationTimeInterval.INTERVAL_20_80);
					
					double dur2080 = Math.exp(gmpe.getMean());
					
					gmpe.getParameter(StdDevTypeParam.NAME).setValue(StdDevTypeParam.STD_DEV_TYPE_INTER);
					double tau2080 = gmpe.getStdDev();
					
					gmpe.getParameter(StdDevTypeParam.NAME).setValue(StdDevTypeParam.STD_DEV_TYPE_INTRA);
					double phi2080 = gmpe.getStdDev();
					
					calcResults = new Result(dur575, tau575, phi575, dur595, tau595, phi595, dur2080, tau2080, phi2080);
				}
			}
			
			return calcResults;
		}
	}
	
	private static class Result {
		final double dur575;
		final double tau575;
		final double phi575;
		final double dur595;
		final double tau595;
		final double phi595;
		final double dur2080;
		final double tau2080;
		final double phi2080;
		public Result(double dur575, double tau575, double phi575, double dur595, double tau595, double phi595,
				double dur2080, double tau2080, double phi2080) {
			super();
			this.dur575 = dur575;
			this.tau575 = tau575;
			this.phi575 = phi575;
			this.dur595 = dur595;
			this.tau595 = tau595;
			this.phi595 = phi595;
			this.dur2080 = dur2080;
			this.tau2080 = tau2080;
			this.phi2080 = phi2080;
		}
	}
	
	private static final String URL = "AttenRelResultSetFiles/AS_2016.csv";
	
	private static List<AS_TestCase> inputs;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		System.out.println("Reading CSV...");
		CSVFile<String> csv = CSVFile.readStream(
				AfshariStewart_2016_AttenRel_Test.class.getResourceAsStream(URL), true);
		System.out.println("Loaded "+csv.getNumRows()+" rows");
		
		inputs = new ArrayList<>();
		for (int row=1; row<csv.getNumRows(); row++) {
			// inputs
			int col = 0;
			double mag = csv.getDouble(row, col++);
			double rRup = csv.getDouble(row, col++);
			int fsInt = csv.getInt(row, col++);
			FaultStyle faultStyle;
			switch (fsInt) {
			case 0:
				faultStyle = FaultStyle.UNKNOWN;
				break;
			case 1:
				faultStyle = FaultStyle.NORMAL;
				break;
			case 2:
				faultStyle = FaultStyle.REVERSE;
				break;
			case 3:
				faultStyle = FaultStyle.STRIKE_SLIP;
				break;

			default:
				throw new IllegalStateException("Bad fault style: "+fsInt);
			}
			double vs30 = csv.getDouble(row, col++);
			Double z10 = csv.getDouble(row, col++);
			if (z10 < 0)
				z10 = null;
			int basinInt = csv.getInt(row, col++);
			BasinDepthModel basinModel;
			switch (basinInt) {
			case 0:
				basinModel = BasinDepthModel.CALIFORNIA;
				break;
			case 1:
				basinModel = BasinDepthModel.JAPAN;
				break;

			default:
				basinModel = null;
				break;
			}
			
			// outputs
			double dur575 = csv.getDouble(row, col++);
			double tau575 = csv.getDouble(row, col++);
			double phi575 = csv.getDouble(row, col++);
			double dur595 = csv.getDouble(row, col++);
			double tau595 = csv.getDouble(row, col++);
			double phi595 = csv.getDouble(row, col++);
			double dur2080 = csv.getDouble(row, col++);
			double tau2080 = csv.getDouble(row, col++);
			double phi2080 = csv.getDouble(row, col++);
			
			inputs.add(new AS_TestCase(mag, rRup, faultStyle, vs30, z10, basinModel,
					new Result(dur575, tau575, phi575, dur595, tau595, phi595, dur2080, tau2080, phi2080)));
			// you can use this to only test subsets
//			inputs = inputs.stream().filter(c -> c.basinModel == BasinDepthModel.CALIFORNIA).collect(Collectors.toList());
//			inputs = inputs.stream().filter(c -> c.z10 != null && c.z10 == 0d).collect(Collectors.toList());
//			inputs = inputs.stream().filter(c -> c.z10 == null).collect(Collectors.toList());
		}
		System.out.println("Loaded "+inputs.size()+" test cases");
	}
	
	private AfshariStewart_2016_AttenRel gmpe;

	@Before
	public void setUp() throws Exception {
		gmpe = new AfshariStewart_2016_AttenRel();
		gmpe.setParamDefaults();
	}

	@Test
	public void testStrikeSlipMean575() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.STRIKE_SLIP).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_5_75));
	}

	@Test
	public void testStrikeSlipMean595() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.STRIKE_SLIP).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_5_95));
	}

	@Test
	public void testStrikeSlipMean2080() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.STRIKE_SLIP).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_20_80));
	}

	@Test
	public void testNormalMean575() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.NORMAL).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_5_75));
	}

	@Test
	public void testNormalMean595() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.NORMAL).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_5_95));
	}

	@Test
	public void testNormalMean2080() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.NORMAL).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_20_80));
	}

	@Test
	public void testReverseMean575() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.REVERSE).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_5_75));
	}

	@Test
	public void testReverseMean595() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.REVERSE).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_5_95));
	}

	@Test
	public void testReverseMean2080() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.REVERSE).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_20_80));
	}

	@Test
	public void testUnknownMean575() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.UNKNOWN).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_5_75));
	}

	@Test
	public void testUnknownMean595() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.UNKNOWN).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_5_95));
	}

	@Test
	public void testUnknownMean2080() {
		inputs.stream().filter(c -> c.faultStyle == FaultStyle.UNKNOWN).forEach(
				new MeanTestConsumer(DurationTimeInterval.INTERVAL_20_80));
	}

	@Test
	public void testNullBasin() {
		inputs.stream().filter(c -> c.z10 == null).forEach(
				new MeanTestConsumer(DurationTimeInterval.values()));
	}

	@Test
	public void testNonNullBasin() {
		inputs.stream().filter(c -> c.z10 != null).forEach(
				new MeanTestConsumer(DurationTimeInterval.values()));
	}

	@Test
	public void testCABasinModel() {
		inputs.stream().filter(c -> c.basinModel == BasinDepthModel.CALIFORNIA).forEach(
				new MeanTestConsumer(DurationTimeInterval.values()));
	}

	@Test
	public void testJapanBasinModel() {
		inputs.stream().filter(c -> c.basinModel == BasinDepthModel.JAPAN).forEach(
				new MeanTestConsumer(DurationTimeInterval.values()));
	}

	@Test
	public void testNullBasinModel() {
		inputs.stream().filter(c -> c.basinModel == null).forEach(
				new MeanTestConsumer(DurationTimeInterval.values()));
	}

	@Test
	public void testStdDev575() {
		inputs.stream().forEach(new StdDevTestConsumer(DurationTimeInterval.INTERVAL_5_75));
	}

	@Test
	public void testStdDev595() {
		inputs.stream().forEach(new StdDevTestConsumer(DurationTimeInterval.INTERVAL_5_95));
	}

	@Test
	public void testStdDev2080() {
		inputs.stream().forEach(new StdDevTestConsumer(DurationTimeInterval.INTERVAL_20_80));
	}
	
	private class MeanTestConsumer implements Consumer<AS_TestCase> {
		
		private DurationTimeInterval[] intervals;

		public MeanTestConsumer(DurationTimeInterval... intervals) {
			this.intervals = intervals;
		}

		@SuppressWarnings("unchecked")
		@Override
		public void accept(AS_TestCase t) {
			for (DurationTimeInterval interval : intervals) {
				Result refResult = t.refResults;
				Result calcResult = t.calcResult(gmpe);
				double refMean, calcMean;
				switch (interval) {
				case INTERVAL_5_75:
					refMean = refResult.dur575;
					calcMean = calcResult.dur575;
					break;
				case INTERVAL_5_95:
					refMean = refResult.dur595;
					calcMean = calcResult.dur595;
					break;
				case INTERVAL_20_80:
					refMean = refResult.dur2080;
					calcMean = calcResult.dur2080;
					break;

				default:
					throw new IllegalStateException("Bad duration interval: "+interval);
				}
				assertEquals("mean "+interval+" failed for\n"+t.inputsString+"\n", refMean, calcMean, delta);
			}
		}
		
	}
	
	private class StdDevTestConsumer implements Consumer<AS_TestCase> {
		
		private DurationTimeInterval[] intervals;

		public StdDevTestConsumer(DurationTimeInterval... intervals) {
			this.intervals = intervals;
		}

		@SuppressWarnings("unchecked")
		@Override
		public void accept(AS_TestCase t) {
			for (DurationTimeInterval interval : intervals) {
				Result refResult = t.refResults;
				Result calcResult = t.calcResult(gmpe);
				double refTau, calcTau, refPhi, calcPhi;
				switch (interval) {
				case INTERVAL_5_75:
					refTau = refResult.tau575;
					refPhi = refResult.phi575;
					calcTau = calcResult.tau575;
					calcPhi = calcResult.phi575;
					break;
				case INTERVAL_5_95:
					refTau = refResult.tau595;
					refPhi = refResult.phi595;
					calcTau = calcResult.tau595;
					calcPhi = calcResult.phi595;
					break;
				case INTERVAL_20_80:
					refTau = refResult.tau2080;
					refPhi = refResult.phi2080;
					calcTau = calcResult.tau2080;
					calcPhi = calcResult.phi2080;
					break;

				default:
					throw new IllegalStateException("Bad duration interval: "+interval);
				}
				assertEquals("tau "+interval+" failed for\n"+t.inputsString+"\n", refTau, calcTau, delta);
				assertEquals("phi "+interval+" failed for\n"+t.inputsString+"\n", refPhi, calcPhi, delta);
			}
		}
		
	}

}
