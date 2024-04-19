package org.processmining.dataawareexplorer.utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.processmining.framework.util.LevenshteinDistance;
import org.processmining.framework.util.ui.widgets.ProMComboBox;
import org.processmining.framework.util.ui.widgets.ProMPropertiesPanel;

import com.google.common.collect.Ordering;

public class VariableMappingPanel extends ProMPropertiesPanel {

	private static final long serialVersionUID = 1L;

	private static final String UNMAPPED_ATTRIBUTE = "";

	Map<String, ProMComboBox<String>> comboBoxes = new HashMap<String, ProMComboBox<String>>();

	private final static float DISTANCE_LIMIT = 0.7F;

	public VariableMappingPanel(Collection<String> variables, Collection<String> attributes) {
		super("Please provide the mapping between variables and attributes:");

		Collection<String> attributesWithDefault = new ArrayList<String>();
		attributesWithDefault.add(UNMAPPED_ATTRIBUTE);
		attributesWithDefault.addAll(Ordering.natural().sortedCopy(attributes));

		for (String node : Ordering.natural().sortedCopy(variables)) {

			ProMComboBox<String> component = new ProMComboBox<String>(attributesWithDefault);
			component.setMinimumSize(null);
			component.setMaximumSize(null);
			component.setPreferredSize(null);

			Object retValue[] = getBestMatch(node, attributesWithDefault);

			Integer distance = (Integer) retValue[1];

			if (distance < DISTANCE_LIMIT * node.length()) {
				component.setSelectedItem(retValue[0]);
				if (distance > 0) {
					component.setForeground(Color.YELLOW);
				}
			}

			comboBoxes.put(node, component);
			this.addProperty(node.toString(), component);
		}

	}

	private Object[] getBestMatch(String value, Collection<String> candidates) {
		LevenshteinDistance ld = new LevenshteinDistance();
		int bestDist = Integer.MAX_VALUE;
		Object bestObject = null;
		for (String candidate : candidates) {
			if (candidate.startsWith(value)) {
				int distance = value.length() - candidate.length();
				return new Object[] { candidate, distance };
			}			
			int ldist;
			if (candidate.toString().length() == 0)
				ldist = value.length();
			else
				ldist = ld.getLevenshteinDistanceLinearSpace(value, candidate.toString());

			if (ldist < bestDist) {
				bestDist = ldist;
				bestObject = candidate;
			}
		}
		return new Object[] { bestObject, bestDist };
	}

	public Map<String, String> getMapping(boolean includeNotMapped) {
		Map<String, String> retValue = new HashMap<String, String>();
		for (String node : comboBoxes.keySet()) {
			String selection = (String) comboBoxes.get(node).getSelectedItem();
			if (selection == UNMAPPED_ATTRIBUTE) {
				if (includeNotMapped)
					retValue.put(node, null);
			} else
				retValue.put(node, selection);
		}
		return retValue;
	}

}
