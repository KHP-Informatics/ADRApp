package gate.gui.jape.plus;

import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.GuiType;
import gate.gui.jape.JapeViewer;

@CreoleResource(name = "JAPE-Plus Viewer", comment = "A JAPE grammar file viewer", resourceDisplayed = "gate.jape.plus.Transducer", mainViewer = true, helpURL = "http://gate.ac.uk/userguide/chap:jape", guiType = GuiType.LARGE)
public class Viewer extends JapeViewer {

  private static final long serialVersionUID = -5950038358004951578L;
  // an empty file to allow the same VR to be used for two different PRs without
  // a common superclass
}