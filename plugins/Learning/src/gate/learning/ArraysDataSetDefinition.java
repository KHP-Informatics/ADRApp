/*
 *  ArraysDataSetDefinition.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: ArraysDataSetDefinition.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import java.util.List;

/**
 * Arrays and variables representing the features from one unit of data set
 * definition, for the purpose of fast computation.
 */
public class ArraysDataSetDefinition {
  /** Array of annotation types for all ATTRIBUTEs. */
  String[] typesInDataSetDef;
  /** Array of annotation featuress for all ATTRIBUTEs. */
  String[] featuresInDataSetDef;
  /** Array of names of all ATTRIBUTEs. */
  String[] namesInDataSetDef;
  /** Array of the semantic type of all ATTRIBUTEs*/
  char[] semanticTypeInDataSetDef;
  /**
   * Array of annotation types of all attributes in Argument 1 of a relation.
   */
  String[] arg1s;
  /**
   * Array of annotation types of all attributes in Argument 2 of a relation.
   */
  String[] arg2s;
  /** Position of the feature's annotation to the instance annotation. */
  int[] featurePosition;
  /** Number of ATTRIBUTEs in the dataset definition unit. */
  int numTypes = 0;
  /** Number of NGRAMs in the dataset definition unit. */
  int numNgrams = 0;
  /** Name of annotation type for class. */
  String classType;
  /** Name of annotation feature for class. */
  String classFeature;
  /** Name of feature in the instance annotation as argument 1 of the relation. */
  String classArg1;
  /** Name of feature in the instance annotation as argument 2 of the relation. */
  String classArg2;
  /**
   * The furthest left-hand position of the features, relative to the instance.
   */
  int maxNegPosition = 0;
  /**
   * The furthest right-hand position of the features, relative to the instance.
   */
  int maxPosPosition = 0;

  /** Put the types and feautures and others into the arrays. */
  void putTypeAndFeatIntoArray(List<? extends Attribute> attrs) {
    numTypes = obtainNumberOfNLPTypes(attrs);
    typesInDataSetDef = new String[numTypes];
    featuresInDataSetDef = new String[numTypes];
    namesInDataSetDef = new String[numTypes];
    featurePosition = new int[numTypes];
    semanticTypeInDataSetDef = new char[numTypes];
    // added allFeat;
    obtainGATETypesAndFeatures(attrs);
    // System.out.println("name0="+namesInDataSetDef[0]);
    for(int i = 0; i < numTypes; ++i) {
      if(featurePosition[i] < maxNegPosition)
        maxNegPosition = featurePosition[i];
      if(featurePosition[i] > maxPosPosition)
        maxPosPosition = featurePosition[i];
    }
    maxNegPosition = -maxNegPosition;
  }

  /** Get the number of features in the dataset definition unit. */
  static int obtainNumberOfNLPTypes(List<? extends Attribute> attrs) {
    int num = 0;
    if(attrs == null) {
      return num;
    } else {
      for(int i = 0; i < attrs.size(); i++) {
        if(!((Attribute)attrs.get(i)).isClass()) num++;
      }
      return num;
    }
  }

  /** Get the type, feature, name and position of each of attribute features. */
  void obtainGATETypesAndFeatures(List<? extends Attribute> attrs) {
    int num0 = 0;
    for(int i = 0; i < attrs.size(); i++) {
      Attribute attr = attrs.get(i);
      if(!attr.isClass()) {
        typesInDataSetDef[num0] = attr.getType();
        featuresInDataSetDef[num0] = attr.getFeature();
        namesInDataSetDef[num0] = attr.getName();
        featurePosition[num0] = attr.getPosition();
        semanticTypeInDataSetDef[num0]=attr.getSemantic_type();
        // System.out.println(new Integer(num0+1) + " " +
        // namesInDataSetDef[num0] + " "
        // + typesInDataSetDef[num0] + " " +
        // featuresInDataSetDef[num0]);
        ++num0;
      } else {
        classType = attr.getType();
        classFeature = attr.getFeature();
      }
    }
  }

  /**
   * Get the annotation features of the two arguments of relation for all the
   * ATTRIBUTE_RELs.
   */
  void obtainArgs(List<AttributeRelation> relAttrs) {
    int num0 = 0;
    arg1s = new String[numTypes];
    arg2s = new String[numTypes];
    for(int i = 0; i < relAttrs.size(); i++) {
      AttributeRelation attr = relAttrs.get(i);
      if(!attr.isClass()) {
        arg1s[num0] = attr.getArg1();
        arg2s[num0] = attr.getArg2();
        ++num0;
      } else {
        classArg1 = attr.getArg1();
        classArg2 = attr.getArg2();
      }
    }
  }
}
