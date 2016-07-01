/**
 *  DataForLearning.java
 * 
 *  Yaoyong Li 21/01/2008
 *
 *  $Id: ActiveLearningSetting.java, v 1.0 2008-01-21 12:58:16 +0000 yaoyong $
 */
package gate.learning;
/** Store the settings for active learning, by reading the settings 
 * from configuration file.
 */
public class ActiveLearningSetting {
  /** Number of tokens used for selecting the document. */
  int numTokensSelect;
  /** Constructor with the number of documents. */
  public ActiveLearningSetting() {
    this.numTokensSelect = 3;
  }
}
