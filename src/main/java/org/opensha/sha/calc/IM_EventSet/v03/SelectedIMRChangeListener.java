package org.opensha.sha.calc.IM_EventSet.v03;

import java.util.EventListener;

/**
 * Listener for changes in the IMR selection from the IMR_ChooserPanel
 */
public interface SelectedIMRChangeListener extends EventListener {
    /**
     * All listeners will be notified by the IMR_ChooserPanel when the selected IMRs change.
     * @param event
     */
    void imrChange(SelectedIMRChangeEvent event);
}
