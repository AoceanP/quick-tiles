/*
 * Copyright (c) 2026, AoceanP <https://github.com/AoceanP>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.quicktiles;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The Quick Tiles sidebar: colours, groups, undo, export and import, and every hotkey with a
 * Change button. Everything here can also be done in game or in the settings; this is the
 * quick way.
 */
class QuickTilesPanel extends PluginPanel
{
	private static final Color MUTED = new Color(168, 168, 168);
	private static final Color SUBTLE = new Color(118, 118, 118);
	private static final Color GOLD = new Color(255, 198, 64);
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color ROW = new Color(24, 24, 24);

	/**
	 * What the panel shows, read on the client thread.
	 */
	static final class State
	{
		final List<Color> colours = new ArrayList<>();
		int brush;
		List<String> groups = Collections.emptyList();
		Map<String, Boolean> hidden = new HashMap<>();
		Map<String, int[]> counts = new HashMap<>();
		@Nullable
		String activeGroup;
		boolean canUndo;
		boolean canRedo;
	}

	private final QuickTilesPlugin plugin;
	private final QuickTilesConfig config;
	private final ConfigManager configManager;
	private final JPanel content = new JPanel();
	private State state = new State();
	// The hotkey being changed, waiting for a key press, or null.
	@Nullable
	private String capturing;

	QuickTilesPanel(QuickTilesPlugin plugin, QuickTilesConfig config, ConfigManager configManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.configManager = configManager;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		add(content, BorderLayout.NORTH);
		rebuild();
	}

	void update(State newState)
	{
		state = newState;
		if (capturing == null)
		{
			rebuild();
		}
	}

	private void rebuild()
	{
		content.removeAll();
		content.add(title());
		content.add(gap(8));
		content.add(colourCard());
		content.add(gap(8));
		content.add(groupCard());
		content.add(gap(8));
		content.add(toolRow());
		content.add(gap(8));
		content.add(hotkeyCard());
		content.add(gap(8));
		content.add(hint());
		content.revalidate();
		content.repaint();
	}

	// ---- sections ----

	private JPanel title()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(label("Quick Tiles", Color.WHITE, true), BorderLayout.WEST);
		return fullWidth(row);
	}

	private JPanel colourCard()
	{
		JPanel card = card();
		card.add(header("Colour", "Next: " + keyText(config.colourHotkey())));
		JPanel swatches = new JPanel(new GridLayout(1, QuickTilesPlugin.COLOUR_COUNT, 4, 0));
		swatches.setOpaque(false);
		for (int i = 0; i < state.colours.size(); i++)
		{
			int index = i;
			JLabel swatch = new JLabel(new Swatch(state.colours.get(i), i == state.brush));
			swatch.setPreferredSize(new Dimension(22, 22));
			swatch.setToolTipText("Colour " + (i + 1) + (i == state.brush ? " (marking in this)" : ""));
			swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			swatch.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					plugin.setBrush(index);
				}
			});
			swatches.add(swatch);
		}
		card.add(fullWidth(swatches));
		return card;
	}

	private JPanel groupCard()
	{
		JPanel card = card();
		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.add(label("Groups", Color.WHITE, true), BorderLayout.WEST);
		JLabel add = link("+ New group", () ->
		{
			String name = JOptionPane.showInputDialog(this, "Name for the new group:", "New group", JOptionPane.PLAIN_MESSAGE);
			if (name != null)
			{
				plugin.getClientThread().invoke(() -> plugin.addGroup(name));
			}
		});
		head.add(add, BorderLayout.EAST);
		card.add(fullWidth(head));
		card.add(gap(4));
		JLabel into = label("New tiles go into: " + TileGroups.display(state.activeGroup), MUTED, false);
		into.setToolTipText("Click a group's name to put new tiles in it. " + keyText(config.groupHotkey()) + " switches too.");
		card.add(fullWidth(row(into)));
		card.add(gap(4));

		card.add(groupRow(null));
		for (String name : state.groups)
		{
			card.add(gap(2));
			card.add(groupRow(name));
		}
		if (state.groups.isEmpty())
		{
			card.add(gap(4));
			card.add(fullWidth(row(label("Make groups for bosses or areas, then show or hide each one.", SUBTLE, false))));
		}
		return card;
	}

	private JPanel groupRow(@Nullable String name)
	{
		boolean hidden = Boolean.TRUE.equals(state.hidden.get(TileGroups.key(name)));
		boolean active = TileGroups.key(name).equals(TileGroups.key(state.activeGroup));
		int[] count = state.counts.getOrDefault(TileGroups.key(name), new int[2]);

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ROW);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		JLabel eye = new JLabel(new Eye(!hidden));
		eye.setToolTipText(hidden ? "Hidden. Click to show." : "Shown. Click to hide.");
		eye.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		eye.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				plugin.getClientThread().invoke(() -> plugin.setGroupHidden(name, !hidden));
			}
		});
		row.add(eye, BorderLayout.WEST);

		JLabel title = label(TileGroups.display(name), hidden ? SUBTLE : (active ? GOLD : Color.WHITE), active);
		title.setToolTipText(active ? "New tiles go into this group" : "Click to put new tiles in this group");
		title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		title.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					plugin.getClientThread().invoke(() -> plugin.setActiveGroup(name));
				}
			}
		});
		row.add(title, BorderLayout.CENTER);

		JLabel tiles = label(count[1] > 0 ? count[0] + " (" + count[1] + " here)" : String.valueOf(count[0]), MUTED, false);
		tiles.setToolTipText(count[0] + " tiles in all, " + count[1] + " around you right now");
		row.add(tiles, BorderLayout.EAST);

		JPopupMenu menu = new JPopupMenu();
		menu.add(item("Put new tiles here", () -> plugin.getClientThread().invoke(() -> plugin.setActiveGroup(name))));
		menu.add(item("Export these tiles", () -> plugin.getClientThread().invoke(() ->
			plugin.getSharing().copy(plugin.groupTiles(name), "in " + TileGroups.display(name)))));
		if (name != null)
		{
			menu.addSeparator();
			menu.add(item("Rename...", () ->
			{
				Object input = JOptionPane.showInputDialog(this, "New name for " + name + ":", "Rename group",
					JOptionPane.PLAIN_MESSAGE, null, null, name);
				if (input != null)
				{
					plugin.getClientThread().invoke(() -> plugin.renameGroup(name, input.toString()));
				}
			}));
			menu.add(item("Delete group, keep its tiles", () ->
				plugin.getClientThread().invoke(() -> plugin.deleteGroup(name, false))));
			menu.add(item("Delete group and its tiles...", () ->
			{
				int answer = JOptionPane.showConfirmDialog(this, "Delete " + name + " and its " + count[0] + " tiles?"
					+ "\nUndo can bring them back this session.", "Delete group", JOptionPane.YES_NO_OPTION);
				if (answer == JOptionPane.YES_OPTION)
				{
					plugin.getClientThread().invoke(() -> plugin.deleteGroup(name, true));
				}
			}));
		}
		row.setComponentPopupMenu(menu);
		title.setComponentPopupMenu(menu);
		tiles.setComponentPopupMenu(menu);
		return fullWidth(row);
	}

	private JPanel toolRow()
	{
		JPanel grid = new JPanel(new GridLayout(2, 2, 4, 4));
		grid.setOpaque(false);
		grid.add(button("Undo", "Undo your last mark or paint stroke (" + keyText(config.undoHotkey()) + ")", state.canUndo,
			() -> plugin.getClientThread().invoke(plugin::undo)));
		grid.add(button("Redo", "Put back what you undid (" + keyText(config.redoHotkey()) + ")", state.canRedo,
			() -> plugin.getClientThread().invoke(plugin::redo)));
		JLabel[] export = new JLabel[1];
		export[0] = button("Export...", "Copy tiles to paste elsewhere, or to share", true,
			() -> exportMenu().show(export[0], 0, export[0].getHeight()));
		grid.add(export[0]);
		grid.add(button("Import", "Add tiles from your clipboard (Quick Tiles, Ground Markers or a tile pack)", true,
			() -> plugin.getClientThread().invoke(() -> plugin.getSharing().promptImport())));
		JPanel wrap = new JPanel(new BorderLayout(0, 4));
		wrap.setOpaque(false);
		wrap.add(grid, BorderLayout.NORTH);
		return fullWidth(wrap);
	}

	private JPopupMenu exportMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		menu.add(item("Tiles around you", () -> plugin.getClientThread().invoke(() -> plugin.getSharing().exportTiles())));
		JMenu group = new JMenu("A group");
		group.add(item(TileGroups.UNGROUPED, () -> plugin.getClientThread().invoke(() ->
			plugin.getSharing().copy(plugin.groupTiles(null), "ungrouped"))));
		for (String name : state.groups)
		{
			group.add(item(name, () -> plugin.getClientThread().invoke(() ->
				plugin.getSharing().copy(plugin.groupTiles(name), "in " + name))));
		}
		menu.add(group);
		return menu;
	}

	private JPanel hotkeyCard()
	{
		JPanel card = card();
		card.add(header("Hotkeys", "Click Change to set one"));
		for (String[] hotkey : QuickTilesPlugin.hotkeys())
		{
			card.add(gap(3));
			card.add(hotkeyRow(hotkey[0], hotkey[1]));
		}
		return card;
	}

	private JPanel hotkeyRow(String key, String name)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.add(label(name, Color.WHITE, false), BorderLayout.CENTER);

		JPanel right = new JPanel(new GridLayout(1, 2, 4, 0));
		right.setOpaque(false);
		boolean waiting = key.equals(capturing);
		JLabel value = label(waiting ? "Press a key" : keyText(plugin.hotkey(key)), waiting ? GOLD : Color.WHITE, false);
		value.setHorizontalAlignment(SwingConstants.CENTER);
		value.setOpaque(true);
		value.setBackground(ROW);
		value.setBorder(BorderFactory.createLineBorder(waiting ? GOLD : ColorScheme.MEDIUM_GRAY_COLOR));
		right.add(value);

		JLabel change = button(waiting ? "Cancel" : "Change", waiting ? "Esc cancels, Backspace clears the key"
			: "Click, then press the key you want", true, () ->
		{
			capturing = waiting ? null : key;
			rebuild();
			if (capturing != null)
			{
				focusChange(key);
			}
		});
		change.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (!key.equals(capturing))
				{
					return;
				}
				e.consume();
				if (Keybind.getModifierForKeyCode(e.getKeyCode()) != null)
				{
					// Waiting for the key that goes with Ctrl, Alt or Shift.
					return;
				}
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					capturing = null;
					rebuild();
					return;
				}
				Keybind chosen = e.getKeyCode() == KeyEvent.VK_BACK_SPACE ? Keybind.NOT_SET : new Keybind(e);
				String clash = clash(key, chosen);
				if (clash != null)
				{
					value.setText("Used by " + clash);
					value.setForeground(new Color(235, 110, 100));
					return;
				}
				capturing = null;
				configManager.setConfiguration(QuickTilesConfig.GROUP, key, chosen);
				rebuild();
			}
		});
		change.setName(key);
		right.add(change);
		right.setPreferredSize(new Dimension(122, 22));
		row.add(right, BorderLayout.EAST);
		return fullWidth(row);
	}

	/**
	 * After a rebuild, puts keyboard focus on the Change button that's waiting for a key.
	 */
	private void focusChange(String key)
	{
		javax.swing.SwingUtilities.invokeLater(() -> find(content, key));
	}

	private boolean find(Component c, String key)
	{
		if (c instanceof JLabel && key.equals(c.getName()))
		{
			c.requestFocusInWindow();
			return true;
		}
		if (c instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) c).getComponents())
			{
				if (find(child, key))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @return the name of another action already on that key, or null
	 */
	@Nullable
	private String clash(String key, Keybind chosen)
	{
		if (chosen.equals(Keybind.NOT_SET))
		{
			return null;
		}
		for (String[] other : QuickTilesPlugin.hotkeys())
		{
			if (!other[0].equals(key) && chosen.equals(plugin.hotkey(other[0])))
			{
				return other[1];
			}
		}
		return null;
	}

	private JPanel hint()
	{
		JLabel text = label("<html><div style='width:165px'>Right-click a tile to mark it. Shift + right-click a "
			+ "marked tile for its label, note, group and colour.</div></html>", SUBTLE, false);
		return fullWidth(row(text));
	}

	// ---- building blocks ----

	/**
	 * Fills the width and takes only the height it needs, however its contents change.
	 */
	private static final class Wide extends JPanel
	{
		Wide(java.awt.LayoutManager layout)
		{
			super(layout);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	private static JPanel card()
	{
		JPanel card = new Wide(null);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD);
		card.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
		card.setAlignmentX(LEFT_ALIGNMENT);
		return card;
	}

	private static JPanel header(String title, String right)
	{
		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.add(label(title, Color.WHITE, true), BorderLayout.WEST);
		head.add(label(right, MUTED, false), BorderLayout.EAST);
		head.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
		return fullWidth(head);
	}

	private static JPanel row(Component c)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(c, BorderLayout.CENTER);
		return row;
	}

	private static JPanel fullWidth(JPanel panel)
	{
		JPanel wide = new Wide(new BorderLayout());
		wide.setOpaque(false);
		wide.add(panel, BorderLayout.CENTER);
		return wide;
	}

	private static Component gap(int height)
	{
		JPanel gap = new JPanel();
		gap.setOpaque(false);
		gap.setPreferredSize(new Dimension(0, height));
		gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		gap.setAlignmentX(LEFT_ALIGNMENT);
		return gap;
	}

	private static JLabel label(String text, Color colour, boolean bold)
	{
		JLabel label = new JLabel(text);
		label.setFont(bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		return label;
	}

	private static JLabel link(String text, Runnable action)
	{
		JLabel link = label(text, GOLD, false);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}
		});
		return link;
	}

	private static final Color BUTTON = new Color(52, 52, 52);
	private static final Color BUTTON_HOVER = new Color(70, 70, 70);

	/**
	 * A flat dark button that reads the same in every look and feel.
	 */
	private static JLabel button(String text, String tooltip, boolean enabled, @Nullable Runnable action)
	{
		JLabel button = new JLabel(text, SwingConstants.CENTER);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setOpaque(true);
		button.setBackground(BUTTON);
		button.setForeground(enabled ? Color.WHITE : SUBTLE);
		button.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 4));
		button.setToolTipText(tooltip);
		button.setFocusable(true);
		if (enabled)
		{
			button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			button.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (action != null && e.getButton() == MouseEvent.BUTTON1)
					{
						action.run();
					}
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					button.setBackground(BUTTON_HOVER);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					button.setBackground(BUTTON);
				}
			});
		}
		return button;
	}

	private static JMenuItem item(String text, Runnable action)
	{
		JMenuItem item = new JMenuItem(text);
		item.addActionListener(e -> action.run());
		return item;
	}

	/**
	 * "Ctrl+A", or "Not set".
	 */
	static String keyText(Keybind keybind)
	{
		return keybind == null || keybind.equals(Keybind.NOT_SET) ? "Not set" : keybind.toString();
	}

	/**
	 * A colour square, outlined in white when it's the one you're marking in.
	 */
	private static final class Swatch implements Icon
	{
		private final Color colour;
		private final boolean selected;

		Swatch(Color colour, boolean selected)
		{
			this.colour = colour;
			this.selected = selected;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue()));
			g.fillRect(x + 3, y + 3, 16, 16);
			if (selected)
			{
				g.setColor(Color.WHITE);
				g.drawRect(x + 1, y + 1, 19, 19);
				g.drawRect(x, y, 21, 21);
			}
		}

		@Override
		public int getIconWidth()
		{
			return 22;
		}

		@Override
		public int getIconHeight()
		{
			return 22;
		}
	}

	/**
	 * Shown (a filled dot) or hidden (an empty ring).
	 */
	private static final class Eye implements Icon
	{
		private final boolean shown;

		Eye(boolean shown)
		{
			this.shown = shown;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(shown ? Color.WHITE : SUBTLE);
			g2.drawOval(x + 1, y + 1, 10, 10);
			if (shown)
			{
				g2.fillOval(x + 4, y + 4, 5, 5);
			}
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 13;
		}

		@Override
		public int getIconHeight()
		{
			return 13;
		}
	}
}
