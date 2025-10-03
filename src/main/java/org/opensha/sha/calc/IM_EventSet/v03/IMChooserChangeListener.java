package org.opensha.sha.calc.IM_EventSet.v03;

/**
 * Listener for changes in the IMT or IMR selection from the IMT_ChooserPanel and IMR_ChooserPanel.
 * This wraps the GUI beans and abstracts the multiple underlying change events to generically
 * describe intensity measure change for firing events in other panels of the IM Event Set Calculator.
 */
@FunctionalInterface
public interface IMChooserChangeListener {
    void selectionChanged();
}
