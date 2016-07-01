/*
 *  NaiveBayesC.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: NaiveBayesC.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners.weka;

import weka.classifiers.bayes.NaiveBayes;
/**
 * Naive Nayes classifier from Weka.
 */
public class NaiveBayesC extends WekaLearner {
  /** serialVersionUID for Serializable class*/
  private static final long serialVersionUID = 1L;
  /** Constructor.*/
  public NaiveBayesC() {
    wekaCl = new NaiveBayes();
    learnerName = "NaiveBayes";
  }
  /** Get the parameters of the Naive Bayes (do nothing). */
  public void getParametersFromOptionsLine(String options) {
    
  }
}
