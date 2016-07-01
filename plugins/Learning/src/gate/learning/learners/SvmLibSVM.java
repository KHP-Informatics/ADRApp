/*
 *  SvmLibSVM.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: SvmLibSVM.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners;

import gate.learning.LogService;
import gate.learning.SparseFeatureVector;
import gate.learning.UsefulFunctions;
import gate.learning.learners.svm.svm;
import gate.learning.learners.svm.svm.decision_function;
import gate.learning.learners.svm.svm_node;
import gate.learning.learners.svm.svm_parameter;
import gate.learning.learners.svm.svm_problem;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * SVM traning using the LibSVM and SVM classification. Currently one the binary
 * SVM classification is implemented.
 */
public class SvmLibSVM extends SupervisedLearner {
  /** The uneven margins parameter. */
  public float tau = (float)1.0;
  svm_parameter param;

  /** Class constructor without parameter. */
  public SvmLibSVM() {
  }

  /** Class constructor with tau parameter. */
  public SvmLibSVM(float t) {
    this.tau = t;
  }

  /** Get the parameters from the command line. */
  public void getParametersFromCommmand() {
    param = new svm_parameter();
    if(commandLine == null) {
      if(LogService.minVerbosityLevel > 1)
        System.out.println("no options specified, using the default ones!");
      parseCommandLine(" ");
    } else {
      String[] items = commandLine.split("[ \t]+");
      // Get the tau value
      for(int i = 0; i < items.length; ++i)
        if(items[i].equalsIgnoreCase("-tau") && i + 1 < items.length)
          this.tau = new Float(items[i + 1]).floatValue();
      commandLine.concat("  ");
      String commandLineSVM = obtainSVMCommandline(commandLine);
      if(LogService.minVerbosityLevel > 1)
        System.out.println("**commandLineSVM= " + commandLineSVM);
      parseCommandLine(commandLineSVM);
    }
  }

  /** Using the methods of libSVM to training the SVM model. */
  public void training(BufferedWriter modelFile,
    SparseFeatureVector[] dataLearning, int totalNumFeatures,
    short[] classLabels, int numTraining) {
    // Set the svm problem for libsvm
    svm_problem svmProb = new svm_problem();
    svmProb.l = numTraining; // set the number of FVs
    svmProb.y = new double[svmProb.l];
    for(int i = 0; i < numTraining; ++i)
      svmProb.y[i] = classLabels[i]; // set the label
    svmProb.x = new svm_node[svmProb.l][];
    for(int i = 0; i < numTraining; ++i) {
      //int len = dataLearning[i].getLen();
      //svmProb.x[i] = new svm_node[len];
      //for(int j = 0; j < len; ++j) { // set each FV
        //svmProb.x[i][j] = new svm_node();
        //svmProb.x[i][j].index = dataLearning[i].indexes[j];
        //svmProb.x[i][j].value = dataLearning[i].values[j];
      //}
      svmProb.x[i] = dataLearning[i].nodes;
    }
    // We got the parameter setting already in svmTrain
    // Call the svm_train_one to train a binary classification
    // set the weight for two class as 1.0, namely j=1 in svm_light
    // System.out.println("svm_one learning begins... ");
    // System.out.println("kernelType="+param.kernel_type);
    // System.out.println("cost="+param.C);
    // System.out.println("cachsize="+param.cache_size);
    decision_function decisionFunc = svm
      .svm_train_one(svmProb, param, param.C, param.C); //1.0, 1.0);
    // System.out.println("end of the SVM_one.");
    // decisionFunc includes the alphas (with *y) and rho (=-b)
    // Write the svm model into the model file
    try {
      float b = -1 * (float)decisionFunc.rho;
      if(param.kernel_type == svm_parameter.LINEAR) {
        // Convert the dual form into primal form
        float[] w = new float[totalNumFeatures];
        for(int i = 0; i < svmProb.l; ++i) {
          if(Math.abs(decisionFunc.alpha[i]) > 0)
            for(int j = 0; j < svmProb.x[i].length; ++j)
              w[svmProb.x[i][j].index] += svmProb.x[i][j].value
                * decisionFunc.alpha[i];
        }
        writeLinearModelIntoFile(modelFile, b, w, totalNumFeatures);
      } else {
        modelFile.append(b + "\n");
        int numSV = 0;
        for(int i = 0; i < svmProb.l; ++i)
          if(Math.abs(decisionFunc.alpha[i]) > 0) ++numSV;
        modelFile.append(numSV + "\n");
        for(int i = 0; i < svmProb.l; ++i) {
          if(Math.abs(decisionFunc.alpha[i]) > 0) {
            modelFile.append(new Double(decisionFunc.alpha[i]).toString());
            for(int j = 0; j < svmProb.x[i].length; ++j)
              modelFile.append(" " + svmProb.x[i][j].index + ":"
                + svmProb.x[i][j].value);
            modelFile.append(" #\n");// in order to keep the same as model file
            // in svm_light
          }
        }
      }
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /** Apply the SVM model to the data. */
  public void applying(BufferedReader modelFile, DataForLearning dataFVinDoc,
    int totalNumFeatures, int classIndex) {
    float optB;
    if(isUseTau)
      optB = (1 - tau) / (1 + tau);
    else optB = 0;
    svmApplying(modelFile, dataFVinDoc, totalNumFeatures, classIndex, optB,
      param, this.isUseTauALLCases);
  }

  /** Applying the svm models. */
  public static void svmApplying(BufferedReader modelFile,
    DataForLearning dataFVinDoc, int totalNumFeatures, int classIndex,
    float optB, svm_parameter svmParam, boolean isUseTauAll) {
    if(svmParam.kernel_type == svm_parameter.LINEAR) {
      if(LogService.minVerbosityLevel > 1)
        System.out.println("Linear kernel used.");
      applyLinearModel(modelFile, dataFVinDoc, totalNumFeatures, classIndex,
        optB, isUseTauAll);
    } else {
      if(LogService.minVerbosityLevel > 1)
        System.out.println("Non-linear kernel used.");
      applyingDualFormModel(modelFile, dataFVinDoc, classIndex, optB, svmParam,
        isUseTauAll);
    }
  }

  /** Applying the svm models in dual form. */
  public static void applyingDualFormModel(BufferedReader modelFile,
    DataForLearning dataFVinDoc, int classIndex, float optB,
    svm_parameter svmParam, boolean isUseTauAll) {
    try {
      // for each class
      if(LogService.minVerbosityLevel > 1) {
        System.out.println("****  classIndex=" + classIndex);
        System.out.println("  d=" + svmParam.degree + ", g=" + svmParam.gamma
          + ", r=" + svmParam.coef0);
      }
      float b;
      String[] items = modelFile.readLine().split(" ");
      // Get the number of positive examples and negative examples
      int[] instDist = new int[2];
      obtainInstDist(items, instDist);
      b = new Float(modelFile.readLine()).floatValue();
      if(isUseTauAll)
        b += optB;
      else {
        if(instDist[0] > 0 && instDist[1] / instDist[0] > 10) b += optB;
        if(instDist[1] > 0 && instDist[0] / instDist[1] > 10) b -= optB;
      }
      
      int numSV = new Integer(modelFile.readLine());
      SparseFeatureVector[] svFVs = new SparseFeatureVector[numSV];
      double[] alphas = new double[numSV];
      for(int i = 0; i < numSV; ++i) {
        alphas[i] = readOneSV(modelFile.readLine(), svFVs, i);
      }
      for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i) {
        SparseFeatureVector[] fvs = dataFVinDoc.trainingFVinDoc[i].getFvs();
        for(int j = 0; j < dataFVinDoc.trainingFVinDoc[i].getNumInstances(); ++j) {
          double sum = 0.0;
          for(int j1 = 0; j1 < numSV; ++j1) {
            sum += alphas[j1] * kernel_function(fvs[j], svFVs[j1], svmParam);
          }
          sum += b;
          sum = UsefulFunctions.sigmoid(sum);
          dataFVinDoc.labelsFVDoc[i].multiLabels[j].probs[classIndex] = (float)sum;
        }
      }
    } catch(NumberFormatException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /** Read one binary svm model. */
  public static void readOneSVMModel(BufferedReader svmModelBuff, int numSV,
    SparseFeatureVector[] svFVs, double[] alphas) throws IOException {
    for(int i = 0; i < numSV; ++i) {
      String line = svmModelBuff.readLine();
      
      alphas[i] = readOneSV(line, svFVs, i);
    }
  }

  /** Read one support vector of the SVM model. */
  public static double readOneSV(String line, SparseFeatureVector[] svFVs, int iCounter) {
    final int i = iCounter;
    String[] items;
    if(line.endsWith("#"))
      line = line.substring(0, line.length()-1);
    line = line.trim();items = line.split(" ");
    double alpha = new Double(items[0]).doubleValue();
    int len = 0;
    len = items.length-1;
    
    svFVs[i] = new SparseFeatureVector(len);
    for(int j = 0; j < len; ++j) {
      String[] indexValue = items[j+1].split(":");
      svFVs[i].nodes[j].index = new Integer(indexValue[0]);
      svFVs[i].nodes[j].value = new Float(indexValue[1]);
    }
    return alpha;
  }

  /** Kernel function computation for non-linear kernel. */
  public static double kernel_function(SparseFeatureVector x,
    SparseFeatureVector y, svm_parameter param) {
    switch(param.kernel_type){
      case svm_parameter.LINEAR:
        return dot(x, y);
      case svm_parameter.POLY:
        return powi(param.gamma * dot(x, y) + param.coef0, param.degree);
      case svm_parameter.RBF: {
        double sum = 0;
        int xlen = x.getLen();
        int ylen = y.getLen();
        int i = 0;
        int j = 0;
        while(i < xlen && j < ylen) {
          if(x.nodes[i].index == y.nodes[j].index) {
            double d = x.nodes[i++].value - y.nodes[j++].value;
            sum += d * d;
          } else if(x.nodes[i].index > y.nodes[i].index) {
            sum += y.nodes[j].value * y.nodes[j].value;
            ++j;
          } else {
            sum += x.nodes[i].value * x.nodes[i].value;
            ++i;
          }
        }
        while(i < xlen) {
          sum += x.nodes[i].value * x.nodes[i].value;
          ++i;
        }
        while(j < ylen) {
          sum += y.nodes[j].value * y.nodes[j].value;
          ++j;
        }
        return Math.exp(-param.gamma * sum);
      }
      case svm_parameter.SIGMOID:
        return Math.tanh(param.gamma * dot(x, y) + param.coef0);
      // case svm_parameter.PRECOMPUTED:
      // return x[(int)(y[0].value)].value;
      default:
        return 0.0;
    }
  }

  /** A fast computation of the polynomial functions. */
  private static double powi(double base, int times) {
    double tmp = base, ret = 1.0;
    for(int t = times; t > 0; t /= 2) {
      if(t % 2 == 1) ret *= tmp;
      tmp = tmp * tmp;
    }
    return ret;
  }

  /** Inner product between two sparse feature vectors. */
  static double dot(SparseFeatureVector x, SparseFeatureVector y) {
    double sum = 0;
    int xlen = x.getLen();
    int ylen = y.getLen();
    int i = 0;
    int j = 0;
    while(i < xlen && j < ylen) {
      if(x.nodes[i].index == y.nodes[j].index)
        sum += x.nodes[i++].value * y.nodes[j++].value;
      else {
        if(x.nodes[i].index > y.nodes[j].index)
          ++j;
        else ++i;
      }
    }
    return sum;
  }

  /** Write the linear SVM model into file. */
  public static void writeLinearModelIntoFile(BufferedWriter modelFile,
    float b, float[] w, int totalFeatures) throws IOException {
    float verySmallFloat = (float)0.000000001;
    modelFile.append(b + "\n");
    int num;
    num = 0;
    for(int i = 0; i < totalFeatures; ++i)
      if(Math.abs(w[i]) > verySmallFloat) ++num;
    modelFile.append(num + " "+totalFeatures+"\n");
    for(int i = 0; i < totalFeatures; ++i)
      if(Math.abs(w[i]) > verySmallFloat)
        modelFile.append(i + " " + w[i] + "\n");
    return;
  }

  /** Read the linear SVM model from file. */
  public static float readWeightVectorFromFile(BufferedReader modelFile,
    float[] w) throws NumberFormatException, IOException {
    float b;
    b = new Float(modelFile.readLine()).floatValue();
    int num;
    String[] lineItems;
    lineItems = modelFile.readLine().split(" ");
    num = new Integer(lineItems[0]);
    int index;
    float value;
    for(int i = 0; i < num; ++i) {
      lineItems = modelFile.readLine().split(" ");
      index = new Integer(lineItems[0]);
      value = new Float(lineItems[1]);
      w[index] = value;
    }
    return b;
  }

  /** Normalisation of weight vector */
  public static float normalisation(float[] w, float b) {
    double sum = 0;
    for(int i = 0; i < w.length; ++i)
      if(Math.abs(w[i]) > 0.0000000001) sum += w[i] * w[i];
    sum += b * b;
    sum = Math.sqrt(sum);
    if(sum > 0.000000000001) {
      for(int i = 0; i < w.length; ++i)
        if(Math.abs(w[i]) > 0.0000000001) w[i] /= sum;
      b /= sum;
    }
    return b;
  }

  /** Apply the linear SVM model to the data. */
  public static void applyLinearModel(BufferedReader modelFile,
    DataForLearning dataFVinDoc, int totalNumFeatures, int classIndex,
    float optB, boolean isUseTauAll) {
    try {
      float b;
      float[] w = new float[totalNumFeatures];
      String items[] = modelFile.readLine().split(" ");
      // Get the number of positive examples and negative examples
      int[] instDist = new int[2];
      obtainInstDist(items, instDist);
      b = readWeightVectorFromFile(modelFile, w);
      // normalise the weight vector
      // b = normalisation(w, b);
      // modify the b by using the uneven margins parameter tau
      if(isUseTauAll)
        b += optB;
      else {
        if(instDist[0] > 0 && instDist[1] / instDist[0] > 10) b += optB;
        if(instDist[1] > 0 && instDist[0] / instDist[1] > 10) b -= optB;
      }
      for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i) {
        SparseFeatureVector[] fvs = dataFVinDoc.trainingFVinDoc[i].getFvs();
        for(int j = 0; j < dataFVinDoc.trainingFVinDoc[i].getNumInstances(); ++j) {
          //int[] index = fvs[j].getIndexes();
          //float[] value = fvs[j].getValues();
          double sum = 0.0;
          for(int j1 = 0; j1 < fvs[j].getLen(); ++j1)
            sum += fvs[j].nodes[j1].value * w[fvs[j].nodes[j1].index];
          sum += b;
          sum = UsefulFunctions.sigmoid(sum);
          dataFVinDoc.labelsFVDoc[i].multiLabels[j].probs[classIndex] = (float)sum;
        }
      }
    } catch(NumberFormatException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Get the numbers of positive and negative examples in the data set.
   */
  static void obtainInstDist(String[] items, int[] instDist) {
    for(int i = 0; i < items.length; ++i) {
      if(items[i].startsWith("numTraining="))
        instDist[1] = Integer.parseInt(items[i]
          .substring(items[i].indexOf("=") + 1));
      if(items[i].startsWith("numPos="))
        instDist[0] = Integer.parseInt(items[i]
          .substring(items[i].indexOf("=") + 1));
    }
    instDist[1] -= instDist[0];
  }

  /**
   * Parse the command line of the SVM to obtain the parameter values for the
   * LibSVM.
   */
  public void parseCommandLine(String commandSVM) {
    commandSVM = commandSVM.trim();
    // System.out.println("The command in parse: *"+commandSVM+"*");
    String[] argv = commandSVM.split("[ \t]+");
    // default values
    param.svm_type = svm_parameter.C_SVC;
    param.kernel_type = svm_parameter.LINEAR;
    param.degree = 3;
    param.gamma = 1; // 1/k;
    param.coef0 = 1;
    param.nu = 0.5;
    param.cache_size = 100;
    param.C = 1;
    param.eps = 1e-3;
    param.p = 0.1;
    param.shrinking = 1;
    param.probability = 0;
    param.nr_weight = 0;
    param.weight_label = new int[0];
    param.weight = new double[0];
    // parse options
    int i = 0;
    while(i < argv.length) {
      if(argv[i].charAt(0) != '-') {
        if(LogService.minVerbosityLevel > 1)
          System.out
            .println("no other options specified for the SVM, using the default options.");
        break;
      }
      ++i;
      if(i >= argv.length) {
        break;
      }
      switch(argv[i - 1].charAt(1)){
        case 's':
          param.svm_type = Integer.parseInt(argv[i]);
          break;
        case 't':
          param.kernel_type = Integer.parseInt(argv[i]);
          break;
        case 'd':
          param.degree = Integer.parseInt(argv[i]);
          break;
        case 'g':
          param.gamma = Double.parseDouble(argv[i]);
          break;
        case 'r':
          param.coef0 = Double.parseDouble(argv[i]);
          break;
        case 'n':
          param.nu = Double.parseDouble(argv[i]);
          break;
        case 'm':
          param.cache_size = Double.parseDouble(argv[i]);
          break;
        case 'c':
          param.C = Double.parseDouble(argv[i]);
          break;
        case 'e':
          param.eps = Double.parseDouble(argv[i]);
          break;
        case 'p':
          param.p = Double.parseDouble(argv[i]);
          break;
        case 'h':
          param.shrinking = Integer.parseInt(argv[i]);
          break;
        case 'b':
          param.probability = Integer.parseInt(argv[i]);
          break;
        case 'v':
          break;
        case 'w':
          ++param.nr_weight;
          {
            int[] old = param.weight_label;
            param.weight_label = new int[param.nr_weight];
            System
              .arraycopy(old, 0, param.weight_label, 0, param.nr_weight - 1);
          }
          {
            double[] old = param.weight;
            param.weight = new double[param.nr_weight];
            System.arraycopy(old, 0, param.weight, 0, param.nr_weight - 1);
          }
          param.weight_label[param.nr_weight - 1] = Integer
            .parseInt(argv[i - 1].substring(2));
          param.weight[param.nr_weight - 1] = Float.parseFloat(argv[i]);
          break;
        default:
          System.err.print("unknown option\n");
      }
      ++i;
    }
  }

  /**
   * Get the command line for the svm_learn, by removing the tau parameter which
   * should be used for application.
   */
  public static String obtainSVMCommandline(String commandLine) {
    StringBuffer commandSVM = new StringBuffer();
    String[] items = commandLine.split("[ \t]+");
    int len = 0;
    while(len < items.length) {
      if(items[len].equalsIgnoreCase("-tau")) {
        ++len;
      } else {
        commandSVM.append(" " + items[len]);
      }
      ++len;
    }
    return commandSVM.toString().trim();
  }
  
  /** Method for training, by reading from data file for feature vectors, and 
   * with label as input. */
  public void trainingWithDataFile(BufferedWriter modelFile,
    BufferedReader dataFile, int totalNumFeatures,
    short[] classLabels, int numTraining) {
    
  }
}
