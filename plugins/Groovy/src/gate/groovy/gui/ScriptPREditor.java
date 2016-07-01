/*
 * ScriptPREditor
 * 
 * Copyright (c) 2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 * 
 * Mark A. Greenwood, 20/05/2011
 */
package gate.groovy.gui;

import gate.Resource;
import gate.creole.AbstractVisualResource;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.GuiType;
import gate.event.ProgressListener;
import gate.groovy.ScriptPR;
import gate.gui.MainFrame;
import gate.util.Files;
import gate.util.GateRuntimeException;
import groovy.ui.ConsoleTextEditor;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A VR for viewing and editing the script behind a Groovy Scripting PR
 * 
 * @author Mark A. Greenwood
 */
@CreoleResource(name = "Script Editor", comment = "Editor for the Groovy script behind this PR", guiType = GuiType.LARGE, mainViewer = true, resourceDisplayed = "gate.groovy.ScriptPR")
public class ScriptPREditor extends AbstractVisualResource implements
                                                          ProgressListener,
                                                          DocumentListener {

  private static final long serialVersionUID = -3397858340190887863L;

  private ConsoleTextEditor editor;

  private ScriptPR pr;

  private File file;

  private JButton btnSave, btnRevert;

  public Resource init() {
    initGuiComponents();

    return this;
  }

  protected void initGuiComponents() {
    setLayout(new BorderLayout());

    // create a Groovy source code editor, register to listen for changes to the
    // source code and then add it as the main component of the VR
    editor = new ConsoleTextEditor();
    editor.getTextEditor().getDocument().addDocumentListener(this);
    add(editor, BorderLayout.CENTER);

    // create the "Save and Reinitialize" button
    btnSave =
            new JButton("Save and Reinitialize",
                    MainFrame.getIcon("crystal-clear-app-download-manager"));
    btnSave.addActionListener(new ActionListener() {

      public void actionPerformed(ActionEvent e) {
        try {
          // when the save button is clicked write the contents of the editor
          // out into the script file the PR is backed by and then...
          FileWriter out = new FileWriter(file);
          out.write(editor.getTextEditor().getText());
          out.flush();
          out.close();

          // re-init the PR so the script is reloaded
          pr.reInit();

          // we have reloaded so there won't be any changes that can be
          // saved/reverted so disable the buttons
          btnRevert.setEnabled(false);
          btnSave.setEnabled(false);
        } catch(Exception ioe) {
          ioe.printStackTrace();
        }
      }
    });

    // create the "Revert Changes" button
    btnRevert =
            new JButton("Revert Changes",
                    MainFrame.getIcon("crystal-clear-action-reload"));
    btnRevert.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        // when the button is clicked just set the source back to that storred
        // in the PR instance
        editor.getTextEditor().setText(pr.getGroovySrc());

        // we have reverted so there won't be any changes that can be
        // saved/reverted so disable the buttons
        btnRevert.setEnabled(false);
        btnSave.setEnabled(false);
      }
    });

    // add both buttons to a toolbar at the top of the VR
    JToolBar toolbar = new JToolBar(JToolBar.HORIZONTAL);
    toolbar.setFloatable(false);
    toolbar.add(btnSave);
    toolbar.add(Box.createHorizontalStrut(5));
    toolbar.add(btnRevert);
    add(toolbar, BorderLayout.NORTH);
  }

  public void setTarget(Object target) {

    // make sure we are being given a target that we know how to display
    if(target == null) return;
    if(!(target instanceof ScriptPR)) { throw new GateRuntimeException(this
            .getClass().getName()
            + " can only be used to display "
            + ScriptPR.class.getName()
            + "\n"
            + target.getClass().getName()
            + " is not a " + ScriptPR.class.getName() + "!"); }

    // if this VR is being reused then stop listening to changes from the
    // previous target
    if(pr != null) {
      pr.removeProgressListener(this);
    }

    // store the PR we are displaying so we can keep refering to it
    pr = (ScriptPR)target;

    // get the script file or null if loaded from a non-file url
    try {
      file = Files.fileFromURL(pr.getScriptURL());
    } catch(Exception e) {
      file = null;
    }

    // get the editor to display the script
    editor.getTextEditor().setText(pr.getGroovySrc());

    // disable editing if we loaded from a URL
    editor.getTextEditor().setEditable(file != null);
    btnSave.setEnabled(false);
    btnRevert.setEnabled(false);

    // listen out for updates to the PR so we can keep in sync
    pr.addProgressListener(this);
  }

  public void progressChanged(int i) {
    // do nothing and wait until the progress has finished
  }

  public void processFinished() {
    SwingUtilities.invokeLater(new Thread() {
      public void run() {
        setTarget(pr);
      }
    });    
  }

  public void changedUpdate(DocumentEvent e) {
    // ignore these events
  }

  public void insertUpdate(DocumentEvent e) {
    // the script has changed so enable the toolbar buttons
    btnRevert.setEnabled(true);
    btnSave.setEnabled(file != null);
  }

  public void removeUpdate(DocumentEvent e) {
    // the script has changed so enable the toolbar buttons
    btnRevert.setEnabled(true);
    btnSave.setEnabled(file != null);
  }
}
