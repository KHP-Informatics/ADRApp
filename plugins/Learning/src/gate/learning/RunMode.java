/*
 *  RunMode.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: NLPFeaturesOfDoc.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

/*
 * Enum data type for learning mode in GUI.
 */
public enum RunMode {
  TRAINING, APPLICATION, EVALUATION, ProduceFeatureFilesOnly, 
  MITRAINING, VIEWPRIMALFORMMODELS, RankingDocsForAL
}
