package org.processmining.dataawareexplorer.explorer.work;

import java.util.HashMap;
import java.util.Map;

import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.processmining.datapetrinets.DataPetriNetsWithMarkings;
import org.processmining.datapetrinets.exception.EvaluatorException;
import org.processmining.datapetrinets.exception.UnsupportedFunctionException;
import org.processmining.datapetrinets.exception.VariableNotFoundException;
import org.processmining.datapetrinets.expression.GuardExpression;
import org.processmining.datapetrinets.expression.VariableProvider;
import org.processmining.log.utils.XUtils;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataElement;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PNWDTransition;
import org.processmining.models.semantics.petrinet.EfficientPetrinetSemantics;
import org.processmining.models.semantics.petrinet.EfficientPetrinetSemantics.PlaceVisitor;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.impl.EfficientPetrinetSemanticsImpl;
import org.processmining.xesalignmentextension.XAlignmentExtension.MoveType;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignmentMove;
import org.processmining.xesalignmentextension.XDataAlignmentExtension;
import org.processmining.xesalignmentextension.XDataAlignmentExtension.DataMoveType;
import org.processmining.xesalignmentextension.XDataAlignmentExtension.XDataAlignmentExtensionException;

public final class AlignmentInformation {

	private static final class AlignmentInfoState {

		private final Map<String, Transition> transitionsLocalId;
		private final EfficientPetrinetSemantics semantics;

		private final Map<String, Object> logViewNormalVariables = new HashMap<String, Object>();
		private final Map<String, Object> logViewPrimeVariables = new HashMap<String, Object>();

		private final byte[] initialMarkingAsIntArray;

		private AlignmentInfoState(PetrinetGraph net, Marking initialMarking,
				Map<String, Transition> transitionsLocalId) {
			super();
			this.transitionsLocalId = transitionsLocalId;
			this.semantics = new EfficientPetrinetSemanticsImpl(net, initialMarking);
			this.initialMarkingAsIntArray = semantics.getState();
		}

		public void reset() {
			// Reset the state to the initial marking
			semantics.setState(initialMarkingAsIntArray);

			// Reset variable state
			logViewNormalVariables.clear();
			logViewPrimeVariables.clear();
		}

		public EfficientPetrinetSemantics getSemantics() {
			return semantics;
		}

		public Map<String, Transition> getTransitionsLocalId() {
			return transitionsLocalId;
		}

		public Map<String, Object> getLogViewVariables() {
			return logViewNormalVariables;
		}

		public Map<String, Object> getLogViewPrimeVariables() {
			return logViewPrimeVariables;
		}

	}

	public final double averageFitness;

	public long numGoodMoves = 0;
	public long numLogMoves = 0;
	public long numModelMoves = 0;

	public long numGoodWriteOperations = 0;
	public long numWrongWriteOperations = 0;
	public long numMissingWriteOperations = 0;

	public final Map<Transition, TransitionStatistics> transitionStatistics;
	public final Map<Place, PlaceStatistics> placeStatistics;
	public final Map<DataElement, VariableStatistics> variableStatistics;

	public final double eventViolations;
	public final double dataViolations;
	public final double overallViolations;

	public AlignmentInformation(DataPetriNetsWithMarkings net, Map<String, Transition> localIdToTransition,
			Map<String, String> variableMapping, Iterable<XAlignment> alignments) {

		this.transitionStatistics = new HashMap<Transition, TransitionStatistics>();
		for (Transition t : net.getTransitions()) {
			transitionStatistics.put(t, new TransitionStatistics());			
		}
		this.placeStatistics = new HashMap<Place, PlaceStatistics>();
		for (Place p : net.getPlaces()) {
			placeStatistics.put(p, new PlaceStatistics());
		}
		this.variableStatistics = new HashMap<DataElement, VariableStatistics>();
		for (DataElement v : net.getVariables()) {
			variableStatistics.put(v, new VariableStatistics());
		}

		AlignmentInfoState state = new AlignmentInfoState(net, net.getInitialMarking(), localIdToTransition);

		int size = 0;
		double sumFitness = 0.0;

		for (final XAlignment alignment : alignments) {

			// Keep track of fitness
			sumFitness += alignment.getFitness();
			size++;

			for (final XAlignmentMove move : alignment) {

				processMoveForStatistics(move, state);
				movePrimeVariables(state);

			}

			state.reset();

		}

		if (size > 0) {
			averageFitness = sumFitness / size;
		} else {
			averageFitness = 1.0;
		}

		long goodEvents = numGoodMoves;
		long wrongEvents = numLogMoves + numModelMoves;
		long allEvents = goodEvents + wrongEvents;
		if (allEvents == 0) {
			eventViolations = 0.0d;
		} else {
			eventViolations = wrongEvents / (double) allEvents;
		}

		long wrongData = numMissingWriteOperations + numWrongWriteOperations;
		long goodData = numGoodWriteOperations;
		long allData = goodData + wrongData;
		if (allData == 0) {
			dataViolations = 0.0d;
		} else {
			dataViolations = wrongData / (double) allData;
		}

		overallViolations = (wrongEvents + wrongData) / (double) (goodEvents + wrongEvents + goodData + wrongData);
	}

	private static void movePrimeVariables(AlignmentInfoState state) {
		state.getLogViewVariables().putAll(state.getLogViewPrimeVariables());
		state.getLogViewPrimeVariables().clear();
	}

	private final void processMoveForStatistics(final XAlignmentMove move, final AlignmentInfoState state) {

		final MoveType moveType = move.getType();

		if (moveType == null) {
			throw new RuntimeException("Could to read alignment:moveType from move "
					+ XUtils.stringifyEvent(move.getEvent()) + "! Alignment is corrupted!");
		}

		if (moveType != MoveType.LOG) {

			final String currentTransitionId = move.getActivityId();
			final PNWDTransition currentTransition = (PNWDTransition) state.getTransitionsLocalId()
					.get(currentTransitionId);
			final TransitionStatistics transitionStats = transitionStatistics.get(currentTransition);
			final XDataAlignmentExtension dataAlignmentExt = XDataAlignmentExtension.instance();

			switch (moveType) {
				case SYNCHRONOUS :
					DataMoveType dataMoveType = dataAlignmentExt.extractDataMoveType(move.getEvent());
					if (dataMoveType != null) {
						handleSynchronousMove(move, state, currentTransition, transitionStats,  dataMoveType);
						if (isGuardViolated(state, currentTransition)) {
							transitionStats.incGuardViolations();
						}
					} else {
						throw new RuntimeException("Could to read dataalignment:dataMoveType from move "
								+ XUtils.stringifyEvent(move.getEvent()) + "! Alignment is corrupted!");
					}
					break;
				case MODEL :
					if (move.isObservable()) {
						handleObservableModelMove(move, state, currentTransition, transitionStats);
					} else {
						handleUnobservableModelMove(move, state, currentTransition, transitionStats);
					}
					if (isGuardViolated(state, currentTransition)) {
						transitionStats.incGuardViolations();
					}
					break;
				case LOG :
				default :
					throw new RuntimeException("Illegal step");
			}

			// Finally execute the transition
			state.getSemantics().directExecuteExecutableTransition(currentTransition);
		} else {
			handleLogMove(state, move);
		}
	}

	private void handleLogMove(final AlignmentInfoState state, final XAlignmentMove move) {
		numLogMoves++;
		state.getSemantics().forEachMarkedPlace(new PlaceVisitor() {

			public void accept(Place place, int numTokens) {
				final PlaceStatistics stats = placeStatistics.get(place);
				stats.incLogMoves();
				stats.addLogMove(move.getEventClassId());
			}
		});
	}

	private void handleUnobservableModelMove(final XAlignmentMove move, final AlignmentInfoState state,
			final PNWDTransition currentTransition, final TransitionStatistics transitionStats) {
		// No write ops possible
		state.getSemantics().forEachMarkedPlace(new PlaceVisitor() {

			public void accept(Place place, int numTokens) {
				final PlaceStatistics stats = placeStatistics.get(place);
				stats.incGoodMoves();
			}
		});
		transitionStats.incGoodMoves();
	}

	private void handleObservableModelMove(final XAlignmentMove move, final AlignmentInfoState state,
			final PNWDTransition currentTransition, final TransitionStatistics transitionStats) {
		// Special case only has missing write ops
		final long missingWritings = countMissingWriteOperations(currentTransition, move);
		numMissingWriteOperations += missingWritings;
		state.getSemantics().forEachMarkedPlace(new PlaceVisitor() {

			public void accept(Place place, int numTokens) {
				final PlaceStatistics stats = placeStatistics.get(place);
				stats.incModelMoves();
				stats.incMissingWriteOps(missingWritings);

			}
		});
		transitionStats.incModelMoves();
		transitionStats.incMissingWriteOps(missingWritings);
		numModelMoves++;
	}

	private int countMissingWriteOperations(PNWDTransition currentTransition, XAlignmentMove move) {
		int count = 0;
		if (!currentTransition.getWriteOperations().isEmpty()) {
			XDataAlignmentExtension alignmentExtension = XDataAlignmentExtension.instance();
			for (XAttribute a : move.getEvent().getAttributes().values()) {
				try {
					if (alignmentExtension.isMissingAttribute(a)) {
						count++;
					}
				} catch (XDataAlignmentExtensionException e) {
					throw new RuntimeException("Invalid alignment", e);
				}
			}
		}
		return count;
	}

	private void handleSynchronousMove(final XAlignmentMove move, final AlignmentInfoState state,
			final PNWDTransition currentTransition, final TransitionStatistics transitionStats,
			DataMoveType dataMoveType) {

		// Might contain a combination of correct, incorrect, missing writes
		Map<String, Object> logPrimeVariables = state.getLogViewPrimeVariables();

		int correctWritings = 0;
		int incorrectWritings = 0;
		int missingWritings = 0;

		XDataAlignmentExtension alignmentExtension = XDataAlignmentExtension.instance();
		XAttributeMap attributes = move.getEvent().getAttributes();
		for (DataElement var: currentTransition.getWriteOperations()) {
			VariableStatistics variableStats = variableStatistics.get(var);
			XAttribute a = attributes.get(var.getVarName());
			try {
				if (alignmentExtension.isCorrectAttribute(a)) {
					correctWritings++;
					variableStats.incGoodWriteOps();
					logPrimeVariables.put(a.getKey(), XUtils.getAttributeValue(a));
				} else if (alignmentExtension.isMissingAttribute(a)) {
					missingWritings++;
					variableStats.incMissingWriteOps();
					// we treat missing as a real missing value not taking the guessed value from the alignment
				} else if (alignmentExtension.isIncorrectAttribute(a)) {
					incorrectWritings++;
					variableStats.incWrongWriteOps();
					XAttribute logValue = alignmentExtension.extractLogValue(a);
					logPrimeVariables.put(a.getKey(), XUtils.getAttributeValue(logValue));
				}
			} catch (XDataAlignmentExtensionException e) {
				throw new RuntimeException("Invalid alignment", e);
			}
		}

		numGoodWriteOperations += correctWritings;
		numMissingWriteOperations += missingWritings;
		numWrongWriteOperations += incorrectWritings;

		transitionStats.incGoodWriteOps(correctWritings);
		transitionStats.incMissingWriteOps(missingWritings);
		transitionStats.incWrongWriteOps(incorrectWritings);

		state.getSemantics()
				.forEachMarkedPlace(new WriteOpPlaceVisitor(missingWritings, incorrectWritings, correctWritings));

		switch (dataMoveType) {
			case CORRECT :
				state.getSemantics().forEachMarkedPlace(new PlaceVisitor() {

					public void accept(Place place, int numTokens) {
						final PlaceStatistics stats = placeStatistics.get(place);
						stats.incGoodMoves();
					}
				});
				transitionStats.incGoodMoves();
				numGoodMoves++;
				break;
			case INCORRECT :
				state.getSemantics().forEachMarkedPlace(new PlaceVisitor() {

					public void accept(Place place, int numTokens) {
						final PlaceStatistics stats = placeStatistics.get(place);
						stats.incDataMoves();
					}
				});
				transitionStats.incDataMoves();
				numGoodMoves++;
				break;
			case MISSING :
				state.getSemantics().forEachMarkedPlace(new PlaceVisitor() {

					public void accept(Place place, int numTokens) {
						final PlaceStatistics stats = placeStatistics.get(place);
						stats.incDataMoves();
					}
				});
				transitionStats.incDataMoves();
				numGoodMoves++;
				break;
			default :
				break;
		}
	}

	private final class WriteOpPlaceVisitor implements PlaceVisitor {

		private final int finalMissingWritings;
		private final int finalIncorrectWritings;
		private final int finalCorrectWritings;

		private WriteOpPlaceVisitor(int finalMissingWritings, int finalIncorrectWritings, int finalCorrectWritings) {
			this.finalMissingWritings = finalMissingWritings;
			this.finalIncorrectWritings = finalIncorrectWritings;
			this.finalCorrectWritings = finalCorrectWritings;
		}

		public void accept(Place place, int numTokens) {
			final PlaceStatistics stats = placeStatistics.get(place);
			stats.incGoodWriteOps(finalCorrectWritings);
			stats.incWrongWriteOps(finalIncorrectWritings);
			stats.incMissingWriteOps(finalMissingWritings);
		}
	}

	private boolean isGuardViolated(final AlignmentInfoState state, final PNWDTransition transition) {
		if (transition.hasGuardExpression()) {
			GuardExpression expression = transition.getGuardExpression();
			if (allVariablesAvailable(state, expression)) {
				boolean violated;
				try {
					violated = expression.isFalse(new VariableProvider() {

						public Object getValue(String variableName) throws VariableNotFoundException {
							Object value;
							if (isPrimeVar(variableName)) {
								value = state.getLogViewPrimeVariables().get(stripPrime(variableName));
							} else {
								value = state.getLogViewVariables().get(variableName);
							}
							if (value == null) {
								throw new VariableNotFoundException("Variable " + variableName + " is missing!");
							}
							return value;
						}
					});
				} catch (EvaluatorException e) {
					if (e.getCause() instanceof UnsupportedFunctionException) {
						violated = false; // Functions cannot be violated!	
					} else {
						throw e;
					}
				}
				if (violated) {
					// Assume this has been changed because the guard, so guard was violated
					return true;
				}
			}
		}
		return false;
	}

	private boolean allVariablesAvailable(final AlignmentInfoState state, GuardExpression expression) {
		boolean allVariablesAvailable = true;
		for (String varName : expression.getNormalVariables()) {
			allVariablesAvailable &= state.getLogViewVariables().containsKey(varName);
		}
		for (String varName : expression.getPrimeVariables()) {
			allVariablesAvailable &= state.getLogViewPrimeVariables().containsKey(varName);
		}
		return allVariablesAvailable;
	}

	private static String stripPrime(String varName) {
		return varName.substring(0, varName.length() - 1);
	}

	private static boolean isPrimeVar(String varName) {
		return varName.charAt(varName.length() - 1) == '\'';
	}

}