package scratch.UCERF3.erf;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.utils.FindEquivUCERF2_Ruptures.FindEquivUCERF2_Ruptures;
import scratch.UCERF3.utils.FindEquivUCERF2_Ruptures.FindEquivUCERF2_Ruptures.UCERF2_FaultModel;

public class TestFaultSysSolPoissonERF {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		/*
		long test = Long.MAX_VALUE;
		long test2 = test+1;
		System.out.println(test+"\t"+test2);

		System.out.println("Long max value: "+Long.MAX_VALUE);
		System.out.println("Long min value: "+Long.MIN_VALUE);
		System.out.println("Long max value(yrs): "+Long.MAX_VALUE/(1000*60*60*24*365.25));
		
		GregorianCalendar calendarTest0 = new GregorianCalendar();
		System.out.println("calendarTest0 Time Zone: "+calendarTest0.getTimeZone());
		calendarTest0.set(1,1,1);
//		calendarTest0.setTimeInMillis(0);
		System.out.println("calendarTest0 time : "+calendarTest0.getTime());
		
		GregorianCalendar calendarTest1 = new GregorianCalendar();
		calendarTest1.setTimeZone(TimeZone.getTimeZone("UTC"));
		calendarTest1.set(1,1,1);
//		calendarTest1.setTimeInMillis(0);
		System.out.println("calendarTest1 Time Zone: "+calendarTest1.getTimeZone());
		System.out.println("calendarTest1 time : "+calendarTest1.getTime());
		
		double timeDiff = ((double)(calendarTest1.getTimeInMillis()-calendarTest0.getTimeInMillis())/(1000*60*60));
		System.out.println("time diff : "+timeDiff);

		System.exit(0);
		
		System.out.println("Default Time Zone: "+TimeZone.getDefault());
		System.out.println("junk Time Zone: "+TimeZone.getTimeZone(" "));
		System.out.println("Time Zone IDs: \n");
		for(String id:TimeZone.getAvailableIDs()) {
//			System.out.println("\t"+id+"\n");
		}

		

		
		// test:
		GregorianCalendar calendarTest = new GregorianCalendar();
		calendarTest.setLenient(false);
		calendarTest.set(Calendar.ERA,GregorianCalendar.AD);
		calendarTest.set(1,0,1,0,0,0);
		long time1 = calendarTest.getTimeInMillis();
		System.out.println("getTimeInMillis="+time1);
		calendarTest.set(Calendar.ERA,GregorianCalendar.BC);
		calendarTest.set(1,11,31, 23, 59, 59);
		long time2 = calendarTest.getTimeInMillis();
		System.out.println("getTimeInMillis="+time2);
		System.out.println("diff="+(time2-time1));
		System.exit(0);
		//
		 * 
		 */
		
		FaultSystemSolutionPoissonERF invERF = new FaultSystemSolutionPoissonERF("/Users/field/ALLCAL_UCERF2.zip");
		invERF.aleatoryMagAreaStdDevParam.setValue(0.12);
		invERF.updateForecast();
		System.out.println("done with invERF");
		invERF.writeSourceNamesToFile("/Users/field/workspace/OpenSHA/dev/scratch/UCERF3/erf/tempSrcNames.txt");
		invERF.testNthRupIndicesForSource();
		
		System.out.println("s=4753 name: "+invERF.getSource(4753).getName()+"; nth rups:\n");
		int[] rupIndices = invERF.get_nthRupIndicesForSource(4753);
		for(int i=0; i<rupIndices.length;i++)
			System.out.println("\t"+rupIndices[i]);
		System.out.println("\n");
		
		System.out.println("getNumSources() = "+invERF.getNumSources());
		System.out.println("getTotNumRups() = "+invERF.getTotNumRups());
		System.out.println(invERF.getIndexN_ForSrcAndRupIndices(0, 0));
		System.out.println(invERF.getIndexN_ForSrcAndRupIndices(1, 0));
		int lastSrcIndex = invERF.getNumSources()-1;
		int lastRupIndex = invERF.getNumRuptures(lastSrcIndex)-1;
		System.out.println(invERF.getIndexN_ForSrcAndRupIndices(lastSrcIndex, lastRupIndex));
//		System.exit(0);

		
		ERF modMeanUCERF2 = FindEquivUCERF2_Ruptures.buildERF(UCERF2_FaultModel.FM2_1);
		modMeanUCERF2.updateForecast();
		System.out.println("done with modMeanUCERF2");
		
		Region testRegion = new CaliforniaRegions.RELM_SOCAL();
		
		long startTime = System.currentTimeMillis();
		SummedMagFreqDist invMFD = ERF_Calculator.getMagFreqDistInRegion(invERF, testRegion, 5.05, 40, 0.1, true);
		long runtime = System.currentTimeMillis()-startTime;
		System.out.println("done with invMFD; took following seconds"+runtime/1000);
		SummedMagFreqDist origMFD = ERF_Calculator.getMagFreqDistInRegion(modMeanUCERF2, testRegion, 5.05, 40, 0.1, true);
		System.out.println("done with origMFD");
		
		
		ArrayList funcs = new ArrayList();
		funcs.add(invMFD);
		funcs.add(origMFD);
		funcs.add(invMFD.getCumRateDistWithOffset());
		funcs.add(origMFD.getCumRateDistWithOffset());
		GraphWindow graph = new GraphWindow(funcs, "Incremental Mag-Freq Dists"); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setYLog(true);
		graph.setY_AxisRange(1e-6, 1.0);

		
		System.out.println("done");

	}

}
