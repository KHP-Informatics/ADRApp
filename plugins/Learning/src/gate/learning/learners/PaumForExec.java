/*
 *  PaumForExec.java
 * 
 *  Yaoyong Li 27/04/2009
 *
 *  $Id: PaumForExec.java, v 1.0 2009-04-27 12:58:16 +0000 yaoyong $
 */

package gate.learning.learners;

import gate.learning.SparseFeatureVector;
import gate.learning.learners.svm.svm_parameter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class PaumForExec extends SupervisedLearner {
  /** The positive margin parameter. */
  @SuppressWarnings("unused")
  private float tauP = 5;
  /** The negative margin parameter. */
  @SuppressWarnings("unused")
  private float tauN = 1;
  /** The modification for the threshold. */
  private float optB = 0;
  /** The data file for svm learning. */
  private String svmDat;
  /** The model file for svm learning. */
  private String modelSVMFile;
  /** The maximal loop number for paum Learning. */
  @SuppressWarnings("unused")
  final private int totalLoops = 300;
  /** The increment value for lambda. */
  @SuppressWarnings("unused")
  final private float lambInc = (float)1.0;

  /** Class constructor with no parameter. */
  public PaumForExec() {
    this.setLearnerName("PAUMForExec");
    tauP = 5;
    tauN = 1;
    optB = 0;
  }

  /** Class constructor with two margin parameters. */
  public PaumForExec(float tauP, float tauN) {
    this.setLearnerName("PAUMForExec");
    this.tauP = tauP;
    this.tauN = tauN;
    optB = 0;
  }

  public void getParametersFromCommmand() {
    String[] items = commandLine.split(" ");
    int len = items.length;
    modelSVMFile = items[len - 1];
    svmDat = items[len - 2];
    for(int i = 0; i < len; ++i) {
      if(items[i].equals("-p") && i + 1 < len)
        this.tauP = new Float(items[i + 1]).floatValue();
      if(items[i].equals("-n") && i + 1 < len)
        this.tauN = new Float(items[i + 1]).floatValue();
      if(items[i].equals("-optB") && i + 1 < len)
        this.optB = new Float(items[i + 1]).floatValue();
    }
  }

  /**
   * Method for training, by reading from data file for feature vectors, and
   * with label as input.
   */
  public void trainingWithDataFile(BufferedWriter modelFile,
    BufferedReader dataFile, int totalNumFeatures, short[] classLabels,
    int numTraining) {
    try {
      // Write the data into the data file for svm learning
      BufferedWriter svmDataBuff = new BufferedWriter(new FileWriter(new File(
        svmDat)));
      for(int iCounter = 0; iCounter < numTraining; ++iCounter) {
        final int i = iCounter;
        if(classLabels[i] > 0)
          svmDataBuff.append("+1 ");
        else svmDataBuff.append("-1 ");
        // int [] indexes = dataLearning[i].getIndexes();
        // float [] values = dataLearning[i].getValues();
        // for(int j=0; j<dataLearning[i].getLen(); ++j)
        // svmDataBuff.append("
        // "+dataLearning[i].nodes[j].index+":"+dataLearning[i].nodes[j].value);
        String line = dataFile.readLine();
        svmDataBuff.append(line);
        svmDataBuff.append("\n");
      }
      svmDataBuff.close();
      // Execute the command for the SVM learning
      // Get the command line for the svm_learn, by getting rid of the tau
      // parameter
      String commandLineSVM = obtainPAUMCommandline(commandLine, numTraining);
      // Run the external svm learn exectuable
      SvmForExec svmIns = new SvmForExec();
      svmIns.runExternalCommand(commandLineSVM);
      // runExternalCommandWithRedirect(commandLineSVM);
      // Read the model from the svm results file and write it into our model
      // file
      writePAUMModelIntoFile(modelSVMFile, modelFile, totalNumFeatures);
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  public void training(BufferedWriter modelFile,
    SparseFeatureVector[] dataLearning, int totalNumFeatures,
    short[] classLabels, int numTraining) {
    try {
      // Write the data into the data file for svm learning
      BufferedWriter svmDataBuff = new BufferedWriter(new FileWriter(new File(
        svmDat)));
      for(int iCounter = 0; iCounter < numTraining; ++iCounter) {
        final int i = iCounter;
        if(classLabels[i] > 0)
          svmDataBuff.append("1");
        else svmDataBuff.append("-1");
        // int [] indexes = dataLearning[i].getIndexes();
        // float [] values = dataLearning[i].getValues();
        for(int j = 0; j < dataLearning[i].getLen(); ++j)
          svmDataBuff.append(" " + dataLearning[i].nodes[j].index + ":"
            + dataLearning[i].nodes[j].value);
        svmDataBuff.append("\n");
      }
      svmDataBuff.close();
      // Execute the command for the SVM learning
      // Get the command line for the svm_learn, by getting rid of the tau
      // parameter
      String commandLineSVM = obtainPAUMCommandline(commandLine, numTraining);
      // Run the external svm learn exectuable
      SvmForExec svmIns = new SvmForExec();
      svmIns.runExternalCommand(commandLineSVM);
      // runExternalCommandWithRedirect(commandLineSVM);
      // Read the model from the svm results file and write it into our model
      // file
      // writeSVMModelIntoFile(modelSVMFile, kernelType, modelFile,
      // totalNumFeatures);
      writePAUMModelIntoFile(modelSVMFile, modelFile, totalNumFeatures);
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  public void applying(BufferedReader modelFile, DataForLearning dataFVinDoc,
    int totalNumFeatures, int numClasses) {
    svm_parameter svmParam = new svm_parameter();
    svmParam.kernel_type = svm_parameter.LINEAR;
    SvmLibSVM.svmApplying(modelFile, dataFVinDoc, totalNumFeatures, numClasses,
      optB, svmParam, this.isUseTauALLCases);
  }

  public static void writePAUMModelIntoFile(String modelSVMFile,
    BufferedWriter modelFile, int totalNumFeatures) {
    BufferedReader svmModelBuff;
    
    try {
      svmModelBuff = new BufferedReader(new FileReader(new File(modelSVMFile)));
      String line = svmModelBuff.readLine();
      while(line !=null) {
        modelFile.append(line+"\n");
        line = svmModelBuff.readLine();
      }
    } catch(FileNotFoundException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }
  public static String obtainPAUMCommandline(String commandLine, int numTrain) {
    StringBuffer commandSVM = new StringBuffer();
    String[] items = commandLine.split("[ \t]+");
    int len = 0;
    commandSVM.append(items[0]);
    commandSVM.append(" -e "+numTrain);
    ++len;
    while(len < items.length) {
      if(items[len].equalsIgnoreCase("-tau") ||
        items[len].equalsIgnoreCase("-optB")) {
        ++len;
      } else {
        commandSVM.append(" " + items[len]);
      }
      ++len;
    }
    return commandSVM.toString().trim();
  }
}
