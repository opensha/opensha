package org.opensha.sha.simulators.stiffness;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipException;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.utils.FaultSystemIO;

public class U3Comparisons {

	public static void main(String[] args) throws ZipException, IOException, DocumentException, InterruptedException, ExecutionException {
		File fssFile = new File("/home/kevin/Simulators/catalogs/rundir4983_stitched/fss/"
				+ "rsqsim_sol_m6.5_skip5000_sectArea0.2.zip");
		FaultModels fm = FaultModels.FM3_1;
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(fssFile);
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		double lambda = 30000;
		double mu = 30000;
		double coeffOfFriction = 0.5;
		SubSectStiffnessCalculator calc = new SubSectStiffnessCalculator(
				subSects, 1d, lambda, mu, coeffOfFriction, PatchAlignment.FILL_OVERLAP, 1d);
		
		AggregatedStiffnessCalculator tauCalc = new AggregatedStiffnessCalculator(StiffnessType.TAU, calc, false,
				AggregationMethod.MAX, AggregationMethod.MAX);
		AggregatedStiffnessCalculator cffCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, calc, false,
				AggregationMethod.MAX, AggregationMethod.MAX);
		
		CoulombRates coulombRates = CoulombRates.loadUCERF3CoulombRates(fm);
		DefaultXY_DataSet tauXY = new DefaultXY_DataSet();
		DefaultXY_DataSet cffXY = new DefaultXY_DataSet();
		
		ExecutorService exec = Executors.newFixedThreadPool(32);
		
		List<Future<?>> futures = new ArrayList<>();
		
		for (IDPairing pair : coulombRates.keySet()) {
			CoulombRatesRecord rec = coulombRates.get(pair);
			
			FaultSection source = subSects.get(pair.getID1());
			FaultSection receiver = subSects.get(pair.getID2());
			
			futures.add(exec.submit(new Runnable() {
				
				@Override
				public void run() {
					double myTau = tauCalc.calc(source, receiver);
					double myCFF = cffCalc.calc(source, receiver);
					synchronized (coulombRates) {
						tauXY.set(rec.getShearStressChange(), myTau);
						cffXY.set(rec.getCoulombStressChange(), myCFF);
					}
				}
			}));
		}
		
		System.out.println("Waiting on "+futures.size()+" futures");
		for (int i=0; i<futures.size(); i++) {
			futures.get(i).get();
			if (i % 500 == 0)
				System.out.println("Done with "+i);
		}
		System.out.println("DONE");
		
		double[] scalars = new double[cffXY.size()];
		for (int i=0; i<cffXY.size(); i++)
			scalars[i] = cffXY.getX(i)/cffXY.getY(i);
		System.out.println("CFF Scalars:");
		System.out.println("Min: "+StatUtils.min(scalars));
		System.out.println("Max: "+StatUtils.max(scalars));
		System.out.println("Mean: "+StatUtils.mean(scalars));
		System.out.println("Median: "+DataUtils.median(scalars));
		
		GraphWindow gw = new GraphWindow(tauXY, "Tau (U3 vs Okada)",
				new PlotCurveCharacterstics(PlotSymbol.CROSS, 2f, Color.BLACK));
		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
		gw.setX_AxisLabel("UCERF3 DT");
		gw.setY_AxisLabel("Okada DT");
		
		gw = new GraphWindow(cffXY, "CFF (U3 vs Okada)",
				new PlotCurveCharacterstics(PlotSymbol.CROSS, 2f, Color.BLACK));
		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
		gw.setX_AxisLabel("UCERF3 DCFF");
		gw.setY_AxisLabel("Okada DCFF");
	}

}
