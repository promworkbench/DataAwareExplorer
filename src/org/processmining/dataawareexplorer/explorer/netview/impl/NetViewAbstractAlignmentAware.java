/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.util.Collection;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;

import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.infoview.InfoData.InfoType;
import org.processmining.dataawareexplorer.explorer.infoview.InfoTable;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.work.AlignmentInformation;
import org.processmining.dataawareexplorer.explorer.work.PlaceStatistics;
import org.processmining.dataawareexplorer.explorer.work.TransitionStatistics;
import org.processmining.dataawareexplorer.explorer.work.VariableStatistics;
import org.processmining.datapetrinets.expression.AtomCollector;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataElement;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PNWDTransition;

import com.google.common.base.Joiner;

abstract class NetViewAbstractAlignmentAware extends NetViewAbstractModelAwareImpl {

	public enum TransformationMode {
		LINEAR("Linear"), SQRT("Square root"), LOG("Logarithm");

		private final String desc;

		private TransformationMode(String desc) {
			this.desc = desc;
		}

		@Override
		public String toString() {
			return desc;
		}

	}

	public NetViewAbstractAlignmentAware(ExplorerContext explorerContext, ExplorerUpdater updater,
			ExplorerModel explorerModel) {
		super(explorerContext, updater, explorerModel);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.processmining.dataawareexplorer.explorer.netview.
	 * NetViewAbstractModelAwareImpl#updateData()
	 */
	@Override
	public void updateData() throws NetVisualizationException {
		super.updateData();
		addGlobalStatistics();
		addLocalStatistics();
	}

	private void addLocalStatistics() {
		AlignmentInformation alignmentInformation = explorerModel.getAlignmentInformation();
		for (final Transition node : explorerModel.getModel().getTransitions()) {
			if (node instanceof PNWDTransition) {
				TransitionStatistics transitionStatistics = alignmentInformation.transitionStatistics.get(node);

				infoData.addLocal(node, "# Missing events", transitionStatistics.numModelMoves, InfoType.INTEGER);
				infoData.addLocal(node, "# Wrong events (data)", transitionStatistics.numDataMoves, InfoType.INTEGER);
				infoData.addLocal(node, "# Correct events", transitionStatistics.numGoodMoves, InfoType.INTEGER);

				if (!((PNWDTransition) node).getWriteOperations().isEmpty()) {
					infoData.addLocal(node, "Writes", Joiner.on(",").join(((PNWDTransition) node).getWriteOperations()),
							InfoType.STRING);
					infoData.addLocal(node, "# Missing writes", transitionStatistics.numMissingWriteOps,
							InfoType.INTEGER);
					infoData.addLocal(node, "# Wrong writes", transitionStatistics.numWrongWriteOps, InfoType.INTEGER);
					infoData.addLocal(node, "# Correct writes", transitionStatistics.numGoodWriteOps, InfoType.INTEGER);
				}

				if (((PNWDTransition) node).getGuardExpression() != null) {
					infoData.addLocal(node, "% Guard violations", transitionStatistics.getGuardViolations(),
							InfoType.PERCENTAGE);
					infoData.addLocal(node, "# Guard Atoms",
							AtomCollector.countComparisonAtoms(((PNWDTransition) node).getGuardExpression()),
							InfoType.INTEGER);

					infoData.addLocal(node, "Guard", ((PNWDTransition) node).getGuardExpression().toTreeLikeString(2),
							InfoType.STRING);
				}

			}

			decorationData.addContextMenuItem(node, new JMenuItem(new AbstractAction("Show information") {

				private static final long serialVersionUID = 1L;

				public void actionPerformed(ActionEvent e) {
					InfoTable infoTable = new InfoTable();
					infoTable.addStatistics(infoData.getLocalInfo().get(node));
					infoTable.packTable();
					explorerContext.getUserQuery().showCustom(infoTable.getTable(), "Information on transition " + node.getLabel(),
							ModalityType.MODELESS);
				}

			}));
		}

		for (final Place node : explorerModel.getModel().getPlaces()) {
			PlaceStatistics placeStats = alignmentInformation.placeStatistics.get(node);

			infoData.addLocal(node, "# Wrong events (log)", placeStats.numLogMoves, InfoType.INTEGER);
			infoData.addLocal(node, "# Missing events", placeStats.numModelMoves, InfoType.INTEGER);
			infoData.addLocal(node, "# Wrong events (data)", placeStats.numDataMoves, InfoType.INTEGER);
			infoData.addLocal(node, "# Good events", placeStats.numGoodMoves, InfoType.INTEGER);

			if ((placeStats.numMissingWriteOps + placeStats.numWrongWriteOps + placeStats.numGoodWriteOps) > 0) {
				infoData.addLocal(node, "# Missing writes", placeStats.numMissingWriteOps, InfoType.INTEGER);
				infoData.addLocal(node, "# Wrong writes", placeStats.numWrongWriteOps, InfoType.INTEGER);
				infoData.addLocal(node, "# Correct writes", placeStats.numGoodWriteOps, InfoType.INTEGER);
			}

			infoData.addLocal(node, "% Event Violations", placeStats.getLocalEventViolations(), InfoType.PERCENTAGE);
			infoData.addLocal(node, "% Data Violations", placeStats.getLocalDataViolations(), InfoType.PERCENTAGE);
			double localAvgAtomsPerTransition = computeAvgAtomsPerTransition(node);
			if (localAvgAtomsPerTransition > 0.0d) {
				infoData.addLocal(node, "% Guard violations", computeAvgGuardViolations(node), InfoType.PERCENTAGE);
				infoData.addLocal(node, "Avg guard atoms", localAvgAtomsPerTransition, InfoType.NUMBER);
			}

			decorationData.addContextMenuItem(node, new JMenuItem(new AbstractAction("Show information") {

				private static final long serialVersionUID = 1L;

				public void actionPerformed(ActionEvent e) {
					InfoTable infoTable = new InfoTable();
					infoTable.addStatistics(infoData.getLocalInfo().get(node));
					infoTable.packTable();
					explorerContext.getUserQuery().showCustom(infoTable.getTable(), "Information on place " + node.getLabel(),
							ModalityType.MODELESS);
				}

			}));
		}
		
		for (DataElement node : explorerModel.getModel().getVariables()) {
			VariableStatistics variableStats = alignmentInformation.variableStatistics.get(node);
			infoData.addLocal(node, "# Missing writes", variableStats.numMissingWriteOps, InfoType.INTEGER);
			infoData.addLocal(node, "# Wrong writes", variableStats.numWrongWriteOps, InfoType.INTEGER);
			infoData.addLocal(node, "# Correct writes", variableStats.numGoodWriteOps, InfoType.INTEGER);
		}
	}

	private void addGlobalStatistics() {
		AlignmentInformation alignmentInformation = explorerModel.getAlignmentInformation();
		infoData.addGlobal("# Missing Events", alignmentInformation.numModelMoves, InfoType.INTEGER);
		infoData.addGlobal("# Wrong Events", alignmentInformation.numLogMoves, InfoType.INTEGER);
		infoData.addGlobal("# Correct Events", alignmentInformation.numGoodMoves, InfoType.INTEGER);

		if (hasWrites()) {
			infoData.addGlobal("# Missing writes", alignmentInformation.numMissingWriteOperations, InfoType.INTEGER);
			infoData.addGlobal("# Wrong writes", alignmentInformation.numWrongWriteOperations, InfoType.INTEGER);
			infoData.addGlobal("# Correct writes", alignmentInformation.numGoodWriteOperations, InfoType.INTEGER);
		}

		infoData.addGlobal("% Data Violations", alignmentInformation.dataViolations, InfoType.PERCENTAGE);
		infoData.addGlobal("% Event Violations", alignmentInformation.eventViolations, InfoType.PERCENTAGE);
		infoData.addGlobal("% Violations", alignmentInformation.overallViolations, InfoType.PERCENTAGE);

		double avgAtomsPerTransition = computeAvgAtomsPerTransition(explorerModel.getModel());
		if (avgAtomsPerTransition > 0) {
			infoData.addGlobal("Avg guard atoms", avgAtomsPerTransition, InfoType.NUMBER);
		}
		infoData.addGlobal("Avg fitness", getAverageFitness(), InfoType.PERCENTAGE);
	}

	private boolean hasWrites() {
		AlignmentInformation alignmentInformation = explorerModel.getAlignmentInformation();
		return (alignmentInformation.numMissingWriteOperations + alignmentInformation.numWrongWriteOperations
				+ alignmentInformation.numGoodWriteOperations) > 0;
	}

	protected double getAverageFitness() {
		return explorerModel.getAlignmentInformation().averageFitness;
	}

	protected double computeAvgGuardViolations(Place place) {
		double total = 0.0;
		int count = 0;

		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = place.getGraph()
				.getOutEdges(place);

		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : outEdges) {
			if (edge.getTarget() instanceof PNWDTransition) {
				PNWDTransition pnwdTransition = (PNWDTransition) edge.getTarget();
				TransitionStatistics stats = explorerModel.getAlignmentInformation().transitionStatistics
						.get(pnwdTransition);
				total += stats.getObservedMoves() * stats.getGuardViolations();
				count += stats.getObservedMoves();
			}
		}

		return count != 0 ? total / count : 0;
	}

}