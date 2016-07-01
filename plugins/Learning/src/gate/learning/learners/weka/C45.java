/*
 *  C45.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: C45.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners.weka;

import weka.classifiers.trees.J48;
/** 
 * The C4.5 classifier from the Weka.
 */
public class C45 extends WekaLearner {
  /** serialVersionUID. */
  private static final long serialVersionUID = 1L;
  /** Constructor from Weka. */
  public C45() {
    wekaCl = new J48();
    learnerName = "C4.5";
  }
  /** Get the parameters of the C4.5 (do nothing). */
  public void getParametersFromOptionsLine(String options) {
  }
}
