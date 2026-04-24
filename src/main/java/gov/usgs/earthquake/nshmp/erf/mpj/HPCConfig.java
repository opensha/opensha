package gov.usgs.earthquake.nshmp.erf.mpj;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.pbs.BatchScriptWriter;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;

import com.google.common.base.Preconditions;

public final class HPCConfig {
	
	private static final int NON_EXCLUSIVE_MEM_BUFFER_GB = 2;

	private final HPCSite site;
	private final File localMainDir;
	private final File remoteMainDir;
	private final int nodes;
	private final int threadsPerNode;
	private final int memGB;
	private final int inversionsPerBundle;
	private final String queue;
	private final String followUpQueue;
	private final String jarFileName;
	private final boolean exclusive;

	private HPCConfig(Builder builder) {
		this.site = builder.site;
		this.localMainDir = builder.localMainDir;
		this.remoteMainDir = builder.remoteMainDir;
		this.nodes = builder.nodes;
		this.threadsPerNode = builder.threadsPerNode;
		this.memGB = builder.memGB;
		this.inversionsPerBundle = builder.inversionsPerBundle;
		this.queue = builder.queue;
		this.followUpQueue = builder.followUpQueue;
		this.jarFileName = builder.jarFileName;
		this.exclusive = builder.exclusive;
	}

	public static Builder builder(HPCSite site) {
		return new Builder(site);
	}

	public static Builder builder(CommandLine cmd) {
		return new Builder(cmd);
	}

	public HPCSite site() {
		return site;
	}

	public File localMainDir() {
		return localMainDir;
	}

	public File remoteMainDir() {
		return remoteMainDir;
	}

	public int nodes() {
		return nodes;
	}

	public int threadsPerNode() {
		return threadsPerNode;
	}

	public int memGB() {
		return memGB;
	}

	public int inversionsPerBundle() {
		return inversionsPerBundle;
	}

	public String queue() {
		return queue;
	}

	public String followUpQueue() {
		if (followUpQueue != null)
			return followUpQueue;
		return site.followUpQueue(queue);
	}

	public String jarFileName() {
		return jarFileName;
	}

	public boolean exclusive() {
		return exclusive;
	}

	public int memGBPerNode() {
		return exclusive ? -1 : memGB + NON_EXCLUSIVE_MEM_BUFFER_GB;
	}

	JavaShellScriptWriter buildMPJWriter(Collection<File> classpath) {
		return site.buildMPJWriter(memGB, classpath);
	}

	JavaShellScriptWriter buildJavaWriter(Collection<File> classpath) {
		return site.buildJavaWriter(memGB, classpath);
	}

	BatchScriptWriter buildBatchWriter() {
		return site.buildBatchWriter();
	}

	void copyEnvVars(JavaShellScriptWriter from, JavaShellScriptWriter to) {
		Map<String, String> envVars = from.getEnvVars();
		if (envVars == null)
			return;
		for (Map.Entry<String, String> entry : envVars.entrySet())
			to.setEnvVar(entry.getKey(), entry.getValue());
	}
	
	public static void addOptions(Options ops) {
		ops.addRequiredOption(null, "local-dir", true, "Local main directory.");
		ops.addOption(null, "remote-dir", true, "Remote main directory; if omitted, it will be assumed that this is "
				+ "being run on the HPC system and remote = local.");
		ops.addRequiredOption(null, "hpc-site", true, "HPC site, one of: "+FaultSysTools.enumOptions(HPCSite.class));
		
		ops.addOption(null, "nodes", true, "Node count override.");
		ops.addOption(null, "threads-per-node", true, "Threads per node override.");
		ops.addOption(null, "mem-gb", true, "Memory in GB override.");
		ops.addOption(null, "inversions-per-bundle", true, "Inversions per bundle override.");
		ops.addOption(null, "queue", true, "Primary queue override.");
		ops.addOption(null, "follow-up-queue", true,
				"Follow-up queue override for post-inversion jobs. If omitted, the site default will be used.");
		ops.addOption(null, "jar-file", true, "Jar file name staged in the run directory; default is opensha-all.jar.");
		ops.addOption(null, "use-dev-jar", false, "Shortcut for --jar-file opensha-dev-all.jar.");
		ops.addOption(null, "exclusive", false, "Request exclusive-node memory allocation behavior.");
		ops.addOption(null, "no-exclusive", false, "Disable exclusive-node memory allocation behavior.");
	}

	public static final class Builder {
		private final HPCSite site;
		private File localMainDir;
		private File remoteMainDir;
		private int nodes;
		private int threadsPerNode;
		private int memGB;
		private int inversionsPerBundle;
		private String queue;
		private String followUpQueue;
		private String jarFileName;
		private boolean exclusive;

		private Builder(CommandLine cmd) {
			this(HPCSite.valueOf(cmd.getOptionValue("hpc-site")));
			
			localMainDir = new File(cmd.getOptionValue("local-dir"));
			if (cmd.hasOption("remote-dir"))
				remoteMainDir = new File(cmd.getOptionValue("remote-dir"));
			else
				remoteMainDir = localMainDir.getAbsoluteFile();
			
			if (cmd.hasOption("nodes"))
				nodes = Integer.parseInt(cmd.getOptionValue("nodes"));
			if (cmd.hasOption("threads-per-node"))
				threadsPerNode = Integer.parseInt(cmd.getOptionValue("threads-per-node"));
			if (cmd.hasOption("mem-gb"))
				memGB = Integer.parseInt(cmd.getOptionValue("mem-gb"));
			if (cmd.hasOption("inversions-per-bundle"))
				inversionsPerBundle = Integer.parseInt(cmd.getOptionValue("inversions-per-bundle"));
			if (cmd.hasOption("queue"))
				queue = cmd.getOptionValue("queue");
			if (cmd.hasOption("follow-up-queue"))
				followUpQueue = cmd.getOptionValue("follow-up-queue");
			Preconditions.checkArgument(!(cmd.hasOption("jar-file") && cmd.hasOption("use-dev-jar")),
					"cannot supply both --jar-file and --use-dev-jar");
			if (cmd.hasOption("jar-file"))
				jarFileName = cmd.getOptionValue("jar-file");
			else if (cmd.hasOption("use-dev-jar"))
				jarFileName = "opensha-dev-all.jar";
			Preconditions.checkArgument(!(cmd.hasOption("exclusive") && cmd.hasOption("no-exclusive")),
					"cannot supply both --exclusive and --no-exclusive");
			if (cmd.hasOption("exclusive"))
				exclusive = true;
			else if (cmd.hasOption("no-exclusive"))
				exclusive = false;
		}

		private Builder(HPCSite site) {
			this.site = Preconditions.checkNotNull(site);
			this.nodes = site.defaultNodes();
			this.threadsPerNode = site.defaultThreadsPerNode();
			this.memGB = site.defaultMemGB();
			this.inversionsPerBundle = site.defaultInversionsPerBundle();
			this.queue = site.defaultQueue();
			this.jarFileName = "opensha-all.jar";
			this.exclusive = site.defaultExclusive();
		}

		public Builder localMainDir(File localMainDir) {
			this.localMainDir = localMainDir;
			return this;
		}

		public Builder remoteMainDir(File remoteMainDir) {
			this.remoteMainDir = remoteMainDir;
			return this;
		}

		public Builder nodes(int nodes) {
			this.nodes = nodes;
			return this;
		}

		public Builder threadsPerNode(int threadsPerNode) {
			this.threadsPerNode = threadsPerNode;
			return this;
		}

		public Builder memGB(int memGB) {
			this.memGB = memGB;
			return this;
		}

		public Builder inversionsPerBundle(int inversionsPerBundle) {
			this.inversionsPerBundle = inversionsPerBundle;
			return this;
		}

		public Builder queue(String queue) {
			this.queue = queue;
			return this;
		}

		public Builder followUpQueue(String followUpQueue) {
			this.followUpQueue = followUpQueue;
			return this;
		}

		public Builder jarFileName(String jarFileName) {
			this.jarFileName = jarFileName;
			return this;
		}

		public Builder exclusive(boolean exclusive) {
			this.exclusive = exclusive;
			return this;
		}

		public HPCConfig build() {
			Preconditions.checkNotNull(localMainDir, "localMainDir is required");
			Preconditions.checkNotNull(remoteMainDir, "remoteMainDir is required");
			Preconditions.checkArgument(nodes > 0, "nodes must be > 0");
			Preconditions.checkArgument(threadsPerNode > 0, "threadsPerNode must be > 0");
			Preconditions.checkArgument(memGB > 0, "memGB must be > 0");
			Preconditions.checkArgument(inversionsPerBundle > 0, "inversionsPerBundle must be > 0");
			Preconditions.checkNotNull(jarFileName, "jarFileName is required");
			return new HPCConfig(this);
		}
	}
}
