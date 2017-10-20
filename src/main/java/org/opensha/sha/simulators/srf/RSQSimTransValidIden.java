package org.opensha.sha.simulators.srf;

import java.io.IOException;

import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.RSQSimEventRecord;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.iden.AbstractRuptureIdentifier;

import com.google.common.base.Preconditions;

public class RSQSimTransValidIden extends AbstractRuptureIdentifier {
	
	private RSQSimStateTransitionFileReader trans;
	private double slipVel;
	private double firstTrans;
	private double lastTrans;
	
	public RSQSimTransValidIden(RSQSimStateTransitionFileReader trans, double slipVel) throws IOException {
		this.trans = trans;
		this.slipVel = slipVel;
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
			RSQSimEventSlipTimeFunc slipTime = null;
			for (EventRecord rec : event) {
				RSQSimEventRecord rRec = (RSQSimEventRecord)rec;
				double[] nextSlipTimes = rRec.getNextSlipTimes();
				for (int i=0; i<nextSlipTimes.length; i++) {
					double nextTime = nextSlipTimes[i];
					if (!Double.isFinite(nextTime)) {
						// check that it finished
						if (slipTime == null) {
							try {
								slipTime = new RSQSimEventSlipTimeFunc(trans.getTransitions((RSQSimEvent)event), slipVel);
							} catch (IOException e) {
								throw ExceptionUtils.asRuntimeException(e);
							} catch (IllegalStateException e) {
								return false;
							}
							int patchID = rRec.getElementIDs()[i];
							double slip = slipTime.getCumulativeEventSlip(patchID, slipTime.getEndTime());
							double pDiff = DataUtils.getPercentDiff(slip, rRec.getElementSlips()[i]);
							if (pDiff > 1)
								return false;
						}
					} else {
						if (nextTime > lastTrans)
							return false;
					}
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
