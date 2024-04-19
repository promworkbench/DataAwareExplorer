package org.processmining.dataawareexplorer.explorer.work;

public final class TransitionStatistics {

	public long numGoodMoves;
	public long numModelMoves;
	public long numDataMoves;

	public long numGoodWriteOps;
	public long numWrongWriteOps;
	public long numMissingWriteOps;

	public long numGuardViolations;

	public long getObservedMoves() {
		return numGoodMoves + numModelMoves + numDataMoves;
	}
	
	public long getObservedWrites() {
		return numGoodWriteOps + numWrongWriteOps + numMissingWriteOps;
	}
	
	public void incGoodMoves() {
		numGoodMoves++;
	}

	public void incModelMoves() {
		numModelMoves++;
	}

	public void incDataMoves() {
		numDataMoves++;
	}
	
	public void incGuardViolations() {
		numGuardViolations ++;
	}

	public void incGoodWriteOps(long increment) {
		numGoodWriteOps += increment;
	}

	public void incWrongWriteOps(long increment) {
		numWrongWriteOps += increment;
	}

	public void incMissingWriteOps(long increment) {
		numMissingWriteOps += increment;
	}

	public double getGuardViolations() {
		if (numGuardViolations > 0) {
			return (numGuardViolations) / (double) getObservedMoves();
		} else {
			return 0.0d;
		}
	}

}