package org.processmining.dataawareexplorer.utils;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import org.processmining.framework.util.ui.widgets.ProMTable;

import com.fluxicon.slickerbox.factory.SlickerFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;

public class InitialValueMappingPanel<T> extends JPanel {

	private static final String NO_MAPPING = "NONE";

	private static final long serialVersionUID = 4946819373227598703L;
	
	private final ProMTable mappingTable;
	private final ImmutableList<T> sourceList;
	private final JCheckBox useInitialValues;

	@SuppressWarnings("serial")
	public InitialValueMappingPanel(String text, Iterable<T> sources, Map<T, String> defaultValues) {
		super();
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(null);
		
		add(new JLabel(text));

		sourceList = ImmutableList.copyOf(sources);
		
		DefaultTableModel tableModel = new DefaultTableModel(sourceList.size(), 2) {

			public boolean isCellEditable(int row, int column) {
				if (column == 0) {
					return false;
				}
				return super.isCellEditable(row, column);
			}
			
		};
		
		mappingTable = new ProMTable(tableModel);
		mappingTable.setPreferredSize(new Dimension(400, 300));
		mappingTable.setMaximumSize(null);
		mappingTable.setMinimumSize(null);
		mappingTable.getTable().setPreferredSize(null);
		mappingTable.getTable().setMaximumSize(null);
		mappingTable.getTable().setMinimumSize(null);
		
		mappingTable.getTableHeader().getColumnModel().getColumn(0).setHeaderValue("Attribute");
		mappingTable.getTableHeader().getColumnModel().getColumn(0).setPreferredWidth(100);
		mappingTable.getTableHeader().getColumnModel().getColumn(1).setHeaderValue("Initial value");
		mappingTable.getTableHeader().getColumnModel().getColumn(1).setPreferredWidth(100);
		
		mappingTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new JTextField()) {

			protected void fireEditingStopped() {
				this.cancelCellEditing();
				super.fireEditingStopped();
			}

			protected void fireEditingCanceled() {
				super.fireEditingCanceled();
			}

		});
		

		int i = 0;
		for (T source: sourceList) {
			mappingTable.getTable().getModel().setValueAt(source, i++, 0);
		}
		
		for (i = 0; i < sourceList.size(); i++) {
			String defaultValue = defaultValues.get(sourceList.get(i));
			if (defaultValue != null) {
				mappingTable.getTable().getModel().setValueAt(defaultValue, i, 1);
			} else {
				mappingTable.getTable().getModel().setValueAt(NO_MAPPING, i, 1);	
			}			
		}
		
		useInitialValues = SlickerFactory.instance().createCheckBox("Use the initial values as specified below to improve the discover of guards", true);
		useInitialValues.addActionListener(new ActionListener() {
			
			public void actionPerformed(ActionEvent e) {
				if (useInitialValues.isSelected()) {
					mappingTable.setVisible(true);
				} else {
					mappingTable.setVisible(false);
				}
			}
		});
	
		add(useInitialValues);
		add(mappingTable);
	}

	public Map<T, String> getResult() {
		Map<T, String> result = new HashMap<>();
		
		int i = 0;
		UnmodifiableIterator<T> iterator = sourceList.iterator();
		while (iterator.hasNext()) {
			T source = iterator.next();
			String target = (String) mappingTable.getTable().getModel().getValueAt(i++, 1);
			if (!target.equals(NO_MAPPING)) {
				result.put(source, target);	
			}			
		}
		
		return result;
	}
	
	public boolean useInitialValues() {
		return useInitialValues.isSelected();
	}

}