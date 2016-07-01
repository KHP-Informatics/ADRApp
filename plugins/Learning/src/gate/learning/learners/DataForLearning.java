/*
 *  DataForLearning.java
 *
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: DataForLearning.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners;
import gate.learning.DocFeatureVectors;
import gate.learning.LabelsOfFeatureVectorDoc;
import gate.learning.SparseFeatureVector;
import gate.learning.learners.svm.svm_node;
import gate.util.BomStrippingInputStreamReader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
/**
 * Data used for learning, read from the feature vector file.
 */
public class DataForLearning {
  /** Number of training (or test) documents. */
  private int numTrainingDocs;
  /** Training feature vectors, array for each document. */
  public DocFeatureVectors[] trainingFVinDoc = null;
  /** Training feature vectors in svm_node format, for libSVM. */
  public svm_node[][] svmNodeFVs = null;
  /** Labels for each feature vector, array for each document. */
  public LabelsOfFeatureVectorDoc[] labelsFVDoc = null;
  /** All the unique labels in the dataset */
  String[] allUniqueLabels;
  /** Total number of training examples. */
  int numTraining = 0;
  /** Total number of NLP features in FVs. */
  int totalNumFeatures = 0;
  /** Trivial constructor. */
  public DataForLearning() {
  }
  /** Constructor with the number of documents. */
  public DataForLearning(int num) {
    this.numTrainingDocs = num;
  }

  /** Read the feature vectors from data file for training or application. */
  public void readingFVsFromFile(File trainingData, boolean isUsingFile, File tempFVDataFile) {
    // the array to store the training data
    trainingFVinDoc = new DocFeatureVectors[numTrainingDocs];
    labelsFVDoc = new LabelsOfFeatureVectorDoc[numTrainingDocs];
    //Open the
    // read the training data from the file
    // first open the training data file
    try {
      //    Write the fv data into a file if the isUsingFile is true
      BufferedWriter fvTempWr = null;
      if(isUsingFile) {
        fvTempWr = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(tempFVDataFile), "UTF-8"));
      }
       //    compute the total number of features
      totalNumFeatures = 0;
      BufferedReader in = new BomStrippingInputStreamReader(
        new FileInputStream(trainingData), "UTF-8");
      String line;
      String[] items;
      for(int iCounter = 0; iCounter < numTrainingDocs; ++iCounter) {
        final int i = iCounter;
        line = in.readLine();
        while(line.startsWith("#"))
          line = in.readLine();
        items = line
          .split(gate.learning.ConstantParameters.ITEMSEPARATOR);
        int num;
        num = new Integer(items[1]);
        trainingFVinDoc[i] = new DocFeatureVectors();
        trainingFVinDoc[i].setDocID(items[2]);
        labelsFVDoc[i] = new LabelsOfFeatureVectorDoc();
        trainingFVinDoc[i].readDocFVFromFile(in, num, labelsFVDoc[i]);

        SparseFeatureVector[] fvs = trainingFVinDoc[i].getFvs();
        for(int j = 0; j < trainingFVinDoc[i].getNumInstances(); ++j) {
          //int[] indexes = fvs[j].getIndexes();
          //if(totalNumFeatures < indexes[indexes.length - 1])
            //totalNumFeatures = indexes[indexes.length - 1];
          int len = fvs[j].nodes.length;
          if(totalNumFeatures < fvs[j].nodes[len - 1].index)
            totalNumFeatures = fvs[j].nodes[len - 1].index;
        }

        if(isUsingFile) {
          SparseFeatureVector[] fvsInDoc = trainingFVinDoc[i].getFvs();
          // For each instance
          for(int jC = 0; jC < fvsInDoc.length; ++jC) {
          final int j = jC;
            int lenM1 = fvsInDoc[j].getLen()-1;
            if(lenM1>0) {
              for(int j1=0; j1<lenM1; ++j1)
                fvTempWr.append(fvsInDoc[j].nodes[j1].index+":"+fvsInDoc[j].nodes[j1].value+" ");
              fvTempWr.append(fvsInDoc[j].nodes[lenM1].index+":"+fvsInDoc[j].nodes[lenM1].value+"\n");
            }
            //fvsInDoc[j] = null; //trying to remove the data from the memory
          }
          trainingFVinDoc[i].deleteFvs(); //trying to remove the fv data from memory
        }
      }//end of loop for each document
      if(isUsingFile) fvTempWr.close();
      in.close();
      // compute the total number of training examples
      numTraining = 0;
      for(int i = 0; i < numTrainingDocs; ++i)
        numTraining += trainingFVinDoc[i].getNumInstances();

      // add 3 for safety, because the index is counted from 1, not 0
      totalNumFeatures += 5;
    } catch(IOException e) {
    }
    return;
  }

  /** Read the feature vectors from data file for training or application. */
  public void readingFVsMultiLabelFromFile(File trainingData) {
    // the array to store the training data
    trainingFVinDoc = new DocFeatureVectors[numTrainingDocs];
    labelsFVDoc = new LabelsOfFeatureVectorDoc[numTrainingDocs];
    // read the training data from the file
    // first open the training data file
    try {
      BufferedReader in = new BomStrippingInputStreamReader(new FileInputStream(
        trainingData), "UTF-8");
      String line;
      String[] items;
      for(int i = 0; i < numTrainingDocs; ++i) {
        line = in.readLine();
        while(line.startsWith("#"))
          line = in.readLine();
        items = line
          .split(gate.learning.ConstantParameters.ITEMSEPARATOR);
        int num;
        num = new Integer(items[1]);
        trainingFVinDoc[i] = new DocFeatureVectors();
        labelsFVDoc[i] = new LabelsOfFeatureVectorDoc();
        trainingFVinDoc[i].readDocFVFromFile(in, num, labelsFVDoc[i]);
      }
      // compute the total number of training examples
      numTraining = 0;
      for(int i = 0; i < numTrainingDocs; ++i)
        numTraining += trainingFVinDoc[i].getNumInstances();
      // compute the total number of features
      totalNumFeatures = 0;
      for(int i = 0; i < numTrainingDocs; ++i) {
        SparseFeatureVector[] fvs = trainingFVinDoc[i].getFvs();
        for(int j = 0; j < trainingFVinDoc[i].getNumInstances(); ++j) {
          //int[] indexes = fvs[j].getIndexes();
          //if(totalNumFeatures < indexes[indexes.length - 1])
            //totalNumFeatures = indexes[indexes.length - 1];
          int len = fvs[j].nodes.length;
          if(totalNumFeatures < fvs[j].nodes[len-1].index)
            totalNumFeatures = fvs[j].nodes[len - 1].index;
        }
      }
      // add 3 for safety, because the index is counted from 1, not 0
      totalNumFeatures += 5;
    } catch(IOException e) {
    }
    return;
  }

  public int getTotalNumFeatures() {
    return this.totalNumFeatures;
  }

  public int getNumTrainingDocs() {
    return this.numTrainingDocs;
  }

  public int getNumTraining() {
    return this.numTraining;
  }
}
