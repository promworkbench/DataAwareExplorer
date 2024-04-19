/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.plaf.LayerUI;

import org.processmining.dataawareexplorer.explorer.events.FilterChangedEvent;
import org.processmining.dataawareexplorer.explorer.events.ModelSelectionChangedEvent;
import org.processmining.dataawareexplorer.explorer.events.TracesSelectionChangedEvent;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.model.FilterConfiguration.SelectionFilterMode;
import org.processmining.dataawareexplorer.explorer.netview.ModelDecorationData;
import org.processmining.plugins.graphviz.dot.DotElement;
import org.processmining.plugins.graphviz.visualisation.listeners.SelectionChangedListener;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;

public final class NetVisualizationImpl implements NetVisualization {

	/**
	 * Adapted from <a href=
	 * "http://docs.oracle.com/javase/tutorial/displayCode.html?code=http://docs.oracle.com/javase/tutorial/uiswing/examples/misc/TapTapTapProject/src/TapTapTap.java"
	 * >Java Documentation</a> (c) Oracle
	 */
	private static class ProgressUI extends LayerUI<JPanel> implements ActionListener {

		private static final long serialVersionUID = 6399493972193149396L;

		private final Timer progressTimer;

		private int currentTick = 0;
		private int maxTick = 10;

		private int angle;

		private ProgressUI() {
			super();
			progressTimer = new Timer(1000 / 18, this);
			progressTimer.setInitialDelay(1500);
		}

		@Override
		public void paint(Graphics g, JComponent c) {
			int width = c.getWidth();
			int height = c.getHeight();

			super.paint(g, c);

			if (progressTimer.isRunning()) {
				Graphics2D g2 = (Graphics2D) g.create();
				Composite composite = g2.getComposite();
				float fadeFactor = currentTick / (float) maxTick;
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .25f * fadeFactor));
				g2.fillRect(0, 0, c.getWidth(), c.getHeight());
				g2.setComposite(composite);

				int s = Math.min(width, height) / 15;
				int cx = width / 2;
				int cy = height / 2;
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setStroke(new BasicStroke(s / 4, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
				g2.setPaint(Color.WHITE);
				g2.rotate(Math.PI * angle / 180, cx, cy);
				for (int i = 0; i < 12; i++) {
					g2.drawLine(cx + s, cy, cx + s * 2, cy);
					g2.rotate(-Math.PI / 6, cx, cy);
				}

				g2.dispose();
			}
		}

		public void showProgress() {
			currentTick = 0;
			progressTimer.start();
		}

		public void hideProgress() {
			progressTimer.stop();
			firePropertyChange("progress", true, false);
		}

		public void applyPropertyChange(PropertyChangeEvent evt, JLayer<? extends JPanel> l) {
			super.applyPropertyChange(evt, l);
			l.repaint();
		}

		public void actionPerformed(ActionEvent e) {
			firePropertyChange("progress", false, true);
			angle += 3;
			if (angle >= 360) {
				angle = 0;
			}
			currentTick = Math.min(maxTick, currentTick + 1);
		}

	}

	private JLayer<JPanel> visualizationLayer;
	private NetVisualizationPanel netPanel;

	private final Timer updateTimer;
	private ProgressUI progressUI;

	public NetVisualizationImpl(final ExplorerUpdater updatableExplorer, ExplorerContext explorerContext, final ExplorerModel explorerModel) {
		super();
		updatableExplorer.getEventBus().register(this);

		updateTimer = new Timer(1000, new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				final Set<Object> previousNodes = ImmutableSet
						.copyOf(explorerModel.getFilterConfiguration().getSelectedNodes());
				final Set<Object> selectedNodes = ImmutableSet.copyOf(netPanel.getSelectedNodes());
				explorerModel.getFilterConfiguration().setSelectedNodes(selectedNodes);
				updatableExplorer.getEventBus().post(new ModelSelectionChangedEvent() {

					public Set<Object> getPreviouslySelectedNodes() {
						return previousNodes;
					}

					public Set<Object> getSelectedNodes() {
						return selectedNodes;
					}

					public Object getSource() {
						return NetVisualizationImpl.this;
					}

				});
				if (explorerModel.getFilterConfiguration().getSelectionFilterMode() != SelectionFilterMode.NONE) {
					updatableExplorer.post(new FilterChangedEvent() {

						public Object getSource() {
							return NetVisualizationImpl.this;
						}
					});
				}
			}

		});
		updateTimer.setRepeats(false);

		netPanel = new NetVisualizationPanel(updatableExplorer, explorerContext, explorerModel);
		netPanel.addUserSelectionChangedListener(new SelectionChangedListener<DotElement>() {

			public void selectionChanged(Set<DotElement> selectedElements) {
				updateTimer.restart();
			}
		});
		netPanel.addMouseClearSelectionListener(new SelectionChangedListener<DotElement>() {

			public void selectionChanged(Set<DotElement> selectedElements) {
				final Collection<XAlignment> previousAlignments = ImmutableList
						.copyOf(explorerModel.getSelectedAlignments());
				final Collection<XAlignment> selectedAlignments = ImmutableSet.of();
				explorerModel.setSelectedAlignments(selectedAlignments);
				updatableExplorer.getEventBus().post(new TracesSelectionChangedEvent() {

					public Collection<XAlignment> getPreviouslySelectedAlignments() {
						return previousAlignments;
					}

					public Collection<XAlignment> getSelectedAlignments() {
						return selectedAlignments;
					}

					public Object getSource() {
						return NetVisualizationImpl.this;
					}

				});
			}
		});
		progressUI = new ProgressUI();
		visualizationLayer = new JLayer<>(netPanel, progressUI);
	}

	public void beforeUpdate() {
		netPanel.showProgress();
		progressUI.showProgress();		
	}

	public void afterUpdate() {
		progressUI.hideProgress();
		netPanel.hideProgress();		
	}

	public void updateData(ModelDecorationData decoration) {
		netPanel.updateData(decoration);
	}

	@Subscribe
	public void traceSelectionUpdated(TracesSelectionChangedEvent event) {
		netPanel.colorSelection();
		netPanel.repaint();
	}

	public void updateUI() {
		netPanel.updatePanel();
	}

	public Component getComponent() {
		return visualizationLayer;
	}

}