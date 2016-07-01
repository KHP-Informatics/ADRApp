/*
 *  ChunkOrEntity.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: ChunkOrEntity.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners;
/**
 * One chunk with start and end instance indexes, 
 * label index, and probablity.
 */
public class ChunkOrEntity {
  /** Index of the start instance. */
  public int start;
  /** Index of the end instance. */
  public int end;
  /** Probability of the chunk with the label. */
  public float prob;
  /** Label index. */
  public int name;
  /** Constructor from the start and end index. */
  public ChunkOrEntity(int s, int e) {
    this.start = s;
    this.end = e;
  }
}
