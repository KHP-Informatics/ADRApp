/*
 *  SupervisedLearner.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: SupervisedLearner.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners;

import gate.learning.SparseFeatureVector;
import java.io.BufferedReader;
import java.io.BufferedWriter;

/**
 * The abstract class for supervised binary learner. The implemented class is
 * SvmLibSVM.java so far.
 */
public abstract class SupervisedLearner {
  /** Learner's name. */
  private static String learnerName;
  /**
   * The executable name of the learner, which is used for the learner as the
   * external executable. Not suitable for the SvmLibSVM.java, because it is
   * implemented in Java class.
   */
  private String learnerExecutable;
  /** The parameters of the learner, in the string format. */
  private String learnerParams;
  /** The actual command line used for external executable. */
  public String commandLine;
  /**
   * Does use the parameter tau to adjust the numeric output of svm.
   */
  boolean isUseTau = true;
  /**
   * Does use the parameter tau to adjust the numeric output of svm.
   */
  boolean isUseTauALLCases = true;

  /** Abstract method of parsing the command to get the parameters. */
  public abstract void getParametersFromCommmand();

  /** Abstract method for training, with binary labeled data as input. */
  public abstract void training(BufferedWriter modelFile,
    SparseFeatureVector[] dataLearning, int totalNumFeatures,
    short[] classLabels, int numTraining);
  
  /** Abstract method for training, by reading from data file for feature vectors, and 
   * with label as input. */
  public abstract void trainingWithDataFile(BufferedWriter modelFile,
    BufferedReader dataFile, int totalNumFeatures,
    short[] classLabels, int numTraining);

  /**
   * Abstract method for application. Called once per class by
   * {@link MultiClassLearning}.
   */
  public abstract void applying(BufferedReader modelFile,
    DataForLearning dataLearning, int totalNumFeatures, int classIndex);

  public void setCommandLine(String command) {
    this.commandLine = command;
    commandLine = commandLine.replaceAll(" +", " ");
  }

  public String getCommandLine() {
    return this.commandLine;
  }

  public final String getLearnerName() {
    return learnerName;
  }

  public final void setLearnerName(String name) {
    learnerName = name;
  }

  public final String getLearnerExecutable() {
    return learnerExecutable;
  }

  public final void setLearnerExecutable(String name) {
    learnerExecutable = name;
  }

  public final String getLearnerParams() {
    return learnerParams;
  }

  public final void setLearnerParams(String name) {
    learnerParams = name;
  }

  public final void setUseTau(boolean choice) {
    this.isUseTau = choice;
  }
}
