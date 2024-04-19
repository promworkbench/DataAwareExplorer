package org.processmining.dataawareexplorer.explorer.work;

public final class VariableStatistics {

	public long numGoodWriteOps;
	public long numWrongWriteOps;
	public long numMissingWriteOps;

	public long getObservedWrites() {
		return numGoodWriteOps + numWrongWriteOps + numMissingWriteOps;
	}
	
	public void incGoodWriteOps() {
		numGoodWriteOps++;
	}

	public void incWrongWriteOps() {
		numWrongWriteOps++;
	}

	public void incMissingWriteOps() {
		numMissingWriteOps++;
	}

}