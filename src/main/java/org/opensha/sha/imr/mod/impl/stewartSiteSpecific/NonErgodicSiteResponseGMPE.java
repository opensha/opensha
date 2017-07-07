package org.opensha.sha.imr.mod.impl.stewartSiteSpecific;

import java.awt.Dimension;
import java.util.EnumSet;
import java.util.ListIterator;

import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListParameterEditor;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.mod.AbstractAttenRelMod;
import org.opensha.sha.imr.mod.ModAttenRelRef;
import org.opensha.sha.imr.mod.ModAttenuationRelationship;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;

/**
 * Steward 2014 modified GMPE which can be used in the Attenuation Relationship app
 * @author kevin
 *
 */
public class NonErgodicSiteResponseGMPE extends ModAttenuationRelationship {
	
	public static final String NAME = "Non Ergodic Site Response GMPE 2016";
	public static final String SHORT_NAME = "NonErgodic2016";
	
	private ParameterList refSiteParams;
	
	public NonErgodicSiteResponseGMPE() {
		this(null);
	}
	
	public NonErgodicSiteResponseGMPE(ParameterChangeWarningListener l) {
//		super(l, EnumSet.copyOf(AttenRelRef.get(ServerPrefUtils.SERVER_PREFS)),
//				EnumSet.of(ModAttenRelRef.STEWART_SITE_SPECIFIC));
		// for now just BSSA 2014
		super(l, EnumSet.of(AttenRelRef.BSSA_2014, AttenRelRef.ASK_2014, AttenRelRef.CY_2014,
				AttenRelRef.CB_2014), EnumSet.of(ModAttenRelRef.STEWART_SITE_SPECIFIC, ModAttenRelRef.ERGODIC_FROM_REF));
		getParameter(IMRS_PARAM_NAME).setValue(AttenRelRef.BSSA_2014);
		getParameter(IMRS_PARAM_NAME).setInfo(
				"The intensity measure used as the driver of nonlinearity representing the level of "
				+ "\nshaking in reference condition (rock motion).");
		getParameter(MODS_PARAM_NAME).setValue(ModAttenRelRef.STEWART_SITE_SPECIFIC);
		
		((ParameterListParameterEditor)modParams.getEditor()).setDialogDimensions(new Dimension(600, 400));
	}

	@Override
	public Parameter getParameter(String name) throws ParameterException {
		// check IM and dependent params
		for (Parameter<?> param : supportedIMParams) {
			if (param.getName().equals(name))
				return param;
			if (param.containsIndependentParameter(name))
				return param.getIndependentParameter(name);
		}
		// check site params
		if (siteParams.containsParameter(name))
			return siteParams.getParameter(name);
		// check underlying IMR
		try {
			if (getCurrentIMR() != null)
				return getCurrentIMR().getParameter(name);
		} catch (ParameterException e) {
			// doesn't exist in the IMR
		}
		
		return super.getParameter(name);
	}

	@Override
	protected ParameterList getReferenceIMRParams(ParameterList paramsFromIMR) {
		if (!(getCurrentMod() instanceof NonErgodicSiteResponseMod))
			return paramsFromIMR;
		ParameterList params = new ParameterList();
		for (Parameter<?> param : paramsFromIMR) {
			if (param.getName().equals(StdDevTypeParam.NAME))
				continue;
			params.addParameter(param);
		}
		// now add site params
		if (refSiteParams == null) {
			NonErgodicSiteResponseMod mod = (NonErgodicSiteResponseMod) getCurrentMod();
			if (mod == null)
				return params;
			refSiteParams = mod.getReferenceSiteParams();
		}
		
		ScalarIMR imr = getCurrentIMR();
		for (Parameter<?> param : refSiteParams) {
			if (imr.getSiteParams().containsParameter(param.getName()))
				params.addParameter(param);
		}
		
		return params;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}
	
	public NonErgodicSiteResponseMod getMod() {
		AbstractAttenRelMod curMod = getCurrentMod();
		if (curMod instanceof NonErgodicSiteResponseMod)
			return (NonErgodicSiteResponseMod)getCurrentMod();
		return null;
	}

}
