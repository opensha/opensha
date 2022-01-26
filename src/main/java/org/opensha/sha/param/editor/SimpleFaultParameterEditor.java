package org.opensha.sha.param.editor;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JOptionPane;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditorOld;
import org.opensha.sha.param.editor.gui.SimpleFaultParameterEditorPanel;
import org.opensha.sha.param.editor.gui.SimpleFaultParameterGUI;


/**
 * <p>Title: SimpleFaultParameterEditor</p>
 * <p>Description: It is a more general parameter than just a Simple Fault Parameter
 * Editor because actually inside it creates an object of the EvenlyGriddedSurface1EvenlyGriddedSurface1.</p>
 * @author : Edward Field, Nitin Gupta and Vipin Gupta
 * @created : July 31, 2003
 * @version 1.0
 */

public class SimpleFaultParameterEditor extends AbstractParameterEditorOld
    implements ActionListener {


  /** Class name for debugging. */
  protected final static String C = "SimpleFaultParameterEditor";
  /** If true print out debug statements. */
  protected final static boolean D = false;
  private Insets defaultInsets = new Insets( 4, 4, 4, 4 );

  SimpleFaultParameterGUI surfaceGUI ;

  public SimpleFaultParameterEditor() {}

  //constructor taking the Parameter as the input argument
  public SimpleFaultParameterEditor(Parameter model){
    super(model);
  }

  /**
   * Set the values in the Parameters for the EvenlyGridded Surface
   */
  public void setParameter(Parameter param)  {
    setParameterInEditor(param);
    valueEditor = new JButton(param.getName());
    ((JButton)valueEditor).addActionListener(this);
    add(valueEditor,  new GridBagConstraints( 0, 0, 1, 1, 1.0, 0.0
        , GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets( 0, 0, 0, 0 ), 0, 0 ) );
    //setting the value for the simpleFaultParameter
    surfaceGUI = new SimpleFaultParameterGUI(param);

    // All done
    if ( D ) System.out.println( "Ending:" );
  }

  /**
   * Called when the parameter has changed independently from
   * the editor, such as with the ParameterWarningListener.
   * This function needs to be called to to update
   * the GUI component ( text field, picklist, etc. ) with
   * the new parameter value.
   */
  public void refreshParamEditor() {
    if(surfaceGUI!=null)
      surfaceGUI.refreshParamEditor();
  }




  /**
   * Main GUI Initialization point. This block of code is updated by JBuilder
   * when using it's GUI Editor.
   */
  protected void jbInit() throws Exception {
    // Main component
    this.setLayout( new GridBagLayout());
  }


  /**
   * This function is called when the user click for the SimpleFaultParameterEditor
   *
   * @param ae
   */
  public void actionPerformed(ActionEvent ae ) {
    try{
      surfaceGUI.setVisible(true);
      surfaceGUI.pack();
    }catch(RuntimeException e){
      JOptionPane.showMessageDialog(this,e.getMessage(),"Incorrect Values",JOptionPane.ERROR_MESSAGE);
    }
  }


  /**
   *
   * @return the instance of the SimpleFaultEditorPanel that actually contains
   * all the parameters for the Surface parameter
   */
  public SimpleFaultParameterEditorPanel getParameterEditorPanel(){
    return this.surfaceGUI.getSimpleFaultEditorPanel();
  }

}



