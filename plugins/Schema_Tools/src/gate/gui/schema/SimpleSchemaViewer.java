/*
 * SimpleSchemaViewer.java
 * 
 * Copyright (c) 2010, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 2, June 1991
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 * 
 * Mark A. Greenwood, 17/09/2010
 */

package gate.gui.schema;

import gate.Resource;
import gate.creole.ANNIEConstants;
import gate.creole.AbstractVisualResource;
import gate.creole.AnnotationSchema;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.GuiType;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.PlainDocument;
import javax.swing.text.PlainView;
import javax.swing.text.Segment;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.Utilities;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

@CreoleResource(name = "Simple Schema Viewer", interfaceName = "gate.VisualResource", comment = "A Simple Annotation Schema Viewer", resourceDisplayed = "gate.creole.AnnotationSchema", mainViewer = true, guiType = GuiType.LARGE)
public class SimpleSchemaViewer extends AbstractVisualResource implements
                                                              ANNIEConstants {
  private JTextPane textArea;

  @Override
  public Resource init() {

    setLayout(new BorderLayout());
    textArea = new JTextPane() {
      {
        this.setEditorKitForContentType("text/xml", new XmlEditorKit());
        this.setContentType("text/xml");
        this.setEditable(false);
      }
    };

    JScrollPane textScroll =
            new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    add(textScroll, BorderLayout.CENTER);

    return this;
  }

  @Override
  public void setTarget(Object target) {
    if(target == null || !(target instanceof AnnotationSchema)) { throw new IllegalArgumentException(
            "The GATE schema viewer can only be used with a GATE annotation schema!"
                    + (target != null ? "\n" + target.getClass().toString()
                            + " is not a GATE annotation schema!" : "")); }

    AnnotationSchema schema = (AnnotationSchema)target;

    textArea.setText(schema.toXSchema());
    textArea.updateUI();
  }

  // http://boplicity.nl/confluence/display/Java/Xml+syntax+highlighting+in+Swing+JTextPane

  class XmlEditorKit extends StyledEditorKit {

    private ViewFactory xmlViewFactory;

    public XmlEditorKit() {
      xmlViewFactory = new XmlViewFactory();
    }

    @Override
    public ViewFactory getViewFactory() {
      return xmlViewFactory;
    }

    @Override
    public String getContentType() {
      return "text/xml";
    }
  }

  class XmlViewFactory extends Object implements ViewFactory {

    /**
     * @see javax.swing.text.ViewFactory#create(javax.swing.text.Element)
     */
    public View create(Element element) {

      return new XmlView(element);
    }

  }

  private static HashMap<Pattern, Color> patternColors;

  private static String TAG_PATTERN = "(</?[a-zA-Z]*)\\s?>?";

  private static String TAG_END_PATTERN = "(/?>)";

  private static String TAG_ATTRIBUTE_PATTERN = "\\s(\\w*)\\=";

  private static String TAG_ATTRIBUTE_VALUE = "[a-z-]*\\=(\"[^\"]*\")";

  private static String TAG_COMMENT = "(<!--.*-->)";

  private static String TAG_CDATA_START = "(\\<!\\[CDATA\\[).*";

  private static String TAG_CDATA_END = ".*(]]>)";

  static {
    // NOTE: the order is important!
    patternColors = new HashMap<Pattern, Color>();
    patternColors.put(Pattern.compile(TAG_CDATA_START),
            new Color(128, 128, 128));
    patternColors.put(Pattern.compile(TAG_CDATA_END), new Color(128, 128, 128));
    patternColors.put(Pattern.compile(TAG_PATTERN), new Color(63, 127, 127));
    patternColors.put(Pattern.compile(TAG_ATTRIBUTE_PATTERN), new Color(127, 0,
            127));
    patternColors
            .put(Pattern.compile(TAG_END_PATTERN), new Color(63, 127, 127));
    patternColors.put(Pattern.compile(TAG_ATTRIBUTE_VALUE), new Color(42, 0,
            255));
    patternColors.put(Pattern.compile(TAG_COMMENT), new Color(63, 95, 191));
  }

  class XmlView extends PlainView {

    public XmlView(Element element) {
      super(element);

      // Set tabsize to 4 (instead of the default 8)
      getDocument().putProperty(PlainDocument.tabSizeAttribute, 4);
    }

    @Override
    protected int drawUnselectedText(Graphics graphics, int x, int y, int p0,
            int p1) throws BadLocationException {

      ((Graphics2D)graphics).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      
      Document doc = getDocument();
      String text = doc.getText(p0, p1 - p0);

      Segment segment = getLineBuffer();

      SortedMap<Integer, Integer> startMap = new TreeMap<Integer, Integer>();
      SortedMap<Integer, Color> colorMap = new TreeMap<Integer, Color>();

      // Match all regexes on this snippet, store positions
      for(Map.Entry<Pattern, Color> entry : patternColors.entrySet()) {

        Matcher matcher = entry.getKey().matcher(text);

        while(matcher.find()) {
          startMap.put(matcher.start(1), matcher.end());
          colorMap.put(matcher.start(1), entry.getValue());
        }
      }

      int i = 0;

      // Colour the parts
      for(Map.Entry<Integer, Integer> entry : startMap.entrySet()) {
        int start = entry.getKey();
        int end = entry.getValue();

        if (start >= i) {
          if(i < start) {
            graphics.setColor(Color.black);
            doc.getText(p0 + i, start - i, segment);
            x = Utilities.drawTabbedText(segment, x, y, graphics, this, i);
          }
  
          graphics.setColor(colorMap.get(start));
          i = end;
          doc.getText(p0 + start, i - start, segment);
          x = Utilities.drawTabbedText(segment, x, y, graphics, this, start);
        }
      }

      // Paint possible remaining text black
      if(i < text.length()) {
        graphics.setColor(Color.black);
        doc.getText(p0 + i, text.length() - i, segment);
        x = Utilities.drawTabbedText(segment, x, y, graphics, this, i);
      }

      return x;
    }
  }
}
