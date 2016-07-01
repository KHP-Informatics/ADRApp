/*
 *  Ngram.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: Ngram.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import gate.util.GateException;
import org.jdom.Element;

/**
 * Desribing the NGAM features defined in the DATASET element of the
 * configuration file.
 */
public class Ngram {
  /** Name of the Ngram feature. */
  private String name;
  /** The N in the N-gram. */
  private short number;
  /** How many GATE features used for the N-gram. */
  private short consnum;
  /** The GATE types of the features used in the N-gram. */
  private String[] typesGate = null;
  /** The GATE features used in the N-gram. */
  private String[] featuresGate = null;
  /**
   * The posistion of the annotation considered relative to the current instance
   * annotation. Normally it should be 0.
   */
  int position;
  /** The weight of ngram. */
  float weight=1.0f;

  /**
   * Load the N-gram definition from an XML element of configuration file.
   */
  public Ngram(Element jdomElement) throws GateException {
    // find the name
    Element anElement = jdomElement.getChild("NAME");
    if(anElement == null)
      throw new GateException(
        "Required element \"NAME\" not present in attribute:\n"
          + jdomElement.toString() + "!");
    else name = anElement.getTextTrim();
    name = name.replaceAll(ConstantParameters.ITEMSEPARATOR,
      ConstantParameters.ITEMSEPREPLACEMENT);
    // find how many tokens (N) are used for the Ngram
    anElement = jdomElement.getChild("NUMBER");
    if(anElement == null)
      throw new GateException(
        "Required element \"NUMBER\" not present in attribute:\n"
          + jdomElement.toString() + "!");
    else number = (new Short(anElement.getTextTrim())).shortValue();
    // find how many constituents are used for the each token
    anElement = jdomElement.getChild("CONSNUM");
    if(anElement == null)
      throw new GateException(
        "Required element \"CONSNUM\" not present in attribute:\n"
          + jdomElement.toString() + "!");
    else consnum = (new Short(anElement.getTextTrim())).shortValue();
    // find the position if present
    anElement = jdomElement.getChild("POSITION");
    if(anElement == null)
      position = 0;
    else position = Integer.parseInt(anElement.getTextTrim());
    //Find the weight if present
    anElement = jdomElement.getChild("WEIGHT");
    if(anElement == null)
      weight = 1.0f;
    else weight = Float.parseFloat(anElement.getTextTrim());
    // allocate memory for the types and features for all the
    // constituents
    typesGate = new String[consnum];
    featuresGate = new String[consnum];
    for(int i = 0; i < consnum; ++i) {
      // find the type
      anElement = jdomElement.getChild("CONS-" + (i + 1));
      if(anElement == null)
        throw new GateException(
          "Required element \"TYPE\" not present in attribute:\n"
            + jdomElement.toString() + "!");
      else {
        obtainTypeAndFeat(anElement, typesGate, featuresGate, i);
      }
    }
  }

  /** Obtain the types and features of one N-gram definition. */
  private void obtainTypeAndFeat(Element anElement, String[] typesGate,
    String[] featuresGate, int i) throws GateException {
    Element lowerElement = anElement.getChild("TYPE");
    if(lowerElement != null) {
      typesGate[i] = lowerElement.getTextTrim();
      typesGate[i] = typesGate[i].replaceAll(ConstantParameters.ITEMSEPARATOR,
        ConstantParameters.ITEMSEPREPLACEMENT);
    } else throw new GateException(
      "Required element \"TYPE\" not present in attribute!");
    lowerElement = anElement.getChild("FEATURE");
    if(lowerElement != null) {
      featuresGate[i] = lowerElement.getTextTrim();
      featuresGate[i] = featuresGate[i]
        .replaceAll(ConstantParameters.ITEMSEPARATOR,
          ConstantParameters.ITEMSEPREPLACEMENT);
    } else throw new GateException(
      "Required element \"FEATURE\" not present in attribute!");
  }

  public Ngram() {
    name = null;
    typesGate = null;
    featuresGate = null;
    number = 0;
    consnum = 0;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setNumber(short number) {
    this.number = number;
  }

  public short getNumber() {
    return number;
  }

  public void setConsnum(short consnum) {
    this.consnum = consnum;
  }

  public short getConsnum() {
    return consnum;
  }

  public void setTypesGate(String[] typesGate) {
    this.typesGate = typesGate;
  }

  public String[] getTypessGate() {
    return typesGate;
  }

  public String[] setFeaturesGate(String[] featuresGate) {
    return this.featuresGate = featuresGate;
  }

  public String[] getFeaturesGate() {
    return featuresGate;
  }

  public String toString() {
    StringBuffer res = new StringBuffer();
    res.append("Name: " + name + "\n");
    res.append("Number: " + this.number + "\n");
    res.append("Consnum: " + this.consnum + "\n");
    for(int i = 0; i < typesGate.length; ++i) {
      res.append("cons-" + (i + 1) + "\n");
      res.append("Types: " + typesGate[i] + "\n");
      res.append("Features: " + featuresGate[i] + "\n");
    }
    return res.toString();
  }
}
