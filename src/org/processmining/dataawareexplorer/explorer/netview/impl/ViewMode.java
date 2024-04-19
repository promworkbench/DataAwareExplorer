/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview.impl;

import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.netview.NetView;
import org.processmining.dataawareexplorer.explorer.netview.NetViewFactory;

public enum ViewMode {

	MODEL("Show Model Mode", new NetViewFactory() {

		public NetView newInstance(ExplorerContext context, ExplorerUpdater updater, 
				ExplorerModel explorerInput) {
			return new NetViewModelImpl(context, updater, explorerInput);
		}

	})
	, DISCOVERY("Show Data Discovery Mode", new NetViewFactory() {

		public NetView newInstance(ExplorerContext context, ExplorerUpdater updater, 
				ExplorerModel explorerInput) {
			return new NetViewDiscoveryImpl(context, updater, explorerInput);
		}

	})
	, PERFORMANCE("Show Performance Mode", new NetViewFactory() {

		public NetView newInstance(ExplorerContext context, ExplorerUpdater updater,
				ExplorerModel explorerInput) {
			return new NetViewPerformanceColoredImpl(context, updater, explorerInput);
		}

	}), FITNESS("Show Fitness Mode", new NetViewFactory() {

		public NetView newInstance(ExplorerContext context, ExplorerUpdater updater,
				ExplorerModel explorerInput) {
			return new NetViewFitnessImpl(context, updater, explorerInput);
		}

	}), PRECISION("Show Precision Mode", new NetViewFactory() {

		public NetView newInstance(ExplorerContext context, ExplorerUpdater updater,
				ExplorerModel explorerInput) {
			return new NetViewPrecisionImpl(context, updater, explorerInput);
		}

	});

	private final String desc;
	private final NetViewFactory netViewFactory;

	private ViewMode(String desc, NetViewFactory netViewFactory) {
		this.desc = desc;
		this.netViewFactory = netViewFactory;
	}

	public NetViewFactory getViewFactory() {
		return netViewFactory;
	}

	public String toString() {
		return desc;
	}

}