package org.opensha.sha.simulators.srf;

import java.io.IOException;

import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.RSQSimEventRecord;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.iden.AbstractRuptureIdentifier;

import com.google.common.base.Preconditions;

public class RSQSimTransValidIden extends AbstractRuptureIdentifier {
	
	private double firstTrans;
	private double lastTrans;
	
	public RSQSimTransValidIden(RSQSimStateTransitionFileReader trans) throws IOException {
		firstTrans = trans.getFirstTransitionTime();
		lastTrans = trans.getLastTransitionTime();
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		Preconditions.checkState(event instanceof RSQSimEvent);
		double eventStart = event.getTime();
		if (eventStart < firstTrans || eventStart > lastTrans)
			return false;
		if (lastTrans - eventStart < 1000) {
			// check ends
			for (EventRecord rec : event) {
				RSQSimEventRecord rRec = (RSQSimEventRecord)rec;
				for (double nextTime : rRec.getNextSlipTimes()) {
					if (Double.isFinite(nextTime) && nextTime > lastTrans)
						return false;
				}
			}
		}
		return true;
	}

	@Override
	public String getName() {
		return "Transition-valid events identifier";
	}

}
