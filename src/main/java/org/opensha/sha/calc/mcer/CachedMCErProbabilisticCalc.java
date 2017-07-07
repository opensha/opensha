package org.opensha.sha.calc.mcer;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public class CachedMCErProbabilisticCalc extends AbstractMCErProbabilisticCalc {
	
	private AbstractMCErProbabilisticCalc calc;
	private File cacheFile;
	
	private Map<Location, DiscretizedFunc> cache;
	private boolean cacheChanged = false;
	
	public CachedMCErProbabilisticCalc(AbstractMCErProbabilisticCalc calc, File cacheFile) {
		this.calc = calc;
		this.cacheFile = cacheFile;
	}

	@Override
	public synchronized DiscretizedFunc calc(Site site, Collection<Double> periods) {
		Preconditions.checkArgument(!periods.isEmpty());
		
		if (cache == null) {
			if (cacheFile != null && cacheFile.exists()) {
				try {
					cache = loadCache(cacheFile);
				} catch (Exception e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			} else {
				cache = Maps.newHashMap();
			}
		}
		
		Location loc = site.getLocation();
		
		DiscretizedFunc func = cache.get(loc);
		
		if (func == null) {
			func = new ArbitrarilyDiscretizedFunc();
			cache.put(loc, func);
			cacheChanged = true;
		}
		
		for (Double period : periods) {
			if (!func.hasX(period)) {
				// need to calculate it
				double val = calc.calc(site, period);
				func.set(period, val);
				cacheChanged = true;
			}
		}
		
		return func;
	}
	
	public void flushCache() throws IOException {
		if (cache != null && cacheFile != null && cacheChanged)
			writeCache(cache, cacheFile);
	}
	
	private static Map<Location, DiscretizedFunc> loadCache(File cacheFile) throws IOException, DocumentException {
		Document doc = XMLUtils.loadDocument(cacheFile);
		Element root = doc.getRootElement();
		
		Map<Location, DiscretizedFunc> cache = Maps.newHashMap();
		
		for (Element el : XMLUtils.getSubElementsList(root, "ProbabilisticSpectrum")) {
			Location loc = Location.fromXMLMetadata(el.element(Location.XML_METADATA_NAME));
			
			Element valEl = el.element("SingleValue");
			DiscretizedFunc func;
			if (valEl != null) {
				// single value
				double period = Double.parseDouble(valEl.attributeValue("period"));
				double value = Double.parseDouble(valEl.attributeValue("value"));
				func = new ArbitrarilyDiscretizedFunc();
				func.set(period, value);
			} else {
				// full spectrum
				func = ArbitrarilyDiscretizedFunc.fromXMLMetadata(el.element("Spectrum"));
			}
			
			cache.put(loc, func);
		}
		
		return cache;
	}
	
	private static void writeCache(Map<Location, DiscretizedFunc> cache, File cacheFile) throws IOException {
		if (cache.isEmpty())
			return;
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		
		for (Location loc : cache.keySet()) {
			DiscretizedFunc func = cache.get(loc);
			Preconditions.checkState(func.size() > 0);
			
			Element el = root.addElement("ProbabilisticSpectrum");
			loc.toXMLMetadata(el);
			
			if (func.size() == 1) {
				// just write the val
				Element valEl = el.addElement("SingleValue");
				valEl.addAttribute("period", func.getX(0)+"");
				valEl.addAttribute("value", func.getY(0)+"");
			} else {
				// write the function
				func.toXMLMetadata(el, "Spectrum");
			}
		}
		
		XMLUtils.writeDocumentToFile(cacheFile, doc);
	}

	@Override
	public void setUseUHS(double uhsVal) {
		if (uhsVal != this.uhsVal)
			cache.clear();
		calc.setUseUHS(uhsVal);
		super.setUseUHS(uhsVal);
	}

}
