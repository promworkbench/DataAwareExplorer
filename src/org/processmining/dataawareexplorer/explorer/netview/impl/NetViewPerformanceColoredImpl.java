/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.utils.ColorScheme;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter.DecorationKey;
import org.processmining.framework.util.ui.widgets.ColorSchemeLegend;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;

import com.fluxicon.slickerbox.factory.SlickerFactory;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;

class NetViewPerformanceColoredImpl extends NetViewPerformanceImpl {

	private ColorScheme colorScheme = ColorScheme.BLUE_SINGLE_HUE;
	private JCheckBox colorCodeCheckbox;

	public NetViewPerformanceColoredImpl(ExplorerContext explorerContext, ExplorerUpdater updater,
			ExplorerModel explorerModel) {
		super(explorerContext, updater, explorerModel);
		colorCodeCheckbox = SlickerFactory.instance().createCheckBox("Color-code", true);
		configPanel.addConfigurationComponent(colorCodeCheckbox);
		ColorSchemeLegend colorLegend = new ColorSchemeLegend(colorScheme.getColors());
		colorLegend.setMinimumSize(new Dimension(200, 30));
		colorLegend.setPreferredSize(new Dimension(200, 30));
		colorLegend.setMaximumSize(new Dimension(200, 40));
		configPanel.addConfigurationComponent(javax.swing.Box.createVerticalStrut(5));
		JPanel colorCodePanel = new JPanel();
		colorCodePanel.setLayout(new BoxLayout(colorCodePanel, BoxLayout.X_AXIS));
		colorCodePanel.add(colorCodeCheckbox);
		colorCodePanel.add(Box.createHorizontalStrut(5));
		colorCodePanel.add(colorLegend);
		configPanel.addConfigurationComponent(colorCodePanel);
		colorCodeCheckbox.addActionListener(new UpdateViewListener(updater));
	}

	@Override
	public void updateData() throws NetVisualizationException {
		super.updateData();

		if (colorCodeCheckbox.isSelected()) {
			if (!getStatistics().transitionMoves.isEmpty()) {
				int maxCount = calcMaxCount(getStatistics().transitionMoves);
				for (Entry<Transition> entry : getStatistics().transitionMoves.entrySet()) {
					int count = entry.getCount();
					double frequency = transform(maxCount, count);
					Color color = colorScheme.getColorFromGradient(frequency);
					decorationData.putAttribute(entry.getElement(), DecorationKey.FILLCOLOR, color);
					decorationData.putAttribute(entry.getElement(), DecorationKey.TEXTCOLOR,
							determineFontColor(null, color));
				}
			}

			if (getEdgeMeasure() == PerformanceMode.FREQUENCY || getEdgeMeasure() == PerformanceMode.TRACE_PERCENTAGE) {
				if (!getStatistics().edgeMoves.isEmpty()) {
					int maxCount = calcMaxCount(getStatistics().edgeMoves);
					for (Entry<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> entry : getStatistics().edgeMoves
							.entrySet()) {
						int count = getStatistics().edgeMoves.count(entry.getElement());
						double frequency = transform(maxCount, count);
						decorationData.putAttribute(entry.getElement(), DecorationKey.LINECOLOR,
								colorScheme.getColorFromGradient(frequency));
					}
				}
			}
		}

	}

	private static final Color determineFontColor(final Color textColor, final Color bgColor) {
		if (textColor == null) {
			double val = Math.sqrt(.299 * Math.pow(bgColor.getRed(), 2) + .587 * Math.pow(bgColor.getGreen(), 2)
					+ .114 * Math.pow(bgColor.getBlue(), 2));
			return (val < 130) ? Color.WHITE : Color.BLACK;
		} else {
			return textColor;
		}
	}

	private double transform(double maxValue, double value) {
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

	private int calcMaxCount(Multiset<?> statistics) {
		ImmutableMultiset<?> highestCountFirst = Multisets.copyHighestCountFirst(statistics);
		return highestCountFirst.entrySet().iterator().next().getCount();
	}

}