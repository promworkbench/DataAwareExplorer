/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer;

import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import org.processmining.framework.plugin.Progress;

final class ExplorerProgressListener implements Progress {

	private final JProgressBar progressBar;
	private boolean isCancelled;

	public ExplorerProgressListener(JProgressBar progressBar) {
		this.progressBar = progressBar;
	}

	public void setValue(final int value) {
		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				progressBar.setValue(value);
			}
		});
	}

	public void setMinimum(final int value) {
		isCancelled = false;
		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				progressBar.setMinimum(value);
			}
		});
	}

	public void setMaximum(final int value) {
		isCancelled = false;
		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				progressBar.setMaximum(value);
			}
		});
	}

	public void setIndeterminate(final boolean makeIndeterminate) {
		isCancelled = false;
		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				progressBar.setIndeterminate(makeIndeterminate);
			}
		});
	}

	public void setCaption(final String message) {
		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				progressBar.setString(message);
			}
		});
	}

	public boolean isIndeterminate() {
		return progressBar.isIndeterminate();
	}

	public boolean isCancelled() {
		return isCancelled;
	}

	public void inc() {
		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				progressBar.setValue(progressBar.getValue() + 1);
			}
		});
	}

	public int getValue() {
		return progressBar.getValue();
	}

	public int getMinimum() {
		return progressBar.getMinimum();
	}

	public int getMaximum() {
		return progressBar.getMaximum();
	}

	public String getCaption() {
		return progressBar.getString();
	}

	public void cancel() {
		isCancelled = true;
	}

}