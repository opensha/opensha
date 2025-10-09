package org.opensha.sha.calc.IM_EventSet.v03;

import java.util.ArrayList;
import java.util.EventObject;

/**
 * SelectedIMRChangeEvents are fired when the selected IMRs change in the IMR_ChooserPanel.
 */
public class SelectedIMRChangeEvent extends EventObject {
    private final ArrayList<String> oldIMRList;
    private final ArrayList<String> newIMRList;

    /**
     * Constructor
     *
     * @param  reference      Object which created this event
     * @param  oldIMRList       Old list of selected IMRs
     * @param  newIMRList       New list of selected IMRs
     */

    public SelectedIMRChangeEvent(Object reference, ArrayList<String> oldIMRList, ArrayList<String> newIMRList) {
        super(reference);
        this.oldIMRList = oldIMRList;
        this.newIMRList = newIMRList;
    }

    public ArrayList<String> getOldIMRList() {
        return oldIMRList;
    }

    public ArrayList<String> getNewIMRList() {
        return newIMRList;
    }
}
