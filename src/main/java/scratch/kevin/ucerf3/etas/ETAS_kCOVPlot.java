package scratch.kevin.ucerf3.etas;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration.Callback;

public class ETAS_kCOVPlot {

	public static void main(String[] args) {
		File resultsFile = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
				+ "2019_10_29-Start2012_500yr_kCOV1p5_Spontaneous_HistoricalCatalog/"
				+ "results_m5_preserve_chain.bin");
		
		HistogramFunction hist = new HistogramFunction(-4.95d, 51, 0.1);
		MinMaxAveTracker track = new MinMaxAveTracker();
		
		ETAS_CatalogIteration.processCatalogs(resultsFile, new Callback() {

			@Override
			public void processCatalog(ETAS_Catalog catalog, int index) {
				for (ETAS_EqkRupture rup : catalog) {
					double k = rup.getETAS_k();
					Preconditions.checkState(Double.isFinite(k), "bad k=%s", k);
					double log10k = Math.log10(k);
					track.addValue(log10k);
//					if (Math.random() < 0.01)
//						System.out.println("k="+(float)k+"\tLog10(k)="+(float)log10k);
					hist.add(hist.getClosestXIndex(log10k), 1d);
				}
			}
			
		});
		System.out.println(track);
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
		
		PlotSpec plot = new PlotSpec(funcs, chars, "k Distribution", "Log10(k)", "Count");
		GraphWindow gw = new GraphWindow(plot);
		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
	}

}
