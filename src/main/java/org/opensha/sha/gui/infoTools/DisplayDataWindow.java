package org.opensha.sha.gui.infoTools;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * <p>Title: DisplayDataWindow</p>
 *
 * <p>Description: Allows the users to display the data in a window.</p>
 *
 * @author Nitin Gupta
 * @version 1.0
 */
public class DisplayDataWindow
    extends JFrame {

  private JScrollPane dataScrollPane = new JScrollPane();
  private JTextArea dataText = new JTextArea();
  private BorderLayout borderLayout1 = new BorderLayout();

  public DisplayDataWindow(Component parent, String data, String title) {
    try {
      jbInit();
      // show the window at center of the parent component
      this.setLocation(parent.getX()+parent.getWidth()/2,
                       parent.getY()+parent.getHeight()/2);
      this.setTitle(title);

    }
    catch (Exception exception) {
      exception.printStackTrace();
    }
    setDataInWindow(data);
  }

  private void jbInit() throws Exception {
    getContentPane().setLayout(borderLayout1);
    dataScrollPane.getViewport().add(dataText, null);
    this.getContentPane().add(dataScrollPane, java.awt.BorderLayout.CENTER);
    dataText.setEditable(false);
    this.setSize(400,300);
  }


  public void setDataInWindow(String data){
    dataText.setText(data);
    dataText.setCaretPosition(0);
  }

}
