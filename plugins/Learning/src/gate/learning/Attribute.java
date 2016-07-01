/*
 *  Copyright (c) 1995-2012, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan 19/11/2002
 *  semantic type added by Mike Dowman 31-03-2004
 *  Weightings added by Mike Dowman 24-5-2004
 *  
 *
 *  $Id: Attribute.java 6974 2005-10-18 11:52:16 +0000 (Tue, 18 Oct 2005) nirajaswani $
 *
 */
package gate.learning;

import gate.util.GateException;
import java.util.ArrayList;
import org.jdom.Element;

/**
 * Desribe and read the ATTRIBUTE, one type of features in the dataset
 * definition.
 */
public class Attribute {
  // These constants are used only for returning values from
  // semanticType
  public static final char NOMINAL = 'N';
  public static final char NUMERIC = 'D';
  public static final char BOOLEAN = 'B';
  boolean isClass = false;
  String name;
  String type;
  String feature;
  int position;
  // Create a feature on an annotation generated
  // by an UniEngine with the confidence as value
  String confidence_feature;
  char semantic_type = Attribute.NOMINAL;
  // The SVMLightWrapper allows weighting for attributes to be specified
  // in
  // the configuration file, and those weightings are stored in this
  // member.
  // Weightings are (at time of writing) ignored by the Weka and Maxent
  // wrappers.
  double weighting;

  /**
   * Constuctor Create an Attribute object from an xml element.
   */
  public Attribute(Element jdomElement) throws GateException {
    // find the name
    Element anElement = jdomElement.getChild("NAME");
    if(anElement == null)
      throw new GateException(
        "Required element \"NAME\" not present in attribute:\n"
          + jdomElement.toString() + "!");
    else name = anElement.getTextTrim();
    // find the semantic type
    anElement = jdomElement.getChild("SEMTYPE");
    if(anElement == null)
      throw new GateException(
        "Required element \"SEMTYPE\" not present in attribute:\n"
          + jdomElement.toString() + "!");
    else {
        this.semantic_type = Attribute.NOMINAL;	//this is the default semantic type
      if(anElement.getTextTrim().equalsIgnoreCase("NOMINAL"))
        this.semantic_type = Attribute.NOMINAL;
      else if(anElement.getTextTrim().equalsIgnoreCase("NUMERIC"))
        this.semantic_type = Attribute.NUMERIC;
      else if(anElement.getTextTrim().equalsIgnoreCase("BOOLEAN"))
        this.semantic_type = Attribute.BOOLEAN;
    }
    // find the type
    anElement = jdomElement.getChild("TYPE");
    if(anElement == null)
      throw new GateException(
        "Required element \"TYPE\" not present in attribute:\n"
          + jdomElement.toString() + "!");
    else type = anElement.getTextTrim();
    // find the feature if present
    anElement = jdomElement.getChild("FEATURE");
    if(anElement != null) feature = anElement.getTextTrim();
    // find the position if present
    anElement = jdomElement.getChild("POSITION");
    if(anElement == null)
      position = 0;
    else position = Integer.parseInt(anElement.getTextTrim());
    // find the weighting if present
    anElement = jdomElement.getChild("WEIGHTING");
    if(anElement == null)
      weighting = 1.0;
    else weighting = Double.parseDouble(anElement.getTextTrim());
    //Replace the possible character "_" which was used as separator
    name = name.replaceAll(ConstantParameters.SEPARAPERINFeaturesName, 
      ConstantParameters.SEPFeatureNamesREPLACEMENT);
    // find the class if present
    // confidence_feature
    anElement = jdomElement.getChild("CLASS");
    isClass = anElement != null;
    
//System.out.println("semtype="+this.semantic_type + ",name="+this.name+", feat="+this.feature);

  }

  public Attribute() {
    name = null;
    type = null;
    feature = null;
    isClass = false;
    position = 0;
    weighting = 1.0;
    confidence_feature = null;
  }

  public String toString() {
    StringBuffer res = new StringBuffer();
    res.append("Name: " + name + "\n");
    res.append("SemType: " + this.semantic_type + "\n");
    res.append("Type: " + type + "\n");
    res.append("Feature: " + feature + "\n");
    res.append("Weighting: " + weighting + "\n");
    if(isClass) res.append("Class");
    return res.toString();
  }

  public boolean isClass() {
    return isClass;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  public void setFeature(String feature) {
    this.feature = feature;
  }

  public String getFeature() {
    return feature;
  }

  public void setWeighting(double weighting) {
    this.weighting = weighting;
  }

  public double getWeighting() {
    return weighting;
  }

  public int getPosition() {
    return position;
  }

  public void setClass(boolean isClass) {
    this.isClass = isClass;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public void setSemanticType(char type) {
    this.semantic_type = type;
  }

  public String getConfidence_feature() {
    return confidence_feature;
  }

  public void setConfidence_feature(String confidence_feature) {
    this.confidence_feature = confidence_feature;
  }

  public char getSemantic_type() {
    return semantic_type;
  }

  public void setSemantic_type(char semantic_type) {
    this.semantic_type = semantic_type;
  }

  /**
   * This method reports whether the attribute is nominal, numeric or boolean.
   * 
   * @return Attribute.NOMINAL, Attribute.NUMERIC or Attribute.BOOLEAN
   */
  public char semanticType() {
    return this.semantic_type;
  }

  public String toXML() {
    StringBuffer sb = new StringBuffer();
    sb.append("     ").append("<ATTRIBUTE>\n");
    sb.append("      ").append("<NAME>").append(this.name).append("</NAME>\n");
    sb.append("      ").append("<SEMTYPE>");
    if(this.semantic_type == Attribute.NOMINAL)
      sb.append("NOMINAL");
    else if(this.semantic_type == Attribute.BOOLEAN)
      sb.append("BOOLEAN");
    else sb.append("NUMERIC");
    sb.append("</SEMTYPE>\n");
    sb.append("      ").append("<TYPE>").append(this.type).append("</TYPE>\n");
    if(feature != null) {
      sb.append("      ").append("<FEATURE>").append(this.feature).append(
        "</FEATURE>\n");
    }
    sb.append("      ").append("<POSITION>").append(this.position).append(
      "</POSITION>\n");
    if(isClass) sb.append("      ").append("<CLASS/>\n");
    sb.append("     ").append("</ATTRIBUTE>\n");
    return sb.toString();
  }

  /**
   * This method is a clone of gate.creole.mi.Attribute.parseSerie method with
   * minor changes to make it compatible with ML API. It basically given an
   * attribute element first locates all required variable and creates multiple
   * attributes for the given RANGE.
   */
  public static java.util.List<Attribute> parseSerie(Element jdomElement)
    throws GateException {
    // find the name
    Element anElement = jdomElement.getChild("NAME");
    if(anElement == null)
      throw new GateException(
        "Required element \"NAME\" not present in attribute:\n"
          + jdomElement.toString() + "!");
    String name = anElement.getTextTrim();
    // find the semantic type
    anElement = jdomElement.getChild("SEMTYPE");
    if(anElement == null)
      throw new GateException(
        "Required element \"SEMTYPE\" not present in attribute:\n"
          + jdomElement.toString() + "!");
    char semantic_type = Attribute.NOMINAL;
    if(anElement.getTextTrim().equalsIgnoreCase("NUMERIC"))
      semantic_type = Attribute.NUMERIC;
    else if(anElement.getTextTrim().equalsIgnoreCase("BOOLEAN"))
      semantic_type = Attribute.BOOLEAN;
    // find the type
    anElement = jdomElement.getChild("TYPE");
    if(anElement == null)
      throw new GateException(
        "Required element \"TYPE\" not present in attribute:\n"
          + jdomElement.toString() + "!");
    String type = anElement.getTextTrim();
    String feature = null;
    // find the feature if present
    anElement = jdomElement.getChild("FEATURE");
    if(anElement != null) feature = anElement.getTextTrim();
    int minpos = 0;
    int maxpos = 0;
    // find the range of this element (e.g. from - to)
    anElement = jdomElement.getChild("RANGE");
    try {
      minpos = Integer.parseInt(anElement.getAttributeValue("from").trim());
      maxpos = Integer.parseInt(anElement.getAttributeValue("to").trim());
    } catch(Exception e) {
      throw new GateException("Range element is uncorrect:\n"
        + jdomElement.toString() + "!");
    }
    double weighting = 1.0;
    // find the weighting if present
    anElement = jdomElement.getChild("WEIGHTING");
    if(anElement != null)
      weighting = Double.parseDouble(anElement.getTextTrim());
    
    //  Replace the possible character "_" which was used as separator
    name = name.replaceAll(ConstantParameters.SEPARAPERINFeaturesName, 
      ConstantParameters.SEPFeatureNamesREPLACEMENT);
    // find the class if present
    boolean isClass = jdomElement.getChild("CLASS") != null;
    if(isClass) { throw new GateException(
      "Cannot define the class in a serie:\n" + jdomElement.toString() + "!"); }
    // Create a list of Attributes
    ArrayList<Attribute>attributes = new ArrayList<Attribute>();
    for(int position = minpos; position < maxpos + 1; position++) {
      Attribute attribute = new Attribute();
      attribute.setClass(false);
      attribute.setFeature(feature);
      attribute.setName(name);
      attribute.setPosition(position);
      attribute.setSemanticType(semantic_type);
      attribute.setType(type);
      attribute.setWeighting(weighting);
      attributes.add(attribute);
    }
    return attributes;
  }
}
