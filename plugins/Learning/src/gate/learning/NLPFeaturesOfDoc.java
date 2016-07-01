/*
 *  NLPFeaturesOfDoc.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: NLPFeaturesOfDoc.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import gate.Annotation;
import gate.AnnotationSet;
import gate.FeatureMap;
import gate.util.OffsetComparator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/*
 * Obtain the NLP (linguistic) features from the GATE annotations of one
 * document.
 */
public class NLPFeaturesOfDoc {
  /** One component stores all the features for one instance. */
  StringBuffer[] featuresInLine;
  /** Feature names and the semantic types for the feature used */
  StringBuffer featuresName;
  /** Document id. */
  private String docId = null;
  /** Number of instances in the document. */
  int numInstances = 0;
  /** Total number of GATE types of NLP features. */
  int totalnumTypes = 0;
  /** Number of features counted for each instance */
  int[] featuresCounted;
  /** store the class name for each instances in. */
  String[] classNames;

  /** Constructor with no parameters. */
  public NLPFeaturesOfDoc() {
    
  }
  /**
   * Constructor, obtain NLP features from GATE annotations for each instance in
   * the document.
   */
  public NLPFeaturesOfDoc(AnnotationSet annotations, String instanceType,
    String docName) {
    // Number of instances (tokens) in the document
    numInstances = annotations.get(instanceType).size();
    featuresInLine = new StringBuffer[numInstances];
    featuresName = new StringBuffer();
    totalnumTypes = 0;
    featuresCounted = new int[numInstances];

    classNames = new String[numInstances];
    docId = docName;
  }

  /**
   * Entry method for getting the NLP features according to the specifications
   * in the dataset defintion files.
   */
  public void obtainDocNLPFeatures(AnnotationSet annotations,
    DataSetDefinition dsd) {
    if(dsd.dataType == DataSetDefinition.RelationData) {
      // get the features for the relation data
      int initialPosition = 0;
      if(dsd.arg1 != null) {
        ArgOfRelation arg = dsd.arg1;
        boolean[][] isArgInRel = matchArgInstanceWithInst(annotations, dsd
          .getInstanceType(), arg.type, dsd.arg1Feat, arg.feat);
        if(arg.arrs.numTypes>0)
          gatedoc2NLPFeaturesArg(annotations, arg.type,
            arg.arrs.typesInDataSetDef, arg.arrs.featuresInDataSetDef,
            arg.arrs.namesInDataSetDef, arg.arrs.featurePosition, isArgInRel, initialPosition, arg.arrs.semanticTypeInDataSetDef);
        if(arg.arrs.numNgrams>0)
          gatedoc2NgramFeaturesArg(annotations, arg.type, arg.ngrams, isArgInRel, initialPosition);
        initialPosition += arg.maxTotalPosition+1;
      }
      if(dsd.arg2 != null) {
        ArgOfRelation arg = dsd.arg2;
        boolean[][] isArgInRel = matchArgInstanceWithInst(annotations, dsd
          .getInstanceType(), arg.type, dsd.arg2Feat, arg.feat);
        if(arg.arrs.numTypes>0)
          gatedoc2NLPFeaturesArg(annotations, arg.type,
            arg.arrs.typesInDataSetDef, arg.arrs.featuresInDataSetDef,
            arg.arrs.namesInDataSetDef, arg.arrs.featurePosition, isArgInRel, initialPosition, arg.arrs.semanticTypeInDataSetDef);
        if(arg.arrs.numNgrams>0)
          gatedoc2NgramFeaturesArg(annotations, arg.type, arg.ngrams, isArgInRel, initialPosition);
      }
      if(dsd.relAttributes != null)
        gatedoc2NLPFeaturesRel(annotations, dsd.getInstanceType(),
          dsd.arg1Feat, dsd.arg2Feat, dsd.arrs.typesInDataSetDef,
          dsd.arrs.featuresInDataSetDef, dsd.arrs.namesInDataSetDef,
          dsd.arrs.arg1s, dsd.arrs.arg2s, dsd.arrs.featurePosition,dsd.arrs.semanticTypeInDataSetDef);
      // get the label from the class attribute
      gatedoc2LabelsCompleteRel(annotations, dsd.getInstanceType(),
        dsd.arg1Feat, dsd.arg2Feat, dsd.arrs.classType, dsd.arrs.classFeature,
        dsd.arrs.classArg1, dsd.arrs.classArg2);
    } else {
      // get the NLP features from the attributes
      if(dsd.arrs.numTypes > 0)
        gatedoc2NLPFeatures(annotations, dsd.getInstanceType(),
          dsd.arrs.typesInDataSetDef, dsd.arrs.featuresInDataSetDef,
          dsd.arrs.namesInDataSetDef, dsd.arrs.featurePosition, dsd.arrs.semanticTypeInDataSetDef); // it
      if(dsd.arrs.numNgrams > 0)
        gatedoc2NgramFeatures(annotations, dsd.getInstanceType(), dsd
          .getNgrams());
      // get the label from the class attribute
      gatedoc2LabelsComplete(annotations, dsd.getInstanceType(),
        dsd.arrs.classType, dsd.arrs.classFeature);
    }
  }

  /** Get the N-gram features from the GATE document. */
  public void gatedoc2NgramFeatures(AnnotationSet annotations,
    String instanceType, List<Ngram> ngrams) {
    AnnotationSet anns = annotations.get(instanceType);
    List<Annotation> annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    if(numInstances != annotationArray.size()) {
      System.out.println("!!Warning: the number of instances "
        + numInstances + " in the document " + docId
        + " is not right!!!");
      return;
    }
    int numNgrams = ngrams.size();
    // For each ngram
    for(int i1 = 0; i1 < numNgrams; ++i1) {
      Ngram ngram = ngrams.get(i1);
      String nameOfNgram = ngram.getName();
      int ngramPosition = ngram.position;
      String positionStr = obtainPositionStr(ngramPosition);
      featuresName.append(nameOfNgram + ConstantParameters.ITEMSEPARATOR);
      int consNum= ngram.getConsnum();
      String [] typeGateNgram = new String[consNum];
      String [] featureGateNgram = new String[consNum];
      for(int j=0; j<consNum; ++j) {
        typeGateNgram[j] = (ngram.getTypessGate())[j];
        featureGateNgram[j] = (ngram.getFeaturesGate())[j];
      }
      AnnotationSet [] annsArray = new AnnotationSet[consNum];
      for(int j=0; j<consNum; ++j) {
        annsArray[j] = annotations.get(typeGateNgram[j]);
      }
      for(int i = 0; i < numInstances; ++i) {
        Annotation annToken = annotationArray.get(i);
        Long tokenStartOffset = annToken.getStartNode().getOffset();
        Long tokenEndOffset = annToken.getEndNode().getOffset();
        //AnnotationSet annsNgramType = annotations.get(typeGateNgram,
         // tokenStartOffset, tokenEndOffset);
        AnnotationSet annsNgramType = annsArray[0].get(tokenStartOffset, tokenEndOffset);
        String[] features;
        features = obtainNgramFeatures(annsNgramType,
          featureGateNgram[0]);
        int numFeats = features.length;
        int number = ngram.getNumber();
        if(numFeats>=number) { //if the instance has enough number of features for the defined ngram
        for(int j = 1; j < consNum; j++) {
          String[] features1;
          if(typeGateNgram[j].equals(typeGateNgram[0]))
            features1 = obtainNgramFeatures(annsNgramType, featureGateNgram[j]);
          else features1 = obtainNgramFeaturesFromDifferentType(annsNgramType,
            annsArray[j].get(tokenStartOffset, tokenEndOffset), 
            featureGateNgram[j]);
          for(int j1 = 0; j1 < features.length; ++j1)
            features[j1] = features[j1] + "_" + features1[j1];
        }
        // get the ngram features
        
        StringBuffer[] featuresNgram = new StringBuffer[numFeats - number + 1];
        for(int j = 0; j < featuresNgram.length; ++j)
          featuresNgram[j] = new StringBuffer();
        for(int j = 0; j < number; ++j) {
          for(int j1 = j; j1 < numFeats - number + 1 + j; ++j1) {
              featuresNgram[j1 - j].append(features[j1]
              + NLPFeaturesList.SYMBOLNGARM);
          }
        }
        Map<String,Integer> ngramTerms = new HashMap<String,Integer>();
        for(int j = 0; j < featuresNgram.length; ++j)
          if(!ngramTerms.containsKey(featuresNgram[j].toString()))
            ngramTerms.put(featuresNgram[j].toString(), 1);
          else ngramTerms.put(featuresNgram[j].toString(),ngramTerms.get(featuresNgram[j].toString()) + 1);
        List<String> keys = new ArrayList<String>(ngramTerms.keySet());
        Collections.sort(keys);
        Iterator<String> iterator = keys.iterator();
        if(featuresInLine[i] == null) featuresInLine[i] = new StringBuffer();
        while(iterator.hasNext()) {
          Object key = iterator.next();
          if(ngramPosition != 0)
            this.featuresInLine[i].append(obtainFeatureNameForNGRAM(nameOfNgram, key
              + NLPFeaturesList.SYMBOLNGARM + ngramTerms.get(key).toString())
              + positionStr + ConstantParameters.ITEMSEPARATOR);
          else this.featuresInLine[i].append(obtainFeatureNameForNGRAM(nameOfNgram, key
            + NLPFeaturesList.SYMBOLNGARM + ngramTerms.get(key).toString())
            + ConstantParameters.ITEMSEPARATOR);
          ++featuresCounted[i];
        }
        }//if the number of features is not less than the n of the n-gram
      }// end of the loop on instances
    } // end of the loop on number of ngrams
  }

  /**
   * Obtain the string for the position, which is attached at the end of the nlp
   * feature.
   */
  String obtainPositionStr(int ngramPosition) {
    return "[" + ngramPosition + "]";
  }

  /** Obtain the N-gram features from an annotation set. */
  private String[] obtainNgramFeatures(AnnotationSet annsNgramType,
    String gateFeature) {
    int num = annsNgramType.size();
    String[] feats = new String[num];
    List<Annotation> annotationArray = (annsNgramType == null || annsNgramType
      .isEmpty()) ? new ArrayList<Annotation>() : new ArrayList<Annotation>(annsNgramType);
    Collections.sort(annotationArray, new OffsetComparator());
    for(int i = 0; i < num; ++i) {
      feats[i] = (String)annotationArray.get(i).getFeatures()
        .get(gateFeature);
      if(feats[i]==null)
        feats[i] = ConstantParameters.NAMENONFEATURE;
      feats[i] = feats[i].trim().replaceAll(ConstantParameters.ITEMSEPARATOR,
        ConstantParameters.ITEMSEPREPLACEMENT);
    }
    return feats;
  }

  /**
   * Obtain the N-gram features from an annotation set for the Annotation type
   * which is different from the instance's type.
   */
  private String[] obtainNgramFeaturesFromDifferentType(
    AnnotationSet annsNgramType, AnnotationSet annsCurrent, String gateFeature) {
    int num = annsNgramType.size();
    String[] feats = new String[num];
    List<Annotation> annotationArray = (annsNgramType == null || annsNgramType
      .isEmpty()) ? new ArrayList<Annotation>() : new ArrayList<Annotation>(annsNgramType);
    Collections.sort(annotationArray, new OffsetComparator());
    for(int i = 0; i < num; ++i) {
      feats[i] = obtainAnnotationForTypeAndFeature(annsCurrent, gateFeature,
        annotationArray.get(i).getStartNode().getOffset(),
        annotationArray.get(i).getEndNode().getOffset());
      if(feats[i] != null)
        feats[i] = feats[i].trim().replaceAll(ConstantParameters.ITEMSEPARATOR,
        ConstantParameters.ITEMSEPREPLACEMENT);
    }
    return feats;
  }

  /** Get the labels of each instance in the document. */
  public void gatedoc2LabelsComplete(AnnotationSet annotations,
    String instanceType, String classType, String classFeature) {
    AnnotationSet anns = annotations.get(instanceType);
    List<Annotation> annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    if(numInstances != annotationArray.size()) {
      System.out.println("!!Warning: the number of instances "
        + numInstances + " in the document " + docId
        + " is not right!!!");
      return;
    }
    // For each of entity
    AnnotationSet annsEntity = annotations.get(classType);
    for(Object obj : annsEntity) {
      Annotation annEntity = (Annotation)obj;
      if(annEntity.getFeatures().get(classFeature) == null) continue;
      String featName = annEntity.getFeatures().get(classFeature).toString();
      featName = featName.trim();
      featName = featName.replaceAll(ConstantParameters.SUFFIXSTARTTOKEN,
        ConstantParameters.SUFFIXSTARTTOKEN + "_");
      featName = featName.replaceAll(ConstantParameters.ITEMSEPARATOR, "_");
      //Get the multilabel from one instance
      String [] featNameArray = featName.split(ConstantParameters.MULTILABELSEPARATOR); 
      boolean isStart = true;
      for(int i = 0; i < numInstances; ++i) {
        Annotation annToken = annotationArray.get(i);
        if(annToken.overlaps(annEntity)) {
          String featName0 = "";
          if(isStart) {
            for(int j=0; j<featNameArray.length; ++j) {
              if(j>0) featName0 += ConstantParameters.ITEMSEPARATOR;
              featName0 += featNameArray[j]+ConstantParameters.SUFFIXSTARTTOKEN;
            } 
            isStart = false;
          } else 
            for(int j=0; j<featNameArray.length; ++j) {
              if(j>0) featName0 += ConstantParameters.ITEMSEPARATOR;
              featName0 += featNameArray[j];
            }
          if(featName0.length() > 0) {
            if(this.classNames[i] != null)
              this.classNames[i] += ConstantParameters.ITEMSEPARATOR
                + featName0;
            else this.classNames[i] = featName0;
          }
        }
      }
    }
  }

  /** Get the Attribute feature for each instance of the document. */
  public void gatedoc2NLPFeatures(AnnotationSet annotations,
    String instanceType, String[] typesGate, String[] featuresGate,
    String[] namesGate, int[] featurePosition, char[] semanticTypesGate) {
    int numTypes = typesGate.length;
    this.totalnumTypes += numTypes;
    for(int i = 0; i < numTypes; ++i) {
  	
      this.featuresName.append(semanticTypesGate[i]+namesGate[i] + ConstantParameters.ITEMSEPARATOR);
    } /* get the semantic type and feature names for the attribute used */
    String[] positionArrStr = new String[numTypes];
    for(int i = 0; i < numTypes; ++i) {
      //if(featurePosition[i] != 0)
        positionArrStr[i] = obtainPositionStr(featurePosition[i]);
    }
    AnnotationSet anns = annotations.get(instanceType);
    List<Annotation>annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    String[] features = new String[numTypes];
    //int[] semanticTypes = new int[numTypes];
    int numInstances0 = annotationArray.size();
    AnnotationSet [] annsArray = new AnnotationSet[numTypes];
    for(int j=0; j<numTypes; ++j) {
      annsArray[j] = annotations.get(typesGate[j]);
    }
    for(int i = 0; i < numInstances0; ++i) {
      // for class
      Annotation annToken;
      for(int j = 0; j < numTypes; j++) {
        // for each attribute in different positions, get the token in
        // the corresponding position
        if(featurePosition[j] == 0)
          annToken = annotationArray.get(i);
        else if((featurePosition[j] < 0 && i + featurePosition[j] >= 0)
          || (featurePosition[j] > 0 && i + featurePosition[j] < numInstances0))
          annToken = annotationArray.get(i + featurePosition[j]);
        else continue;
        if(typesGate[j].equals(instanceType)) {
        	
        	//System.out.println(j+", feat="+featuresGate[j]);
        	
        	Object featObject = annToken.getFeatures().get(featuresGate[j]);
        	if(featObject != null) { 
                features[j] = featObject.toString();
        	} else {
        		features[j]=null;
        	}
        } else { // if not belongs to token
          Long tokenStartOffset = annToken.getStartNode().getOffset();
          Long tokenEndOffset = annToken.getEndNode().getOffset();
          features[j] = obtainAnnotationForTypeAndFeature(annsArray[j], featuresGate[j], tokenStartOffset,
           tokenEndOffset);
        }
        // put the name into the feature name
        if(features[j] != null) {
          features[j] = features[j].trim().replaceAll(
            ConstantParameters.ITEMSEPARATOR,
            ConstantParameters.ITEMSEPREPLACEMENT);
          features[j] = obtainFeatureName(namesGate[j], features[j], semanticTypesGate[j]);
        }
      }// end of the loop on the types
      int numCounted = 0;
      if(featuresInLine[i] == null) featuresInLine[i] = new StringBuffer();
      for(int j = 0; j < numTypes; ++j) {
        if(features[j] != null) {
          ++numCounted;
          //if(featurePosition[j]!=0)
            this.featuresInLine[i].append(features[j]
                 + positionArrStr[j]+ConstantParameters.ITEMSEPARATOR);
          /*else           
            this.featuresInLine[i].append(features[j]
            + ConstantParameters.ITEMSEPARATOR);*/
        } else {
          //if(featurePosition[j]!=0)
            this.featuresInLine[i].append(ConstantParameters.NAMENONFEATURE
              + positionArrStr[j]+ConstantParameters.ITEMSEPARATOR);
          /*else 
            this.featuresInLine[i].append(ConstantParameters.NAMENONFEATURE
              +ConstantParameters.ITEMSEPARATOR);*/
        }
        featuresCounted[i] += numCounted;
      }
    }// end of the loop on instances
  }
  /** Get the N-gram features from the GATE document. */
  public void gatedoc2NgramFeaturesArg(AnnotationSet annotations,
    String instanceType, List<Ngram> ngrams, boolean[][] isArgInRel, int initialPosition) {
    AnnotationSet anns = annotations.get(instanceType);
    List<Annotation>annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    int numInstances0 = annotationArray.size();
    int numNgrams = ngrams.size();
    // For each ngram
    for(int i1 = 0; i1 < numNgrams; ++i1) {
      Ngram ngram = ngrams.get(i1);
      String nameOfNgram = ngram.getName();
      int ngramPosition = ngram.position;
      if(ngramPosition>=0) ngramPosition += initialPosition;
      else ngramPosition -= initialPosition;
      String positionStr = obtainPositionStr(ngramPosition);
      featuresName.append(nameOfNgram + ConstantParameters.ITEMSEPARATOR);
      int consNum= ngram.getConsnum();
      //String typeGateNgram = (ngram.getTypessGate())[0];
      String [] typeGateNgram = new String[consNum];
      String [] featureGateNgram = new String[consNum];
      for(int j=0; j<consNum; ++j) {
        typeGateNgram[j] = (ngram.getTypessGate())[j];
        featureGateNgram[j] = (ngram.getFeaturesGate())[j];
      }
      AnnotationSet [] annsArray = new AnnotationSet[consNum];
      for(int j=0; j<consNum; ++j) {
        annsArray[j] = annotations.get(typeGateNgram[j]);
      }
      for(int i = 0; i < numInstances0; ++i) {
        Annotation annToken = annotationArray.get(i);
        Long tokenStartOffset = annToken.getStartNode().getOffset();
        Long tokenEndOffset = annToken.getEndNode().getOffset();
        AnnotationSet annsNgramType = annsArray[0].get(tokenStartOffset, tokenEndOffset);
        String[] features = obtainNgramFeatures(annsNgramType,
          featureGateNgram[0]);
        int numFeats = features.length;
        int number = ngram.getNumber();
        if(numFeats>=number) {
        for(int j = 1; j < consNum; j++) {
          String[] features1;
          if(typeGateNgram[j].equals(typeGateNgram[0]))
            features1 = obtainNgramFeatures(annsNgramType, featureGateNgram[j]);
          else features1 = obtainNgramFeaturesFromDifferentType(annsNgramType,
            annsArray[j].get(tokenStartOffset, tokenEndOffset), 
            featureGateNgram[j]);
          for(int j1 = 0; j1 < features.length; ++j1)
            features[j1] = features[j1] + "_" + features1[j1];
        }
        // get the ngram features
        
        StringBuffer[] featuresNgram = new StringBuffer[numFeats - number + 1];
        for(int j = 0; j < featuresNgram.length; ++j)
          featuresNgram[j] = new StringBuffer();
        for(int j = 0; j < number; ++j) {
          for(int j1 = j; j1 < numFeats - number + 1 + j; ++j1) {
            featuresNgram[j1 - j].append(features[j1]
              + NLPFeaturesList.SYMBOLNGARM);
          }
        }
        Map<String,Integer>ngramTerms = new HashMap<String,Integer>();
        for(int j = 0; j < featuresNgram.length; ++j)
          if(!ngramTerms.containsKey(featuresNgram[j].toString()))
            ngramTerms.put(featuresNgram[j].toString(), 1);
          else ngramTerms.put(featuresNgram[j].toString(),
            ngramTerms.get(featuresNgram[j].toString()) + 1);
        List<String>keys = new ArrayList<String>(ngramTerms.keySet());
        Collections.sort(keys);
        //Iterator iterator = keys.iterator();
        //while(iterator.hasNext()) {
        for(int iK=0; iK<keys.size(); ++iK) {
          //Object key = iterator.next();
          String key = keys.get(iK);
          //For each relation data with the current one as its argument
          for(int ii = 0; ii < numInstances; ++ii) {
            if(isArgInRel[i][ii]) {
              if(featuresInLine[ii] == null) 
                featuresInLine[ii] = new StringBuffer();
              if(ngramPosition != 0)
                this.featuresInLine[ii].append(obtainFeatureNameForNGRAM(nameOfNgram, key
                  + NLPFeaturesList.SYMBOLNGARM + ngramTerms.get(key))
                  + positionStr + ConstantParameters.ITEMSEPARATOR);
              else 
                this.featuresInLine[ii].append(obtainFeatureNameForNGRAM(nameOfNgram, key
                  + NLPFeaturesList.SYMBOLNGARM + ngramTerms.get(key))
                  + ConstantParameters.ITEMSEPARATOR);
              ++featuresCounted[ii];
            }
          }//for each instance
        }
      }
      }// end of the loop on instances
    } // end of the loop on number of ngrams
  }

  /** Get the NLP feature for the argument feature of relation data. */
  public void gatedoc2NLPFeaturesArg(AnnotationSet annotations,
    String instanceType, String[] typesGate, String[] featuresGate,
    String[] namesGate, int[] featurePosition, boolean[][] isArgInRel, 
    int initialPosition, char[] semanticTypesGate) {
    int numTypes = typesGate.length;
    this.totalnumTypes += numTypes;
    for(int i = 0; i < numTypes; ++i) {
      this.featuresName.append(semanticTypesGate[i]+namesGate[i] + ConstantParameters.ITEMSEPARATOR);
    }
    int [] positionNum = new int[numTypes];
    String[] positionArrStr = new String[numTypes];
    for(int i = 0; i < numTypes; ++i) {
      if(featurePosition[i]>=0)
        positionNum[i] = featurePosition[i] + initialPosition;
      else positionNum[i] = featurePosition[i] - initialPosition;
      if(positionNum[i] != 0)
        positionArrStr[i] = obtainPositionStr(featurePosition[i]);
    }
    AnnotationSet anns = annotations.get(instanceType);
    List<Annotation> annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    String[] features = new String[numTypes];
    int numInstances0 = annotationArray.size();
    AnnotationSet [] annsArray = new AnnotationSet[numTypes];
    for(int j=0; j<numTypes; ++j) {
      annsArray[j] = annotations
      .get(typesGate[j]);
    }
    for(int i = 0; i < numInstances0; ++i) {
      // for class
      Annotation annToken;
      for(int j = 0; j < numTypes; j++) {
        // for each attribute in different positions, get the token in
        // the corresponding position
        if(featurePosition[j] == 0)
          annToken = annotationArray.get(i);
        else if((featurePosition[j] < 0 && i + featurePosition[j] >= 0)
          || (featurePosition[j] > 0 && i + featurePosition[j] < numInstances0))
          annToken = annotationArray.get(i + featurePosition[j]);
        else continue;
        if(typesGate[j].equals(instanceType)) {
          features[j] = (String)annToken.getFeatures().get(featuresGate[j]);// types[i];
          // //(String)annToken.getFeatures().get(attr.getFeature());
        } else { // if not belongs to token
          Long tokenStartOffset = annToken.getStartNode().getOffset();
          Long tokenEndOffset = annToken.getEndNode().getOffset();
          features[j] = obtainAnnotationForTypeAndFeature(annsArray[j], featuresGate[j], tokenStartOffset,
            tokenEndOffset);
        }
        // put the name into the feature name
        if(features[j] != null) {
          features[j] = features[j].trim().replaceAll(
            ConstantParameters.ITEMSEPARATOR,
            ConstantParameters.ITEMSEPREPLACEMENT);
          features[j] = obtainFeatureName(namesGate[j], features[j], semanticTypesGate[j]);
        }
      }// end of the loop on the types
      // For each relation data with the current one as its argument
      for(int ii = 0; ii < numInstances; ++ii) {
        if(isArgInRel[i][ii]) {
          int numCounted = 0;
          if(featuresInLine[ii] == null)
            featuresInLine[ii] = new StringBuffer();
          for(int j = 0; j < numTypes; ++j) {
            if(features[j] != null) {
              ++numCounted;
              //if(positionNum[j]!=0)
                this.featuresInLine[ii].append(features[j]
                     + positionArrStr[j]+ ConstantParameters.ITEMSEPARATOR);
              /*else 
                this.featuresInLine[ii].append(features[j]
                     + ConstantParameters.ITEMSEPARATOR);*/
            } else 
              //if(positionNum[j]!=0)
                this.featuresInLine[ii].append(ConstantParameters.NAMENONFEATURE
                   + positionArrStr[j]+ConstantParameters.ITEMSEPARATOR);
              /*else
                this.featuresInLine[ii].append(ConstantParameters.NAMENONFEATURE
                + ConstantParameters.ITEMSEPARATOR);*/
          }
          featuresCounted[ii] += numCounted;
        }
      }
    }// end of the loop on instances
  }

  /** Match the argument instance with the relation instance. */
  boolean[][] matchArgInstanceWithInst(AnnotationSet annotations,
    String relInstanceType, String instanceType, String relArgF, String argF) {
    // Get the intance array
    AnnotationSet anns = annotations.get(instanceType);
    List<Annotation> annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    // Get the relation intance array
    AnnotationSet relAnns = annotations.get(relInstanceType);
   List<Annotation> relAnnotationArray = (relAnns == null || relAnns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(relAnns);
    Collections.sort(relAnnotationArray, new OffsetComparator());
    // Assign the match
    boolean[][] isArgInRel = new boolean[annotationArray.size()][relAnnotationArray
      .size()];
    for(int i = 0; i < annotationArray.size(); ++i) {
      Annotation ann = annotationArray.get(i);
      String argV = ann.getFeatures().get(argF).toString();
      for(int ii = 0; ii < relAnnotationArray.size(); ++ii) {
        String argRelV = relAnnotationArray.get(ii).getFeatures()
          .get(relArgF).toString();
        if(argV.equals(argRelV))
          isArgInRel[i][ii] = true;
        else isArgInRel[i][ii] = false;
      }
    }
    return isArgInRel;
  }

  /** Get the annotation with different type from the instance. */
  String obtainAnnotationForTypeAndFeature(AnnotationSet singleAnnSet,
    String gateFeature, Long tokenStartOffset, Long tokenEndOffset) {
    if(singleAnnSet != null) {
      AnnotationSet coverAnnSet = singleAnnSet.get(
        tokenStartOffset, tokenEndOffset);
      Iterator<Annotation> overlappingIterator = coverAnnSet.iterator();
      if(overlappingIterator.hasNext()) {
        Annotation superannotation = overlappingIterator.next();
        return (String)superannotation.getFeatures().get(gateFeature);
      }
    }
    return null;
  }

  /**
   * Get the annotation with different type from the instance for relation
   * learning.
   */
  String obtainAnnotationForTypeAndFeatureRel(String arg1V, String arg2V,
    AnnotationSet singleAnnSet, String gateFeature, String arg1F, String arg2F) {
    if(singleAnnSet != null) {
      Iterator<Annotation> overlappingIterator = singleAnnSet.iterator();
      if(overlappingIterator.hasNext()) {
        Annotation superannotation = overlappingIterator.next();
        FeatureMap feat0 = superannotation.getFeatures();
        if(arg1V.equals(feat0.get(arg1F)) && arg2V.equals(feat0.get(arg2F))) {
          String feat = feat0.get(gateFeature).toString();
          return feat;
        }
      }
    }
    return null;
  }

  /**
   * Get the Attribute-Rel features from annotations for relation learning.
   */
  public void gatedoc2NLPFeaturesRel(AnnotationSet annotations,
    String instanceType, String arg1Inst, String arg2Inst, String[] typesGate,
    String[] featuresGate, String[] namesGate, String[] arg1s, String[] arg2s,
    int[] featurePosition, char[] semanticTypesGate) {
    int numTypes = typesGate.length;
    this.totalnumTypes += numTypes;
    for(int i = 0; i < numTypes; ++i) {
      this.featuresName.append(semanticTypesGate[i]+namesGate[i] + ConstantParameters.ITEMSEPARATOR);
    }
    String [] strPosition = new String[numTypes];
    for(int i=0; i<numTypes; ++i) {
      if(featurePosition[i]!=0)
        strPosition[i] = obtainPositionStr(featurePosition[i]);
    }
    AnnotationSet anns = annotations.get(instanceType);
    List<Annotation> annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    if(numInstances != annotationArray.size()) {
      System.out.println("!!Warning: the number of instances "
        + numInstances + " in the document " + docId
        + " is not right!!!");
      return;
    }
    AnnotationSet [] annsArray = new AnnotationSet[numTypes];
    for(int j=0; j<numTypes; ++j) {
      annsArray[j] = annotations
      .get(typesGate[j]);
    }
    String[] features = new String[numTypes];
    for(int i = 0; i < numInstances; ++i) {
      // for class
      Annotation annToken;
      for(int j = 0; j < numTypes; j++) {
        // for each attribute in different positions, get the token in
        // the corresponding position
        if(featurePosition[j] == 0)
          annToken = annotationArray.get(i);
        else if((featurePosition[j] < 0 && i + featurePosition[j] >= 0)
          || (featurePosition[j] > 0 && i + featurePosition[j] < numInstances))
          annToken = annotationArray.get(i + featurePosition[j]);
        else continue;
        FeatureMap feat = annToken.getFeatures();
        String arg1Value = feat.get(arg1s[j]).toString();
        String arg2Value = feat.get(arg2s[j]).toString();
        if(typesGate[j].equals(instanceType)) {
          if(arg1Value.equals(feat.get(arg1Inst))
            && arg2Value.equals(feat.get(arg2Inst)))
            features[j] = feat.get(featuresGate[j]).toString();// types[i];
          // //(String)annToken.getFeatures().get(attr.getFeature());
        } else { // if not belongs to token
          features[j] = obtainAnnotationForTypeAndFeatureRel(arg1Value,
            arg2Value, annsArray[j], featuresGate[j],
            arg1s[j], arg2s[j]);
        }
        // put the name into the feature name
        if(features[j] != null) {
          features[j] = features[j].trim().replaceAll(
            ConstantParameters.ITEMSEPARATOR,
            ConstantParameters.ITEMSEPREPLACEMENT);
          features[j] = obtainFeatureName(namesGate[j], features[j], semanticTypesGate[j]);
        }
      }// end of the loop on the types
      int numCounted = 0;
      if(featuresInLine[i] == null) featuresInLine[i] = new StringBuffer();
      for(int j = 0; j < numTypes; ++j)
        if(features[j] != null) {
          ++numCounted;
          //if(featurePosition[j]!=0)
            this.featuresInLine[i].append(features[j]
                +strPosition[j]+ConstantParameters.ITEMSEPARATOR);
          /*else
            this.featuresInLine[i].append(features[j]
               + ConstantParameters.ITEMSEPARATOR);*/
        } else 
          //(featurePosition[j]!=0)
            this.featuresInLine[i].append(ConstantParameters.ITEMSEPREPLACEMENT+ Attribute.NOMINAL+ConstantParameters.NAMENONFEATURE
              +strPosition[j] + ConstantParameters.ITEMSEPARATOR);
          /*else
            this.featuresInLine[i].append(ConstantParameters.NAMENONFEATURE
              + ConstantParameters.ITEMSEPARATOR);*/
      featuresCounted[i] = numCounted;
    }// end of the loop on instances
  }

  /** Get the label for the relation learning. */
  public void gatedoc2LabelsCompleteRel(AnnotationSet annotations,
    String instanceType, String arg1Inst, String arg2Inst, String classType,
    String classFeature, String arg1C, String arg2C) {
    AnnotationSet anns = annotations.get(instanceType);
    List<Annotation> annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    if(numInstances != annotationArray.size()) {
      System.out.println("!!Warning: the number of instances "
        + new Integer(numInstances) + " in the document " + docId
        + " is not right!!!");
      return;
    }
    // For each of entity
    AnnotationSet annsEntity = annotations.get(classType);
    for(Object obj : annsEntity) {
      Annotation annEntity = (Annotation)obj;
      if(annEntity.getFeatures().get(classFeature) == null) continue;
      String featName = annEntity.getFeatures().get(classFeature).toString();
      featName = featName.trim();
      featName = featName.replaceAll(ConstantParameters.SUFFIXSTARTTOKEN,
        ConstantParameters.SUFFIXSTARTTOKEN + "_");
      // Get the values of the entity args
      String arg1CV = annEntity.getFeatures().get(arg1C).toString();
      String arg2CV = annEntity.getFeatures().get(arg2C).toString();
      boolean isStart = true;
      for(int i = 0; i < numInstances; ++i) {
        Annotation annToken = annotationArray.get(i);
        FeatureMap feats = annToken.getFeatures();
        if(arg1CV.equals(feats.get(arg1Inst))
          && arg2CV.equals(feats.get(arg2Inst))) {
          String featName0 = featName;
          if(isStart) {
            featName0 += ConstantParameters.SUFFIXSTARTTOKEN;
            isStart = false;
          }
          if(featName0.length() > 0) {
            if(this.classNames[i] != null)
              this.classNames[i] += ConstantParameters.ITEMSEPARATOR
                + featName0;
            else this.classNames[i] = featName0;
          }
        }
      }
    }
  }

  /** Write the NLP data into a file. */
  public void writeNLPFeaturesToFile(BufferedWriter out, String docId,
    int docIndex, int[] featurePosition) {
    if(LogService.minVerbosityLevel > 1)
      System.out.println("number=" + new Integer(numInstances));
    try {
      if(docIndex == 0) {
        StringBuffer sline = new StringBuffer("Class(es)");
        String[] featNs = this.featuresName.toString().split(
          ConstantParameters.ITEMSEPARATOR);
        for(int i = 0; i < featNs.length; ++i)
          if(featurePosition.length > i)
            sline.append(ConstantParameters.ITEMSEPARATOR + featNs[i] + "("
              + featurePosition[i] + ")");
          else sline.append(ConstantParameters.ITEMSEPARATOR + featNs[i]+"(0)");
        out.write(sline.toString());
        out.newLine();
      }
      out.write(new Integer(docIndex) + ConstantParameters.ITEMSEPARATOR + 
        docId + ConstantParameters.ITEMSEPARATOR
        + new Integer(numInstances));
      out.newLine();
      for(int i = 0; i < numInstances; ++i) {
        if(classNames[i] != null) {
          int num = classNames[i].split(ConstantParameters.ITEMSEPARATOR).length;
          out.write(num + ConstantParameters.ITEMSEPARATOR + classNames[i]
            + ConstantParameters.ITEMSEPARATOR
            + this.featuresInLine[i].toString().trim());
        } else out.write("0" + ConstantParameters.ITEMSEPARATOR
          + this.featuresInLine[i].toString().trim());
        out.newLine();
      }
    } catch(IOException e) {
      System.out.println("Error occured in writing the NLP data to a file!");
    }
  }
  
  /** Read the NLP data of one document from the NLP feature file. */
  public void readNLPFeaturesFromFile(BufferedReader in) {
    try {
      String [] lineItems = in.readLine().split(ConstantParameters.ITEMSEPARATOR);
      numInstances = Integer.parseInt(lineItems[2]);
      docId = lineItems[1];
      featuresInLine = new StringBuffer[numInstances];
      classNames = new String[numInstances];
      int num;
      for(int i=0; i<numInstances; ++i) {
        String [] lineItems1 = in.readLine().split(ConstantParameters.ITEMSEPARATOR);
        num = Integer.parseInt(lineItems1[0]);
        if(num>0) {
          StringBuffer classNs = new StringBuffer();
          for(int j=1; j<num; ++j)
            classNs.append(lineItems1[j]+ConstantParameters.ITEMSEPARATOR);
          classNs.append(lineItems1[num]);
          classNames[i] = classNs.toString();
        }
        featuresInLine[i] = new StringBuffer();
        if(num+1<lineItems1.length)
          featuresInLine[i].append(lineItems1[num+1]);
        for(int j=num+2; j<lineItems1.length; ++j)
          featuresInLine[i].append(ConstantParameters.ITEMSEPARATOR+lineItems1[j]);
      }
    } catch(IOException e) {
      System.out.println("**Error occured in reading the NLP data from file for converting to FVs!");
    }
    
  }

  public void setDocId(String docId) {
    this.docId = new String(docId);
  }

  public String getDocId() {
    return this.docId;
  }

  /** Put the semantic type, type and feature together. */
  static String obtainFeatureName(String type, String feat, char semanticType) {
    return ConstantParameters.ITEMSEPREPLACEMENT+semanticType+type
      + ConstantParameters.ITEMSEPREPLACEMENT + feat;
  }
  
  /** Put the type and feature together for ngram. */
  static String obtainFeatureNameForNGRAM(String type, String feat) {
    return ConstantParameters.ITEMSEPREPLACEMENT + type
      + ConstantParameters.ITEMSEPREPLACEMENT + feat;
  }
}
