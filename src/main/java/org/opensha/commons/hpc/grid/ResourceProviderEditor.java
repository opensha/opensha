package org.opensha.commons.hpc.grid;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class ResourceProviderEditor extends JDialog {
	
	JTextField name = new JTextField();
	JTextField hostName = new JTextField();
	JTextField batchScheduler = new JTextField();
	JTextField forkScheduler = new JTextField();
	JTextField javaPath = new JTextField();
	JTextField storagePath = new JTextField();
	JTextField requirements = new JTextField();
	JTextField gridFTPHost = new JTextField();
	JTextField universe = new JTextField();
	JTextField queue = new JTextField();
	
	ResourceProvider rp;
	
	public ResourceProviderEditor(ResourceProvider rp) {
		this.rp = rp;
		ArrayList<JComponent> leftCol = new ArrayList<JComponent>();
		ArrayList<JComponent> rightCol = new ArrayList<JComponent>();
		
		leftCol.add(new JLabel("Name"));
		name.setText(rp.getName());
		rightCol.add(name);
		
		leftCol.add(new JLabel("Host Name"));
		hostName.setText(rp.getHostName());
		rightCol.add(hostName);
		
		leftCol.add(new JLabel("Batch Scheduler"));
		batchScheduler.setText(rp.getBatchScheduler());
		rightCol.add(batchScheduler);
		
		leftCol.add(new JLabel("Fork Scheduler"));
		forkScheduler.setText(rp.getForkScheduler());
		rightCol.add(forkScheduler);
		
		leftCol.add(new JLabel("Java Path"));
		javaPath.setText(rp.getJavaPath());
		rightCol.add(javaPath);
		
		leftCol.add(new JLabel("Storage Path"));
		storagePath.setText(rp.getStoragePath());
		rightCol.add(storagePath);
		
		leftCol.add(new JLabel("Requirements"));
		requirements.setText(rp.getRequirements());
		rightCol.add(requirements);
		
		leftCol.add(new JLabel("GridFTP Host Name"));
		gridFTPHost.setText(rp.getGridFTPHost());
		rightCol.add(gridFTPHost);
		
		leftCol.add(new JLabel("Condor Universe"));
		universe.setText(rp.getUniverse());
		rightCol.add(universe);
		
		this.createGUI(leftCol, rightCol);
		this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		this.setTitle("Resource Provider");
//		this.pack();
	}
	
	public void createGUI(ArrayList<JComponent> leftCol, ArrayList<JComponent> rightCol) {
		int num = leftCol.size();
		
		this.setLayout(new GridLayout(num, 2));
		
		for (int i=0; i<leftCol.size(); i++) {
			JComponent left = leftCol.get(i);
			JComponent right = rightCol.get(i);
			
			this.add(left);
			this.add(right);
		}
		
		this.setPreferredSize(new Dimension(200, 500));
		this.setSize(new Dimension(500, 300));
	}
	
	public ResourceProvider getResourceProvider() {
		int wallTime = rp.getGlobusRSL().getMaxWallTime();
		String queue = this.queue.getText();
		GlobusRSL rsl = new GlobusRSL(GlobusRSL.SINGLE_JOB_TYPE, wallTime);
		if (queue.length() > 0)
			rsl.setQueue(queue);
		
		String name = this.name.getText();
		String hostName = this.hostName.getText();
		String batchScheduler = this.batchScheduler.getText();
		String forkScheduler = this.forkScheduler.getText();
		String javaPath = this.javaPath.getText();
		String storagePath = this.storagePath.getText();
		String requirements = this.requirements.getText();
		String gridFTPHost = this.gridFTPHost.getText();
		String universe = this.universe.getText();
		
		return new ResourceProvider(name, hostName, batchScheduler, forkScheduler,
				javaPath, storagePath, requirements,
				gridFTPHost, universe, rsl);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ResourceProvider rp = ResourceProvider.ABE_GLIDE_INS();
		ResourceProviderEditor editor = new ResourceProviderEditor(rp);
		
		editor.setVisible(true);

	}

}
