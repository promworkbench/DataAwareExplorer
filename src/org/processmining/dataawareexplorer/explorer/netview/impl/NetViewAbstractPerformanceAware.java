/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import java.awt.Color;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.math3.stat.StatUtils;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.model.XEvent;
import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.netview.ModelDecorationDataImpl;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter.DecorationKey;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Arc;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.xesalignmentextension.XAlignmentExtension.MoveType;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignmentMove;
import org.processmining.xesalignmentextension.XDataAlignmentExtension;
import org.processmining.xesalignmentextension.XDataAlignmentExtension.DataMoveType;
import org.processmining.xesalignmentextension.XDataAlignmentExtension.XDataAlignmentMove;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;
import com.google.common.primitives.Doubles;

abstract class NetViewAbstractPerformanceAware extends NetViewAbstractAlignmentAware {

	protected enum LabelMode {
		BOTH("<FONT POINT-SIZE=\"26\">{0} ({1})</FONT>"), //
		FIRST("<FONT POINT-SIZE=\"26\">{0}</FONT>"), //
		SECOND("<FONT POINT-SIZE=\"26\">{1}</FONT>"), //
		NONE("");

		private final String labelPattern;

		LabelMode(String labelPattern) {
			this.labelPattern = labelPattern;
		}

		public String getLabelPattern() {
			return labelPattern;
		}

	}

	protected enum TimeResolution {

		MILLISECONDS(1l, "ms"), //
		SECONDS(1000l, "s"), //
		MINUTES(1000l * 60l, "m"), // 
		HOURS(1000l * 60l * 60l, "h"), //
		DAYS(1000l * 60l * 60l * 24l, "d"), //
		YEARS(1000l * 60l * 60l * 24l * 365l, "y");

		private final long factor;
		private final String unit;

		private TimeResolution(long millisecondFactor, String unit) {
			this.factor = millisecondFactor;
			this.unit = unit;
		}

		public long getFactor() {
			return factor;
		}

		public String getUnit() {
			return unit;
		}

		public String toString() {
			return getUnit();
		}

	}

	protected enum PerformanceMode {
		FREQUENCY("Frequency"), //
		LOCAL_PERCENTAGE("Percentage (local)"), //
		TRACE_PERCENTAGE("Percentage (trace)"), //
		TIME_AVERAGE("Average time"), // 
		TIME_MAX("Maximum time"), // 
		TIME_MIN("Minimum time"), //
		TIME_MEDIAN("Median time"), //
		TIME_1ST_QUARTILE("1st quartile time"), //
		TIME_3RD_QUARTILE("3rd quartile time"), NONE("None");

		private final String desc;

		private PerformanceMode(String desc) {
			this.desc = desc;
		}

		public String toString() {
			return desc;
		}

	}

	protected final static class PerformanceStatistics {

		final Multiset<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> edgeMoves = ConcurrentHashMultiset
				.create();
		final Multiset<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> edgeCorrectMovesMultiset = ConcurrentHashMultiset
				.create();
		final Multiset<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> edgeInCorrectMovesMultiset = ConcurrentHashMultiset
				.create();
		final Multiset<Place> placeMoves = ConcurrentHashMultiset.create();
		final Multiset<Transition> transitionMoves = ConcurrentHashMultiset.create();

		//todo replace by map together with multiset
		final ListMultimap<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>, Long> waitingTime = Multimaps
				.synchronizedListMultimap(
						ArrayListMultimap.<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>, Long>create());
		AtomicInteger numTraces = new AtomicInteger();

	}

	public NetViewAbstractPerformanceAware(ExplorerContext explorerContext, ExplorerUpdater updater,
			ExplorerModel explorerInput) {
		super(explorerContext, updater, explorerInput);
	}

	protected double computeGlobalRelativeFrequency(double maxValue, double value) {
		if (maxValue == 0.0d) {
			return 1.0;
		}
		switch (getTransformationMode()) {
			case LINEAR :
				return value / maxValue;
			case LOG :
				return Math.log(value) / Math.log(maxValue);
			case SQRT :
			default :
				return Math.sqrt(value) / Math.sqrt(maxValue);
		}
	}

	protected TransformationMode getTransformationMode() {
		return TransformationMode.LINEAR;
	}

	protected PerformanceStatistics calculatePerformanceStats(DataPetriNet netView, Iterable<XAlignment> alignments) {

		final PerformanceStatistics statistics = new PerformanceStatistics();

		Iterable<List<XAlignment>> partitionedAlignments = Iterables.partition(alignments, 10000);
		List<Callable<Void>> callables = new ArrayList<>();
		for (final List<XAlignment> subAlignments : partitionedAlignments) {
			callables.add(new Callable<Void>() {

				public Void call() throws Exception {
					for (XAlignment a : subAlignments) {
						computeForAlignment(a, statistics);
						statistics.numTraces.incrementAndGet();
					}
					return null;
				}
			});
		}
		try {
			explorerContext.getExecutor().invokeAll(callables);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return statistics;
	}

	private final void computeForAlignment(XAlignment alignment, PerformanceStatistics statistics) {

		long currentTime = getFirstTime(alignment);

		Map<Place, Long> timePlaceMarked = new HashMap<>();
		// All initial places were marked directly before the first event occurred
		for (Place p : explorerModel.getAlignmentConfiguration().getInitialMarking()) {
			timePlaceMarked.put(p, currentTime);
		}

		for (XAlignmentMove move : alignment) {

			XDataAlignmentMove dataMove = XDataAlignmentExtension.instance().extendXAlignmentMove(move);
			MoveType moveType = dataMove.getType();

			if (moveType != MoveType.LOG) {

				// Model move
				String transitionId = move.getActivityId();

				Collection<? extends PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = explorerModel
						.getInEdgeMap().get(transitionId);
				statistics.edgeMoves.addAll(inEdges);
				Collection<? extends PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = explorerModel
						.getOutEdgeMap().get(transitionId);
				statistics.edgeMoves.addAll(outEdges);

				if (isSynchronousDataMove(moveType, dataMove.getDataMoveType()) || !move.isObservable()) {
					statistics.edgeCorrectMovesMultiset.addAll(inEdges);
					statistics.edgeCorrectMovesMultiset.addAll(outEdges);
				} else {
					// Model move or move with wrong data
					statistics.edgeInCorrectMovesMultiset.addAll(inEdges);
					statistics.edgeInCorrectMovesMultiset.addAll(outEdges);
				}

				Transition transition = explorerModel.getTransitionsLocalId().get(transitionId);
				statistics.transitionMoves.add(transition);

				for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> inEdge : inEdges) {
					statistics.placeMoves.add((Place) inEdge.getSource());
				}

				if (move.getType() == MoveType.SYNCHRONOUS) {
					Date time = XTimeExtension.instance().extractTimestamp(move.getEvent());

					if (time != null) {

						for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> inEdge : inEdges) {
							Long timeInputPlaceLastMarked = timePlaceMarked.get(inEdge.getSource());
							if (timeInputPlaceLastMarked != null) {
								statistics.waitingTime.put(inEdge, time.getTime() - timeInputPlaceLastMarked);
							} else {
								throw new IllegalStateException(
										"Missing time information for input place " + inEdge.getSource().getLabel());
							}
						}
						currentTime = time.getTime();
					}
				}

				// Ignore other moves, as we don't know the time but remember when the output places were marked
				for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> outEdge : outEdges) {
					timePlaceMarked.put((Place) outEdge.getTarget(), currentTime);
				}
			}
		}
	}

	private final long getFirstTime(XAlignment a) {
		for (XAlignmentMove move : a) {
			if (move.getType() == MoveType.SYNCHRONOUS) { // Data does not matter here
				XEvent activityObject = move.getEvent();
				Date time = XTimeExtension.instance().extractTimestamp(activityObject);
				if (time != null) {
					return time.getTime();
				}
			}
		}
		return 0;
	}

	private static boolean isSynchronousDataMove(MoveType moveType, DataMoveType dataMoveType) {
		return moveType == MoveType.SYNCHRONOUS && dataMoveType == DataMoveType.CORRECT;
	}

	protected void addFrequencies(DataPetriNet netView, ModelDecorationDataImpl modelDecoration,
			Iterable<XAlignment> alignments) {
		addFrequencies(netView, modelDecoration, alignments, LabelMode.BOTH);
	}

	protected void addFrequencies(DataPetriNet model, ModelDecorationDataImpl modelDecoration,
			Iterable<XAlignment> alignments, LabelMode labelMode) {

		PerformanceStatistics statistics = calculatePerformanceStats(model, alignments);
		hideUnobseredPaths(model, modelDecoration, statistics);
		addMeasures(model, modelDecoration, statistics, labelMode, PerformanceMode.FREQUENCY,
				PerformanceMode.LOCAL_PERCENTAGE, PerformanceMode.FREQUENCY);
	}

	protected void addMeasures(DataPetriNet model, ModelDecorationDataImpl modelDecoration,
			PerformanceStatistics statistics, LabelMode labelMode, PerformanceMode edgeMeasureMode,
			PerformanceMode firstMeasureMode, PerformanceMode secondMeasureMode) {

		for (Entry<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> edgeEntry : statistics.edgeMoves
				.entrySet()) {

			Double maxCount = calculateMaxEdgeMeasure(model, edgeMeasureMode, statistics);
			Double edgeMeasure = calculateMeasure(edgeEntry.getElement(), statistics, edgeMeasureMode);

			if (edgeMeasure != null) {
				float edgeWidth = calcEdgeWidth(maxCount, edgeMeasure.doubleValue());
				modelDecoration.putAttribute(edgeEntry.getElement(), DecorationKey.LINEWIDTH, edgeWidth);
			} else {
				modelDecoration.putAttribute(edgeEntry.getElement(), DecorationKey.LINEWIDTH,
						explorerModel.getMinLineWidth());
			}

			String firstMeasureStr = "";
			String secondMeasureStr = "";

			if (isFrequencyEdge(edgeEntry.getElement(), firstMeasureMode)) {
				Double firstMeasure = calculateMeasure(edgeEntry.getElement(), statistics, firstMeasureMode);
				firstMeasureStr = formatAsString(firstMeasureMode, firstMeasure);
			}

			if (isFrequencyEdge(edgeEntry.getElement(), secondMeasureMode)) {
				Double secondMeasure = calculateMeasure(edgeEntry.getElement(), statistics, secondMeasureMode);
				secondMeasureStr = formatAsString(secondMeasureMode, secondMeasure);
			}

			modelDecoration.putAttribute(edgeEntry.getElement(), DecorationKey.EXTRALABEL,
					createEdgeLabel(labelMode, firstMeasureStr, secondMeasureStr));

		}

	}

	private String formatAsString(PerformanceMode mode, Double measure) {
		if (measure == null) {
			return "";
		}
		switch (mode) {
			case FREQUENCY :
				return MessageFormat.format("{0,number}", measure);
			case TRACE_PERCENTAGE :
				return MessageFormat.format("{0,number,#.#%}", measure);
			case LOCAL_PERCENTAGE :
				return MessageFormat.format("{0,number,#.#%}", measure);
			case TIME_1ST_QUARTILE :
			case TIME_3RD_QUARTILE :
			case TIME_AVERAGE :
			case TIME_MAX :
			case TIME_MEDIAN :
			case TIME_MIN :
				if (measure == 0) {
					return "";
				}
				TimeResolution resolution = determineBestResolution(measure);
				return MessageFormat.format("{0,number,#.#} {1}", measure / resolution.getFactor(),
						resolution.getUnit());
			case NONE :
				return "";
			default :
				throw new RuntimeException("Failed to format measure " + measure);
		}
	}

	private final TimeResolution determineBestResolution(double value) {
		if (value < (TimeResolution.SECONDS.getFactor())) {
			return TimeResolution.MILLISECONDS;
		} else if (value < (TimeResolution.MINUTES.getFactor())) {
			return TimeResolution.SECONDS;
		} else if (value < (TimeResolution.HOURS.getFactor())) {
			return TimeResolution.MINUTES;
		} else if (value < (TimeResolution.DAYS.getFactor())) {
			return TimeResolution.HOURS;
		} else if (value < (TimeResolution.YEARS.getFactor())) {
			return TimeResolution.DAYS;
		} else {
			return TimeResolution.YEARS;
		}
	}

	private Double calculateMaxEdgeMeasure(DataPetriNet model, PerformanceMode edgeMeasureMode,
			PerformanceStatistics statistics) {
		switch (edgeMeasureMode) {
			case FREQUENCY :
				//TODO avoid copy
				return Double.valueOf(
						Multisets.copyHighestCountFirst(statistics.edgeMoves).entrySet().iterator().next().getCount());
			case TRACE_PERCENTAGE :
				//TODO might be more than 100%
				return 1.0;
			case LOCAL_PERCENTAGE :
				return 1.0;
			case TIME_1ST_QUARTILE :
			case TIME_3RD_QUARTILE :
			case TIME_AVERAGE :
			case TIME_MAX :
			case TIME_MEDIAN :
			case TIME_MIN :
				double maxValue = 0;
				for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : model.getEdges()) {
					List<Long> waitingTimes = statistics.waitingTime.get(edge);
					if (!waitingTimes.isEmpty()) {
						Double value = computeValue(edgeMeasureMode, waitingTimes);
						maxValue = Math.max(maxValue, value);
					}
				}
				return maxValue;
			case NONE :
				return null;
			default :
				throw new RuntimeException("Failed to compute measure " + edgeMeasureMode);
		}
	}

	private Double calculateMeasure(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge,
			PerformanceStatistics statistics, PerformanceMode measure) {
		switch (measure) {
			case FREQUENCY :
				return Double.valueOf((statistics.edgeCorrectMovesMultiset.count(edge)
						+ statistics.edgeInCorrectMovesMultiset.count(edge)));

			case TRACE_PERCENTAGE :
				return Double.valueOf((statistics.edgeCorrectMovesMultiset.count(edge)
						+ statistics.edgeInCorrectMovesMultiset.count(edge)) / (double) statistics.numTraces.get());

			case LOCAL_PERCENTAGE :
				int totalMovesSourcePlace = statistics.placeMoves.count(edge.getSource());
				return Double.valueOf(Double.valueOf((statistics.edgeCorrectMovesMultiset.count(edge)
						+ statistics.edgeInCorrectMovesMultiset.count(edge)) / (double) totalMovesSourcePlace));

			case TIME_1ST_QUARTILE :
			case TIME_3RD_QUARTILE :
			case TIME_AVERAGE :
			case TIME_MAX :
			case TIME_MEDIAN :
			case TIME_MIN :
				List<Long> waitingTimes = statistics.waitingTime.get(edge);
				if (!waitingTimes.isEmpty()) {
					return computeValue(measure, waitingTimes);
				} else {
					return null;
				}
			case NONE :
				return null;
			default :
				throw new RuntimeException("Failed to compute measure " + measure);
		}
	}

	protected void hideUnobseredPaths(DataPetriNet netView, ModelDecorationDataImpl modelDecoration,
			PerformanceStatistics statistics) {
		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : netView.getEdges()) {
			if (edge instanceof Arc) {
				if (statistics.edgeMoves.count(edge) == 0) {
					modelDecoration.putAttribute(edge, DecorationKey.LINECOLOR, Color.LIGHT_GRAY);
					PetrinetNode source = edge.getSource();
					PetrinetNode target = edge.getTarget();
					int countSource;
					int countTarget;
					if (source instanceof Place) {
						countSource = statistics.placeMoves.count(source);
						countTarget = statistics.transitionMoves.count(target);
					} else {
						countSource = statistics.transitionMoves.count(source);
						countTarget = statistics.placeMoves.count(target);
					}
					if (countSource == 0) {
						modelDecoration.putAttribute(source, DecorationKey.LINECOLOR, Color.LIGHT_GRAY);
						modelDecoration.putAttribute(source, DecorationKey.TEXTCOLOR, Color.LIGHT_GRAY);
						if (source instanceof Transition && ((Transition) source).isInvisible()) {
							modelDecoration.putAttribute(source, DecorationKey.FILLCOLOR, Color.LIGHT_GRAY);
						}
					}
					if (countTarget == 0) {
						modelDecoration.putAttribute(target, DecorationKey.LINECOLOR, Color.LIGHT_GRAY);
						modelDecoration.putAttribute(target, DecorationKey.TEXTCOLOR, Color.LIGHT_GRAY);
						if (target instanceof Transition && ((Transition) target).isInvisible()) {
							modelDecoration.putAttribute(target, DecorationKey.FILLCOLOR, Color.LIGHT_GRAY);
						}
					}
				}
			}
		}
	}

	protected boolean isFrequencyEdge(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge,
			PerformanceMode mode) {
		PetrinetNode sourceNode = edge.getSource();
		Marking initialMarking = explorerModel.getAlignmentConfiguration().getInitialMarking();
		boolean isFrequencyEdge = sourceNode instanceof Place && (isSplitPlace((Place) sourceNode)
				|| initialMarking.contains(sourceNode) || nextTransitionIsANDSplit((Place) sourceNode));
		switch (mode) {
			case FREQUENCY :
			case LOCAL_PERCENTAGE :
			case TRACE_PERCENTAGE :
				return isFrequencyEdge;
			case TIME_1ST_QUARTILE :
			case TIME_3RD_QUARTILE :
			case TIME_AVERAGE :
			case TIME_MAX :
			case TIME_MEDIAN :
			case TIME_MIN :
			case NONE :
			default :
				return true;
		}
	}

	private boolean nextTransitionIsANDSplit(Place place) {
		if (place.getGraph().getOutEdges(place).size() == 1) {
			PetrinetNode target = place.getGraph().getOutEdges(place).iterator().next().getTarget();
			Transition next = (Transition) target;
			if (next.getGraph().getOutEdges(next).size() > 1) {
				return true;
			}
		}
		return false;
	}

	protected final float calcEdgeWidth(double maxValue, double count) {
		if (maxValue == 0) {
			return explorerModel.getMinLineWidth();
		} else {
			return (float) (explorerModel.getMinLineWidth()
					+ computeGlobalRelativeFrequency(maxValue, count) * explorerModel.getMaxLineWidth());
		}
	}

	protected final String createEdgeLabel(LabelMode labelMode, String firstMeasure, String secondMeasure) {
		if (firstMeasure.isEmpty() && secondMeasure.isEmpty()) {
			return "";
		} else if (firstMeasure.isEmpty()) {
			return MessageFormat.format(LabelMode.SECOND.getLabelPattern(), firstMeasure, secondMeasure);
		} else if (secondMeasure.isEmpty()) {
			return MessageFormat.format(LabelMode.FIRST.getLabelPattern(), firstMeasure, secondMeasure);
		} else {
			return MessageFormat.format(labelMode.getLabelPattern(), firstMeasure, secondMeasure);
		}
	}

	private boolean isSplitPlace(Place place) {
		return place.getGraph().getOutEdges(place).size() > 1;
	}

	protected final Double computeValue(PerformanceMode mode, Collection<Long> times) {
		switch (mode) {
			case TIME_3RD_QUARTILE :
				return thirdQuartile(times);
			case TIME_1ST_QUARTILE :
				return firstQuartile(times);
			case TIME_MEDIAN :
				return median(times);
			case TIME_AVERAGE :
				return average(times);
			case TIME_MAX :
				return max(times);
			case TIME_MIN :
				return min(times);
			default :
				return average(times);
		}
	}

	private final double thirdQuartile(Collection<Long> times) {
		double[] waitingTimes = Doubles.toArray(times);
		return StatUtils.percentile(waitingTimes, 75d);
	}

	private final double firstQuartile(Collection<Long> times) {
		double[] waitingTimes = Doubles.toArray(times);
		return StatUtils.percentile(waitingTimes, 25d);
	}

	private final double median(Collection<Long> times) {
		double[] waitingTimes = Doubles.toArray(times);
		return StatUtils.percentile(waitingTimes, 50d);
	}

	private final double min(Collection<Long> times) {
		double[] waitingTimes = Doubles.toArray(times);
		return StatUtils.min(waitingTimes);
	}

	private final double max(Collection<Long> times) {
		double[] waitingTimes = Doubles.toArray(times);
		return StatUtils.max(waitingTimes);
	}

	private double average(Collection<Long> times) {
		double[] waitingTimes = Doubles.toArray(times);
		return StatUtils.mean(waitingTimes);
	}

}