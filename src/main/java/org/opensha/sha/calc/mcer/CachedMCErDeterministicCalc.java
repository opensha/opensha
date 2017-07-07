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
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

public class CachedMCErDeterministicCalc extends AbstractMCErDeterministicCalc implements Serializable {
	
	private transient AbstractMCErDeterministicCalc calc;
	private File cacheFile;
	
	private Table<Double, Location, DeterministicResult> cache;
	private boolean cacheChanged = false;
	
	public CachedMCErDeterministicCalc(AbstractMCErDeterministicCalc calc, File cacheFile) {
		this.calc = calc;
		this.cacheFile = cacheFile;
	}

	@Override
	public synchronized Map<Double, DeterministicResult> calc(Site site,
			Collection<Double> periods) {
		
		checkInitCache();
		
		Location loc = site.getLocation();
		
		Map<Double, DeterministicResult> result = Maps.newHashMap();
		
		for (Double period : periods) {
			DeterministicResult val = cache.get(period, loc);
			
			if (val == null) {
				val = calc.calc(site, period);
				cache.put(period, loc, val);
				cacheChanged = true;
			}
			
			result.put(period, val);
		}
		
		return result;
	}
	
	public synchronized void flushCache() throws IOException {
		if (cache != null && cacheFile != null && cacheChanged) {
			writeCache(cache, cacheFile);
			cacheChanged = false;
		}
	}
	
	private static Table<Double, Location, DeterministicResult> loadCache(File cacheFile) throws IOException, DocumentException {
		Document doc = XMLUtils.loadDocument(cacheFile);
		Element root = doc.getRootElement();
		
		Table<Double, Location, DeterministicResult> cache = HashBasedTable.create();
		
		for (Element el : XMLUtils.getSubElementsList(root, "DeterministicSpectrum")) {
			Location loc = Location.fromXMLMetadata(el.element(Location.XML_METADATA_NAME));
			
			for (Element periodEl : XMLUtils.getSubElementsList(el, "PeriodResult")) {
				double period = Double.parseDouble(periodEl.attributeValue("period"));
				
				Element resultEl = periodEl.element(DeterministicResult.XML_METADATA_NAME);
				
				DeterministicResult result = DeterministicResult.fromXMLMetadata(resultEl);
				
				cache.put(period, loc, result);
			}
		}
		
		return cache;
	}
	
	private static void writeCache(Table<Double, Location, DeterministicResult> cache, File cacheFile) throws IOException {
		if (cache.isEmpty())
			return;
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		
		for (Location loc : cache.columnKeySet()) {
			Map<Double, DeterministicResult> result = cache.column(loc);
			
			Element el = root.addElement("DeterministicSpectrum");
			loc.toXMLMetadata(el);
			
			for (Double period : result.keySet()) {
				Element periodEl = el.addElement("PeriodResult");
				periodEl.addAttribute("period", period+"");
				
				result.get(period).toXMLMetadata(periodEl);
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
	
	public synchronized void addToCache(Table<Double, Location, DeterministicResult> cache) {
		checkInitCache();
		this.cache.putAll(cache);
		cacheChanged = true;
	}
	
	public synchronized void addToCache(CachedMCErDeterministicCalc o) {
		o.checkInitCache();
		addToCache(o.cache);
	}

}
