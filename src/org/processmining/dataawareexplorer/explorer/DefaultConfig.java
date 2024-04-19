package org.processmining.dataawareexplorer.explorer;

import com.google.common.collect.ImmutableSet;

public final class DefaultConfig {

	public static final int INCORRECT_COST = 1;
	public static final int MISSING_COST = 1;
	public static final int LOG_MOVE_COST = 3;
	public static final int MODEL_MOVE_COST = 2;

	public static final ImmutableSet<String> SOURCE_PLACES = ImmutableSet.of("source", "start", "initial", "init",
			"src");
	public static final ImmutableSet<String> SINK_PLACES = ImmutableSet.of("sink", "end", "final", "snk");

	private DefaultConfig() {
		super();
	}

}