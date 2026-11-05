package org.opensha.sha.calc.sourceFilters;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.opensha.commons.data.Site;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.sha.calc.sourceFilters.params.TectonicRegionTypesParam;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.EqkSource;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * Source filter that includes only the supplied tectonic region types.
 */
public class TectonicRegionSourceFilter implements SourceFilter, ParameterChangeListener {

	private final TectonicRegionTypesParam includedParam;
	private final ParameterList params;
	private Set<TectonicRegionType> included;

	public TectonicRegionSourceFilter() {
		includedParam = new TectonicRegionTypesParam();
		includedParam.addParameterChangeListener(this);
		params = new ParameterList();
		params.addParameter(includedParam);
		updateIncluded();
	}

	public void setIncludedTectonicRegionTypes(Collection<TectonicRegionType> included) {
		Preconditions.checkNotNull(included, "included tectonic region types cannot be null");
		EnumSet<TectonicRegionType> copy = included.isEmpty()
				? EnumSet.noneOf(TectonicRegionType.class) : EnumSet.copyOf(included);
		includedParam.setValue(copy);
	}

	public Set<TectonicRegionType> getIncludedTectonicRegionTypes() {
		return Set.copyOf(included);
	}

	@Override
	public boolean canSkipSource(EqkSource source, Site site, double sourceSiteDistance) {
		return !included.contains(source.getTectonicRegionType());
	}

	@Override
	public boolean canSkipRupture(EqkRupture rup, Site site) {
		// TRT is a source-level property; all unwanted sources are skipped above.
		return false;
	}

	@Override
	public ParameterList getAdjustableParams() {
		return params;
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		updateIncluded();
	}

	private void updateIncluded() {
		Set<TectonicRegionType> value = includedParam.getValue();
		included = value.isEmpty() ? EnumSet.noneOf(TectonicRegionType.class) : EnumSet.copyOf(value);
	}

	@Override
	public String toString() {
		return "Included tectonic region types: "+included;
	}
}
