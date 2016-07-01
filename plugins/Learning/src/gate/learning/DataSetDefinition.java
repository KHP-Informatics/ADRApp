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
 *
 *  $Id: DatasetDefintion.java 6974, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 *
 */
package gate.learning;

import gate.util.GateException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jdom.Element;

/**
 * Stores data described in the DATASET element of configuration file.
 */
public class DataSetDefinition {
  /** List of ATTRIBUTE type features. */
  protected java.util.List<Attribute>attributes;
  /** ATTRIBUTE for the class label. */
  protected Attribute classAttribute = null;
  /** Instance type. */
  protected String instanceType;
  /** Class attibute index in the attribute list. */
  protected int classIndex;
  /** List of Ngram type features. */
  protected List<Ngram> ngrams;
  /** Data set for relation learning or others. */
  public short dataType;
  /** Chunking learning data type. */
  public final static short ChunkLearningData = 1;
  /** Text classification data type. */
  public final static short ClassificationData = 2;
  /** Relation learning data type. */
  public final static short RelationData = 3;
  /** The arrays and variables for fast computations. */
  ArraysDataSetDefinition arrs;
  // The variables for relation extraction
  /** The feature in instance for the first argument of relation. */
  String arg1Feat;
  /** The feature in instance for the second argument of relation. */
  String arg2Feat;
  /** The first argument of relation. */
  ArgOfRelation arg1 = null;
  /** The second argument of relation. */
  ArgOfRelation arg2 = null;
  /** List of ATTRIBUTE_REL type features. */
  protected List<AttributeRelation> relAttributes;
  /** Is the same window size for all NLP features. */
  public boolean isSameWinSize=false;
  /** The common window size. */
  public int windowSizeLeft = 1;
  public int windowSizeRight = 1;
  /** value type of the ngram feature, 1--binary, 2--tf, 3--tf*idf. */
  public int valueTypeNgram =3;
 
  /**
   * Constructor A DataSetDefinition is built using an XML element in
   * configuration file.
   */
  public DataSetDefinition(Element domElement) throws GateException {
    if(!domElement.getName().equals("DATASET"))
      throw new GateException("Dataset defintion element is \""
        + domElement.getName() + "\" instead of \"DATASET\"!");
    // find instance the type
    Element anElement = domElement.getChild("INSTANCE-TYPE");
    if(anElement != null)
      instanceType = anElement.getTextTrim();
    else throw new GateException(
      "Required element \"INSTANCE-TYPE\" not present!");
    //Check if use the same window size to speed up preprocessing
    windowSizeLeft = 1;
    windowSizeRight = 1;
    anElement = domElement.getChild("WINDOWSIZE");
    if(anElement != null) {
      isSameWinSize = true;
      String value;
      value = anElement.getAttributeValue("windowSizeLeft");
      if(value!= null) 
        windowSizeLeft = Integer.parseInt(value);
      value = anElement.getAttributeValue("windowSizeRight");
      if(value != null) 
        windowSizeRight = Integer.parseInt(value);
    } else {
      isSameWinSize = false;
    }
   
    //  Check if specify the value type of ngram feature
    valueTypeNgram = 3;
    anElement = domElement.getChild("ValueTypeNgram");
    if(anElement != null) {
      valueTypeNgram = Integer.parseInt(anElement.getTextTrim());
    }
    // Check the dataset definition file is for relation extraction or
    // not
    anElement = domElement.getChild("INSTANCE-ARG1");
    if(anElement != null) { //
      dataType = RelationData;
      arg1Feat = anElement.getTextTrim();
      anElement = domElement.getChild("INSTANCE-ARG2");
      if(anElement != null)
        arg2Feat = anElement.getTextTrim();
      else throw new GateException(
        "Required element \"INSTANCE-ARG2\" not present!");
      // Get the features associated with arg1
      anElement = domElement.getChild("FEATURES-ARG1");
      if(anElement != null) {// Features for the first argument.
        arg1 = new ArgOfRelation();
        Element element1 = anElement.getChild("ARG");
        if(element1 != null) {
          arg1.type = element1.getChild("TYPE").getTextTrim();
          arg1.feat = element1.getChild("FEATURE").getTextTrim();
        } else throw new GateException(
          "Required element \"ARG\" in \"FEATURES-ARG1\" not present!");
        // Find the attribute features of the argument
        obtainArgumentFeatures(anElement, arg1);
        // Put the type and feat of data types into some arrays for fast
        // computation
        arg1.arrs = new ArraysDataSetDefinition();
        arg1.arrs.putTypeAndFeatIntoArray(arg1.attributes);
        arg1.arrs.numNgrams = arg1.ngrams.size();
        // Get the maximal posistion
        arg1.maxTotalPosition = obtainMaxTotalPosisiont(arg1);
      }
      // Get the features associated with arg2
      anElement = domElement.getChild("FEATURES-ARG2");
      if(anElement != null) {// Features for the first argument.
        arg2 = new ArgOfRelation();
        Element element1 = anElement.getChild("ARG");
        if(element1 != null) {
          arg2.type = element1.getChild("TYPE").getTextTrim();
          arg2.feat = element1.getChild("FEATURE").getTextTrim();
        } else throw new GateException(
          "Required element \"ARG\" in \"FEATURES-ARG1\" not present!");
        // Find the attribute features of the argument
        obtainArgumentFeatures(anElement, arg2);
        // Put the type and feat of data types into some arrays for fast
        // computation
        arg2.arrs = new ArraysDataSetDefinition();
        arg2.arrs.putTypeAndFeatIntoArray(arg2.attributes);
        arg2.arrs.numNgrams = arg2.ngrams.size();
        // Get the maximal posistion
        arg2.maxTotalPosition = obtainMaxTotalPosisiont(arg2);
      }
      // find the relation attributes
      int attrIndex = 0;
      relAttributes = new ArrayList<AttributeRelation>();
      Iterator<?> childrenIter = domElement.getChildren("ATTRIBUTE_REL")
        .iterator();
      while(childrenIter.hasNext()) {
        Element child = (Element)childrenIter.next();
        AttributeRelation relAttribute = new AttributeRelation(child);
        if(relAttribute.isClass()) {
          if(classAttribute != null)
            throw new GateException(
              "RelAttribute \""
                + relAttribute.getName()
                + "\" marked as class attribute but the class is already known to be\""
                + classAttribute.getName() + "\"!");
          classAttribute = relAttribute;
          classIndex = attrIndex;
        }
        relAttributes.add(relAttribute);
        attrIndex++;
      }
      arrs = new ArraysDataSetDefinition();
      arrs.putTypeAndFeatIntoArray(relAttributes);
      // get the args for the relation attribute terms
      arrs.obtainArgs(relAttributes);
    } else {// for other types of learning
      dataType = ChunkLearningData;
      // find the attributes
      int attrIndex = 0;
      attributes = new ArrayList<Attribute>();
      Iterator<?> childrenIter = domElement.getChildren("ATTRIBUTE").iterator();
      while(childrenIter.hasNext()) {
        Element child = (Element)childrenIter.next();
        Attribute attribute = new Attribute(child);
        if(attribute.isClass()) {
          if(classAttribute != null)
            throw new GateException(
              "Attribute \""
                + attribute.getName()
                + "\" marked as class attribute but the class is already known to be\""
                + classAttribute.getName() + "\"!");
          classAttribute = attribute;
          classIndex = attrIndex;
        }
        attributes.add(attribute);
        attrIndex++;
      }
      Iterator<?> childrenSerieIter = domElement.getChildren("ATTRIBUTELIST")
        .iterator();
      while(childrenSerieIter.hasNext()) {
        Element child = (Element)childrenSerieIter.next();
        if(isSameWinSize) {
          anElement = child.getChild("RANGE");
          if(anElement == null) {
            Element rangeElement = new Element("RANGE");
            rangeElement.setAttribute("from", new Integer(this.windowSizeLeft*(-1)).toString());
            rangeElement.setAttribute("to", new Integer(this.windowSizeRight).toString());
            child.addContent(rangeElement);
          } else {
            anElement.setAttribute("from", new Integer(this.windowSizeLeft*(-1)).toString());
            anElement.setAttribute("to", new Integer(this.windowSizeRight).toString());
          }
          
        }
        List<Attribute>attributelist = Attribute.parseSerie(child);
        /*if(isSameWinSize) {
          //if(attributelist.size()>0) {
            //Attribute att0 = (Attribute)attributelist.get(0);
            //att0.position=0;
            //attributes.add(att0);
            //++attrIndex;
          //}
          
          
         for(int i=0; i<attributelist.size(); ++i) { 
           Attribute att0 = (Attribute)attributelist.get(i);
           if(att0.position == 0) {
             attributes.add(att0);
             ++attrIndex;
             break;
           }
         }
         
        } else {
          //attributes.addAll(attributelist);
          //attrIndex += attributelist.size();
        }*/
        attributes.addAll(attributelist);
        attrIndex += attributelist.size();
      }
      if(classAttribute == null)
        System.out.println("!! Warning: No class attribute defined! You CANNOT learn, but it's OK for producing the feature files.");
      // find the Ngrams
      ngrams = new ArrayList<Ngram>();
      childrenIter = domElement.getChildren("NGRAM").iterator();
      while(childrenIter.hasNext()) {
        Element child = (Element)childrenIter.next();
        Ngram ngram = new Ngram(child);
        ngrams.add(ngram);
      }
      arrs = new ArraysDataSetDefinition();
      arrs.putTypeAndFeatIntoArray(attributes);
      arrs.numNgrams = ngrams.size();
    }
    if(LogService.minVerbosityLevel > 1)
      System.out.println("*** dataType=" + dataType + " classType="
        + arrs.classType + " classFeat=" + arrs.classFeature);
  }

  int obtainMaxTotalPosisiont(ArgOfRelation arg1) {
    int maxP = 0;
    int maxN = 0;
    for(int i = 0; i < arg1.attributes.size(); ++i) {
      if(arg1.attributes.get(i).position > maxP)
        maxP = arg1.attributes.get(i).position;
      else if(arg1.attributes.get(i).position < maxN)
        maxN = arg1.attributes.get(i).position;
    }
    for(int i = 0; i < arg1.ngrams.size(); ++i) {
      if(arg1.ngrams.get(i).position > maxP)
        maxP = arg1.ngrams.get(i).position;
      else if(arg1.ngrams.get(i).position < maxN)
        maxN = arg1.ngrams.get(i).position;
    }
    // Minus for maxN because it's a negative number.
    return maxP - maxN;
  }

  /** Obtain the ATTRIBUTEs and other features of one argument. */
  private int obtainArgumentFeatures(Element domElement, ArgOfRelation argRel)
    throws GateException {
    int attrIndex = 0;
    argRel.attributes = new ArrayList<Attribute>();
    Iterator<?> childrenIter = domElement.getChildren("ATTRIBUTE").iterator();
    while(childrenIter.hasNext()) {
      Element child = (Element)childrenIter.next();
      Attribute attribute = new Attribute(child);
      argRel.attributes.add(attribute);
      attrIndex++;
    }
    Iterator<?> childrenSerieIter = domElement.getChildren("ATTRIBUTELIST")
      .iterator();
    while(childrenSerieIter.hasNext()) {
      Element child = (Element)childrenSerieIter.next();
      List<Attribute> attributelist = Attribute.parseSerie(child);
      argRel.attributes.addAll(attributelist);
      attrIndex += attributelist.size();
    }
    // find the Ngrams
    argRel.ngrams = new ArrayList<Ngram>();
    childrenIter = domElement.getChildren("NGRAM").iterator();
    while(childrenIter.hasNext()) {
      Element child = (Element)childrenIter.next();
      Ngram ngram = new Ngram(child);
      argRel.ngrams.add(ngram);
    }
    return attrIndex;
  }

  public String toString() {
    StringBuffer res = new StringBuffer();
    res.append("Instance type: " + instanceType + "\n");
    Iterator<?> attrIter = attributes.iterator();
    while(attrIter.hasNext()) {
      res.append("Attribute:" + attrIter.next().toString() + "\n");
    }
    res.append("Ngrams\n");
    attrIter = ngrams.iterator();
    while(attrIter.hasNext()) {
      res.append("Ngram:" + attrIter.next().toString() + "\n");
    }
    return res.toString();
  }

  public List<Attribute> getAttributes() {
    return attributes;
  }

  public Attribute getClassAttribute() {
    return classAttribute;
  }

  public String getInstanceType() {
    return instanceType;
  }

  public int getClassIndex() {
    return classIndex;
  }

  public List<Ngram> getNgrams() {
    return ngrams;
  }

  public void setClassAttribute(Attribute classAttribute) {
    this.classAttribute = classAttribute;
  }

  public void setClassIndex(int classIndex) {
    this.classIndex = classIndex;
  }

  public void setInstanceType(String instanceType) {
    this.instanceType = instanceType;
  }
}
