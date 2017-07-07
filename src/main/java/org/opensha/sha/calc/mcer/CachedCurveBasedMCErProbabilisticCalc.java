package org.opensha.sha.calc.mcer;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
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
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

public class CachedCurveBasedMCErProbabilisticCalc extends CurveBasedMCErProbabilisitCalc implements Serializable {
	
	private transient CurveBasedMCErProbabilisitCalc calc;
	private File cacheFile;
	
//	private Map<Location, DiscretizedFunc> cache;
	private Table<Location, Double, DiscretizedFunc> cache;
	private boolean cacheChanged = false;
	
	public CachedCurveBasedMCErProbabilisticCalc(CurveBasedMCErProbabilisitCalc calc, File cacheFile) {
		this.calc = calc;
		this.cacheFile = cacheFile;
	}

	@Override
	protected Map<Double, DiscretizedFunc> calcHazardCurves(Site site,
			Collection<Double> periods) {
		Preconditions.checkArgument(!periods.isEmpty());
		
		checkInitCache();
		
		Location loc = site.getLocation();
		
		Map<Double, DiscretizedFunc> ret = Maps.newHashMap();
		
		for (double period : periods) {
			DiscretizedFunc curve = cache.get(loc, period);
			
			if (curve == null) {
				curve = calc.calcHazardCurves(site, Lists.newArrayList(period)).get(period);
				synchronized (this) {
					if (!cache.contains(loc, period))
						cache.put(loc, period, curve);
				}
				cacheChanged = true;
			}
			
			ret.put(period, curve);
		}
		
		return ret;
	}
	
	public void flushCache() throws IOException {
		if (cache != null && cacheFile != null && cacheChanged)
			writeCache(cache, cacheFile);
	}
	
	private static Table<Location, Double, DiscretizedFunc> loadCache(File cacheFile)
			throws IOException, DocumentException {
		Document doc = XMLUtils.loadDocument(cacheFile);
		Element root = doc.getRootElement();
		
		Table<Location, Double, DiscretizedFunc> cache = HashBasedTable.create();
		
		for (Element el : XMLUtils.getSubElementsList(root, "ProbabilisticSpectrum")) {
			Location loc = Location.fromXMLMetadata(el.element(Location.XML_METADATA_NAME));
			
			for (Element periodEl : XMLUtils.getSubElementsList(el, "Period")) {
				double period = Double.parseDouble(periodEl.attributeValue("value"));
				DiscretizedFunc curve = ArbitrarilyDiscretizedFunc.fromXMLMetadata(periodEl.element("HazardCurve"));
				cache.put(loc, period, curve);
			}
		}
		
		return cache;
	}
	
	private static void writeCache(Table<Location, Double, DiscretizedFunc> cache, File cacheFile)
			throws IOException {
		if (cache.isEmpty())
			return;
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		
		for (Location loc : cache.rowKeySet()) {
			Map<Double, DiscretizedFunc> rowMap = cache.row(loc);
			Preconditions.checkState(rowMap.size() > 0);
			
			Element el = root.addElement("ProbabilisticSpectrum");
			loc.toXMLMetadata(el);
			
			for (Double period : rowMap.keySet()) {
				Element periodEl = el.addElement("Period");
				periodEl.addAttribute("value", period+"");
				rowMap.get(period).toXMLMetadata(periodEl, "HazardCurve");
			}
		}
		
		XMLUtils.writeDocumentToFile(cacheFile, doc);
	}
	
	private void checkInitCache() {
		if (cache == null) {
			if (cacheFile != null && cacheFile.exists()) {
				try {
					cache = loadCache(cacheFile);
				} catch (Exception e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			} else {
				cache = HashBasedTable.create();
			}
		}
	}
	
	public synchronized void addToCache(Table<Location, Double, DiscretizedFunc> cache) {
		checkInitCache();
		this.cache.putAll(cache);
		cacheChanged = true;
	}
	
	public synchronized void addToCache(CachedCurveBasedMCErProbabilisticCalc o) {
		o.checkInitCache();
		addToCache(o.cache);
	}

	@Override
	public void setXVals(DiscretizedFunc xVals) {
		calc.setXVals(xVals);
	}

}
