package org.opensha.sha.imr.attenRelImpl.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JSplitPane;

import org.opensha.commons.gui.plot.ButtonControlPanel;
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.util.FileUtils;


/**
 * <p>Title: AttenuationRelationshipWebBasedApplet</p>
 *
 * <p>Description: This class extends AttenuationRelationshipApplet so that its
 * GUI can be customized for giving it the look and feel so that it looks good
 * when launched in the Web Browser.</p>
 * @author : Nitin Gupta
 * @since Feb 17 2005,
 * @version 1.0
 */
public class AttenuationRelationshipWebBasedApplet
    extends AttenuationRelationshipApplet {


	   /**
     *  Component initialization
     *
     * @exception  Exception  Description of the Exception
     */
    protected void jbInit() throws Exception {

        String S = C + ": jbInit(): ";


        border1 = BorderFactory.createLineBorder(new Color(80, 80, 133),2);
        this.setFont( new java.awt.Font( "Dialog", 0, 10 ) );
        this.setSize(new Dimension(900, 690) );
        this.getContentPane().setLayout( GBL);
        outerPanel.setLayout( GBL );
        mainPanel.setBorder(border1 );
        mainPanel.setLayout( GBL );
        titlePanel.setMinimumSize(new Dimension(40, 40));
        titlePanel.setPreferredSize(new Dimension(40, 40));
        titlePanel.setLayout( GBL);
        //creating the Object the GraphPaenl class
        graphWidget = new GraphWidget();

        
        plotPanel.setLayout(GBL);
        innerPlotPanel.setLayout(GBL);
        innerPlotPanel.setBorder( null );
        controlPanel.setLayout(GBL);
        controlPanel.setBorder(BorderFactory.createEtchedBorder(1));
        outerControlPanel.setLayout(GBL);


        clearButton.setText( "Clear Plot" );

        clearButton.addActionListener(
            new java.awt.event.ActionListener() {
                public void actionPerformed(ActionEvent e){
                    clearButton_actionPerformed(e);
                }
            }
        );

        addButton.setText( "Add Curve" );

        addButton.addActionListener(new java.awt.event.ActionListener() {
          public void actionPerformed(ActionEvent e) {
            addButton_actionPerformed(e);
          }
        });

        buttonPanel.setLayout(flowLayout1 );


        parametersSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        parametersSplitPane.setBorder( null );
        parametersSplitPane.setDividerSize( 5 );

        mainSplitPane.setOrientation( JSplitPane.HORIZONTAL_SPLIT );
        mainSplitPane.setBorder( null );
        mainSplitPane.setDividerSize( 2 );

        plotSplitPane.setOrientation( JSplitPane.VERTICAL_SPLIT );
        plotSplitPane.setBorder( null );
        plotSplitPane.setDividerSize( 2 );
        
        plotSplitPane.setBottomComponent( buttonPanel );
        plotSplitPane.setTopComponent(mainPanel );
        plotSplitPane.setDividerLocation(570 );
 
        
        attenRelLabel.setForeground( darkBlue );
        attenRelLabel.setFont(new java.awt.Font( "Dialog", Font.BOLD, 13 ));
        attenRelLabel.setText( "Choose Model:    " );


        attenRelComboBox.setFont( new java.awt.Font( "Dialog", Font.BOLD, 16 ) );


        attenRelComboBox.addItemListener( this );


        plotColorCheckBox.setText("Black Background");

        plotColorCheckBox.addItemListener( this );

        //setting the layout for the Parameters panels
        parametersPanel.setLayout( GBL );
        controlPanel.setLayout( GBL );
        sheetPanel.setLayout( GBL );
        inputPanel.setLayout( GBL );

        //loading the OpenSHA Logo
        imgLabel.setText("");
        imgLabel.setIcon(new ImageIcon(FileUtils.loadImage(this.POWERED_BY_IMAGE)));
        
	    xyDatasetButton.setText("Add Data Points");
	    xyDatasetButton.addActionListener(new java.awt.event.ActionListener() {
	      public void actionPerformed(ActionEvent e) {
	        xyDatasetButton_actionPerformed(e);
	      }
	    });
	    peelOffButton.setText("Peel Off");
	    peelOffButton.addActionListener(new java.awt.event.ActionListener() {
	      public void actionPerformed(ActionEvent e) {
	        peelOffButton_actionPerformed(e);
	      }
	    });
	    this.getContentPane().add( outerPanel, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
	            , GridBagConstraints.CENTER, GridBagConstraints.BOTH, emptyInsets, 0, 0 ) );

        outerPanel.add( plotSplitPane,         new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
            ,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 5, 0, 5), 0, 0) );

        titlePanel.add( this.attenRelLabel, new GridBagConstraints( 0, 0 , 1, 1, 1.0, 0.0
            , GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, emptyInsets, 0, 0 ) );


        titlePanel.add( this.attenRelComboBox, new GridBagConstraints( 1, 0 , 1, 1, 1.0, 0.0
            , GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, emptyInsets, 0, 0 ) );


        mainPanel.add( mainSplitPane, new GridBagConstraints( 0, 1, 1, 1, 1.0, 1.0
            , GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 2, 4, 4, 4 ), 0, 0 ) );


        controlPanel.add(parametersPanel, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
            , GridBagConstraints.CENTER, GridBagConstraints.BOTH, emptyInsets, 0, 0 ) );

        outerControlPanel.add(controlPanel, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
            , GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 5, 0, 0 ), 0, 0 ) );

        parametersPanel.add( parametersSplitPane, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
            , GridBagConstraints.CENTER, GridBagConstraints.BOTH, emptyInsets, 0, 0 ) );

        plotPanel.add( titlePanel, new GridBagConstraints( 0, 0, 1, 1, 1.0, 0.0
            ,GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets( 4, 4, 2, 4 ), 0, 0 ) );


        plotPanel.add( innerPlotPanel, new GridBagConstraints( 0, 1, 1, 1, 1.0, 1.0
            , GridBagConstraints.CENTER, GridBagConstraints.BOTH, defaultInsets, 0, 0 ) );




        //object for the ButtonControl Panel
        buttonPanel.add(addButton, 0);
        buttonPanel.add(clearButton, 1);
        buttonPanel.add(peelOffButton, 2);
        buttonPanel.add(xyDatasetButton, 3);
        buttonPanel.add(plotColorCheckBox, 4);
        buttonPanel.add(imgLabel, 5);
        

        parametersSplitPane.setBottomComponent( sheetPanel );
        parametersSplitPane.setTopComponent( inputPanel );
        parametersSplitPane.setDividerLocation(220 );

        parametersSplitPane.setOneTouchExpandable( false );

        mainSplitPane.setBottomComponent( outerControlPanel );
        mainSplitPane.setTopComponent(plotPanel );
        mainSplitPane.setDividerLocation(600 );
 

        // Big function here, sets all the AttenuationRelationship stuff and puts in sheetsPanel and
        // inputsPanel
        updateChoosenAttenuationRelationship();

    }


  /**
   *  Main method
   *
   * @param  args  The command line arguments
   */
  public static void main( String[] args ) {

      AttenuationRelationshipWebBasedApplet applet = new AttenuationRelationshipWebBasedApplet();
	  applet.init();
	  applet.setVisible(true);     

  }


}
