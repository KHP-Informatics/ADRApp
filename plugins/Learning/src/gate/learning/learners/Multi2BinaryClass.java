/*
 *  Multi2BinaryClass.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: Multi2BinaryClass.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners;

import gate.learning.LabelsOfFV;
import gate.learning.SparseFeatureVector;
/**
 * Convert a multi-class problem into several binary
 * problems.
 */
public class Multi2BinaryClass {
  /** One vs all others method: examples in one class are positive, 
   * and all other examples are negative. 
   */
  static int oneVsOthers(DataForLearning dataFVinDoc, String className,
    short[] labels, SparseFeatureVector[] fvs) {
    int kk = 0;
    //For each document.
    for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i) { 
      SparseFeatureVector[] fvsInDoc = dataFVinDoc.trainingFVinDoc[i].getFvs();
      //For each instance
      for(int j = 0; j < fvsInDoc.length; ++j) { 
        fvs[kk] = fvsInDoc[j];
        // For the class
        LabelsOfFV multiLabel = dataFVinDoc.labelsFVDoc[i].multiLabels[j];
        labels[kk] = -1;
        for(int j1 = 0; j1 < multiLabel.num; ++j1) {
          if(className.equals(((Integer)multiLabel.labels[j1]).toString())) {
            labels[kk] = 1;
            break;
          }
        }
        ++kk;
      }
    }
    return kk;
  }
  /** One vs. another method: examples of one class are positive,
   * and examples in another class are negative. 
   */
  static int oneVsAnother(DataForLearning dataFVinDoc, String className1,
    String className2, short[] labels, SparseFeatureVector[] fvs) {
    int kk = 0;
    for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i) {
      SparseFeatureVector[] fvsInDoc = dataFVinDoc.trainingFVinDoc[i].getFvs();
      for(int j = 0; j < fvsInDoc.length; ++j) { // for each instance
        LabelsOfFV multiLabel = dataFVinDoc.labelsFVDoc[i].multiLabels[j];
        boolean isCounted = false;
        short labelT = 0;
        for(int j1 = 0; j1 < multiLabel.num; ++j1) {
          // if(className1.equals(multiLabel.labels[j1])) {
          if(className1.equals(((Integer)multiLabel.labels[j1]).toString())) {
            // labels[kk] = 1;
            // isCounted = true;
            labelT = 1;
            if(isCounted)
              isCounted = false;
            else isCounted = true;
            // break;
          } else if(className2.equals(((Integer)multiLabel.labels[j1])
            .toString())) {
            // labels[kk] = 1;
            // isCounted = true;
            labelT = -1;
            if(isCounted)
              isCounted = false;
            else isCounted = true;
          }
        }
        if(isCounted) {
          labels[kk] = labelT;
          fvs[kk] = fvsInDoc[j];
          ++kk;
        }
      }
    }
    return kk;
  }
  /** One class vs Null (non-class). */
  static int oneVsNull(DataForLearning dataFVinDoc, String className,
    short[] labels, SparseFeatureVector[] fvs) {
    int kk = 0;
    for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i) {
      SparseFeatureVector[] fvsInDoc = dataFVinDoc.trainingFVinDoc[i].getFvs();
      for(int j = 0; j < fvsInDoc.length; ++j) { // for each instance
        LabelsOfFV multiLabel = dataFVinDoc.labelsFVDoc[i].multiLabels[j];
        boolean isCounted = false;
        if(multiLabel.num == 0) {
          labels[kk] = -1;
          isCounted = true;
        } else {
          for(int j1 = 0; j1 < multiLabel.num; ++j1) {
            // if(className.equals(multiLabel.labels[j1])) {
            if(className.equals(((Integer)multiLabel.labels[j1]).toString())) {
              labels[kk] = 1;
              isCounted = true;
              break;
            }
          }
        }
        if(isCounted) {
          fvs[kk] = fvsInDoc[j];
          ++kk;
        }
      }
    }
    return kk;
  }
}
