/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import java.awt.Color;
import java.awt.Dimension;
import java.util.Map;
import java.util.Map.Entry;

import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.infoview.InfoData.InfoType;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.netview.ModelDecorationDataImpl;
import org.processmining.dataawareexplorer.explorer.work.AlignmentInformation;
import org.processmining.dataawareexplorer.explorer.work.PlaceStatistics;
import org.processmining.dataawareexplorer.explorer.work.TransitionStatistics;
import org.processmining.dataawareexplorer.explorer.work.VariableStatistics;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter.DecorationKey;
import org.processmining.framework.util.ui.widgets.ColorScheme;
import org.processmining.framework.util.ui.widgets.ColorSchemeLegend;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataElement;

import com.google.common.collect.Iterables;
import com.google.common.collect.Multisets;

class NetViewFitnessImpl extends NetViewPerformanceImpl {

	//Color brewer
	private Color[] colorPalette = new Color[] {
			new Color(128,0,38),
			new Color(227,26,28),
			new Color(253,141,60),
			new Color(254,217,118),
			new Color(255,255,204)
	};
			
	public NetViewFitnessImpl(ExplorerContext explorerContext, ExplorerUpdater updater, ExplorerModel explorerInput) {
		super(explorerContext, updater, explorerInput);
		ColorSchemeLegend colorLegend = new ColorSchemeLegend(colorPalette);
		colorLegend.setMinimumSize(new Dimension(200, 30));
		colorLegend.setPreferredSize(new Dimension(200, 30));
		colorLegend.setMaximumSize(new Dimension(200, 40));
		configPanel.addConfigurationComponent(javax.swing.Box.createVerticalStrut(5));
		configPanel.addConfigurationComponent("Fitness legend", colorLegend);
		setEdgeMeasure(PerformanceMode.NONE);
		setFirstMeasure(PerformanceMode.NONE);
		setSecondMeasure(PerformanceMode.NONE);
	}

	public void updateData() throws NetVisualizationException {
		super.updateData();
		AlignmentInformation alignmentInformation = explorerModel.getAlignmentInformation();
		addTransitionColoring(decorationData, alignmentInformation.transitionStatistics);
		addPlaceColoring(decorationData, alignmentInformation.placeStatistics);
		addVariableColoring(decorationData, alignmentInformation.variableStatistics);
	}

	private void addVariableColoring(ModelDecorationDataImpl modelDecoration,
			Map<DataElement, VariableStatistics> variableStatistics) {
		for (Entry<DataElement, VariableStatistics> entry : variableStatistics.entrySet()) {
			VariableStatistics stats = entry.getValue();
			float wrongWriteRatio = 1.0f;
			if (stats.getObservedWrites() != 0) {
				wrongWriteRatio = 1.0f - (float) (stats.numMissingWriteOps + stats.numWrongWriteOps)
						/ (float) stats.getObservedWrites();
			}
			modelDecoration.putAttribute(entry.getKey(), DecorationKey.FILLCOLOR,
					ColorScheme.getColorFromGradient(wrongWriteRatio, colorPalette, Color.WHITE));
			infoData.addLocal(entry.getKey(), "Variable fitness", wrongWriteRatio, InfoType.PERCENTAGE);
		}
	}

	private void addPlaceColoring(ModelDecorationDataImpl modelDecoration,
			Map<Place, PlaceStatistics> placeStatistics) {
		for (final Entry<Place, PlaceStatistics> entry : placeStatistics.entrySet()) {
			final PlaceStatistics stats = entry.getValue();
			float fitnessValue = 1.0f;
			if (stats.getObservedMoves() != 0) {
				if (explorerModel.getFinalMarkings()[0].contains(entry.getKey()) && explorerModel.getModel().getOutEdges(entry.getKey()).isEmpty()) {
					// final place without possibility to continue, we cannot expect any good move here
					fitnessValue = 1.0f;
				} else {
					// normalize with number of traces
					fitnessValue = 1.0f - (float) stats.numLogMoves / (float) Iterables.size(explorerModel.getFilteredAlignments());
				}
			}
			modelDecoration.putAttribute(entry.getKey(), DecorationKey.FILLCOLOR,
					ColorScheme.getColorFromGradient(fitnessValue, colorPalette, Color.WHITE));
			infoData.addLocal(entry.getKey(), "Wrong events", Multisets.copyHighestCountFirst(stats.observedLogMoves),
					InfoType.STRING);
			infoData.addLocal(entry.getKey(), "Place fitness", fitnessValue, InfoType.PERCENTAGE);
		}
	}

	private void addTransitionColoring(ModelDecorationDataImpl modelDecoration,
			Map<Transition, TransitionStatistics> transitionStatistics) {
		for (final Entry<Transition, TransitionStatistics> entry : transitionStatistics.entrySet()) {
			TransitionStatistics stats = entry.getValue();
			float fitnessValue = 1.0f;
			if (stats.getObservedMoves() != 0) {
				fitnessValue = 1.0f
						- (float) (stats.numModelMoves + stats.numDataMoves) / (float) stats.getObservedMoves();
			}
			modelDecoration.putAttribute(entry.getKey(), DecorationKey.FILLCOLOR,
					ColorScheme.getColorFromGradient(fitnessValue, colorPalette, Color.WHITE));
			infoData.addLocal(entry.getKey(), "Transition fitness", fitnessValue, InfoType.PERCENTAGE);		
		}
	}

}