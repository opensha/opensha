package org.opensha.refFaultParamDb.gui.view;


import java.awt.GridBagConstraints;
import java.awt.Insets;

import javax.swing.JPanel;

import org.opensha.commons.data.estimate.Estimate;
import org.opensha.commons.gui.LabeledBoxPanel;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.refFaultParamDb.gui.infotools.GUI_Utils;
import org.opensha.refFaultParamDb.gui.infotools.InfoLabel;
import org.opensha.refFaultParamDb.gui.params.CommentsParameterEditor;


/**
 * <p>Title: ViewNumEvents.java </p>
 * <p>Description: Voew num event information for a site for a time period</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ViewNumEvents extends LabeledBoxPanel {
  private final static String NUM_EVENTS_TITLE = "Number of Events";
  private final static String NUM_EVENTS_PANEL_TITLE = "Num Events Estimate";
  private final static String NUM_EVENTS = "# of Events";
  private final static String PROB = "Prob this is correct value";
  private InfoLabel numEventsEstimateLabel = new InfoLabel();
  private StringParameter commentsParam = new StringParameter("Num Events Comments");
  private CommentsParameterEditor commentsParamEditor;

  public ViewNumEvents() {
    super(GUI_Utils.gridBagLayout);
    try {
      viewNumEventsForTimePeriod();
      setTitle(NUM_EVENTS_TITLE);
    }catch(Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Set the information about number of events estimate, comments and references
   * based on site selected by the user.
   *
   * @param numEventsEstimate
   * @param comments
   * @param references
   */
  public void setInfo(Estimate numEventsEstimate, String comments) {
    numEventsEstimateLabel.setTextAsHTML(numEventsEstimate, NUM_EVENTS, PROB);
    commentsParam.setValue(comments);
    commentsParamEditor.refreshParamEditor();
  }

  /**
  * display the Num events info for the selected time period
  */
 private void viewNumEventsForTimePeriod() throws Exception {



   JPanel slipRateEstimatePanel = GUI_Utils.getPanel(numEventsEstimateLabel, NUM_EVENTS_PANEL_TITLE);

   // comments
   commentsParamEditor = new CommentsParameterEditor(commentsParam);
   commentsParamEditor.setEnabled(false);


   // add the slip rate info the panel
   int yPos=0;
   add(slipRateEstimatePanel,new GridBagConstraints( 0, yPos++, 1, 1, 1.0, 1.0
       ,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ) );
   add(commentsParamEditor,new GridBagConstraints( 0, yPos++, 1, 1, 1.0, 1.0
       ,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ) );
 }

}
