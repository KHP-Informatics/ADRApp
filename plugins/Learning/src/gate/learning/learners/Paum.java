package gate.learning.learners;

import gate.learning.SparseFeatureVector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

/** The Java implementation of the PAUM learning algorithm,
 * the Perceptron algorithm with uneven margins. See the following reference
 * for more about the PAUM.
 * Y. Li, H. Zaragoza, R. Herbrich, J. Shawe-Taylor, and J. Kandola.
 * The Perceptron Algorithm with Uneven Margins. In Proceedings of the 9th
 * International Conference on Machine Learning (ICML-2002), p379-386, 2002.
 */
public class Paum extends SupervisedLearner {
  
  /** The positive margin parameter. */
  private float tauP = 5;
  /** The negative margin parameter. */
  private float tauN =1;
  /** The modification for the threshold. */
  private float optB = 0;
  
  /** The maximal loop number for paum Learning. */
  final private int totalLoops = 300;
  /** The increment value for lambda. */
  final private float lambInc = (float)1.0;
  
  /** Class constructor with no parameter. */
  public Paum() {
    this.setLearnerName("PAUM");
    
  }
  
  /** Class constructor with two margin parameters. */ 
  public Paum(float tauP, float tauN) {
    this.setLearnerName("PAUM");
    this.tauP = tauP;
    this.tauN = tauN;
  }
  
  public void getParametersFromCommmand() {
    String [] items = commandLine.split(" ");
    int len = items.length;
    for(int i=0; i<len; ++i) {
      if(items[i].equals("-p") && i+1 <len)
        this.tauP = new Float(items[i+1]).floatValue();
      if(items[i].equals("-n") && i+1 <len)
        this.tauN = new Float(items[i+1]).floatValue();
      if(items[i].equals("-optB") && i+1 <len)
        this.optB = new Float(items[i+1]).floatValue();
    }
  }
  
  /** PAUM learning and obtain a Percptron model in primal form, read training data from a file,
   * and save the model into another files, and another parameter
   * for the number of training documents.
   * @throws IOException 
   */ 
  public void training(BufferedWriter modelFile, 
          SparseFeatureVector [] dataLearning, int totalNumFeatures, short [] classLabels, int numTraining) {
    
    //let the tauN be negative
    if(tauN>0)
      tauN = -tauN;
    
    //allocate float array for the parameter lambda for PAUM
    //we suppose that the initial value of every element in the array is zero 
    float [] lambs = new float[numTraining];
    
    float b=0; //biase parameter
    //weight vector, initial values should be zero
    float [] w = new float[totalNumFeatures];
    
    boolean isModified;
    for(int iLoop=0; iLoop<this.totalLoops; ++iLoop) {
      isModified  = false;
      for(int iCounter=0; iCounter<numTraining; ++iCounter) {
        final int i = iCounter;
          double sum =0;
          int len = dataLearning[i].getLen();
          //int [] indexes = dataLearning[i].getIndexes();
          //float [] values = dataLearning[i].getValues();
          
          for(int j1=0; j1<len; ++j1)
            sum += w[dataLearning[i].nodes[j1].index]*dataLearning[i].nodes[j1].value;
          
          sum += b;
          sum += lambs[i];
          
          short y00 = classLabels[i];
          if(y00>0 && sum<=tauP) {
            for(int j1=0; j1<len; ++j1)
              w[dataLearning[i].nodes[j1].index] += dataLearning[i].nodes[j1].value;
            lambs[i] += lambInc;
            b += 1;
            isModified = true;
          } 
          else if(y00<0 && sum>=tauN) {
            for(int j1=0; j1<len; ++j1)
              w[dataLearning[i].nodes[j1].index] -= dataLearning[i].nodes[j1].value;
            lambs[i] -= lambInc;
            b -= 1;
            isModified = true;
          }
      }//end of loop for training instances
      if(!isModified)
        break;
    }//end of loop for paum learning
    
    //finally, write the learned model into model file
    try {
      SvmLibSVM.writeLinearModelIntoFile(modelFile, b, w, totalNumFeatures);
    } catch(IOException e) {
      e.printStackTrace();
    }
    
    
  }
  
  public void applying(BufferedReader modelFile, DataForLearning dataFVinDoc, int totalNumFeatures, int numClasses) {
    if(!isUseTau)
      optB = 0;
    //System.out.println("optB="+optB+", tauP="+this.tauP+", tauN="+this.tauN);
    SvmLibSVM.applyLinearModel(modelFile, dataFVinDoc, totalNumFeatures, numClasses, optB, this.isUseTauALLCases);
    return;
  }
  
  /** Method for training, by reading from data file for feature vectors, and 
   * with label as input. */
  public void trainingWithDataFile(BufferedWriter modelFile,
    BufferedReader dataFile, int totalNumFeatures,
    short[] classLabels, int numTraining) {
    
  }
   
}
