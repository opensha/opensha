package org.opensha.sha.gui.beans;

import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.AbstractParameterEditorOld;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.sha.earthquake.EqkRupture;

/**
 * <p>Title: EqkRupSelectorGuiBeanAPI</p>
 * <p>Description: This class defines methods that any class providing the
 * user the functionality of getting EqkRupture to EqkRupSelectorGuiBean.</p>
 * @author : Ned Field, Nitin Gupta and Vipin Gupta
 * @since Dec 03,2004
 * @version 1.0
 */

public interface EqkRupSelectorGuiBeanAPI {

    /**
     *
     * @return the Hypocenter Location if selected else return null
     */
    public Location getHypocenterLocation();


    /**
     *
     * @return the panel which allows user to select Eqk rupture from existing
     * ERF models
     */
    public EqkRupSelectorGuiBeanAPI getEqkRuptureSelectorPanel();



    /**
     *
     * @return the Metadata String of parameters that constitute the making of this
     * ERF_RupSelectorGUI  bean.
     */
    public String getParameterListMetadataString();

    /**
     *
     * @return the timespan Metadata for the selected Rupture.
     * If no timespan exists for the rupture then it returns the Message:
     * "No Timespan exists for the selected Rupture".
     */
    public String getTimespanMetadataString();

    /**
     *
     * @return the EqkRupture Object
     */
    public EqkRupture getRupture();

    /**
     *
     * @param paramName
     * @return the parameter from the parameterList with paramName.
     */
    public Parameter getParameter(String paramName);

    /**
     *
     * @param paramName
     * @return the ParameterEditor associated with paramName
     */
    public ParameterEditor getParameterEditor(String paramName);

    /**
     *
     * @return the visible parameters in the list
     */
    public ParameterList getVisibleParameterList();

    /**
     *
     * @return the parameterlist editor
     */
    public ParameterListEditor getParameterListEditor();


  }
