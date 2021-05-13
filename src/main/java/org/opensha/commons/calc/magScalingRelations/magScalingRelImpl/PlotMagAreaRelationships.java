package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import java.util.ArrayList;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWindow;

public class PlotMagAreaRelationships {
	
	
	public static void makePlot() {
		ArbitrarilyDiscretizedFunc ellB_func = new ArbitrarilyDiscretizedFunc();
		ellB_func.setName("Ellsworth B");
		ArbitrarilyDiscretizedFunc hanksBakun_func = new ArbitrarilyDiscretizedFunc();
		hanksBakun_func.setName("Hanks and Bakun");
		ArbitrarilyDiscretizedFunc shaw09_func = new ArbitrarilyDiscretizedFunc();
		shaw09_func.setName("Shaw (2009)");
		ArbitrarilyDiscretizedFunc wc1994_func = new ArbitrarilyDiscretizedFunc();
		wc1994_func.setName("Wells and Coppersmith (1994)");
		
		Ellsworth_B_WG02_MagAreaRel ellB = new Ellsworth_B_WG02_MagAreaRel();
		HanksBakun2002_MagAreaRel hb = new HanksBakun2002_MagAreaRel();
		Shaw_2009_MagAreaRel sh09 = new Shaw_2009_MagAreaRel();
		WC1994_MagAreaRelationship wc94 = new WC1994_MagAreaRelationship();
		
		
		// log10 area from 1 to 5
    	for(int i=0; i<=41; i++) {
    		double logArea = 1+(double)i/10;
    		double area = Math.pow(10,logArea);
    		ellB_func.set(area,ellB.getMedianMag(area));
    		hanksBakun_func.set(area,hb.getMedianMag(area));
    		shaw09_func.set(area,sh09.getMedianMag(area));
    		wc1994_func.set(area,wc94.getMedianMag(area));
    	}
    	
    	ArrayList<ArbitrarilyDiscretizedFunc> funcs = new ArrayList<ArbitrarilyDiscretizedFunc>();
    	funcs.add(ellB_func);
    	funcs.add(hanksBakun_func);
    	funcs.add(shaw09_func);
    	funcs.add(wc1994_func);
    	
		GraphWindow graph = new GraphWindow(funcs, "Mag Area Relationships"); 
		graph.setX_AxisLabel("Area");
		graph.setY_AxisLabel("Mag");
		graph.setXLog(true);
		graph.setX_AxisRange(10, 1e5);
		graph.setY_AxisRange(4, 9);


	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		PlotMagAreaRelationships.makePlot();


	}

}
