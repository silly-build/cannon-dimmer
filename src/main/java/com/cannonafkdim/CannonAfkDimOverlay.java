package com.cannonafkdim;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class CannonAfkDimOverlay extends Overlay
{
	private final Client client;
	private final CannonAfkDimPlugin plugin;
	private final CannonAfkDimConfig config;

	@Inject
	public CannonAfkDimOverlay(Client client, CannonAfkDimPlugin plugin, CannonAfkDimConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.shouldRenderOverlay())
		{
			return null;
		}

		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();

		if (width <= 0 || height <= 0)
		{
			return null;
		}

		int opacity = plugin.getCurrentDimOpacity();

		if (opacity <= 0)
		{
			return null;
		}

		graphics.setColor(new Color(0, 0, 0, opacity));
		graphics.fillRect(0, 0, width, height);

		renderText(graphics, width, height);

		return new Dimension(width, height);
	}

	private void renderText(Graphics2D graphics, int width, int height)
	{
		List<String> lines = new ArrayList<>();

		if (config.showCannonballText())
		{
			String prefix = config.textPrefix();

			if (prefix == null || prefix.trim().isEmpty())
			{
				prefix = "Cannonballs";
			}

			lines.add(prefix.trim() + ": " + plugin.getCannonballsLeft());
		}

		if (config.showEstimatedTime())
		{
			String estimatedTimeText = plugin.getEstimatedTimeText();

			if (estimatedTimeText != null && !estimatedTimeText.trim().isEmpty())
			{
				lines.add(estimatedTimeText.trim());
			}
		}

		if (lines.isEmpty())
		{
			return;
		}

		Font originalFont = graphics.getFont();
		Color originalColor = graphics.getColor();

		Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, config.textSize()));
		graphics.setFont(font);

		FontMetrics metrics = graphics.getFontMetrics(font);

		int lineHeight = metrics.getHeight();
		int totalTextHeight = lineHeight * lines.size();

		int y = (height - totalTextHeight) / 2 + metrics.getAscent();

		graphics.setColor(Color.WHITE);

		for (String line : lines)
		{
			int lineX = (width - metrics.stringWidth(line)) / 2;
			graphics.drawString(line, lineX, y);
			y += lineHeight;
		}

		graphics.setFont(originalFont);
		graphics.setColor(originalColor);
	}
}