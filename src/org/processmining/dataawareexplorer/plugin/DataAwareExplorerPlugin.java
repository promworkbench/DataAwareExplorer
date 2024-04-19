/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.plugin;

import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.dataawareexplorer.explorer.DataAwareExplorer;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.datapetrinets.DataPetriNetsWithMarkings;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginCategory;
import org.processmining.framework.plugin.annotations.PluginLevel;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PetriNetWithDataFactory;

@Plugin(name = "Multi-perspective Process Explorer", level = PluginLevel.PeerReviewed, parameterLabels = {
		"Petri Net With Data", "Log" }, returnLabels = { "Multi-perspective Process Explorer" }, returnTypes = {
				DataAwareExplorer.class }, userAccessible = true, handlesCancel = true, categories = {
						PluginCategory.Analytics, PluginCategory.Discovery,
						PluginCategory.ConformanceChecking }, keywords = { "conformance", "discovery", "DPN", "data",
								"alignment",
								"intreractive" }, help = "Explore the data-, resource-, time- and control-flow perspective of the event log."
										+ " This plug-in starts an interactive tool to discover data-aware models, analyse them and check the conformance according to event data. "
										+ " It integrates functionality of the packages 'DataAwareReplayer', 'DataPetriNets' and 'LogEnhancement'. The 'GraphViz' package is used for graph visualization."
										+ " This plug-in is described in the BPM'15 Demo paper 'The Multi-perspective Process Explorer' (<a href=\"http://ceur-ws.org/Vol-1418/paper27.pdf\">http://ceur-ws.org/Vol-1418/paper27.pdf</a>)")
public class DataAwareExplorerPlugin {
	
	@PluginVariant(variantLabel = "Multi-perspective Process Explorer", requiredParameterLabels = { 0, 1 })
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = " F. Mannhardt", email = "f.mannhardt@tue.nl", pack = "DataAwareExplorer")
	public DataAwareExplorer explore(PluginContext context, Petrinet net, XLog log) {
		context.getProgress().setIndeterminate(true);
		return new DataAwareExplorer(context, log, wrapPetrinet(net));
	}

	@PluginVariant(variantLabel = "Multi-perspective Process Explorer", requiredParameterLabels = { 0, 1 })
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = " F. Mannhardt", email = "f.mannhardt@tue.nl", pack = "DataAwareExplorer")
	public DataAwareExplorer explore(PluginContext context, DataPetriNet net, XLog log) {
		context.getProgress().setIndeterminate(true);
		return new DataAwareExplorer(context, log, wrapPetrinet(net));
	}

	private static DataPetriNetsWithMarkings wrapPetrinet(PetrinetGraph net) {
		if (net instanceof DataPetriNet) {
			return (DataPetriNetsWithMarkings) net;
		} else {
			PetriNetWithDataFactory factory = new PetriNetWithDataFactory(net, net.getLabel(), false);
			return factory.getRetValue();
		}
	}

}
