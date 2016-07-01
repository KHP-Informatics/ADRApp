/*
 *  LabelsOfFeatureVectorDoc.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: LabelsOfFeatureVectorDoc.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import gate.learning.DocFeatureVectors.LongCompactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Labels (indexes) of feature vectors in one document. It represents
 * multi-label via the LabelsOfFV object.
 */
public class LabelsOfFeatureVectorDoc {
  /** Array of multi-labels for all instances in a document. */
  public LabelsOfFV[] multiLabels = null;

  /** Constructor, trivial case. */
  public LabelsOfFeatureVectorDoc() {
  }

  /**
   * Get the labels from the NLP label feautues of the document. For surround
   * mode, get the start and end token label as 1 and 2. For entity with single
   * token, the token has two labels, 1 and 2.
   */
  public void obtainMultiLabelsFromNLPDocSurround(NLPFeaturesOfDoc nlpDoc,
    Label2Id label2Id, boolean surroundMode) {
    String currentN;
    int num = nlpDoc.numInstances;
    multiLabels = new LabelsOfFV[num];
    if(!surroundMode) {// not the surroundMode
      for(int i = 0; i < num; ++i) {
        Set<Integer> setLabels = new HashSet<Integer>();
        if (nlpDoc.classNames[i] == null) continue;
        String[] items = nlpDoc.classNames[i]
          .split(ConstantParameters.ITEMSEPARATOR);
        for(int j = 0; j < items.length; ++j) {
          currentN = items[j];
          if(currentN.endsWith(ConstantParameters.SUFFIXSTARTTOKEN))
            currentN = currentN.substring(0, currentN
              .lastIndexOf(ConstantParameters.SUFFIXSTARTTOKEN));
          if(label2Id.label2Id.containsKey(currentN))
            // just use the labels in the LabelsList.save file
            setLabels.add(label2Id.label2Id.get(currentN));
          // Integer.valueOf(label2Id.label2Id.get(currentN).toString());
        }
        
        multiLabels[i] = new LabelsOfFV(setLabels.size());
        if(setLabels.size() > 0) {
          multiLabels[i].labels = new int[setLabels.size()];
          List<Integer> indexes = new ArrayList<Integer>(setLabels);
          LongCompactor c = new LongCompactor();
          Collections.sort(indexes, c);
          for(int j = 0; j < indexes.size(); ++j)
            multiLabels[i].labels[j] = indexes.get(j);
              //.toString()); // Integer.valueOf(obj.toString());
        }
      }// end of the i loop
    } else
    // for the surrond mode
    for(int i = 0; i < num; ++i) {
      Set<Integer> setLabels = new HashSet<Integer>();
      if(nlpDoc.classNames[i] != null) {
        String[] items = nlpDoc.classNames[i]
          .split(ConstantParameters.ITEMSEPARATOR);
        for(int j = 0; j < items.length; ++j) {
          currentN = items[j];
          if(currentN.endsWith(ConstantParameters.SUFFIXSTARTTOKEN)) {
            String label = currentN.substring(0, currentN
              .lastIndexOf(ConstantParameters.SUFFIXSTARTTOKEN));
            if(label2Id.label2Id.containsKey(label)) {
              setLabels.add(label2Id.label2Id.get(label) * 2 - 1);
              if(i + 1 == num
                || !hasTheSameLabel(label, nlpDoc.classNames[i + 1]))
                // single token
                setLabels.add(label2Id.label2Id.get(label) * 2);
            }
          } else { // no start token
            if(label2Id.label2Id.containsKey(currentN)) {
              if(i + 1 == num) {// the last token, hence the
                // end token
                setLabels.add(label2Id.label2Id.get(currentN) * 2);
              } else if(!hasTheSameLabel(currentN, nlpDoc.classNames[i + 1]))
                setLabels.add(label2Id.label2Id.get(currentN) * 2);
            }
          }
        }
      }
      multiLabels[i] = new LabelsOfFV(setLabels.size());
      if(setLabels.size() > 0) {
        multiLabels[i].labels = new int[setLabels.size()];
        List<Integer> indexes = new ArrayList<Integer>(setLabels);
        LongCompactor c = new LongCompactor();
        Collections.sort(indexes, c);
        for(int j = 0; j < indexes.size(); ++j) {
          multiLabels[i].labels[j] = indexes.get(j); // Integer.valueOf(obj.toString());
        }
      }
    }// end of the i loop
  }

  /** Is a squence of labels contains one particular label. */
  private boolean hasTheSameLabel(String label, String classNames) {
    if(classNames != null) {
      String[] items = classNames.split(ConstantParameters.ITEMSEPARATOR);
      for(int i = 0; i < items.length; ++i) {
        String currentN = items[i];
        if(currentN.endsWith(ConstantParameters.SUFFIXSTARTTOKEN))
          currentN = currentN.substring(0, currentN
            .lastIndexOf(ConstantParameters.SUFFIXSTARTTOKEN));
        if(currentN.equals(label)) return true;
      }
    }
    return false;
  }
}
