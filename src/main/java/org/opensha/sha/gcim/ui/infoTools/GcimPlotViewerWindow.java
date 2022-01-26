package org.opensha.sha.gcim.ui.infoTools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.JToolBar;


/**
 * <p>Title: GcimPlotViewerWindow</p>
 * <p>Description: this Class presents the GCIM results in a seperate window.  The long-term 
 * intention is to add graphics, but presently only results are printed </p>
 * @author: Brendon Bradley Aug 2010
 * @version 1.0
 */

public class GcimPlotViewerWindow extends JFrame {
  private final static int W=650;
  private final static int H=730;


  private final static String MAP_WINDOW = "GCIM distribution results";
//  private JSplitPane mapSplitPane = new JSplitPane();
  private JScrollPane mapScrollPane = new JScrollPane();

  JToolBar jToolBar = new JToolBar();

  private BorderLayout borderLayout1 = new BorderLayout();
  private JPanel mapPanel = new JPanel();
  private GridBagLayout layout = new GridBagLayout();


  //creates the tab panes for the user to view different information for the
  private JTabbedPane infoTabPane = new JTabbedPane();
  //If GCIM results needs to be scrolled
  private JScrollPane gcimResultsScrollPane = new JScrollPane();

  //TextPane 
  private JTextPane gcimResultsPane = new JTextPane();

  private String gcimResultsText;


  /**
   * Class constructor
   * @param imageFileName : Name of the image file to be shown
   * @param mapInfo : Metadata about the Map
   * @param gmtFromServer : boolean to check if map to be generated using the Server GMT
   * @throws RuntimeException
   */
  public GcimPlotViewerWindow(String gcimResultsString)
      throws RuntimeException{

    gcimResultsText = gcimResultsString;
    
    try {
      jbInit();
    }catch(RuntimeException e) {
      e.printStackTrace();
      throw new RuntimeException(e.getMessage());
    }
    
    this.setVisible(true);
  }

  protected void jbInit() throws RuntimeException {
    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    this.setSize(W,H);
    this.setTitle(MAP_WINDOW);
    this.getContentPane().setLayout(borderLayout1);

    this.getContentPane().add(jToolBar, BorderLayout.NORTH);

    infoTabPane.addTab("GCIM Results", gcimResultsScrollPane);
    gcimResultsScrollPane.getViewport().add(gcimResultsPane,null);

    gcimResultsPane.setForeground(Color.blue);
    gcimResultsPane.setText(gcimResultsText);
    gcimResultsPane.setEditable(false);

    this.getContentPane().add(infoTabPane, BorderLayout.CENTER);
    infoTabPane.setTabPlacement(JTabbedPane.BOTTOM);
    mapPanel.setLayout(layout);
    mapScrollPane.getViewport().add(mapPanel, null);

  }


}


