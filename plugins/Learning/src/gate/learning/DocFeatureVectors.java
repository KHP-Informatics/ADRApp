/*
 *  DocFeatureVectors.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: DocFeatureVectors.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import gate.util.GateException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Convert the NLP features into a sparse feature vector for each instance of
 * one document.
 */
public class DocFeatureVectors {
  /** Document ID. */
  public String docId = null;
  /** Number of instance in the document. */
  public int numInstances;
  /**
   * Array containing all sparse feature vectors of instances in the documents.
   */
  public SparseFeatureVector[] fvs;
  /** Default value of one component of feature vector. */
  final static float DEFAULTVALUE = 1.0f;

  /** Constructor in trival case. */
  public DocFeatureVectors() {
    numInstances = 0;
  }

  /**
   * The main method for obtaining the sparse feature vector and label(s) from
   * the NLP features of each instance in the documents.
   */
  public void obtainFVsFromNLPFeatures(NLPFeaturesOfDoc nlpDoc,
    NLPFeaturesList featList, int[] featurePosition, int maxNegPosition,
    int numDocs, float ngramWeight, int valueType) {
    numInstances = nlpDoc.numInstances;
    docId = new String(nlpDoc.getDocId());
    fvs = new SparseFeatureVector[numInstances];
    // For each istance
    for(int i = 0; i < numInstances; ++i) {
      Map<Object,Object> indexValues = new HashMap<Object,Object>();
      String[] feat = nlpDoc.featuresInLine[i].toString().split(
        ConstantParameters.ITEMSEPARATOR);
      // Some variables for normalising the feature values of the same ngram
      // boolean sameNgram=true;
      int prevPosition = -99999;
      float[] tempVals = new float[9999];
      long[] tempInds = new long[9999];
      int tempSize = 0;
      int positionCurr = 0;
      for(int j = 0; j < feat.length; ++j) {
        // First get the position information for the current NLP feature
        positionCurr = 0;
        
        if(feat[j] != null && Pattern.matches((".+\\[-?[0-9]+\\]$"), feat[j])) {
          int ind = feat[j].lastIndexOf('[');
          String positionStr = feat[j].substring(ind + 1, feat[j].length() - 1);
          try {
	      positionCurr = Integer.parseInt(positionStr);
	  }
	  catch(NumberFormatException e) {
	      // shouldn't happen now I've "fixed" the regexp, fingers crossed!
	      e.printStackTrace();
	      break;
	  }
          feat[j] = feat[j].substring(0, ind);
        }
        if(prevPosition != positionCurr && tempSize > 0) {
          double sum = 0.0;
          for(int ii = 0; ii < tempSize; ++ii)
            sum += tempVals[ii] * tempVals[ii];
          sum = Math.sqrt(sum);
          for(int ii = 0; ii < tempSize; ++ii) {
            tempVals[ii] /= sum;
            indexValues.put(new Long(tempInds[ii]), new Float(tempVals[ii]));
          }
          tempSize = 0;
        }
        String featCur = feat[j];
        String featVal = null;
        char featType = Attribute.NOMINAL;
        int kk = -3;
        if(featCur.contains(NLPFeaturesList.SYMBOLNGARM)) {//for the ngram feature
          kk = feat[j].lastIndexOf(NLPFeaturesList.SYMBOLNGARM);
          featCur = feat[j].substring(0, kk);
          featVal = feat[j].substring(kk + 2);
        } else { /* for the attribute feature*/
        	
          String [] featComponents = featCur.split(ConstantParameters.ITEMSEPREPLACEMENT);
          
          //for(int ii=0; ii<featComponents.length; ++ii) {  
        	//  System.out.println(ii+ ", featCur="+featComponents[ii]);
          //}
          
          
          featType = featCur.charAt(ConstantParameters.ITEMSEPREPLACEMENT.length());
          
          if( featType != Attribute.NOMINAL) {/* if not the nominal feature */
            featVal = featComponents[2];
            featCur = ConstantParameters.ITEMSEPREPLACEMENT+featComponents[1];
          }
        }
        if(featCur.length() > 0) { // if there is any feature
          if(featList.featuresList.containsKey(featCur)) {
            if(featCur.contains(NLPFeaturesList.SYMBOLNGARM)) { /* for ngram feature*/
              int shiftNum = 0;
              if(positionCurr > 0)
                shiftNum = maxNegPosition + positionCurr;
              else shiftNum = -positionCurr;
              long featInd = Long.parseLong(featList.featuresList.get(featCur)
                .toString())
                + shiftNum * ConstantParameters.MAXIMUMFEATURES;
              double val = 0.0;
              switch(valueType){
                case 1: // for only the presence of Ngram in the sentence
                  val = 1.0;
                  break;
                case 2: // for tf representation
                  val = Long.parseLong(featVal);
                  break;
                case 3: // for tf*idf representation
                  val = (Long.parseLong(featVal) + 1)
                    * Math.log((double)numDocs
                      / featList.idfFeatures.get(featCur));
                  break;
                default:
                  try {
                    throw new GateException(
                      "The value type for ngram is not defined!");
                  } catch(GateException e) {
                    e.printStackTrace();
                  }
              }
              // indexValues.put(featInd, new Float(val));
              tempInds[tempSize] = featInd;
              tempVals[tempSize] = (float)val;
              ++tempSize;
            } else {
              float valNow=0;
              switch(featType) {
                case Attribute.NUMERIC:
                  valNow=new Float(featVal);
                  break;
                case Attribute.BOOLEAN:
                  if(featVal.equalsIgnoreCase("true"))
                    valNow=1;
                  else valNow=0;
                  break;
                case Attribute.NOMINAL:
                  valNow=1;
                  break;
                default: 
                  try {
                    throw new GateException(
                      "The semantic type for one attribute is not defined!");
                  } catch(GateException e) {
                    e.printStackTrace();
                  }
              }
       
              if(positionCurr == 0)
                indexValues.put(featList.featuresList.get(featCur), valNow);
              else if(positionCurr < 0)
                  indexValues.put(featList.featuresList
                    .get(featCur) - positionCurr
                    * ConstantParameters.MAXIMUMFEATURES, -1f*valNow
                    / (double)positionCurr);
                else indexValues.put(
                  (featList.featuresList.get(featCur)
                     + (positionCurr + maxNegPosition)
                    * ConstantParameters.MAXIMUMFEATURES), 
                    valNow / (double)positionCurr);
              
            }
          }
        }
        prevPosition = positionCurr;
      } // end of the loop on the features of one instances
      // For the last ngram features
      if(tempSize > 0) {
        if(valueType == 3) {
          double sum = 0.0;
          for(int ii = 0; ii < tempSize; ++ii)
            sum += tempVals[ii] * tempVals[ii];
          sum = Math.sqrt(sum);
          for(int ii = 0; ii < tempSize; ++ii) {
            tempVals[ii] /= sum;
            tempVals[ii] *= ngramWeight;
            indexValues.put(tempInds[ii], tempVals[ii]);
          }
        } else {
          for(int ii = 0; ii < tempSize; ++ii) {
            indexValues.put(tempInds[ii], tempVals[ii]);
          }
        }
        tempSize = 0;
      }
      // if(LogService.minVerbosityLevel > 1)
      // if(n != nlpDoc.featuresCounted[i]) {
      // System.out.println("Error: the number of features (" + n
      // + ") is not the same as the number recorded ("
      // + nlpDoc.featuresCounted[i] + ")in document " + docId);
      // }
      // sort the indexes in ascending order
      List<Object> indexes = new ArrayList<Object>(indexValues.keySet());
      Collections.sort(indexes, new LongCompactor());
      // Iterator iterator = indexes.iterator();
      // n = 0;
      // while(iterator.hasNext()) {
      // Object key = iterator.next();
      // fvs[i].indexes[n] = ((Long)key).longValue();
      fvs[i] = new SparseFeatureVector(indexes.size());
      // for(int j=0; j<n; ++j) {
      for(int j = 0; j < indexes.size(); ++j) {
        // System.out.println(new Integer(j) +" index=*"+
        // indexes.get(j)+"*");
        fvs[i].nodes[j].index = Integer.parseInt(indexes.get(j).toString());
        // for the constant value 1
        // fvs[i].values[j] = DEFAULTVALUE;
        // for the tf or tf*idf value
        fvs[i].nodes[j].value = Double.parseDouble(indexValues.get(indexes.get(j))
          .toString());
      }
    } // end of the loop on the instances
  }

  /** A static class for comparing two long numbers. */
  public static class LongCompactor implements Comparator<Object> {
    public int compare(Object l1, Object l2) {
      // return (new Long((new Long(l1.toString()).longValue()- new
      // Long(l2.toString()).longValue()))).intValue();
      return (int)(Long.parseLong(l1.toString()) - Long
        .parseLong(l2.toString()));
    }
  }

  /** Read the feature vectors of a document from the feature vector file. */
  public void readDocFVFromFile(BufferedReader dataFile, int num,
    LabelsOfFeatureVectorDoc labelsDoc) {
    numInstances = num;
    fvs = new SparseFeatureVector[numInstances];
    labelsDoc.multiLabels = new LabelsOfFV[numInstances];
    try {
      String line;
      for(int i = 0; i < num; ++i) {
        line = dataFile.readLine();
        // System.out.println("i="+i+"line="+line);
        String[] items = line.split(ConstantParameters.ITEMSEPARATOR);
        // get the label from the line
        int iEndLabel;
        // get the multilabel directly
        iEndLabel = obtainMultiLabels(items, labelsDoc.multiLabels, i);
        // get the feature vector
        int len = items.length - iEndLabel;
        if(len == 0) {
          // If there is no feature vector, creat one for it
          fvs[i] = new SparseFeatureVector(1);
          fvs[i].nodes[0].index = 1;
          fvs[i].nodes[0].value = 0;
        } else {
          fvs[i] = new SparseFeatureVector(len);
          obtainFVs(items, iEndLabel, len, fvs[i]);
        }
      }
    } catch(IOException e) {
      e.printStackTrace();
    }
    return;
  }

  /** Get the multi label(s) from one line of feature vector. */
  private int obtainMultiLabels(String[] items, LabelsOfFV[] multiLabels, int i) {
    int num;
    int kk = 1;
    num = Integer.valueOf(items[kk++]);
    multiLabels[i] = new LabelsOfFV(num);
    if(num > 0) {
      multiLabels[i].labels = new int[num];
      for(int j = 0; j < num; ++j)
        multiLabels[i].labels[j] = Integer.valueOf(items[kk++]);
    }
    return kk;
  }

  /** Get the feature vector in parse format from a String arrays. */
  private void obtainFVs(String[] items, int iEndLabel, int len,
    SparseFeatureVector fv) {
    String[] indexValue;
    for(int i = 0; i < len; ++i) {
      indexValue = items[i + iEndLabel]
        .split(ConstantParameters.INDEXVALUESEPARATOR);
      if(indexValue.length <= 1) {
        System.out.println("i=" + i + " item=" + items[i + iEndLabel]);
      }
      fv.nodes[i].index = new Integer(indexValue[0]);
      fv.nodes[i].value = new Float(indexValue[1]);
    }
    return;
  }

  /** Get number of instances in the document. */
  public int getNumInstances() {
    return numInstances;
  }

  /** Get the fv array. */
  public SparseFeatureVector[] getFvs() {
    return fvs;
  }
  
  /** Delete the fv array. */
  public void deleteFvs() {
    for(int i=0; i<fvs.length; ++i)
      fvs[i] = null;
    //fvs= null;
  }

  /** Set the DocID. */
  public void setDocID(String docI) {
    this.docId = docI;
  }

  /** Expand the feature vector to including the context tokens. */
  public void expandFV(int winSizeLeft, int winSizeRight) {
    SparseFeatureVector[] fvsExpand = new SparseFeatureVector[fvs.length];
    for(int i = 0; i < fvs.length; ++i) {
      int lenT0 = fvs[i].len;
      for(int j = -1; j >= -winSizeLeft; --j) {
        if(j + i >= 0) lenT0 += fvs[j + i].len;
      }
      for(int j = 1; j <= winSizeRight; ++j)
        if(j + i < fvs.length) lenT0 += fvs[j + i].len;
      fvsExpand[i] = new SparseFeatureVector(lenT0);
      // System.out.println("lent0="+lenT0);
      for(int j1 = 0; j1 < fvs[i].len; ++j1) {
        fvsExpand[i].nodes[j1].index = fvs[i].nodes[j1].index;
        fvsExpand[i].nodes[j1].value = fvs[i].nodes[j1].value;
      }
      int lenTotal = fvs[i].len;
      for(int j = -1; j >= -winSizeLeft; --j) {
        int kk = j + i;
        if(kk >= 0) {
          int gapLen = -j * (int)ConstantParameters.MAXIMUMFEATURES;
          for(int j1 = 0; j1 < fvs[kk].len; ++j1) {
            if(j1 + lenTotal >= lenT0)
              System.out.println("i=" + i + ", j=" + j + ",j1=" + j1
                + ", newlen=" + lenTotal);
            fvsExpand[i].nodes[j1 + lenTotal].index = fvs[kk].nodes[j1].index + gapLen;
            fvsExpand[i].nodes[j1 + lenTotal].value = fvs[kk].nodes[j1].value / (-j);
          }
          lenTotal += fvs[kk].len;
        }
      }
      for(int j = 1; j <= winSizeRight; ++j) {
        int kk = j + i;
        if(kk < fvs.length) {
          int gapLen = (j + winSizeLeft)
            * (int)ConstantParameters.MAXIMUMFEATURES;
          for(int j1 = 0; j1 < fvs[kk].len; ++j1) {
            fvsExpand[i].nodes[j1 + lenTotal].index = fvs[kk].nodes[j1].index + gapLen;
            fvsExpand[i].nodes[j1 + lenTotal].value = fvs[kk].nodes[j1].value / j;
          }
          lenTotal += fvs[kk].len;
        }
      }
    }// end of the loop for each fv
    fvs = fvsExpand;
  }
  
  /** Write the FVs of one document into file. */
  public void addDocFVsToFile(int index, BufferedWriter out, int[] labels) {
    try {
      out.write(index + ConstantParameters.ITEMSEPARATOR
        + numInstances + ConstantParameters.ITEMSEPARATOR
        + docId);
      out.newLine();
      for(int i = 0; i < numInstances; ++i) {
        StringBuffer line = new StringBuffer();
        line.append((i + 1) + ConstantParameters.ITEMSEPARATOR
          + labels[i]);
        for(int j = 0; j < fvs[i].len; ++j)
          line.append(ConstantParameters.ITEMSEPARATOR
            + fvs[i].nodes[j].index + ConstantParameters.INDEXVALUESEPARATOR
            + fvs[i].nodes[j].value);
        out.write(line.toString());
        out.newLine();
      }
    } catch(IOException e) {
    }
  }

  /** Write the FVs with labels of one document into file. */
  public void addDocFVsMultiLabelToFile(int index, BufferedWriter out,
    LabelsOfFV[] multiLabels) {
    try {
      out.write(index + ConstantParameters.ITEMSEPARATOR
        + numInstances + ConstantParameters.ITEMSEPARATOR
        + docId);
      out.newLine();
      for(int i = 0; i < numInstances; ++i) {
        StringBuffer line = new StringBuffer(); 
        
        if(multiLabels[i]==null) { //if there is no lable for this example
        	line.append(1+ConstantParameters.ITEMSEPARATOR
                    + 0);
        	
        } else {
        line.append((i + 1) + ConstantParameters.ITEMSEPARATOR
          + multiLabels[i].num);
        for(int j = 0; j < multiLabels[i].num; ++j) {
          line.append(ConstantParameters.ITEMSEPARATOR
            + multiLabels[i].labels[j]);
        }
        }
        for(int j = 0; j < fvs[i].len; ++j)
          line.append(ConstantParameters.ITEMSEPARATOR
            + fvs[i].nodes[j].index + ConstantParameters.INDEXVALUESEPARATOR
            + fvs[i].nodes[j].value);
        out.write(line.toString());
        out.newLine();
      }
    } catch(IOException e) {
    }
  }

}
