/*
 *  KNNIBK.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: KNNIBK.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners.weka;

import gate.learning.LogService;
import weka.classifiers.lazy.IBk;
/** 
 * The KNN from Weka.
 */
public class KNNIBK extends WekaLearner {
  /** serialVersionUID. */
  private static final long serialVersionUID = 1L;
  /** The number of neighbours used in the KNN. */
  int kk = 1;
  /** Constructor for the KNN, with the default number of neighbours. */
  public KNNIBK() {
    if(LogService.minVerbosityLevel>0)
      System.out.println("For KNN, kk="+kk);
    wekaCl = new IBk(kk);
    learnerName = "KNN";
  }
  
  public KNNIBK(String options) {
    getParametersFromOptionsLine(options);
    if(LogService.minVerbosityLevel>0)
      System.out.println("For KNN, kk="+kk);
    wekaCl = new IBk(kk);
    learnerName = "KNN";
  }
  
  /** Get the parameter, number of the neighbours, of the KNN. */
  public void getParametersFromOptionsLine(String options) {
    String [] items = options.split("[ \t]+");
    for(int i=0; i<items.length; ++i)
      if(items[i].equals("-k") && i<items.length-1) {
        kk = Integer.parseInt(items[i+1]);
        break;
      }
  }
}
