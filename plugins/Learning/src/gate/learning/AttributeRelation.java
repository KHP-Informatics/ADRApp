/*
 *  AttributeRelation.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: AttributeRelation.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import gate.util.GateException;

import org.jdom.Element;

/**
 * Extend the Attribute class a bit in order to accommodate the two arguments of
 * one relation.
 */
public class AttributeRelation extends Attribute {
  /** Annotation feature for argument 1. */
  private String arg1;
  /** Annotation feature for argument 2. */
  private String arg2;

  /**
   * Constuctor Create an AttributeRelation object from an xml element.
   */
  public AttributeRelation(Element jdomElement) throws GateException {
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
    // find the arg1 if present
    anElement = jdomElement.getChild("ARG1");
    if(anElement != null) arg1 = anElement.getTextTrim();
    // find the arg2 if present
    anElement = jdomElement.getChild("ARG2");
    if(anElement != null) arg2 = anElement.getTextTrim();
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
    // find the class if present
    // confidence_feature
    anElement = jdomElement.getChild("CLASS");
    isClass = anElement != null;
  }

  public AttributeRelation() {
    name = null;
    type = null;
    feature = null;
    arg1 = null;
    arg2 = null;
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
    res.append("Arg1: " + arg1 + "\n");
    res.append("Arg2: " + arg2 + "\n");
    res.append("Weighting: " + weighting + "\n");
    if(isClass) res.append("Class");
    return res.toString();
  }

  public void setArg1(String arg) {
    this.arg1 = arg;
  }

  public String getArg1() {
    return arg1;
  }

  public void setArg2(String arg) {
    this.arg2 = arg;
  }

  public String getArg2() {
    return arg2;
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
    if(arg1 != null) {
      sb.append("      ").append("<ARG1>").append(this.arg1)
        .append("</ARG1>\n");
    }
    if(arg2 != null) {
      sb.append("      ").append("<ARG2>").append(this.arg2)
        .append("</ARG2>\n");
    }
    sb.append("      ").append("<POSITION>").append(this.position).append(
      "</POSITION>\n");
    if(isClass) sb.append("      ").append("<CLASS/>\n");
    sb.append("     ").append("</ATTRIBUTE>\n");
    return sb.toString();
  }
}
