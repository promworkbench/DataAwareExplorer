/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.plugin;

import javax.swing.JComponent;

import org.processmining.contexts.uitopia.annotations.Visualizer;
import org.processmining.dataawareexplorer.explorer.DataAwareExplorer;
import org.processmining.dataawareexplorer.explorer.ExplorerControllerImpl;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginLevel;
import org.processmining.framework.plugin.annotations.PluginVariant;

@Plugin(name = "Data-aware Explorer", level = PluginLevel.PeerReviewed, returnLabels = { "Data Aware Explorer UI" }, returnTypes = { JComponent.class }, userAccessible = true, parameterLabels = { "Matching Instances" })
@Visualizer
public class DataAwareExplorerVisualization {

	@PluginVariant(requiredParameterLabels = { 0 })
	public JComponent visualise(PluginContext pluginContext, DataAwareExplorer rootExplorerInstance) {
		return new ExplorerControllerImpl(rootExplorerInstance).getComponent();
	}
	
}
