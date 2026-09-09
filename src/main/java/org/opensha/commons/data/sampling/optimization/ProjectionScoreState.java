package org.opensha.commons.data.sampling.optimization;

import org.opensha.commons.data.sampling.scoring.ProjectionDiscrepancyScore.ProjectionResult;

interface ProjectionScoreState {
	ProjectionResult score();
}
