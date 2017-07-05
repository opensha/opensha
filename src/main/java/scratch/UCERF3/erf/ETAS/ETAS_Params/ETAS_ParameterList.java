package scratch.UCERF3.erf.ETAS.ETAS_Params;

import java.util.Iterator;

import org.dom4j.Element;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;

import com.google.common.base.Preconditions;

/**
 * This holds a complete list of ETAS parameters
 * @author field
 *
 */
public class ETAS_ParameterList extends ParameterList implements XMLSaveable {
	
	private static final long serialVersionUID = 1L;
	
	public static final String XML_METADATA_NAME = "ETAS_ParameterList";

	ETAS_ProductivityParam_k kParam = new ETAS_ProductivityParam_k();
	ETAS_TemporalDecayParam_p pParam = new ETAS_TemporalDecayParam_p();
	ETAS_MinTimeParam_c cParam = new ETAS_MinTimeParam_c();
	ETAS_DistanceDecayParam_q qParam = new ETAS_DistanceDecayParam_q();
	ETAS_MinDistanceParam_d dParam = new ETAS_MinDistanceParam_d();
	U3ETAS_ProbabilityModelParam probModelParam = new U3ETAS_ProbabilityModelParam();
	ETAS_ImposeGR_SamplingParam imposeGR = new ETAS_ImposeGR_SamplingParam();
	U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam applySubSeisForSupraNuclParam = new U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam();
	U3ETAS_ApplyGridSeisCorrectionParam applyGridSeisCorrParam = new U3ETAS_ApplyGridSeisCorrectionParam();
	U3ETAS_TotalRateScaleFactorParam totalRateScaleFactorParam = new U3ETAS_TotalRateScaleFactorParam();
	//	U3ETAS_MaxCharFactorParam maxCharFactorParam = new U3ETAS_MaxCharFactorParam();
//	ETAS_FractionSpontaneousParam fractSpontParam = new ETAS_FractionSpontaneousParam();
//	ETAS_ApplyLongTermRatesInSamplingParam applyLongTermRatesParam = new ETAS_ApplyLongTermRatesInSamplingParam();

	public ETAS_ParameterList() {
		this.addParameter(kParam);
		this.addParameter(pParam);
		this.addParameter(cParam);
		this.addParameter(qParam);
		this.addParameter(dParam);
		this.addParameter(probModelParam);
		this.addParameter(imposeGR);
		this.addParameter(applySubSeisForSupraNuclParam);
		this.addParameter(applyGridSeisCorrParam);
		this.addParameter(totalRateScaleFactorParam);
//		this.addParameter(maxCharFactorParam);
//		this.addParameter(fractSpontParam); // computed from other things
//		this.addParameter(applyLongTermRatesParam);
		
	}
	
	public double get_k() {return kParam.getValue();}
	public double get_p() {return pParam.getValue();}
	public double get_c() {return cParam.getValue();}
	public double get_q() {return qParam.getValue();}
	public double get_d() {return dParam.getValue();}
	public U3ETAS_ProbabilityModelOptions getU3ETAS_ProbModel() {return probModelParam.getValue();}
	public boolean getImposeGR() {return imposeGR.getValue();}
	public boolean getApplySubSeisForSupraNucl() {return applySubSeisForSupraNuclParam.getValue();}
	public boolean getApplyGridSeisCorr() {return applyGridSeisCorrParam.getValue();}
	public double getTotalRateScaleFactor() {return totalRateScaleFactorParam.getValue();}
//	public double getMaxCharFactor() {return maxCharFactorParam.getValue(); }
//	public double getFractSpont() {return fractSpontParam.getValue();}
//	public boolean getApplyLongTermRates() {return applyLongTermRatesParam.getValue();}
	
	
	public void set_k(double k) {kParam.setValue(k);}
	public void set_p(double p) {pParam.setValue(p);}
	public void set_c(double c) {cParam.setValue(c);}
	public void set_q(double q) {qParam.setValue(q);}
	public void get_d(double d) {dParam.setValue(d);}
	public void setImposeGR(boolean value) { imposeGR.setValue(value);}
	public void setU3ETAS_ProbModel(U3ETAS_ProbabilityModelOptions value) { probModelParam.setValue(value); }
	public void setApplySubSeisForSupraNucl(boolean value) {applySubSeisForSupraNuclParam.setValue(value);}
	public void setApplyGridSeisCorr(boolean value) {applyGridSeisCorrParam.setValue(value);}
	public void setTotalRateScaleFactor(double value) {totalRateScaleFactorParam.setValue(value);}

	
//	public void setMaxCharFactor(double value) {maxCharFactorParam.setValue(value); }
//	public void setFractSpont(double fractSpont) {fractSpontParam.setValue(fractSpont);}
//	public void setApplyLongTermRates(boolean value) { applyLongTermRatesParam.setValue(value);}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

	}

	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		for (Parameter<?> param : this)
			param.toXMLMetadata(el);
		return root;
	}
	
	public static ETAS_ParameterList fromXMLMetadata(Element paramsEl) {
		ETAS_ParameterList params = new ETAS_ParameterList();
		
		// if xml file doesn't have this parameter, it should be false, so make sure it's initiated false
		params.setApplyGridSeisCorr(false);
		
		Iterator<Element> it = paramsEl.elementIterator();
		while (it.hasNext()) {
			Element el = it.next();
			String name = el.attributeValue("name");
			Parameter<?> param;
			try {
				param = params.getParameter(name);
			} catch (ParameterException e) {
				System.err.println("WARNING: Parameter no longer exists in ETAS Parameter List: "+name);
				continue;
			}
			Preconditions.checkNotNull(param);
			boolean success = param.setValueFromXMLMetadata(el);
			if (!success)
				System.err.println("WARNING: Couldn't set "+name+" from metadata file");
		}
		
		return params;
	}

}
