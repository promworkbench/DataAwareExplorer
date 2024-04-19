package org.processmining.dataawareexplorer.explorer.work;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

public final class PlaceStatistics {

	public long numGoodMoves;
	public long numModelMoves;
	public long numLogMoves;
	public long numDataMoves;

	public long numGoodWriteOps;
	public long numWrongWriteOps;
	public long numMissingWriteOps;

	public long numGoodWritesForGuard;
	public long numWrongWritesForGuard;
	public long numMissingWritesForGuard;

	public Multiset<String> observedLogMoves = HashMultiset.create();

	public long getObservedMoves() {
		return numGoodMoves + numLogMoves + numModelMoves + numDataMoves;
	}

	public long getObservedWrites() {
		return numGoodWriteOps + numWrongWriteOps + numMissingWriteOps;
	}

	public double getLocalEventViolations() {
		if (getObservedMoves() > 0) {
			return (numModelMoves + numLogMoves + numDataMoves) / (double) getObservedMoves();
		} else {
			return 0.0;
		}
	}

	public double getLocalDataViolations() {
		if (getObservedWrites() > 0) {
			return (numWrongWriteOps + numMissingWriteOps) / (double) getObservedWrites();
		} else {
			return 0.0;
		}
	}

	public void incLogMoves() {
		numLogMoves++;
	}

	public void incGoodMoves() {
		numGoodMoves++;
	}

	public void incModelMoves() {
		numModelMoves++;
	}

	public void incMissingWriteOps(long missingWriteOps) {
		numMissingWriteOps += missingWriteOps;
	}

	public void incDataMoves() {
		numDataMoves++;
	}

	public void incWrongWriteOps(long incorrectWritings) {
		numWrongWriteOps += incorrectWritings;
	}

	public void incGoodWriteOps(long correctWritings) {
		numGoodWriteOps += correctWritings;
	}

	public void addLogMove(String string) {
		observedLogMoves.add(string);
	}

}