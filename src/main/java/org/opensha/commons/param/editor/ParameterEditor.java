/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.param.editor;

import javax.swing.JComponent;
import javax.swing.border.Border;

import org.opensha.commons.param.Parameter;

/**
 * <b>Title:</b> ParameterEditorAPI<p>
 *
 * <b>Description:</b> Common interface functions that all implementing
 * ParameterEditors must implement so that they can be plugged transparently
 * into GUI frameworks. <p>
 *
 * This allows classes that use the ParameterEditors to deal with any
 * Editor type equally. Using this interface they all look the same. This
 * permits new editors to be added to the framework without changing the
 * using classes. <p>
 *
 * Note that all editors edit a Parameter. Internally they maintain a reference
 * to the particular parameter type they know how to handle. <p>
 *
 * @author     Steven W. Rock
 * @created    April 17, 2002
 * @version    1.0
 */

public interface ParameterEditor<E> {

    /** Set the value of the Parameter this editor is editing. */
    public void setValue( E object );

    /** Returns the value of the parameter object.  */
    public E getValue();

    /**
     * Needs to be called by subclasses when editable widget field change fails
     * due to constraint problems. Allows rollback to the previous good value.
     */
    public void unableToSetValue( Object object );

    /**
     * Called when the parameter has changed independently from
     * the editor. This function needs to be called to to update
     * the GUI component ( text field, picklsit, etc. ) with
     * the new parameter value.
     */
    public void refreshParamEditor();

    /** Returns the parameter that is stored internally that this GUI widget is editing */
    public Parameter<E> getParameter();

    /** Sets the parameter that is stored internally for this GUI widget to edit */
    public void setParameter( Parameter<E> model );


    /** Sets the focusEnabled boolean indicating this is the GUI componet with the current focus */
    public void setFocusEnabled( boolean newFocusEnabled );

    /** Returns the focusEnabled boolean indicating this is the GUI componet with the current focus */
    public boolean isFocusEnabled();
    
    public void setEnabled(boolean isEnabled);
    
    public void setVisible(boolean isVisible);
    
    public boolean isVisible();
    
    public JComponent getComponent();
    
    public void setEditorBorder(Border b);

}

