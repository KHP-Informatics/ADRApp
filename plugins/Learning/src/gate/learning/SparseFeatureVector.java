/*
 *  SparseFeatureVector.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: SparseFeatureVector.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import gate.learning.learners.svm.svm_node;

/**
 * One feature vector in the sparse format, with length, indexes and values.
 */
public class SparseFeatureVector {
  /** length of feature vector (number of non-zero elements). */
  int len;
  /** indexes of non-zero elements. */
  //public int[] indexes;
  
  //public int[] indexes;
  /** Values of non-zero elements. */
  //public float[] values;
  
  public svm_node [] nodes;

  /** Trivial constructor. */
  public SparseFeatureVector() {
    len = 0;
    nodes = null;
  }

  /** Constructor with length and two arrays. */
  public SparseFeatureVector(int num) {
    len = num;
    //indexes = new int[num];
    //values = new float[num];
    nodes = new svm_node[num];
    for(int i=0; i<num; ++i)
      nodes[i] = new svm_node();
  }

  public int getLen() {
    return len;
  }

  //public int[] getIndexes() {
   // return indexes;
  //}

  //public float[] getValues() {
    //return values;
  //}
}
