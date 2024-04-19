/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import java.util.Collection;

import org.apache.commons.lang3.StringEscapeUtils;
import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.utils.UIUtils;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.datapetrinets.expression.AtomCollector;
import org.processmining.datapetrinets.expression.GuardExpression;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter.DecorationKey;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PNWDTransition;

abstract class NetViewAbstractModelAwareImpl extends NetViewAbstractImpl {

	protected final ExplorerModel explorerModel;

	public NetViewAbstractModelAwareImpl(ExplorerContext explorerContext, ExplorerUpdater updater,
			ExplorerModel explorerModel) {
		super(explorerContext, updater);
		this.explorerModel = explorerModel;
	}

	private static final int MAX_EDGE_LABEL_OVERALL_LENGTH = 101;
	private static final int MAX_EDGE_LABEL_LINE_LENGTH = 30;

	protected static String convertGuardToEdgeLabel(GuardExpression guard) {
		String label = guard.toPrettyString(1);
		if (label.length() > MAX_EDGE_LABEL_OVERALL_LENGTH) {
			label = org.apache.commons.lang3.StringUtils.abbreviate(label, MAX_EDGE_LABEL_OVERALL_LENGTH);
		}
		label = StringEscapeUtils.escapeXml10(label);
		if (label.length() > MAX_EDGE_LABEL_LINE_LENGTH) {
			label = UIUtils.wrapLineWithSeparator(label, "<BR/>", MAX_EDGE_LABEL_LINE_LENGTH);
		}
		return label;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.processmining.dataawareexplorer.explorer.netview.NetView#updateData()
	 */
	public void updateData() throws NetVisualizationException {
		super.updateData();
		decorationData.clear();
	}

	protected void setGuardLabel(DataPetriNet net, Transition transition, GuardExpression guard) {
		if (!guard.toCanonicalString().equals("true")) {
			for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : net.getInEdges(transition)) {
				if (edge.getSource() instanceof Place) {
					decorationData.putAttribute(edge, DecorationKey.EXTRALABEL, convertGuardToEdgeLabel(guard));
				}
			}
		} else {
			for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : net.getInEdges(transition)) {
				if (edge.getSource() instanceof Place) {
					decorationData.putAttribute(edge, DecorationKey.EXTRALABEL, "");
				}
			}
		}
	}

	protected double computeAvgAtomsPerTransition(DataPetriNet net) {
		int atomCount = 0;
		for (Transition t : net.getTransitions()) {
			if (((PNWDTransition) t).getGuardExpression() != null) {
				GuardExpression guardExpression = ((PNWDTransition) t).getGuardExpression();
				atomCount += AtomCollector.countComparisonAtoms(guardExpression);
			}
		}
		return ((double) atomCount) / net.getTransitions().size();
	}

	protected double computeAvgAtomsPerTransition(Place place) {
		int atomCount = 0;
		int transitionCount = 0;

		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = place.getGraph()
				.getOutEdges(place);

		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : outEdges) {
			if (edge.getTarget() instanceof PNWDTransition) {
				PNWDTransition pnwdTransition = (PNWDTransition) edge.getTarget();
				transitionCount++;
				if (pnwdTransition.getGuardExpression() != null) {
					GuardExpression guardExpression = pnwdTransition.getGuardExpression();
					atomCount += AtomCollector.countComparisonAtoms(guardExpression);
				}
			}
		}

		return ((double) atomCount) / transitionCount;
	}

}
