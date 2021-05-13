package org.opensha.sha.simulators.srf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.parsers.RSQSimFileReader;
import org.opensha.sha.simulators.srf.RSQSimStateTransitionFileReader.TransVersion;

import com.google.common.base.Stopwatch;

class TransSpeedTest {

	public static void main(String[] args) throws IOException {
//		File dir = new File("/home/kevin/Simulators/catalogs/singleSS/old_trans");
//		File geomFile = new File(dir, "test.flt");
//		File transFile = new File(dir, "trans.test.out");
		File dir = new File("/home/kevin/Simulators/catalogs/bruce/rundir4950");
		File geomFile = new File(dir, "zfault_Deepen.in");
		File transFile = new File(dir, "transV..out");
		
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'N');
		System.out.println("Reading events...");
		List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(dir, elements);
//		events = events.subList(0, 10);
		System.out.println("Read "+events.size()+" events");
		
		RSQSimStateTransitionFileReader transReader = new RSQSimStateTransitionFileReader(transFile, elements);
		transReader.setQuiet(true);
		if (transReader.getVersion() == TransVersion.ORIGINAL) {
			Map<Integer, Double> velMap = new HashMap<>();
			for (SimulatorElement elem : elements)
				velMap.put(elem.getID(), 1d);
			transReader.setPatchFixedVelocities(velMap);
		}
		
		// iterate over all transitions
		Stopwatch iterateWatch = Stopwatch.createStarted();
		long numTrans = 0;
		System.out.println("Iterating...");
		for (RSQSimStateTime trans : transReader.getTransitions(0d, Double.POSITIVE_INFINITY))
			numTrans++;
		iterateWatch.stop();
		
		double iterateSecs = iterateWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		System.out.println("Iterated through "+numTrans+" transitions in "+iterateSecs+" seconds");
		double transPerSec = numTrans/iterateSecs;
		System.out.println("\trate: "+(float)transPerSec+" trans/sec");
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {}
		
		System.out.println("Event-iterating");
		Stopwatch eventWatch = Stopwatch.createStarted();
		long numEventTrans = 0;
		for (int i = 0; i < events.size(); i++) {
			RSQSimEvent event = events.get(i);
			List<RSQSimStateTime> transList = new ArrayList<>();
			transReader.getTransitions(event, transList);
			numEventTrans += transList.size();
			if (i % 10000 == 0) {
				double eventSecs = eventWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
				System.out.println("Event "+i+" at "+eventSecs);
			}
		}
		eventWatch.stop();
		
		double eventSecs = eventWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		System.out.println("Loaded transitions for "+events.size()+" events ("+numEventTrans
					+" transitions) in "+eventSecs+" seconds");
		double eventsPerSec = events.size()/eventSecs;
		System.out.println("\trate: "+(float)eventsPerSec+" events/sec");
		double eventsTransPerSec = numEventTrans/eventSecs;
		System.out.println("\trate: "+(float)eventsTransPerSec+" eventTrans/sec");
	}

}
