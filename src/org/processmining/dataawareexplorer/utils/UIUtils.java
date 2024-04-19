/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.utils;

import java.awt.Color;

import javax.swing.JLabel;

public class UIUtils {

	// Colors from www.ColorBrewer.org by Cynthia A. Brewer, Geography, Pennsylvania State University under Apache 2.0 License
	public final static Color[] CATEGORICAL_COLOR_SET = new Color[] { new Color(228, 26, 28), new Color(55, 126, 184),
			new Color(77, 175, 74), new Color(152, 78, 163), new Color(255, 127, 0), new Color(255, 255, 51) };

	// Colors from www.ColorBrewer.org by Cynthia A. Brewer, Geography, Pennsylvania State University under Apache 2.0 License
	public static final Color[] SEQ_PALETTE_YlOrRd = new Color[] { new Color(255, 255, 229), new Color(255, 255, 204),
			new Color(255, 237, 160), new Color(254, 217, 118), new Color(254, 178, 76), new Color(253, 141, 60),
			new Color(252, 78, 42), new Color(227, 26, 28), new Color(189, 0, 38), new Color(128, 0, 38)

	};

	public static Color colorGradient(Color darkColor, Color lightColor, float factor) {
		float[] hsb = Color.RGBtoHSB(darkColor.getRed(), darkColor.getGreen(), darkColor.getBlue(), null);
		float[] hsb2 = Color.RGBtoHSB(lightColor.getRed(), lightColor.getGreen(), lightColor.getBlue(), null);
		float brightness = 1f;
		float saturation = 1 - factor;
		float minHueFactor = 0.0f;
		float maxHueFactor = 1.0f;
		float hue = hsb[0] + (hsb2[0] - hsb[0]) * ((factor - minHueFactor) / (maxHueFactor - minHueFactor));
		return Color.getHSBColor(hue, saturation, brightness);
	}

	public static Color colorGradientFromPalette(Color[] colorPalette, float factor) {
		if (factor >= 1.0) {
			// Special case
			return Color.WHITE;
		}
		float bucketSize = 1.0f / colorPalette.length;
		int maxIndex = colorPalette.length - 1;
		int minIndex = 0;
		int bucket = Math.min(maxIndex, Math.max(minIndex, (int) Math.floor(factor / bucketSize)));
		return colorPalette[bucket];
	}
	
	public static String wrapLineWithSeparator(String escapeXml, String separator, int maxLineLength) {
		StringBuilder sb = new StringBuilder(escapeXml);
		int i = 0;
		while ((i = sb.indexOf(" ", i + maxLineLength)) != -1) {
			sb.replace(i, i + 1, separator);
		}
		escapeXml = sb.toString();
		return escapeXml;
	}

	public static JLabel createHeading(String label) {
		JLabel heading = new JLabel(label);
		heading.setFont(heading.getFont().deriveFont(14.0f));
		return heading;
	}

	public static JLabel createSubHeading(String label) {
		JLabel subheading = new JLabel(label);
		subheading.setFont(subheading.getFont().deriveFont(12.0f));
		return subheading;
	}

}
