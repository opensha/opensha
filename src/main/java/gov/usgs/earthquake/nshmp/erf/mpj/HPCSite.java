package gov.usgs.earthquake.nshmp.erf.mpj;

import java.io.File;
import java.util.Collection;

import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.mpj.FastMPJShellScriptWriter;
import org.opensha.commons.hpc.pbs.BatchScriptWriter;
import org.opensha.commons.hpc.pbs.HovenweepScriptWriter;
import org.opensha.commons.hpc.pbs.USC_CARC_ScriptWriter;

public enum HPCSite {

	USC_CARC_FMPJ("scec", 36, 20, 50, 1,
			true, USC_CARC_ScriptWriter.JAVA_BIN, USC_CARC_ScriptWriter.FMPJ_HOME) {
		@Override
		BatchScriptWriter buildBatchWriter() {
			return new USC_CARC_ScriptWriter();
		}

		@Override
		String followUpQueue(String queue) {
			if ("scec".equals(queue))
				return "scec_hiprio";
			return queue;
		}
	},

	HOVENWEEP_FMPJ(null, 25, 16, 60, 1,
			false, HovenweepScriptWriter.JAVA_BIN, HovenweepScriptWriter.FMPJ_HOME) {
		@Override
		BatchScriptWriter buildBatchWriter() {
			return new HovenweepScriptWriter();
		}
	};

	private final String defaultQueue;
	private final int defaultNodes;
	private final int defaultThreadsPerNode;
	private final int defaultMemGB;
	private final int defaultInversionsPerBundle;
	private final boolean defaultExclusive;
	private final File javaBin;
	private final File mpjHome;

	HPCSite(String defaultQueue, int defaultNodes, int defaultThreadsPerNode, int defaultMemGB,
			int defaultInversionsPerBundle, boolean defaultExclusive, File javaBin, File mpjHome) {
		this.defaultQueue = defaultQueue;
		this.defaultNodes = defaultNodes;
		this.defaultThreadsPerNode = defaultThreadsPerNode;
		this.defaultMemGB = defaultMemGB;
		this.defaultInversionsPerBundle = defaultInversionsPerBundle;
		this.defaultExclusive = defaultExclusive;
		this.javaBin = javaBin;
		this.mpjHome = mpjHome;
	}

	public String defaultQueue() {
		return defaultQueue;
	}

	public int defaultNodes() {
		return defaultNodes;
	}

	public int defaultThreadsPerNode() {
		return defaultThreadsPerNode;
	}

	public int defaultMemGB() {
		return defaultMemGB;
	}

	public int defaultInversionsPerBundle() {
		return defaultInversionsPerBundle;
	}

	public boolean defaultExclusive() {
		return defaultExclusive;
	}

	JavaShellScriptWriter buildMPJWriter(int memGB, Collection<File> classpath) {
		FastMPJShellScriptWriter writer = new FastMPJShellScriptWriter(javaBin, memGB*1024, classpath, mpjHome);
		writer.setUseLaunchWrapper(true);
		return writer;
	}

	JavaShellScriptWriter buildJavaWriter(int memGB, Collection<File> classpath) {
		return new JavaShellScriptWriter(javaBin, memGB*1024, classpath);
	}

	abstract BatchScriptWriter buildBatchWriter();

	String followUpQueue(String queue) {
		return queue;
	}
}
