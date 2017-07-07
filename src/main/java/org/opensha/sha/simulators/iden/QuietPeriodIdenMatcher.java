package org.opensha.sha.simulators.iden;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.parsers.EQSIMv06FileReader;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class QuietPeriodIdenMatcher implements RuptureIdentifier {
	
	private RuptureIdentifier matchIden;
	private double allowedAftershockYears;
	private double quietYears;
	private RuptureIdentifier[] quietMatchIdens;
	
	public QuietPeriodIdenMatcher(RuptureIdentifier matchIden, double allowedAftershockYears,
			double quietYears, List<RuptureIdentifier> quietMatchIdens) {
		this(matchIden, allowedAftershockYears, quietYears, quietMatchIdens.toArray(new RuptureIdentifier[0]));
	}
	
	public QuietPeriodIdenMatcher(RuptureIdentifier matchIden, double allowedAftershockYears,
			double quietYears, RuptureIdentifier... quietMatchIdens) {
		this.matchIden = matchIden;
		this.allowedAftershockYears = allowedAftershockYears;
		this.quietYears = quietYears;
		this.quietMatchIdens = quietMatchIdens;
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		throw new IllegalStateException("Can't call this on QuietPeriod iden as we don't have the event list");
	}

	@Override
	public <E extends SimulatorEvent> List<E> getMatches(List<E> events) {
		List<E> matches = Lists.newArrayList(matchIden.getMatches(events));
		int origNum = matches.size();
		HashSet<Integer> nonQuiets = new HashSet<Integer>();
		
		for (RuptureIdentifier quietIden : quietMatchIdens) {
			// look for any ruptures within the window of any matches
			List<E> quietMatches = quietIden.getMatches(events);
			
			int targetStartIndex = 0;
			for (int i=0; i<matches.size(); i++) {
				SimulatorEvent match = matches.get(i);
				double matchTime = match.getTimeInYears();
				double targetWindowStart = matchTime + allowedAftershockYears;
				double targetWindowEnd = matchTime + quietYears;
				
				for (int j=targetStartIndex; j<quietMatches.size(); j++) {
					double targetTime = quietMatches.get(j).getTimeInYears();
					if (targetTime  == matchTime)
						continue;
					if (targetTime <= targetWindowStart) {
						targetStartIndex = j;
						continue;
					}
					if (targetTime <= targetWindowEnd)
						// this means that we're in a quiet period, therefore this match isn't quiet
						nonQuiets.add(i);
					// skip to next match
					break;
				}
			}
		}
		List<Integer> nonQuietsList = Lists.newArrayList(nonQuiets);
		// sort low to high
		Collections.sort(nonQuietsList);
		// needs to be from high to low for removal
		Collections.reverse(nonQuietsList);
		for (int ind : nonQuietsList)
			matches.remove(ind);
		
		System.out.println("Quiet Matcher: "+matches.size()+"/"+origNum+" match with quiet period of "+quietYears+" years");
		Collections.sort(matches);
		
		return matches;
	}

	public double getQuietYears() {
		return quietYears;
	}
	
	public static void main(String[] args) throws IOException {
		File dir = new File("/home/kevin/Simulators");
		File geomFile = new File(dir, "ALLCAL2_1-7-11_Geometry.dat");
		System.out.println("Loading geometry...");
		General_EQSIM_Tools tools = new General_EQSIM_Tools(geomFile);
//		File eventFile = new File(dir, "eqs.ALLCAL2_RSQSim_sigma0.5-5_b=0.015.barall");
		File eventFile = new File(dir, "eqs.ALLCAL2_RSQSim_sigma0.5-5_b=0.015.long.barall");
		System.out.println("Loading events...");
		List<? extends SimulatorEvent> events = EQSIMv06FileReader.readEventsFile(eventFile, tools.getElementsList());
		
		ElementMagRangeDescription mojaveCoachellCorupture = new ElementMagRangeDescription(
				"SAF Mojave/Coachella Corupture", 6d, 10d,
				ElementMagRangeDescription.SAF_MOJAVE_ELEMENT_ID, ElementMagRangeDescription.SAF_COACHELLA_ELEMENT_ID);
		
		List<RuptureIdentifier> rupIdens = Lists.newArrayList();
		List<Color> colors = Lists.newArrayList();
		
		rupIdens.add(new ElementMagRangeDescription("SAF Cholame 7+",
				ElementMagRangeDescription.SAF_CHOLAME_ELEMENT_ID, 7d, 10d));
		colors.add(Color.RED);
		
		rupIdens.add(new ElementMagRangeDescription("SAF Carrizo 7+",
				ElementMagRangeDescription.SAF_CARRIZO_ELEMENT_ID, 7d, 10d));
		colors.add(Color.BLUE);
		
		rupIdens.add(new ElementMagRangeDescription("Garlock 7+",
				ElementMagRangeDescription.GARLOCK_WEST_ELEMENT_ID, 7d, 10d));
		colors.add(Color.GREEN);
		
		rupIdens.add(new ElementMagRangeDescription("SAF Mojave 7+",
				ElementMagRangeDescription.SAF_MOJAVE_ELEMENT_ID, 7d, 10d));
		colors.add(Color.BLACK);
		
		rupIdens.add(new ElementMagRangeDescription("SAF Coachella 7+",
				ElementMagRangeDescription.SAF_COACHELLA_ELEMENT_ID, 7d, 10d));
		colors.add(Color.RED);
		
		rupIdens.add(new ElementMagRangeDescription("San Jacinto 7+",
				ElementMagRangeDescription.SAN_JACINTO__ELEMENT_ID, 7d, 10d));
		colors.add(Color.CYAN);
		
//		List<RuptureIdentifier> quietIdens = rupIdens;
		List<RuptureIdentifier> quietIdens = Lists.newArrayList(rupIdens.get(0), rupIdens.get(1), rupIdens.get(3), rupIdens.get(4));
		
		RuptureIdentifier quietIden = new QuietPeriodIdenMatcher(mojaveCoachellCorupture,
				5, 150, quietIdens);
		
		List<? extends SimulatorEvent> matches = quietIden.getMatches(events);
		
		for (SimulatorEvent e : matches)  {
			System.out.println(e.getID()+": "+e.getTimeInYears()+" ("+e.getMagnitude()+")");
			double time = e.getTimeInYears();
			for (SimulatorEvent e1 : events) {
				if (e1.getMagnitude() < 7)
					continue;
				double time2 = e1.getTimeInYears();
				if (time2 <= time)
					continue;
				double timeDiff = time2 - time;
				if (timeDiff > 200)
					break;
				
				List<String> idenMatches = Lists.newArrayList();
				for (int i=0; i<rupIdens.size(); i++)
					if (rupIdens.get(i).isMatch(e1))
						idenMatches.add(rupIdens.get(i).getName());
				
				System.out.println("\t"+timeDiff+": "+e1.getMagnitude()
						+" (matches: "+Joiner.on(",").join(idenMatches)+")");
			}
		}
	}

	@Override
	public String getName() {
		return "Quiet Perdiod: "+quietYears+" years, iden="+matchIden.getName();
	}

}
