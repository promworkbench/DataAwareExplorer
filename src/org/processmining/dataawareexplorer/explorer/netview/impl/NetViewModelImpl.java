/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.infoview.InfoData.InfoType;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.datapetrinets.expression.AtomCollector;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PNWDTransition;

import com.google.common.base.Joiner;

public class NetViewModelImpl extends NetViewAbstractModelAwareImpl {

	private static class LogInfo {
		int numTraces;
		long numEvents;

		int numEventClasses;

		Date firstEvent;
		Date lastEvent;
	}

	public NetViewModelImpl(ExplorerContext explorerContext, ExplorerUpdater updater, ExplorerModel explorerInput) {
		super(explorerContext, updater, explorerInput);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.processmining.dataawareexplorer.explorer.netview.
	 * NetViewAbstractModelAwareImpl#updateData()
	 */
	public void updateData() throws NetVisualizationException {
		super.updateData();

		LogInfo logInfo = calcLogInfo(explorerModel.getFilteredLog());

		double avgAtomsPerTransition = computeAvgAtomsPerTransition(explorerModel.getModel());

		if (explorerModel.hasLog()) {

			infoData.addGlobal("Last event", logInfo.lastEvent, InfoType.TIME);
			infoData.addGlobal("First event", logInfo.firstEvent, InfoType.TIME);

			infoData.addGlobal("# Log Attributes", explorerModel.getLogAttributes().size(), InfoType.INTEGER);

			infoData.addGlobal("# Event Classes", logInfo.numEventClasses, InfoType.INTEGER);
			infoData.addGlobal("# Events", logInfo.numEvents, InfoType.INTEGER);
			infoData.addGlobal("# Traces", logInfo.numTraces, InfoType.INTEGER);

		}

		if (avgAtomsPerTransition > 0) {
			infoData.addGlobal("Avg guard atoms", avgAtomsPerTransition, InfoType.NUMBER);
		}

		for (Transition node : explorerModel.getModel().getTransitions()) {
			if (node instanceof PNWDTransition) {
				if (((PNWDTransition) node).getGuardExpression() != null) {
					String guardWrapped = ((PNWDTransition) node).getGuardExpression().toPrettyString(1);
					infoData.addLocal(node, "Guard", guardWrapped, InfoType.STRING);
					infoData.addLocal(node, "# Atoms",
							AtomCollector.countComparisonAtoms(((PNWDTransition) node).getGuardExpression()),
							InfoType.INTEGER);
				}
				if (!((PNWDTransition) node).getWriteOperations().isEmpty()) {
					infoData.addLocal(node, "Write operations",
							Joiner.on(",").join(((PNWDTransition) node).getWriteOperations()), InfoType.STRING);
				}
			}
		}
		for (Place node : explorerModel.getModel().getPlaces()) {
			double localAvgAtomsPerTransition = computeAvgAtomsPerTransition(node);
			if (localAvgAtomsPerTransition > 0) {
				infoData.addLocal(node, "Local Avg guard atoms", localAvgAtomsPerTransition, InfoType.NUMBER);
			}
		}

	}

	private LogInfo calcLogInfo(Iterable<XTrace> traces) {
		LogInfo logInfo = new LogInfo();

		long numEvents = 0;
		Set<XEventClass> eventClasses = new HashSet<>();
		long firstEvent = Long.MAX_VALUE;
		long lastEvent = Long.MIN_VALUE;

		int numTraces = 0;
		for (XTrace trace : traces) {
			numEvents += trace.size();
			numTraces++;
			for (XEvent event : trace) {
				Date time = XTimeExtension.instance().extractTimestamp(event);
				if (time != null) {
					firstEvent = Math.min(firstEvent, time.getTime());
					lastEvent = Math.max(lastEvent, time.getTime());
				}
				eventClasses.add(explorerModel.getEventClasses().getClassOf(event));
			}
		}

		logInfo.numTraces = numTraces;
		logInfo.numEvents = numEvents;
		logInfo.numEventClasses = eventClasses.size();

		logInfo.firstEvent = new Date(firstEvent);
		logInfo.lastEvent = new Date(lastEvent);

		return logInfo;
	}

}