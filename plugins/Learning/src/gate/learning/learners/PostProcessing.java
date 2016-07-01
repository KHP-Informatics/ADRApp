/*
 *  PostProcessing.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: PostProcessing.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners;

import gate.learning.ChunkLengthStats;
import gate.learning.LabelsOfFV;
import gate.learning.LabelsOfFeatureVectorDoc;
import gate.learning.LogService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * Post-processing the resutls from the classifiers for annotate
 * text. Some specific post-processing procedure is used
 * for improving the performance of chunk learning.
 */
public class PostProcessing {
  /** Threshold of the probability of the start and end tokens for chunk. */
  double boundaryProb = 0.42;
  /** Threshold of the probability of the chunk (multipliaction of the
   * probabilities for the start and end tokens.
   */
  double entityProb = 0.2;
  /** Threshold of the probability for text classification. */
  double thresholdC = 0.5;
  /** Constructor with the three probability thresholds. */
  public PostProcessing(float boundaryP, float entityP,
    float thresholdClassificaton) {
    this.boundaryProb = boundaryP;
    this.entityProb = entityP;
    this.thresholdC = thresholdClassificaton;
  }
  /** Trivial constructor. */
  public PostProcessing() {
  }
  /** Post-processing the classification results fro chunk learning,
   * by keeping the consistency of start and end tokens of the same label,
   * using the length information of chunk from the training data,
   * selecting the chunk with the maximal probability from the overlapped
   * chunks.  
   */
  public void postProcessingChunk(short stage, LabelsOfFV[] multiLabels,
    int numClasses, Set<ChunkOrEntity> chunks, Map<Integer,ChunkLengthStats> chunkLenHash) {
    int num = multiLabels.length;
    Map<ChunkOrEntity,Integer> tempChunks = new HashMap<ChunkOrEntity,Integer>();
    for(int j = 0; j < numClasses; j += 2) { // for start and end token
      ChunkLengthStats chunkLen;
      //String labelS = new Integer(j / 2 + 1).toString();
      int labelS = j / 2 + 1;
      if(chunkLenHash.get(labelS) != null)
        chunkLen = chunkLenHash.get(labelS);
      else chunkLen = new ChunkLengthStats();
      for(int i = 0; i < num; ++i) {
        if(multiLabels[i].probs[j] > boundaryProb) {
          for(int i1 = i; i1 < num; ++i1)
            // Use the boundary probability and the length of chunk statistics
            if(multiLabels[i1].probs[j + 1] > boundaryProb
              && i1 - i + 1 < ChunkLengthStats.maxLen
              && chunkLen.lenStats[i1 - i + 1] > 0) {
               //if(multiLabels[i1].probs[j+1]>boundaryProb) {
              float entityP = multiLabels[i].probs[j]*multiLabels[i1].probs[j + 1];
              if(entityP>entityProb) {
                ChunkOrEntity chunk = new ChunkOrEntity(i, i1);
                chunk.prob = entityP;
                chunk.name = j / 2 + 1;
                tempChunks.put(chunk, 1);
                //break;
              }
            }
        }
      }// End of loop for each instance (i)
    }
    // Solve the overlap case so that every entity has just one label
    if(LogService.minVerbosityLevel>1)
      System.out.println("*** numberinTempChunks=" + tempChunks.size());
    HashMap<String,ChunkOrEntity>mapChunks = new HashMap<String,ChunkOrEntity>();
    for(Object obj : tempChunks.keySet()) {
      ChunkOrEntity entity = (ChunkOrEntity)obj;
      mapChunks
        .put(entity.start + "_" + entity.end + "_" + entity.name, entity);
    }
    List<String>indexes = new ArrayList<String>(mapChunks.keySet());
    // LongCompactor c = new LongCompactor();
    Collections.sort(indexes);
    for(int i1 = 0; i1 < indexes.size(); ++i1) {
      // for(Object ob1:tempChunks.keySet() ) {
      Object ob1 = mapChunks.get(indexes.get(i1));
      if(tempChunks.get(ob1).toString().equals("1")) {
        ChunkOrEntity chunk1 = (ChunkOrEntity)ob1;
        for(int j1 = i1 + 1; j1 < indexes.size(); ++j1) {
          Object ob2 = mapChunks.get(indexes.get(j1));
          // for(Object ob2:tempChunks.keySet()) {
          if(tempChunks.get(ob2).toString().equals("1")) {
            ChunkOrEntity chunk2 = (ChunkOrEntity)ob2;
            if(chunk2.start != chunk1.start || chunk2.end != chunk1.end
              || chunk2.name != chunk1.name) {
              // if the two entities overlap
              if((chunk1.start >= chunk2.start && chunk1.start <= chunk2.end)
                || (chunk1.end <= chunk2.end && chunk1.end >= chunk2.start)) {
                if(chunk1.prob > chunk2.prob) {
                  tempChunks.put(chunk2, 0);
                } else if(chunk1.prob < chunk2.prob) {
                  tempChunks.put(chunk1, 0);
                  break; // break the inner loop (ob2)
               } else if(chunk1.end - chunk1.start>chunk2.end - chunk2.start) {
                  tempChunks.put(chunk1, 0);
                  break;
                }
                else {
                  tempChunks.put(chunk2, 0);
                }
              }
            }
          }
        }// end of the inner loop
      }
    }// end of the outer loop
    for(ChunkOrEntity ob1 : tempChunks.keySet()) {
      if(tempChunks.get(ob1).intValue() == 1) chunks.add(ob1);
    }
  }
  /** Post-processing the results for the classification problem
   * for the one vs all others method: select the label with the 
   * maximal probability which is bigger than the pre-defined threshold, 
   * for one instance. 
   */
  public void postProcessingClassification(short stage,
    LabelsOfFV[] multiLabels, int[] selectedLabels, float[] valueLabels) {
    int num = multiLabels.length;
    float maxValue;
    int maxLabel;
    for(int i = 0; i < num; ++i) { // for each instance
      maxValue = (float)thresholdC;
      maxLabel = -1;
      for(int j = 0; j < multiLabels[i].num; ++j) { // for each class
        if(multiLabels[i].probs[j] > maxValue) {
          maxValue = multiLabels[i].probs[j];
          maxLabel = j;
        }
        selectedLabels[i] = maxLabel;
        valueLabels[i] = maxValue;
      }
    }
  }
  /** Post-processing for the one vs another method,
   * for the training data with null label. 
   */
  public void voteForOneVSAnotherNull(DataForLearning dataFVinDoc,
    int numClasses) {
    for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i) { 
      LabelsOfFeatureVectorDoc labelFVsDoc = dataFVinDoc.labelsFVDoc[i];
      for(int j = 0; j < labelFVsDoc.multiLabels.length; ++j) { 
        LabelsOfFV multiLabel0 = dataFVinDoc.labelsFVDoc[i].multiLabels[j];
        int[] voteResults = new int[numClasses];
        int voteNull;
        int kk = 0;
        // for the null
        voteNull = 0;
        for(int j1 = 0; j1 < numClasses; ++j1) {
          if(multiLabel0.probs[kk] > thresholdC)
            voteResults[j1]++;
          else voteNull++;
          ++kk;
        }
        // for other label
        for(int i1 = 0; i1 < numClasses; ++i1)
          for(int j1 = i1 + 1; j1 < numClasses; ++j1) {
            if(multiLabel0.probs[kk] > thresholdC)
              voteResults[i1]++;
            else voteResults[j1]++;
            ++kk;
          }
        // Convert the vote results into label
        int maxVote = voteNull;
        kk = -1;
        for(int i1 = 0; i1 < numClasses; ++i1)
          if(maxVote < voteResults[i1]) {
            maxVote = voteResults[i1];
            kk = i1;
          }
        LabelsOfFV multiLabel = new LabelsOfFV(numClasses);
        multiLabel.probs = new float[multiLabel.num];
        if(kk >= 0) multiLabel.probs[kk] = (float)1.0;
        dataFVinDoc.labelsFVDoc[i].multiLabels[j] = multiLabel;
      }
    }
  }

  /** Post-processing for the one vs another method,
   * for the training data without the null label. 
   */
  public void voteForOneVSAnother(DataForLearning dataFVinDoc, int numClasses) {
    for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i) { 
      LabelsOfFeatureVectorDoc labelFVsDoc = dataFVinDoc.labelsFVDoc[i];
      for(int j = 0; j < labelFVsDoc.multiLabels.length; ++j) { 
        LabelsOfFV multiLabel0 = dataFVinDoc.labelsFVDoc[i].multiLabels[j];
        int[] voteResults = new int[numClasses];
        int kk = 0;
        // for other label
        for(int i1 = 0; i1 < numClasses; ++i1)
          for(int j1 = i1 + 1; j1 < numClasses; ++j1) {
            if(multiLabel0.probs[kk] > thresholdC)
              voteResults[i1]++;
            else voteResults[j1]++;
            ++kk;
          }
        // Convert the vote results into label
        int maxVote = -1;
        kk = -1;
        for(int i1 = 0; i1 < numClasses; ++i1)
          if(maxVote < voteResults[i1]) {
            maxVote = voteResults[i1];
            kk = i1;
          }
        LabelsOfFV multiLabel = new LabelsOfFV(numClasses);
        multiLabel.probs = new float[multiLabel.num];
        if(kk >= 0) multiLabel.probs[kk] = (float)1.0;
        dataFVinDoc.labelsFVDoc[i].multiLabels[j] = multiLabel;
      }
    }
  }
}
