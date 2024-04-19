package org.processmining.dataawareexplorer.explorer.model;

import java.util.Map;

import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignedLog;

import com.google.common.collect.BiMap;

public class ParentAlignment {

	private final XAlignedLog alignment;	
	private final BiMap<Transition, Transition> modelTransitionMapping;
	private final Map<String, Transition> transitionsLocalId;

	public ParentAlignment(XAlignedLog alignment, BiMap<Transition, Transition> modelTransitionMapping, Map<String, Transition> transitionsLocalId) {
		this.modelTransitionMapping = modelTransitionMapping;
		this.alignment = alignment;
		this.transitionsLocalId = transitionsLocalId;
	}

	public XAlignedLog getAlignment() {
		return alignment;
	}

	/**
	 * @return mapping from new transition in the discovered model to old transition
	 */
	public BiMap<Transition, Transition> getModelTransitionMapping() {
		return modelTransitionMapping;
	}

	public Map<String, Transition> getTransitionsLocalId() {
		return transitionsLocalId;
	}

}
