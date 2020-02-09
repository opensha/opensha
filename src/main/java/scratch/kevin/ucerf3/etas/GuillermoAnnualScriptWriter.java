package scratch.kevin.ucerf3.etas;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ComcatConfigBuilder;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ConfigBuilder;

public class GuillermoAnnualScriptWriter {

	public static void main(String[] args) {
		int startYear = 1986;
		int endYear = 2020;
		int deltaYears = 1;
		double duration = 1d;
		int numCatalogs = 10000;
		
//		String kCOV = "1.5";
		String kCOV = null;
//		U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelOptions.FULL_TD;
		U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelOptions.NO_ERT;
		
		DecimalFormat optionalDigitDF = new DecimalFormat("0.##");
		
		for (int simStartYear=startYear; simStartYear<=endYear; simStartYear += deltaYears) {
			List<String> argz = new ArrayList<>();
			argz.add("--num-simulations"); argz.add(numCatalogs+"");
			argz.add("--include-spontaneous");
			argz.add("--historical-catalog");
			argz.add("--duration"); argz.add((float)duration+"");
			if (kCOV != null && !kCOV.isEmpty()) {
				argz.add("--etas-k-cov"); argz.add(kCOV);
			}
			
			boolean comcat = simStartYear > 2012;
			
			String name = "Start "+simStartYear+", "+optionalDigitDF.format(duration)+" yr";
			if (kCOV != null && !kCOV.isEmpty())
				name += ", kCOV="+kCOV;
			if (comcat)
				name += ", ComCat Stitch";
			if (probModel != U3ETAS_ProbabilityModelOptions.FULL_TD) {
				name += ", "+probModel.toString();
				argz.add("--prob-model"); argz.add(probModel.name());
			}
			name += ", Spontaneous, Historical Catalog";
			
			if (comcat) {
				argz.add("--start-after-historical");
				argz.add("--end-year"); argz.add(simStartYear+"");
				argz.add("--finite-surf-shakemap");
				argz.add("--finite-surf-shakemap-min-mag"); argz.add("6");
			} else {
				argz.add("--start-year"); argz.add(simStartYear+"");
			}
			argz.add("--name"); argz.add(name);
			
			argz.add("--hpc-site"); argz.add("TACC_STAMPEDE2");
			argz.add("--nodes"); argz.add("10");
			argz.add("--hours"); argz.add("3");
			argz.add("--queue"); argz.add("skx-normal");
			
			System.out.println("YEAR: "+simStartYear);
			System.out.println("\tARGS: "+argz);
			args = argz.toArray(new String[0]);
			if (comcat)
				ETAS_ComcatConfigBuilder.main(args);
			else
				ETAS_ConfigBuilder.main(args);
		}
	}

}
