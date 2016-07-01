/*
 *  LabelsOfFV.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: LabelsOfFV.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

/**
 * The multi-label of one feature vector (FV) (or one example).
 */
public class LabelsOfFV {
  /** Number of labels of the exampele. */
  public int num;
  /** Labels in integer format, indexes of labels. */
  public int[] labels;
  /** The probability of the example has the label. */
  public float[] probs;

  /** Constructor, with number of labels. */
  public LabelsOfFV(int n) {
    this.num = n;
  }

  /** Constructor, with number of labels, labels and probabilities. */
  public LabelsOfFV(int n, int[] labels, float[] probs) {
    this.num = n;
    this.labels = labels;
    this.probs = probs;
  }

  public void setNum(int n) {
    this.num = n;
  }

  public int getNum() {
    return this.num;
  }

  public void setLabels(int[] labels) {
    this.labels = labels;
  }

  public int[] getLabels() {
    return this.labels;
  }

  public void setProbs(float[] probs) {
    this.probs = probs;
  }

  public float[] getProbs() {
    return this.probs;
  }

  /**
   * Convert the multi-label into a string, used in the feature vector file.
   */
  public String toOneLine() {
    StringBuffer line = new StringBuffer();
    line.append(num);
    line.append(ConstantParameters.ITEMSEPARATOR);
    for(int i = 0; i < num; ++i) {
      line.append(labels[i]);
      line.append(ConstantParameters.ITEMSEPARATOR);
      line.append(probs[i]);
      line.append(ConstantParameters.ITEMSEPARATOR);
    }
    return line.substring(0, line.length()).toString();
  }
}
