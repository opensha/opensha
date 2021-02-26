/**
 * 
 */
package scratch.UCERF3.enumTreeBranches;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.Ellsworth_B_WG02_MagAreaRel;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.HanksBakun2002_MagAreaRel;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.Shaw_2009_ModifiedMagAreaRel;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.GraphWindow;

import com.google.common.collect.Lists;

import scratch.UCERF3.logicTree.LogicTreeBranchNode;

/**
 * @author field
 *
 */
public enum ScalingRelationships implements LogicTreeBranchNode<ScalingRelationships> {
		
	
	AVE_UCERF2("Average UCERF2", "AveU2") {
		
		public double getAveSlip(double area, double length, double origWidth) {
			double areaKm = area/1e6;
			double mag = (ellB_magArea.getMedianMag(areaKm) + hb_magArea.getMedianMag(areaKm))/2;
			double moment = MagUtils.magToMoment(mag);
			return FaultMomentCalc.getSlip(area, moment);
		}
		
		public double getMag(double area, double origWidth) {
			double areaKm = area/1e6;
			return (ellB_magArea.getMedianMag(areaKm) + hb_magArea.getMedianMag(areaKm))/2;
		}		
		
		public double getArea(double mag, double origWidth) {
			return Double.NaN;
		}
		
		@Override
		public double getRelativeWeight(InversionModels im) {
			return 0d;
		}
		
	},
	
	
	SHAW_2009_MOD("Shaw (2009) Modified", "Shaw09Mod") {
		
		public double getAveSlip(double area, double length, double origWidth) {
			double mag = getMag(area, origWidth);
			double moment = MagUtils.magToMoment(mag);
			return FaultMomentCalc.getSlip(area, moment);
		}
		
		public double getMag(double area, double origWidth) {
			return sh09_ModMagArea.getWidthDepMedianMag(area*1e-6, origWidth*1e-3);
		}		
		
		public double getArea(double mag, double origWidth) {
			return sh09_ModMagArea.getMedianArea(mag)*1e6;
		}

		
		@Override
		public double getRelativeWeight(InversionModels im) {
			return 0.2d;
		}
	},

	HANKS_BAKUN_08("Hanks & Bakun (2008)", "HB08") {
		
		public double getAveSlip(double area, double length, double origWidth) {
			double mag = hb_magArea.getMedianMag(area/1e6);
			double moment = MagUtils.magToMoment(mag);
			return FaultMomentCalc.getSlip(area, moment);
		}
		
		public double getMag(double area, double origWidth) {
			return hb_magArea.getMedianMag(area*1e-6);
		}		
		
		public double getArea(double mag, double origWidth) {
			return hb_magArea.getMedianArea(mag)*1e6;
		}

		
		@Override
		public double getRelativeWeight(InversionModels im) {
			return 0.2d;
		}
	},
	
	

	ELLSWORTH_B("Ellsworth B", "EllB") {
		
		public double getAveSlip(double area, double length, double origWidth) {
			double mag = ellB_magArea.getMedianMag(area/1e6);
			double moment = MagUtils.magToMoment(mag);
			return FaultMomentCalc.getSlip(area, moment);
		}
		
		public double getMag(double area, double origWidth) {
			return ellB_magArea.getMedianMag(area*1e-6);
		}		
		
		public double getArea(double mag, double origWidth) {
			return ellB_magArea.getMedianArea(mag)*1e6;
		}

		
		@Override
		public double getRelativeWeight(InversionModels im) {
			return 0.2d;
		}
	},
	
	
	ELLB_SQRT_LENGTH("EllB M(A) & Shaw12 Sqrt Length D(L)", "EllBsqrtLen") {
		
		public double getAveSlip(double area, double length, double origWidth) {
			double impliedAseis = 1.0 - (area/length)/origWidth;
//System.out.println("impliedAseis="+impliedAseis);
			if(impliedAseis>=0.2) {
				double moment = MagUtils.magToMoment(getMag(area, origWidth));
				return FaultMomentCalc.getSlip(area, moment);
			}
			double c6 = 5.69e-5;
			double xi = 1.25;
			double w = 15e3;  // units of m
//			double w = xi*area/length;  // units of m
			return c6*Math.sqrt(length*w);
		}
		
		public double getMag(double area, double origWidth) {
			return ellB_magArea.getMedianMag(area*1e-6);
		}		
		
		public double getArea(double mag, double origWidth) {
			return ellB_magArea.getMedianArea(mag)*1e6;
		}

		
		@Override
		public double getRelativeWeight(InversionModels im) {
			return 0.2d;
		}
	},

	SHAW_CONST_STRESS_DROP("Shaw09 M(A) & Shaw12 Const Stress Drop D(L)", "ShConStrDrp") {
		
		public double getAveSlip(double area, double length, double origWidth) {
			double impliedAseis = 1.0 - (area/length)/origWidth;
			if(impliedAseis>=0.2) {
				double moment = MagUtils.magToMoment(getMag(area, origWidth));
				return FaultMomentCalc.getSlip(area, moment);
			}
			double stressDrop = 4.54;  // MPa
			double xi = 1.25;
			double w = 15e3; // unit of meters
//			double w = xi*area/length; // unit of meters
			double temp = 1.0/(7.0/(3.0*length) + 1.0/(2.0*w))*1e6;
			return stressDrop*temp/FaultMomentCalc.SHEAR_MODULUS;
		}
		
		public double getMag(double area, double origWidth) {
			return sh09_ModMagArea.getWidthDepMedianMag(area*1e-6, origWidth*1e-3);
		}		
		
		public double getArea(double mag, double origWidth) {
			return sh09_ModMagArea.getMedianArea(mag)*1e6;
		}

		
		@Override
		public double getRelativeWeight(InversionModels im) {
			return 0.2d;
		}
	},
	
	MEAN_UCERF3("Mean UCERF3 Scaling Relationship", "MeanU3Scale") {
		
		private List<Double> meanWeights;
		private List<ScalingRelationships> scales;
		
		private List<Double> getNormalizedMeanWeights() {
			if (meanWeights == null) {
				synchronized (this) {
					List<Double> weights = Lists.newArrayList();
					List<ScalingRelationships> scales = Lists.newArrayList();
					double sum = 0;
					for (ScalingRelationships s : ScalingRelationships.values()) {
						double weight = s.getRelativeWeight(null);
						if (weight > 0) {
							weights.add(weight);
							sum += weight;
							scales.add(s);
						}
					}
					if (sum != 0)
						for (int i=0; i<weights.size(); i++)
							weights.set(i, weights.get(i)/sum);
					meanWeights = weights;
					this.scales = scales;
				}
			}
			return meanWeights;
		}
		
		@Override
		public double getRelativeWeight(InversionModels im) {
			return 0;
		}

		@Override
		public double getAveSlip(double area, double length, double origWidth) {
			List<Double> weights = getNormalizedMeanWeights();
			double slip = 0;
			for (int i=0; i<weights.size(); i++) {
				slip += weights.get(i)*scales.get(i).getAveSlip(area, length, origWidth);
			}
			return slip;
		}

		@Override
		public double getMag(double area, double origWidth) {
			List<Double> weights = getNormalizedMeanWeights();
			double map = 0;
			for (int i=0; i<weights.size(); i++) {
				map += weights.get(i)*scales.get(i).getMag(area, origWidth);
			}
			return map;
		}
		
		public double getArea(double mag, double origWidth) {
			List<Double> weights = getNormalizedMeanWeights();
			double map = 0;
			for (int i=0; i<weights.size(); i++) {
				map += weights.get(i)*scales.get(i).getArea(mag, origWidth);
			}
			return map;
		}

	};
	
	private static Ellsworth_B_WG02_MagAreaRel ellB_magArea = new Ellsworth_B_WG02_MagAreaRel();
	private static HanksBakun2002_MagAreaRel hb_magArea = new HanksBakun2002_MagAreaRel();
	private static Shaw_2009_ModifiedMagAreaRel sh09_ModMagArea = new Shaw_2009_ModifiedMagAreaRel();

	private String name, shortName;
	
	private ScalingRelationships(String name, String shortName) {
		this.name = name;
		this.shortName = shortName;
	}
	
	
	
	/**
	 * This returns the slip (m) for the given rupture area (m-sq) or rupture length (m)
	 * @param area (m)
	 * @param length (m)
	  * @param origWidth (m) - the original down-dip width (before reducing by aseismicity factor)
	 * @return
	 */
	 public abstract double getAveSlip(double area, double length, double origWidth);
	 
	 /**
	  * This returns the magnitude for the given rupture area (m-sq) and width (m)
	  * @param area (m)
	  * @param origWidth (m) - the original down-dip width (before reducing by aseismicity factor)
	  * @return
	  */
	 public abstract double getMag(double area, double origWidth);
	 
	 public abstract double getArea(double mag, double origWidth);


	
	@Override
	public String encodeChoiceString() {
		return getShortName();
	}

	
	public String getName() {
		return name;
	}
	
	public String getShortName() {
		return shortName;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	@Override
	public String getBranchLevelName() {
		return "Scaling Relationship";
	}
	
	public static void makeSlipLengthPlot(double downDipWidth, int maxLength, boolean saveFiles) {
		
		ArbitrarilyDiscretizedFunc u2_func = new ArbitrarilyDiscretizedFunc();
		u2_func.setName("AVE_UCERF2");
		ArbitrarilyDiscretizedFunc sh09_funcMod = new ArbitrarilyDiscretizedFunc();
		sh09_funcMod.setName("SHAW_2009_MOD");
		ArbitrarilyDiscretizedFunc ellB_func = new ArbitrarilyDiscretizedFunc();
		ellB_func.setName("ELLSWORTH_B");
		ArbitrarilyDiscretizedFunc hb_func = new ArbitrarilyDiscretizedFunc();
		hb_func.setName("HANKS_BAKUN_08");
		ArbitrarilyDiscretizedFunc sh12_sqrtL_func = new ArbitrarilyDiscretizedFunc();
		sh12_sqrtL_func.setName("ELLB_SQRT_LENGTH");
		ArbitrarilyDiscretizedFunc sh12_csd_func = new ArbitrarilyDiscretizedFunc();
		sh12_csd_func.setName("SHAW_CONST_STRESS_DROP");
		
		
		ScalingRelationships u2 = ScalingRelationships.AVE_UCERF2;
		ScalingRelationships sh09_Mod = ScalingRelationships.SHAW_2009_MOD;
		ScalingRelationships ellB = ScalingRelationships.ELLSWORTH_B;
		ScalingRelationships hb = ScalingRelationships.HANKS_BAKUN_08;
		ScalingRelationships sh12_sqrtL = ScalingRelationships.ELLB_SQRT_LENGTH;
		ScalingRelationships sh12_csd = ScalingRelationships.SHAW_CONST_STRESS_DROP;
		
		
		// log10 area from 1 to 5
    	for(int i=1; i<=maxLength; i++) {
    		double lengthKm = (double)i;
    		double length = lengthKm*1e3;
    		double area = length*downDipWidth*1e3;
    		u2_func.set(lengthKm,u2.getAveSlip(area, length, downDipWidth*1e3));
    		sh09_funcMod.set(lengthKm,sh09_Mod.getAveSlip(area, length, downDipWidth*1e3));
    		ellB_func.set(lengthKm,ellB.getAveSlip(area, length, downDipWidth*1e3));
    		hb_func.set(lengthKm,hb.getAveSlip(area, length, downDipWidth*1e3));
    		sh12_sqrtL_func.set(lengthKm,sh12_sqrtL.getAveSlip(area, length, downDipWidth*1e3));
    		sh12_csd_func.set(lengthKm,sh12_csd.getAveSlip(area, length, downDipWidth*1e3));
    	}
    	
    	ArrayList<ArbitrarilyDiscretizedFunc> funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
    	funcs.add(sh09_funcMod);
    	funcs.add(ellB_func);
    	funcs.add(hb_func);
    	funcs.add(sh12_sqrtL_func);
    	funcs.add(sh12_csd_func);
    	
    	ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.GREEN));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.MAGENTA));

    	
		GraphWindow graph = new GraphWindow(funcs, "Slip-Length Relationships; DDW="+downDipWidth+" km", plotChars); 
		graph.setX_AxisLabel("Length (km)");
		graph.setY_AxisLabel("Slip (m)");
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(18);
		graph.setTickLabelFontSize(16);
		
		if(saveFiles) {
			try {
				graph.saveAsPDF("slipLengthScalingPlot.pdf");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	
	public static void makeSlipAreaPlot(double downDipWidth, int maxLength, boolean saveFiles) {
		
		ArbitrarilyDiscretizedFunc u2_func = new ArbitrarilyDiscretizedFunc();
		u2_func.setName("AVE_UCERF2");
		ArbitrarilyDiscretizedFunc sh09_funcMod = new ArbitrarilyDiscretizedFunc();
		sh09_funcMod.setName("SHAW_2009_MOD");
		ArbitrarilyDiscretizedFunc ellB_func = new ArbitrarilyDiscretizedFunc();
		ellB_func.setName("ELLSWORTH_B");
		ArbitrarilyDiscretizedFunc hb_func = new ArbitrarilyDiscretizedFunc();
		hb_func.setName("HANKS_BAKUN_08");
		ArbitrarilyDiscretizedFunc sh12_sqrtL_func = new ArbitrarilyDiscretizedFunc();
		sh12_sqrtL_func.setName("ELLB_SQRT_LENGTH");
		ArbitrarilyDiscretizedFunc sh12_csd_func = new ArbitrarilyDiscretizedFunc();
		sh12_csd_func.setName("SHAW_CONST_STRESS_DROP");
		
		
		ScalingRelationships u2 = ScalingRelationships.AVE_UCERF2;
		ScalingRelationships sh09_Mod = ScalingRelationships.SHAW_2009_MOD;
		ScalingRelationships ellB = ScalingRelationships.ELLSWORTH_B;
		ScalingRelationships hb = ScalingRelationships.HANKS_BAKUN_08;
		ScalingRelationships sh12_sqrtL = ScalingRelationships.ELLB_SQRT_LENGTH;
		ScalingRelationships sh12_csd = ScalingRelationships.SHAW_CONST_STRESS_DROP;
		
		
		// log10 area from 1 to 5
    	for(int i=1; i<=maxLength; i++) {
    		double lengthKm = (double)i;
    		double length = lengthKm*1e3;
    		double area = length*downDipWidth*1e3;
    		double areaKm = area*1e-6;
    		u2_func.set(areaKm,u2.getAveSlip(area, length, downDipWidth*1e3));
    		sh09_funcMod.set(areaKm,sh09_Mod.getAveSlip(area, length, downDipWidth*1e3));
    		ellB_func.set(areaKm,ellB.getAveSlip(area, length, downDipWidth*1e3));
    		hb_func.set(areaKm,hb.getAveSlip(area, length, downDipWidth*1e3));
    		sh12_sqrtL_func.set(areaKm,sh12_sqrtL.getAveSlip(area, length, downDipWidth*1e3));
    		sh12_csd_func.set(areaKm,sh12_csd.getAveSlip(area, length, downDipWidth*1e3));
    	}
    	
    	ArrayList<ArbitrarilyDiscretizedFunc> funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
    	funcs.add(sh09_funcMod);
    	funcs.add(ellB_func);
    	funcs.add(hb_func);
    	funcs.add(sh12_sqrtL_func);
    	funcs.add(sh12_csd_func);
    	
    	ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.GREEN));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.MAGENTA));

    	
		GraphWindow graph = new GraphWindow(funcs, "Slip-Area Relationships; DDW="+downDipWidth+" km", plotChars); 
		graph.setX_AxisLabel("Area (km-sq)");
		graph.setY_AxisLabel("Slip (m)");
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(18);
		graph.setTickLabelFontSize(16);
		
		if(saveFiles) {
			try {
				graph.saveAsPDF("slipAreaScalingPlot.pdf");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	
	public static void makeSlipMagPlot(double downDipWidth, int maxLength, boolean saveFiles) {
		
		ArbitrarilyDiscretizedFunc heckerEq9b = new ArbitrarilyDiscretizedFunc();
		heckerEq9b.setName("Hecker Eq 9b");
		ArbitrarilyDiscretizedFunc sh09_funcMod = new ArbitrarilyDiscretizedFunc();
		sh09_funcMod.setName("SHAW_2009_MOD");
		ArbitrarilyDiscretizedFunc ellB_func = new ArbitrarilyDiscretizedFunc();
		ellB_func.setName("ELLSWORTH_B");
		ArbitrarilyDiscretizedFunc hb_func = new ArbitrarilyDiscretizedFunc();
		hb_func.setName("HANKS_BAKUN_08");
		ArbitrarilyDiscretizedFunc sh12_sqrtL_func = new ArbitrarilyDiscretizedFunc();
		sh12_sqrtL_func.setName("ELLB_SQRT_LENGTH");
		ArbitrarilyDiscretizedFunc sh12_csd_func = new ArbitrarilyDiscretizedFunc();
		sh12_csd_func.setName("SHAW_CONST_STRESS_DROP");
		
		
		ScalingRelationships sh09_Mod = ScalingRelationships.SHAW_2009_MOD;
		ScalingRelationships ellB = ScalingRelationships.ELLSWORTH_B;
		ScalingRelationships hb = ScalingRelationships.HANKS_BAKUN_08;
		ScalingRelationships sh12_sqrtL = ScalingRelationships.ELLB_SQRT_LENGTH;
		ScalingRelationships sh12_csd = ScalingRelationships.SHAW_CONST_STRESS_DROP;
		
		
		// log10 area from 1 to 5
    	for(int i=(int)downDipWidth; i<=maxLength; i++) {
    		double lengthKm = (double)i;
    		double length = lengthKm*1e3;
    		double area = length*downDipWidth*1e3;
    		sh09_funcMod.set(sh09_Mod.getMag(area, downDipWidth*1e3),sh09_Mod.getAveSlip(area, length, downDipWidth*1e3));
    		ellB_func.set(ellB.getMag(area, downDipWidth*1e3),ellB.getAveSlip(area, length, downDipWidth*1e3));
    		hb_func.set(hb.getMag(area, downDipWidth*1e3),hb.getAveSlip(area, length, downDipWidth*1e3));
    		sh12_sqrtL_func.set(ellB.getMag(area, downDipWidth*1e3),sh12_sqrtL.getAveSlip(area, length, downDipWidth*1e3));
    		sh12_csd_func.set(sh09_Mod.getMag(area, downDipWidth*1e3),sh12_csd.getAveSlip(area, length, downDipWidth*1e3));
    		
    		double heckerMag = hb.getMag(area, downDipWidth*1e3);
//    		WC1994_MagAreaRelationship wc94 = new WC1994_MagAreaRelationship();
//    		double heckerMag = wc94.getMedianMag(area*1e-6);
    		double heckerSlip = Math.pow(10.0,0.41*heckerMag-2.79);
    		heckerEq9b.set(heckerMag,heckerSlip);
    		
//    		System.out.println((float)area*1e-6+"\t"+(float)length*1e-3+"\t"+(float)(downDipWidth)+
//    				"\t"+(float)sh09_Mod.getMag(area, downDipWidth*1e3)+
//    				"\t"+(float)ellB.getMag(area, downDipWidth*1e3)+
//    				"\t"+(float)hb.getMag(area, downDipWidth*1e3));

    	}
    	
    	ArrayList<ArbitrarilyDiscretizedFunc> funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
    	funcs.add(sh09_funcMod);
    	funcs.add(ellB_func);
    	funcs.add(hb_func);
    	funcs.add(sh12_sqrtL_func);
    	funcs.add(sh12_csd_func);
    	funcs.add(heckerEq9b);
    	
    	ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.GREEN));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.MAGENTA));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.CYAN));

    	
		GraphWindow graph = new GraphWindow(funcs, "Implied Slip vs Mag Relationships; DDW="+downDipWidth+" km", plotChars); 
		graph.setX_AxisLabel("Magnitude");
		graph.setY_AxisLabel("Slip (m)");
		graph.setX_AxisRange(6.0, 8.5);
		graph.setY_AxisRange(0.0, 20.0);
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(18);
		graph.setTickLabelFontSize(16);
		
		if(saveFiles) {
			try {
				graph.saveAsPDF("slipMagScalingPlot.pdf");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	
	
	/**
	 * This tests the magnitudes and implied slip amounts for creeping-section faults
	 * assuming a length and DDW
	 */
	public static void testCreepingSectionSlips() {
		double lengthKm = 150;
		double origWidthKm = 11;
		double widthKm = 1.2;
		double areaKm = lengthKm*widthKm;
		
		ArrayList<ScalingRelationships> magAreaList = new ArrayList<ScalingRelationships>();
		magAreaList.add(ScalingRelationships.ELLSWORTH_B);
		magAreaList.add(ScalingRelationships.HANKS_BAKUN_08);
		magAreaList.add(ScalingRelationships.SHAW_2009_MOD);
		
		ArrayList<ScalingRelationships> aveSlipForRupModelsList= new ArrayList<ScalingRelationships>();
		aveSlipForRupModelsList.add(ScalingRelationships.ELLSWORTH_B);
		aveSlipForRupModelsList.add(ScalingRelationships.HANKS_BAKUN_08);
		aveSlipForRupModelsList.add(ScalingRelationships.SHAW_2009_MOD);
		aveSlipForRupModelsList.add(ScalingRelationships.ELLB_SQRT_LENGTH);
		aveSlipForRupModelsList.add(ScalingRelationships.SHAW_CONST_STRESS_DROP);
		
		
		FaultModels fm = FaultModels.FM3_1;
		DeformationModels dm = DeformationModels.GEOLOGIC;
		
		String result = "CREEPING SECTION Mag and AveSlip (assuming length=150, origDDW=11, and DDW=1.2 km):\n";
		
		for(ScalingRelationships scale : ScalingRelationships.values()) {
			double mag = scale.getMag(areaKm*1e6, origWidthKm*1e3);
			double slip = scale.getAveSlip(areaKm*1e6, lengthKm*1e3, origWidthKm*1e3);
			mag = Math.round(mag*100)/100.;
			slip = Math.round(slip*100)/100.;
			result += (float)mag+"\t"+(float)slip+"\tfor\t"+scale.getShortName()+"\n";
		}

		
		System.out.println(result);

	}
	
	
	/**
	 * This assumes no aseismicity
	 * @param saveFiles
	 */
	public static void makeMagAreaPlot(boolean saveFiles) {
		
		double downDipWidth=11;	// orig down-dip width equals reduced
		
		ArbitrarilyDiscretizedFunc sh09mod_func = new ArbitrarilyDiscretizedFunc();
		sh09mod_func.setName("SHAW_2009_Mod; downDipWidth="+downDipWidth);
		ArbitrarilyDiscretizedFunc ellB_func = new ArbitrarilyDiscretizedFunc();
		ellB_func.setName("ELLSWORTH_B");
		ArbitrarilyDiscretizedFunc hb_func = new ArbitrarilyDiscretizedFunc();
		hb_func.setName("HANKS_BAKUN_08");
		
		ScalingRelationships sh09mod = ScalingRelationships.SHAW_2009_MOD;
		ScalingRelationships ellB = ScalingRelationships.ELLSWORTH_B;
		ScalingRelationships hb = ScalingRelationships.HANKS_BAKUN_08;
		
		// log10 area from 1 to 5
    	for(int i=50; i<=20000; i+=10) {
    		double area = (double)i;
     		sh09mod_func.set(area,sh09mod.getMag(area*1e6,downDipWidth*1e3));
    		ellB_func.set(area,ellB.getMag(area*1e6,downDipWidth*1e3));
    		hb_func.set(area,hb.getMag(area*1e6,downDipWidth*1e3));
    	}
    	
    	ArrayList<ArbitrarilyDiscretizedFunc> funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
    	funcs.add(sh09mod_func);
    	funcs.add(ellB_func);
    	funcs.add(hb_func);
    	
    	ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 1f, Color.GREEN));

    	
		GraphWindow graph = new GraphWindow(funcs, "Mag-Area Relationships",plotChars); 
		graph.setX_AxisLabel("Area (km-sq)");
		graph.setY_AxisLabel("Magnitude");
		graph.setXLog(true);
		graph.setX_AxisRange(50, 2e4);
		graph.setY_AxisRange(5, 9);
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(18);
		graph.setTickLabelFontSize(16);
		
		if(saveFiles) {
			try {
				graph.saveAsPDF("magAreaScalingPlot.pdf");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}


	
	//public 
	public static void main(String[] args) throws IOException {
		
		 makeSlipAreaPlot(11, 1000, true);
		 makeSlipLengthPlot(11, 1000, true);
		 makeMagAreaPlot(true);

		
//		ArrayList<ScalingRelationships> ScRelList = new ArrayList<ScalingRelationships>();
//		ScRelList.add(ScalingRelationships.ELLSWORTH_B);
//		ScRelList.add(ScalingRelationships.HANKS_BAKUN_08);
//		ScRelList.add(ScalingRelationships.SHAW_2009_MOD);
//		ScRelList.add(ScalingRelationships.ELLB_SQRT_LENGTH);
//		ScRelList.add(ScalingRelationships.SHAW_CONST_STRESS_DROP);
//		double[] lengthArray = {11e3, 36e3};
//
//		double origDDW = 11e3;
//		double[] aseisFactorArray = {0,0.8};
//		for(double length:lengthArray) {
//			for(double aseisFactor:aseisFactorArray) {
//				double ddw = origDDW*(1-aseisFactor);
//				double area = ddw*length;
//				System.out.println("Results for\tlength="+length+"\torigDDW="+origDDW+"\taseisFactor="+aseisFactor+":");
//				for(ScalingRelationships scRel : ScRelList) {
//					System.out.println("\t"+scRel+":");
//					//					System.out.println("\tOld Way:");
//					System.out.println("\t\tmagnitue = "+(float)scRel.getMag(length*ddw, origDDW));
//					double aveSlip = (float)scRel.getAveSlip(length*ddw, length, origDDW);
//					System.out.println("\t\taveSlip = "+(float)aveSlip);
//					double moment = FaultMomentCalc.getMoment(area, aveSlip);
//					double magFromMoment = MagUtils.momentToMag(moment);
//					System.out.println("\t\tmomentFromAreaAndAveSlip = "+(float)moment);
//					System.out.println("\t\tmagFromMoment = "+(float)magFromMoment);
//
//					//					System.out.println("\tNew Way:");
//					//					System.out.println("\t\tmagnitue = "+(float)scRel.getMag(length*ddw, origDDW));
//					//					aveSlip = (float)scRel.getAveSlip((length*ddw)/2.0, length, origDDW);
//					//					System.out.println("\t\taveSlip = "+(float)aveSlip);
//					//					moment = FaultMomentCalc.getMoment(area, aveSlip);
//					//					magFromMoment = MagUtils.momentToMag(moment);
//					//					System.out.println("\t\tmomentFromAreaAndAveSlip = "+(float)moment);
//					//					System.out.println("\t\tmagFromMoment = "+(float)magFromMoment);
//				}			
//			}
//		}


		
//		ddw = 2.33*1e3;
//		length = area/ddw;
//		System.out.println("length = "+(float)length);
//		System.out.println(sh09_Mod+"\tmag = "+sh09_Mod.getMag(length*ddw, ddw));
//		System.out.println(sh09_Mod+"\taveD = "+sh09_Mod.getAveSlip(length*ddw, length));
//		System.out.println(hb_Mod+"\tmag = "+hb_Mod.getMag(length*ddw, ddw));
//		System.out.println(hb_Mod+"\taveD = "+hb_Mod.getAveSlip(length*ddw, length));



		
//		makeSlipMagPlot(15, 2000, true);
		
	//	testCreepingSectionSlips();
		
		
//		// the following code tested the revised shaw09 mod implementation (he verified the numbers produced)
//		MagAreaRelationship sh09_Mod = MagAreaRelationships.SHAW_09_MOD.getMagAreaRelationships().get(0);
//		System.out.println("length\twidth\tarea\tmag\tmoment\tslip\timplWidth\timplWidth/width");
//		for(double width = 5; width<16; width += 5) {
//			for(double length = 10; length<200; length += 30) {
//				double area = length*width;
//				double mag = ((MagAreaRelDepthDep)sh09_Mod).getWidthDepMedianMag(area, width);			
//				double moment = MagUtils.magToMoment(mag);
//				double slip = AveSlipForRupModels.SHAW_2009_MOD.getAveSlip(area*1e6, length*1e3);
//				double implWidth = 1e-3*moment/(slip*length*1e3*FaultMomentCalc.SHEAR_MODULUS);			
//				double ratio = implWidth/width;			
//				System.out.println((float)length+
//						"\t"+(float)width+
//						"\t"+(float)area+
//						"\t"+(float)mag+
//						"\t"+(float)moment+
//						"\t"+(float)slip+
//						"\t"+(float)implWidth+
//						"\t"+(float)ratio);
//			}
//		}
	}


}
