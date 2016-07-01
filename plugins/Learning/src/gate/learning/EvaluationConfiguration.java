/*
 *  EvaluationConfiguration.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: EvaluationConfiguration.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import org.jdom.Element;

/**
 * Store the spefications of evaluation method and the details.
 */
public class EvaluationConfiguration {
  /** k-fold. */
  public static final int kfold = 1;
  /** Hold-out test */
  public static final int split = 2;
  /** Evaluation method used. */
  public int mode = EvaluationConfiguration.split;
  /** Ratio of training data in the whole data for hold-out test. */
  public double ratio = 0.66d;
  /** k for the k-fold and the number of runs for the hold-out test. */
  public int kk = 1;

  /** Creates an EvaluationConfiguration with the default values * */
  public EvaluationConfiguration() {
  }

  /** Uses K-fold cross validation. */
  public EvaluationConfiguration(int kk) {
    mode = EvaluationConfiguration.kfold;
    this.kk = kk;
  }

  /**
   * Hold-out evaluation. Ratio has a value between 0 and 1 and indicates the
   * ratio of instances to be used for training. A typical value is 0.66. kk is
   * the number of runs for the random selection.
   */
  public EvaluationConfiguration(double ratio, int kk) {
    this.mode = EvaluationConfiguration.split;
    this.ratio = ratio;
    this.kk = kk;
  }

  /** Constructor just using the ratio. */
  public EvaluationConfiguration(double ratio) {
    this.mode = EvaluationConfiguration.split;
    this.ratio = ratio;
    this.kk = 1;
  }

  /** Create an object from an XML element in configuration file. */
  public static EvaluationConfiguration fromXML(Element domElement) {
    if(domElement == null)
      return new EvaluationConfiguration();
    String method = domElement.getAttributeValue("method");
    String kk = domElement.getAttributeValue("runs");
    String value = domElement.getAttributeValue("ratio");
    boolean kfold = method.equalsIgnoreCase("kfold");
    if(kfold) { return new EvaluationConfiguration(Integer.parseInt(kk)); }
    if(kk == null) {
      return new EvaluationConfiguration(Double.parseDouble(value));
    } else {
      return new EvaluationConfiguration(Double.parseDouble(value), Integer
        .parseInt(kk));
    }
  }
}
