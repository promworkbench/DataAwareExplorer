/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.utils;

import info.clearthought.layout.TableLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

import org.processmining.framework.util.collection.AlphanumComparator;
import org.processmining.framework.util.ui.widgets.ProMList;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.petrinet.finalmarkingprovider.MarkingEditorPanel;

import com.fluxicon.slickerbox.factory.SlickerFactory;

/**
 * @author F. Mannhardt (adapted from {@link MarkingEditorPanel})
 *
 */
public class MarkingPanel extends JPanel {

	private static final long serialVersionUID = 6926102952413907428L;

	private final ProMList<Place> placeList;
	private final DefaultListModel<Place> placeListMdl;

	private final ProMList<Place> candidateMarkings;
	private final DefaultListModel<Place> candidateMarkingsMdl;

	private final JButton addPlacesBtn;
	private final JButton removePlacesBtn;

	private static final Place EMPTYMARKING = new Place("<Empty Marking>", null);

	public MarkingPanel(String title, PetrinetGraph net, Set<String> defaultPlaceNames) {

		// factory 
		SlickerFactory factory = SlickerFactory.instance();

		// place selection
		placeListMdl = new DefaultListModel<>();
		Set<Place> places = new TreeSet<>(new Comparator<Place>() {
			private AlphanumComparator comp = new AlphanumComparator();

			public int compare(Place o1, Place o2) {
				return comp.compare(o1.getLabel(), o2.getLabel());
			}
		});

		// add places
		candidateMarkingsMdl = new DefaultListModel<>();
		candidateMarkingsMdl.addElement(EMPTYMARKING);

		places.addAll(net.getPlaces());
		for (Place p : places) {
			placeListMdl.addElement(p);
			if (defaultPlaceNames.contains(p.getLabel().toLowerCase())) {
				candidateMarkingsMdl.removeAllElements();
				candidateMarkingsMdl.addElement(p);
				break;
			}
		}
		placeList = new ProMList<>("List of Places", placeListMdl);
		placeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		candidateMarkings = new ProMList<>("Candidate " + title, candidateMarkingsMdl);

		addPlacesBtn = factory.createButton("Add Place >>");
		addPlacesBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!placeList.getSelectedValuesList().isEmpty()) {
					if ((candidateMarkingsMdl.size() == 1) && (candidateMarkingsMdl.elementAt(0) == EMPTYMARKING)) {
						candidateMarkingsMdl.removeAllElements();
					}
					candidateMarkingsMdl.addElement(placeList.getSelectedValuesList().get(0));
				}
			}
		});

		removePlacesBtn = factory.createButton("<< Remove Place");
		removePlacesBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for (Object obj : candidateMarkings.getSelectedValuesList()) {
					candidateMarkingsMdl.removeElement(obj);
					if (candidateMarkingsMdl.size() == 0) {
						candidateMarkingsMdl.addElement(EMPTYMARKING);
					}
				}
			}
		});

		// now add the elements
		double[][] size = new double[][] { { 250, 10, 200, 10, 250 }, { TableLayout.FILL, 30, 5, 30, TableLayout.FILL } };
		TableLayout layout = new TableLayout(size);
		setLayout(layout);
		add(placeList, "0,0,0,4");
		add(addPlacesBtn, "2,1");
		add(removePlacesBtn, "2,3");
		add(candidateMarkings, "4,0,4,4");
	}

	public Marking getMarking() {
		Marking newMarking = new Marking();
		if (candidateMarkingsMdl.size() > 1) {
			Enumeration<?> elements = candidateMarkingsMdl.elements();
			while (elements.hasMoreElements()) {
				newMarking.add((Place) elements.nextElement());
			}
		} else {
			if (!(EMPTYMARKING == candidateMarkingsMdl.elementAt(0))) {
				newMarking.add(candidateMarkingsMdl.elements().nextElement());
			}
		}
		return newMarking;
	}

}
