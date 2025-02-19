/*
 * GrabKeyDialog.java - Grabs keys from the keyboard
 * Copyright (C) 2001 Slava Pestov
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

package org.gjt.sp.jedit.gui;

import javax.swing.border.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import org.gjt.sp.jedit.*;
import org.gjt.sp.util.Log;

/**
 * A dialog for getting shortcut keys.
 */
public class GrabKeyDialog extends JDialog
{
	/**
	 * Create and show a new modal dialog.
	 *
	 * @param  comp  center dialog on this component.
	 * @param  binding  the action/macro that should get a binding.
	 * @param  allBindings  all other key bindings.
	 * @since jEdit 3.2pre9
	 */
	public GrabKeyDialog(Component comp, KeyBinding binding,
		Vector allBindings)
	{
		super(JOptionPane.getFrameForComponent(comp),
			jEdit.getProperty("grab-key.title"),true);
		this.binding = binding;
		this.allBindings = allBindings;

		enableEvents(AWTEvent.KEY_EVENT_MASK);

		// create a panel with a BoxLayout. Can't use Box here
		// because Box doesn't have setBorder().
		JPanel content = new JPanel(new GridLayout(0,1,0,6))
		{
			/**
			 * Returns if this component can be traversed by pressing the
			 * Tab key. This returns false.
			 */
			public boolean isManagingFocus()
			{
				return false;
			}

			/**
			 * Makes the tab key work in Java 1.4.
			 * @since jEdit 3.2pre4
			 */
			public boolean getFocusTraversalKeysEnabled()
			{
				return false;
			}
		};
		content.setBorder(new EmptyBorder(12,12,12,12));
		setContentPane(content);

		JLabel label = new JLabel(jEdit.getProperty(
			"grab-key.caption",new String[] { binding.label }));

		Box input = Box.createHorizontalBox();

		shortcut = new InputPane();
		input.add(shortcut);
		input.add(Box.createHorizontalStrut(12));

		clear = new JButton(jEdit.getProperty("grab-key.clear"));
		clear.addActionListener(new ActionHandler());
		input.add(clear);

		assignedTo = new JLabel();
		updateAssignedTo(null);

		Box buttons = Box.createHorizontalBox();
		buttons.add(Box.createGlue());

		ok = new JButton(jEdit.getProperty("common.ok"));
		ok.addActionListener(new ActionHandler());
		buttons.add(ok);
		buttons.add(Box.createHorizontalStrut(12));

		if(binding.isAssigned()) {
			// show "remove" button
			remove = new JButton(jEdit.getProperty("grab-key.remove"));
			remove.addActionListener(new ActionHandler());
			buttons.add(remove);
			buttons.add(Box.createHorizontalStrut(12));
		}

		cancel = new JButton(jEdit.getProperty("common.cancel"));
		cancel.addActionListener(new ActionHandler());
		buttons.add(cancel);
		buttons.add(Box.createGlue());

		content.add(label);
		content.add(input);
		content.add(assignedTo);
		content.add(buttons);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		pack();
		setLocationRelativeTo(comp);
		setResizable(false);
		show();
	}

	/**
	 * Returns the shortcut, or null if the current shortcut should be
	 * removed or the dialog either has been cancelled. Use isOK()
	 * to determine if the latter is true.
	 */
	public String getShortcut()
	{
		if(isOK)
			return shortcut.getText();
		else
			return null;
	}

	/**
	 * Returns true, if the dialog has not been cancelled.
	 * @since jEdit 3.2pre9
	 */
	public boolean isOK()
	{
		return isOK;
	}

	/**
	 * Returns if this component can be traversed by pressing the
	 * Tab key. This returns false.
	 */
	public boolean isManagingFocus()
	{
		return false;
	}

	/**
	 * Makes the tab key work in Java 1.4.
	 * @since jEdit 3.2pre4
	 */
	public boolean getFocusTraversalKeysEnabled()
	{
		return false;
	}

	// protected members
	protected void processKeyEvent(KeyEvent evt)
	{
		shortcut.processKeyEvent(evt);
	}

	// private members

	private InputPane shortcut; // this is a bad hack
	private JLabel assignedTo;
	private JButton ok;
	private JButton remove;
	private JButton cancel;
	private JButton clear;
	private boolean isOK;
	private KeyBinding binding;
	private Vector allBindings;

	private String getSymbolicName(int keyCode)
	{
		if(keyCode == KeyEvent.VK_UNDEFINED)
			return null;
		/* else if(keyCode == KeyEvent.VK_OPEN_BRACKET)
			return "[";
		else if(keyCode == KeyEvent.VK_CLOSE_BRACKET)
			return "]"; */

		if(keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z)
			return String.valueOf(Character.toLowerCase((char)keyCode));

		try
		{
			Field[] fields = KeyEvent.class.getFields();
			for(int i = 0; i < fields.length; i++)
			{
				Field field = fields[i];
				String name = field.getName();
				if(name.startsWith("VK_")
					&& field.getInt(null) == keyCode)
				{
					return name.substring(3);
				}
			}
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,this,e);
		}

		return null;
	}

	private void updateAssignedTo(String shortcut)
	{
		String text = jEdit.getProperty("grab-key.assigned-to.none");
		KeyBinding kb = getKeyBinding(shortcut);

		if(kb != null)
			if(kb.isPrefix)
				text = jEdit.getProperty(
					"grab-key.assigned-to.prefix",
					new String[] { shortcut });
			else
				text = kb.label;

		if(ok != null)
			ok.setEnabled(kb == null || !kb.isPrefix);

		assignedTo.setText(
			jEdit.getProperty("grab-key.assigned-to",
				new String[] { text }));
	}

	private KeyBinding getKeyBinding(String shortcut)
	{
		if(shortcut == null || shortcut.length() == 0)
			return null;

		String spacedShortcut = shortcut + " ";
		Enumeration enum = allBindings.elements();

		while(enum.hasMoreElements())
		{
			KeyBinding kb = (KeyBinding)enum.nextElement();

			if(!kb.isAssigned())
				continue;

			String spacedKbShortcut = kb.shortcut + " ";

			// eg, trying to bind C+n C+p if C+n already bound
			if(spacedShortcut.startsWith(spacedKbShortcut))
				return kb;

			// eg, trying to bind C+e if C+e is a prefix
			if(spacedKbShortcut.startsWith(spacedShortcut))
			{
				// create a temporary (synthetic) prefix
				// KeyBinding, that won't be saved
				return new KeyBinding(kb.name,kb.label,
					shortcut,true);
			}
		}

		return null;
	}

	/**
	 * A jEdit action or macro with its two possible shortcuts.
	 * @since jEdit 3.2pre8
	 */
	public static class KeyBinding
	{
		public KeyBinding(String name, String label,
			String shortcut, boolean isPrefix)
		{
			this.name = name;
			this.label = label;
			this.shortcut = shortcut;
			this.isPrefix = isPrefix;
		}

		public String name;
		public String label;
		public String shortcut;
		public boolean isPrefix;

		public boolean isAssigned()
		{
			return shortcut != null && shortcut.length() > 0;
		}
	}

	class InputPane extends JTextField
	{
		/**
		 * Makes the tab key work in Java 1.4.
		 * @since jEdit 3.2pre4
		 */
		public boolean getFocusTraversalKeysEnabled()
		{
			return false;
		}

		protected void processKeyEvent(KeyEvent _evt)
		{
			if(_evt.getID() != KeyEvent.KEY_PRESSED)
				return;

			KeyEvent evt = KeyEventWorkaround.processKeyEvent(_evt);
			if(evt == null)
			{
				Log.log(Log.DEBUG,this,"Event " + _evt + " filtered");
				return;
			}
			else
				Log.log(Log.DEBUG,this,"Event " + _evt + " passed");

			evt.consume();

			StringBuffer keyString = new StringBuffer(getText());

			if(getDocument().getLength() != 0)
				keyString.append(' ');

			boolean appendPlus = false;

			// On MacOS, C+ is command, M+ is control
			// so that jEdit's default shortcuts are
			// mapped to the Command
			if(evt.isControlDown())
			{
				keyString.append(macOS ? 'M' : 'C');
				appendPlus = true;
			}

			if(evt.isAltDown())
			{
				keyString.append('A');
				appendPlus = true;
			}

			if(evt.isMetaDown())
			{
				keyString.append(macOS ? 'C' : 'M');
				appendPlus = true;
			}

			// don't want Shift+'d' recorded as S+D
			if(evt.getID() != KeyEvent.KEY_TYPED && evt.isShiftDown())
			{
				keyString.append('S');
				appendPlus = true;
			}

			if(appendPlus)
				keyString.append('+');

			int keyCode = evt.getKeyCode();

			String symbolicName = getSymbolicName(keyCode);

			if(symbolicName == null)
				return;

			keyString.append(symbolicName);

			setText(keyString.toString());
			updateAssignedTo(keyString.toString());
		}

		private boolean macOS = (System.getProperty("os.name")
			.indexOf("MacOS") != -1);
	}

	class ActionHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent evt)
		{
			if(evt.getSource() == ok)
			{
				if(canClose())
					dispose();
			}
			else if(evt.getSource() == remove)
			{
				shortcut.setText(null);
				isOK = true;
				dispose();
			}
			else if(evt.getSource() == cancel)
				dispose();
			else if(evt.getSource() == clear)
			{
				shortcut.setText(null);
				updateAssignedTo(null);
			}
		}

		private boolean canClose()
		{
			String shortcutString = shortcut.getText();
			if(shortcutString.length() == 0
				&& binding.isAssigned())
			{
				// ask whether to remove the old shortcut
				int answer = GUIUtilities.confirm(
					GrabKeyDialog.this,
					"grab-key.remove-ask",
					null,
					JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE);
				if(answer == JOptionPane.YES_OPTION)
				{
					shortcut.setText(null);
					isOK = true;
				}
				else if(answer == JOptionPane.CANCEL_OPTION)
					return false;
				return true;
			}

			// check whether this shortcut already exists
			KeyBinding other = getKeyBinding(shortcutString);
			if(other == null || other == binding)
			{
				isOK = true;
				return true;
			}

			// check whether the other shortcut is the alt. shortcut
			if(other.name == binding.name)
			{
				// we don't need two identical shortcuts
				GUIUtilities.error(GrabKeyDialog.this,
					"grab-key.duplicate-alt-shortcut",
					null);
				return false;
			}

			// check whether shortcut is a prefix to others
			if(other.isPrefix)
			{
				// can't override prefix shortcuts
				GUIUtilities.error(GrabKeyDialog.this,
					"grab-key.prefix-shortcut",
					null);
				return false;
			}

			// ask whether to override that other shortcut
			int answer = GUIUtilities.confirm(GrabKeyDialog.this,
				"grab-key.duplicate-shortcut",
				new Object[] { other.label },
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE);
			if(answer == JOptionPane.YES_OPTION)
			{
				if(other.shortcut != null
					&& shortcutString.startsWith(other.shortcut))
				{
					other.shortcut = null;
				}
				isOK = true;
				return true;
			}
			else if(answer == JOptionPane.CANCEL_OPTION)
				return false;

			return true;
		}
	}
}
