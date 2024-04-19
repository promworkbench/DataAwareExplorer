/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import java.awt.Component;
import java.awt.Font;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.infoview.InfoData;
import org.processmining.dataawareexplorer.explorer.infoview.InfoDataImpl;
import org.processmining.dataawareexplorer.explorer.netview.ConfigurationPanel;
import org.processmining.dataawareexplorer.explorer.netview.ModelDecorationData;
import org.processmining.dataawareexplorer.explorer.netview.ModelDecorationDataImpl;
import org.processmining.dataawareexplorer.explorer.netview.NetView;
import org.processmining.framework.plugin.PluginContext;

abstract class NetViewAbstractImpl implements NetView {

	static final class ConfigurationPanelImpl implements ConfigurationPanel {

		private final JPanel configurationPanel;

		private ConfigurationPanelImpl() {
			super();
			configurationPanel = new JPanel();
			configurationPanel.setLayout(new BoxLayout(configurationPanel, BoxLayout.Y_AXIS));
		}

		public Component getComponent() {
			return configurationPanel;
		}

		public void addConfigurationComponent(Component component, int index) {
			configurationPanel.add(component, index);
		}
		
		public void addConfigurationComponent(Component component) {
			configurationPanel.add(component);
		}

		public void addConfigurationComponent(String label, Component component, int index) {
			configurationPanel.add(createTwoColumnLayout(createHeading(label), component), index);
		}
		
		public void addConfigurationComponent(String label, Component component) {
			configurationPanel.add(createTwoColumnLayout(createHeading(label), component));
		}

		private static Component createTwoColumnLayout(Component left, Component right) {
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
			panel.add(left);
			panel.add(Box.createHorizontalStrut(5));
			panel.add(right);
			return panel;
		}

		public void clear() {
			configurationPanel.removeAll();
		}

		private static JLabel createHeading(String label) {
			JLabel title = new JLabel(label);
			title.setFont(title.getFont().deriveFont(Font.BOLD));
			title.setForeground(null);
			return title;
		}

	}

	protected final ModelDecorationDataImpl decorationData;
	protected final InfoDataImpl infoData;

	protected final ConfigurationPanelImpl configPanel;

	protected final ExplorerContext explorerContext;
	protected final ExplorerUpdater updater;

	NetViewAbstractImpl(ExplorerContext explorerContext, ExplorerUpdater updater) {
		super();
		this.explorerContext = explorerContext;
		this.updater = updater;

		// Data Containers
		decorationData = new ModelDecorationDataImpl();
		infoData = new InfoDataImpl();

		// Panels
		configPanel = new ConfigurationPanelImpl();
	}

	public void updateData() throws NetVisualizationException {
		infoData.clear();
		decorationData.clear();
	}

	public ModelDecorationData getModelDecorationData() {
		return decorationData;
	}

	public InfoData getInfoData() {
		return infoData;
	}

	public void updateUI() {
	}

	public ConfigurationPanel getConfigurationPanel() {
		return configPanel;
	}

	protected PluginContext getContext() {
		return explorerContext.getContext();
	}

}