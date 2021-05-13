package org.opensha.commons.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class DeadlockDetectionThread extends Thread {
	
	private long checkMillis;
	private ThreadMXBean tmx;
	
	private boolean killed = false;
	
	public DeadlockDetectionThread(long checkMillis) {
		this.checkMillis = checkMillis;
		this.setDaemon(true);
	}

	@Override
	public void run() {
		killed = false;
		if (tmx == null)
			tmx = ManagementFactory.getThreadMXBean();
		
		while (!killed) {
//			System.out.print("Checking for deadlock...");
			long[] ids = tmx.findDeadlockedThreads();
//			System.out.println("DONE.");
			if (ids != null) {
				ThreadInfo[] infos = tmx.getThreadInfo(ids, true, true);
				System.out.println("The following threads are deadlocked:");
				for (ThreadInfo ti : infos) {
					System.out.println(ti);
				}
			}
			
			try {
				Thread.sleep(checkMillis);
			} catch (InterruptedException e) {}
		}
	}
	
	public void kill() {
		killed = true;
	}

}
