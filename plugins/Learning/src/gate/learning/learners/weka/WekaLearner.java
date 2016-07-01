/*
 *  WekaLearner.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: WekaLearner.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners.weka;

import gate.learning.LabelsOfFV;
import gate.learning.LabelsOfFeatureVectorDoc;
import gate.learning.LogService;

import java.io.Serializable;
import weka.core.Instances;
/**
 * The abstract class for Weka learner.
 * It implements the training and applying methods for
 * learners.  
 */
public abstract class WekaLearner implements Serializable {

  private static final long serialVersionUID = -3601046262665415307L;

  /** The name of learner. */
  String learnerName = null;
  /** The weka classifier. */
  public weka.classifiers.Classifier wekaCl;
  /** The options for one Weka learner. */
  String options;
  /** The abstract method of getting parameters from options. */
  public abstract void getParametersFromOptionsLine(String options);
  /** Training by calling th buildClassifier method of the learner. */
  public void training(Instances instancesData) {
    try {
      if(LogService.minVerbosityLevel>0)
        System.out.println("Learning start:");
      wekaCl.buildClassifier(instancesData);
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
  /** Applying the model to the data. The output of the
   * application could be a distribution among all labels
   * or the maximal output for one class. 
   */
  public void applying(Instances instancesData,
    LabelsOfFeatureVectorDoc[] labelsFVDoc, boolean distributionOutput) {
    int numInst = 0;
    //Not count the label for null class
    int numClasses = instancesData.numClasses() - 1; 
    //Get the map from the output to values(true label of the problem)
    // From the attribute index to true labels
    int[] trueLabels = new int[numClasses + 1];
    for(int i = 0; i <= numClasses; ++i) {
      trueLabels[i] = Integer.parseInt(instancesData.classAttribute().value(i));
    }
    if(LogService.minVerbosityLevel>0)
      System.out.println("Application starts...");
    try {
      if(distributionOutput) {
        double[] distr;
        for(int iDoc = 0; iDoc < labelsFVDoc.length; ++iDoc) {
          int num = labelsFVDoc[iDoc].multiLabels.length;
          for(int i = 0; i < num; ++i) {
            distr = wekaCl.distributionForInstance(instancesData
              .instance(numInst++));
            labelsFVDoc[iDoc].multiLabels[i] = new LabelsOfFV(numClasses);
            labelsFVDoc[iDoc].multiLabels[i].probs = new float[numClasses];
            double sum = 0.0;
            for(int j = 0; j < distr.length; ++j)
              sum += distr[j] * distr[j];
            sum = Math.sqrt(sum);
            if(sum < 0.00000000001) sum = 1.0;
            for(int j = 0; j < distr.length; ++j)
              distr[j] /= sum;
            for(int j = 0; j <= numClasses; ++j)
              if(trueLabels[j] != -1) {
                labelsFVDoc[iDoc].multiLabels[i].probs[trueLabels[j] - 1] = (float)distr[j]; // as                                                                                   // class
              }
          }
        }
      } else {
        double outputV;
        for(int iDoc = 0; iDoc < labelsFVDoc.length; ++iDoc) {
          int num = labelsFVDoc[iDoc].multiLabels.length;
          for(int i = 0; i < num; ++i) {
            outputV = wekaCl
              .classifyInstance(instancesData.instance(numInst++));
            labelsFVDoc[iDoc].multiLabels[i] = new LabelsOfFV(numClasses);
            labelsFVDoc[iDoc].multiLabels[i].probs = new float[numClasses];
            if(trueLabels[(int)outputV] != -1)
              labelsFVDoc[iDoc].multiLabels[i].probs[trueLabels[(int)outputV] - 1] = 1.0f;
          }
        }
      }
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  public String getLearnerName() {
    return learnerName;
  }
}
