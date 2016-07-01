/*
 *  MultiClassLearning.java
 *
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: MultiClassLearning.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners;

import gate.learning.ConstantParameters;
import gate.learning.DocFeatureVectors.LongCompactor;
import gate.learning.LabelsOfFV;
import gate.learning.LogService;
import gate.learning.SparseFeatureVector;
import gate.util.BomStrippingInputStreamReader;
import gate.util.GateException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;

/**
 * Learning and application by converting the multi-class problem into several
 * binary class problems.
 */
public class MultiClassLearning {
  /** The training data -- FVs in doc format. */
  public DataForLearning dataFVinDoc;
  /** Use the data file to save the memory or not. */
  //boolean isUsingDataFile = false;
  /** Labels for training instances */
  ThreadLocal<short[]> labelsTraining;
  /** Feature vectors for training instances. */
  ThreadLocal<SparseFeatureVector[]> fvsTraining;
  /** Labels for training instances - non thread. */
  short[] labelsTrainingNT;
  /** Feature vectors for training instances - non thread. */
  SparseFeatureVector[] fvsTrainingNT;
  /** Number of classes for learning */
  public int numClasses;
  /** The name of class and the number of instances in training document */
  public Map<Integer,Integer> class2NumberInstances;
  /**
   * Use the one against all others, or use the one against another. 1 for one
   * against all others, 2 for one against another.
   */
  short multi2BinaryMode = 1;
  /**
   * The number of instances in the training data without label (or with label
   * null).
   */
  public int numNull = 0;
  /**
   * The executor used to run the (possibly concurrent) binary learning and
   * classification tasks.
   */
  ExecutorService executor = new InThreadExecutorService();

  /** Constructor */
  public MultiClassLearning() {
    //isUsingDataFile = false;
  }

  /** Constructor with conversion mode. */
  public MultiClassLearning(short mode) {
    //isUsingDataFile = false;
    multi2BinaryMode = mode;
  }

  public void setExecutor(ExecutorService executor) {
    this.executor = executor;
  }

  /** Get the training data -- feature vectors and labels. */
  public void getDataFromFile(int numDocs, File trainingDataFile, boolean isUsingFile, File tempFVDataFile) {
    dataFVinDoc = new DataForLearning(numDocs);
    //Open the temp file for writing the fv data
    dataFVinDoc.readingFVsFromFile(trainingDataFile, isUsingFile, tempFVDataFile);
    // First, get the unique labels from the trainign data
    class2NumberInstances = new HashMap<Integer,Integer>();
    numNull = obtainUniqueLabels(dataFVinDoc, class2NumberInstances);
    numClasses = class2NumberInstances.size();
    return;
  }

  /** Reset the labels for learning for training data filtering. */
  public int resetClassInData() {
    // Reset the data's class as 1 or -1
    int numNeg = 0;
    for(int i = 0; i < dataFVinDoc.labelsFVDoc.length; ++i) {
      // LabelsOfFeatureVectorDoc labelsDoc = dataFVinDoc.labelsFVDoc[i];
      for(int j = 0; j < dataFVinDoc.labelsFVDoc[i].multiLabels.length; ++j) {
        if(dataFVinDoc.labelsFVDoc[i].multiLabels[j].num > 0) { // if it has
          // label
          LabelsOfFV simpLabels = new LabelsOfFV(1);
          simpLabels.labels = new int[1];
          simpLabels.labels[0] = 1;
          dataFVinDoc.labelsFVDoc[i].multiLabels[j] = simpLabels;
        } else ++numNeg;
      }
    }
    // Reset the label collection
    class2NumberInstances = new HashMap<Integer,Integer>();
    numNull = obtainUniqueLabels(dataFVinDoc, class2NumberInstances);
    numClasses = class2NumberInstances.size();
    return numNeg;
  }

  /** Learn the models and write them into a set of files */
  public void training(final SupervisedLearner learner, File modelFile) {
    final int totalNumFeatures = dataFVinDoc.getTotalNumFeatures();
    Set<Integer> classesName = class2NumberInstances.keySet();
    final List<Integer> array1 = new ArrayList<Integer>(classesName);
    LongCompactor c = new LongCompactor();
    Collections.sort(array1, c);
    if(LogService.minVerbosityLevel > 1)
      System.out.println("total Number of classes for learning is "
        + array1.size());
    LogService.logMessage("total Number of classes for learning is "
      + array1.size(), 1);
    // Open the mode file for writing the model into it
    try {
      if(modelFile.exists()) {
        deleteRecursively(modelFile);
      }
      
//      if(modelFile.exists() && !modelFile.isDirectory()) {
//        if(!modelFile.delete()) { throw new IOException(
//          "Existing single-file model " + modelFile + " could not be deleted."); }
//      }
      if(!modelFile.exists()) {
        if(!modelFile.mkdirs()) { throw new IOException(
          "Couldn't create directory for model files"); }
      }
      // create a temporary directory for the new learned models
      File tmpDirFile = new File(modelFile, "tmp");
      if(tmpDirFile.exists()) {
        deleteRecursively(tmpDirFile);
      }
      if(!tmpDirFile.mkdir()) { throw new IOException(
        "Couldn't create temporary directory for training"); }
      File metaDataFile = new File(tmpDirFile,
        ConstantParameters.FILENAMEOFModelMetaData);
      BufferedWriter metaDataBuff = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(metaDataFile), "UTF-8"));
      // convert the multi-class to binary class -- labels conversion
      // can't share these arrays between concurrent threads, so must use
      // ThreadLocal
      labelsTraining = new ThreadLocal<short[]>() {
        protected short[] initialValue() {
          return new short[dataFVinDoc.numTraining];
        }
      };
      fvsTraining = new ThreadLocal<SparseFeatureVector[]>() {
        protected SparseFeatureVector[] initialValue() {
          return new SparseFeatureVector[dataFVinDoc.numTraining];
        }
      };
      List<Callable<String>> tasks = new ArrayList<Callable<String>>();
      int classIndex = 1;
      switch(multi2BinaryMode){
        case 1: // if using the one vs all others appoach
          // Write some meta information into the model as a header
          LogService.logMessage(
            "One against others for multi to binary class conversion.", 1);
          writeTrainingMetaData(metaDataBuff, numClasses, numNull, dataFVinDoc
            .getNumTrainingDocs(), dataFVinDoc.getTotalNumFeatures(), modelFile
            .getAbsolutePath(), learner);
          metaDataBuff.close();
          for(int iCounter = 0; iCounter < array1.size(); ++iCounter) {
            final int i = iCounter;
            final File thisClassModelFile = new File(tmpDirFile, String.format(
              ConstantParameters.FILENAMEOFPerClassModel, Integer
                .valueOf(classIndex++)));
            tasks.add(new Callable<String>() {
              public String call() throws Exception {
                short[] myLabelsTraining = labelsTraining.get();
                SparseFeatureVector[] myFvsTraining = fvsTraining.get();
                BufferedWriter modelBuff = new BufferedWriter(
                  new OutputStreamWriter(new FileOutputStream(
                    thisClassModelFile), "UTF-8"));
                Multi2BinaryClass.oneVsOthers(dataFVinDoc, array1.get(i)
                  .toString(), myLabelsTraining, myFvsTraining);
                int numTraining = myLabelsTraining.length;
                int numP = 0;
                for(int i1 = 0; i1 < numTraining; ++i1)
                  if(myLabelsTraining[i1] > 0) ++numP;
                modelBuff.append("Class=" + array1.get(i).toString()
                  + " numTraining=" + numTraining + " numPos=" + numP + "\n");
                long time1 = new Date().getTime();
                learner.training(modelBuff, myFvsTraining, totalNumFeatures,
                  myLabelsTraining, numTraining);
                long time2 = new Date().getTime();
                time2 -= time1;
                modelBuff.close();
                LogService.logMessage("Training time for class "
                  + array1.get(i).toString() + ": " + time2 + "ms", 1);
                return null;
              }
            });
          }
          break;
        case 2: // if using the one vs another appoach
          // new numClasses
          int numClasses0;
          if(numNull > 0)
            numClasses0 = (numClasses + 1) * numClasses / 2;
          else numClasses0 = (numClasses - 1) * numClasses / 2;
          LogService.logMessage(
            "One against another for multi to binary class conversion.\n"
              + "So actually we have " + numClasses0 + " binary classes.", 1);
          writeTrainingMetaData(metaDataBuff, numClasses0, numNull, dataFVinDoc
            .getNumTrainingDocs(), dataFVinDoc.getTotalNumFeatures(), modelFile
            .getAbsolutePath(), learner);
          metaDataBuff.close();
          // first for null vs label
          if(numNull > 0) {
            for(int jCounter = 0; jCounter < array1.size(); ++jCounter) {
              final int j = jCounter;
              final File thisClassModelFile = new File(tmpDirFile, String
                .format(ConstantParameters.FILENAMEOFPerClassModel, Integer
                  .valueOf(classIndex++)));
              tasks.add(new Callable<String>() {
                public String call() throws Exception {
                  short[] myLabelsTraining = labelsTraining.get();
                  SparseFeatureVector[] myFvsTraining = fvsTraining.get();
                  BufferedWriter modelBuff = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(
                      thisClassModelFile), "UTF-8"));
                  int numTraining;
                  numTraining = Multi2BinaryClass.oneVsNull(dataFVinDoc, array1
                    .get(j).toString(), myLabelsTraining, myFvsTraining);
                  int numP = 0;
                  for(int i1 = 0; i1 < numTraining; ++i1) {
                    if(myLabelsTraining[i1] > 0) ++numP;
                  }
                  modelBuff.append("Class1=_NULL" + " Class2="
                    + array1.get(j).toString() + " numTraining=" + numTraining
                    + " numPos=" + numP + "\n");
                  long time1 = new Date().getTime();
                  learner.training(modelBuff, myFvsTraining, totalNumFeatures,
                    myLabelsTraining, numTraining);
                  long time2 = new Date().getTime();
                  time2 -= time1;
                  modelBuff.close();
                  LogService.logMessage("Training time for class null against "
                    + array1.get(j).toString() + ": " + time2 + "ms", 1);
                  return null;
                }
              });
            }
          }
          // then for one vs. another
          for(int iCounter = 0; iCounter < array1.size(); ++iCounter) {
            final int i = iCounter;
            for(int jCounter = i + 1; jCounter < array1.size(); ++jCounter) {
              final int j = jCounter;
              final File thisClassModelFile = new File(tmpDirFile, String
                .format(ConstantParameters.FILENAMEOFPerClassModel, Integer
                  .valueOf(classIndex++)));
              tasks.add(new Callable<String>() {
                public String call() throws Exception {
                  short[] myLabelsTraining = labelsTraining.get();
                  SparseFeatureVector[] myFvsTraining = fvsTraining.get();
                  BufferedWriter modelBuff = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(
                      thisClassModelFile), "UTF-8"));
                  int numTraining;
                  numTraining = Multi2BinaryClass.oneVsAnother(dataFVinDoc,
                    array1.get(i).toString(), array1.get(j).toString(),
                    myLabelsTraining, myFvsTraining);
                  int numP = 0;
                  for(int i1 = 0; i1 < numTraining; ++i1) {
                    if(myLabelsTraining[i1] > 0) ++numP;
                  }
                  modelBuff.append("Class1=" + array1.get(i).toString()
                    + " Class2=" + array1.get(j).toString() + " numTraining="
                    + numTraining + " numPos=" + numP + "\n");
                  long time1 = new Date().getTime();
                  learner.training(modelBuff, myFvsTraining, totalNumFeatures,
                    myLabelsTraining, numTraining);
                  long time2 = new Date().getTime();
                  time2 -= time1;
                  modelBuff.close();
                  LogService.logMessage("Training time for class "
                    + array1.get(i).toString() + " against "
                    + array1.get(j).toString() + ": " + time2 + "ms", 1);
                  return null;
                }
              });
            }
          }
          break;
        default:
          System.out.println("Incorrect multi2BinaryMode value="
            + multi2BinaryMode);
          LogService.logMessage("Incorrect multi2BinaryMode value="
            + multi2BinaryMode, 0);
      }
      // actually run the tasks, print any exception traces that result
      LogService.logMessage("Running tasks using executor " + executor, 1);
      List<Future<String>> futures = executor.invokeAll(tasks);
      boolean success = true;
      for(Future<String> f : futures) {
        try {
          String message = f.get();
          if(message != null) {
            LogService.logMessage(message, 1);
          }
        } catch(java.util.concurrent.ExecutionException e) {
          success = false;
          e.printStackTrace();
        }
      }
      if(success) {
        // replace the old model with the new one
        moveAllFiles(tmpDirFile, modelFile);
        // delete any classNNN.model files beyond the last one we have learned
        // on
        // this run
        for(File orphanedModelFile = new File(modelFile, String.format(
          ConstantParameters.FILENAMEOFPerClassModel, Integer
            .valueOf(classIndex))); orphanedModelFile.exists(); classIndex++) {
          orphanedModelFile.delete();
        }
      } else {
        LogService.logMessage(
          "Error during training, old model not overwritten", 1);
      }
      // delete the temporary directory
      deleteRecursively(tmpDirFile);
    } catch(IOException e) {
      e.printStackTrace();
    } catch(InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Delete a file or directory. If the argument is a directory, delete its
   * contents first, then remove the directory itself.
   */
  private void deleteRecursively(File fileOrDir) throws IOException {
    if(fileOrDir.isDirectory()) {
      for(File f : fileOrDir.listFiles()) {
        deleteRecursively(f);
      }
    }
    if(!fileOrDir.delete()) { throw new IOException("Couldn't delete "
      + (fileOrDir.isDirectory() ? "directory " : "file ") + fileOrDir); }
  }

  /**
   * Move all the files from one directory into another.
   *
   * @param src
   *          the directory whose contents are to be moved
   * @param dest
   *          the directory into which the files should go
   */
  private void moveAllFiles(File src, File dest) throws IOException {
    for(String fileName : src.list()) {
      File srcFile = new File(src, fileName);
      File targetFile = new File(dest, fileName);
      if(targetFile.exists() && !targetFile.delete()) { throw new IOException(
        "Couldn't delete file " + targetFile); }
      if(!srcFile.renameTo(targetFile)) { throw new IOException(
        "Couldn't move " + srcFile + " to directory " + dest); }
    }
  }

  /** Apply the model to the data. */
  public void apply(final SupervisedLearner learner, File modelFile) {
    // Open the mode file and read the model
    try {
      if(modelFile.exists() && !modelFile.isDirectory()) {
        // see whether we're trying to apply an old-style model
        // stored all in one file
        BufferedReader buff = new BomStrippingInputStreamReader(
                new FileInputStream(modelFile), "UTF-8");
        String firstLine = buff.readLine();
        buff.close();
        if(firstLine != null && firstLine.endsWith("#numTrainingDocs")) {
          // this is an old-style model, so try and transparently upgrade it to
          // the new format
          upgradeSingleFileModelToDirectory(modelFile);
        } else {
          throw new IOException("Unrecognised model file format for file "
            + modelFile);
        }
      }
      if(!modelFile.exists()) { throw new IllegalStateException(
        "Model directory " + modelFile + " does not exist"); }
      File metaDataFile = new File(modelFile,
        ConstantParameters.FILENAMEOFModelMetaData);
      BufferedReader metaDataBuff = new BomStrippingInputStreamReader(
        new FileInputStream(metaDataFile), "UTF-8");
      // Read the training meta information from the model file's header
      // include the total number of features and number of tags (numClasses)
      int totalNumFeatures;
      String learnerNameFromModel = learner.getLearnerName();
      // note that reading the training meta data also read the number of class
      // in the model, e.g. changing the numClasses.
      totalNumFeatures = ReadTrainingMetaData(metaDataBuff,
        learnerNameFromModel);
      if(LogService.minVerbosityLevel > 1)
        System.out.println(" *** numClasses=" + numClasses + " totalfeatures="
          + totalNumFeatures);
      metaDataBuff.close();
      // compare with the meta data of test data
      if(totalNumFeatures < dataFVinDoc.getTotalNumFeatures())
        totalNumFeatures = dataFVinDoc.getTotalNumFeatures();
      final int finalTotalNumFeatures = totalNumFeatures;
      // Apply the model to test feature vectors
      long time1 = new Date().getTime();
      int classIndex = 1;
      List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
      List<Future<Boolean>> futures = null;
      switch(multi2BinaryMode){
        case 1:
          LogService.logMessage(
            "One against others for multi to binary class conversion.\n"
              + "Number of classes in model: " + numClasses, 1);
          // Use the tau modification in all cases
          learner.isUseTauALLCases = true;
          for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i)
            for(int j = 0; j < dataFVinDoc.trainingFVinDoc[i].getNumInstances(); ++j) {
              dataFVinDoc.labelsFVDoc[i].multiLabels[j] = new LabelsOfFV(
                numClasses);
              dataFVinDoc.labelsFVDoc[i].multiLabels[j].probs = new float[numClasses];
            }
          // for each class
          for(int iClassCounter = 0; iClassCounter < numClasses; ++iClassCounter) {
            final int iClass = iClassCounter;
            final File thisClassModelFile = new File(modelFile, String.format(
              ConstantParameters.FILENAMEOFPerClassModel, Integer
                .valueOf(classIndex++)));
            tasks.add(new Callable<Boolean>() {
              public Boolean call() throws Exception {
                BufferedReader modelBuff = new BomStrippingInputStreamReader(
                        new FileInputStream(thisClassModelFile), "UTF-8");
                learner.applying(modelBuff, dataFVinDoc, finalTotalNumFeatures,
                  iClass);
                modelBuff.close();
                return Boolean.TRUE;
              }
            });
          }
          // actually run the tasks, print any exception traces that result
          futures = executor.invokeAll(tasks);
          for(Future<Boolean> f : futures) {
            try {
              f.get();
            } catch(java.util.concurrent.ExecutionException e) {
              e.printStackTrace();
            }
          }
          if(LogService.minVerbosityLevel > 1)
            System.out.println("**** One against all others, numNull="
              + numNull);
          break;
        case 2:
          LogService.logMessage(
            "One against another for multi to binary class conversion.", 1);
          // not use the tau modification in all cases
          learner.isUseTauALLCases = false;
          // set the multi class number and allocate the memory
          for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i)
            for(int j = 0; j < dataFVinDoc.trainingFVinDoc[i].getNumInstances(); ++j) {
              dataFVinDoc.labelsFVDoc[i].multiLabels[j] = new LabelsOfFV(
                numClasses);
              dataFVinDoc.labelsFVDoc[i].multiLabels[j].probs = new float[numClasses];
            }
          // for each class
          for(int iClassCounter = 0; iClassCounter < numClasses; ++iClassCounter) {
            final int iClass = iClassCounter;
            final File thisClassModelFile = new File(modelFile, String.format(
              ConstantParameters.FILENAMEOFPerClassModel, Integer
                .valueOf(classIndex++)));
            tasks.add(new Callable<Boolean>() {
              public Boolean call() throws Exception {
                BufferedReader modelBuff = new BomStrippingInputStreamReader(
                        new FileInputStream(thisClassModelFile), "UTF-8");
                learner.applying(modelBuff, dataFVinDoc, finalTotalNumFeatures,
                  iClass);
                modelBuff.close();
                return Boolean.TRUE;
              }
            });
          }
          // actually run the tasks, print any exception traces that result
          futures = executor.invokeAll(tasks);
          for(Future<Boolean> f : futures) {
            try {
              f.get();
            } catch(java.util.concurrent.ExecutionException e) {
              e.printStackTrace();
            }
          }
          PostProcessing postProc = new PostProcessing();
          // Get the number of classes of the problem, since the numClasses
          // refers to the number
          // of classes in the one against another method.
          int numClassesL = numClasses * 2;
          numClassesL = rootQuaEqn(numClassesL);
          if(numNull == 0) numClassesL += 1;
          LogService.logMessage("Number of classes in training data: "
            + numClassesL + "\nActuall number of binary classes in model: "
            + numClasses, 1);
          if(LogService.minVerbosityLevel > 1)
            System.out.println("**** One against another, numNull=" + numNull);
          if(numNull > 0)
            postProc.voteForOneVSAnotherNull(dataFVinDoc, numClassesL);
          else postProc.voteForOneVSAnother(dataFVinDoc, numClassesL);
          // Set the number of classes with the correct value.
          numClasses = numClassesL;
          break;
        default:
          System.out.println("Incorrect multi2BinaryMode value="
            + multi2BinaryMode);
          LogService.logMessage("Incorrect multi2BinaryMode value="
            + multi2BinaryMode, 1);
      }
      long time2 = new Date().getTime();
      time2 -= time1;
      LogService.logMessage("Application time for class: " + time2 + "ms", 1);
    } catch(IOException e) {
      e.printStackTrace();
    } catch(InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Upgrade an old-style single file model to directory format, with the meta
   * data and the individual models in separate files.
   */
  public void upgradeSingleFileModelToDirectory(File modelFile)
    throws IOException {
    // copy the old model file to a backup
    byte[] buf = new byte[8192];
    int bytesRead = 0;
    File backupModelFile = new File(modelFile.getPath() + ".bak");
    FileInputStream oldModelIn = new FileInputStream(modelFile);
    FileOutputStream backupModelOut = new FileOutputStream(backupModelFile);
    while((bytesRead = oldModelIn.read(buf)) >= 0) {
      backupModelOut.write(buf, 0, bytesRead);
    }
    backupModelOut.close();
    oldModelIn.close();
    buf = null;
    // delete the old model file and create a directory in its place
    modelFile.delete();
    modelFile.mkdir();
    // open the backup model file and copy its sections into new files
    BufferedReader oldModelsBuff = null;
    
    try {
      oldModelsBuff =
        new BomStrippingInputStreamReader(new FileInputStream(backupModelFile),
          "UTF-8");
      // first 8 lines are the meta data
      File metaDataFile =
        new File(modelFile, ConstantParameters.FILENAMEOFModelMetaData);
      BufferedWriter metaDataBuff =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
          metaDataFile), "UTF-8"));
      for(int i = 0; i < 8; i++) {
        metaDataBuff.write(oldModelsBuff.readLine());
        metaDataBuff.write('\n');
      }
      metaDataBuff.close();
      int classIndex = 1;
      BufferedWriter modelWriter = null;
      String line = null;
      while((line = oldModelsBuff.readLine()) != null) {
        if(line.startsWith("Class=") && line.contains("numTraining=") &&
          line.contains("numPos=")) {
          // found the start of a new model, so close the previous file and
          // start
          // the next one
          if(modelWriter != null) {
            modelWriter.close();
          }
          File nextModel =
            new File(modelFile, String.format(
              ConstantParameters.FILENAMEOFPerClassModel,
              Integer.valueOf(classIndex++)));
          modelWriter =
            new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
              nextModel), "UTF-8"));
        }
        modelWriter.write(line);
        modelWriter.write('\n');
      }
      if(modelWriter != null) {
        modelWriter.close();
      }
    } finally {
      IOUtils.closeQuietly(oldModelsBuff);
    }
  }

  /**
   * Get the number of classes in the problem from the number of classes in the
   * one vs. another method, by solving a quadratic equation.
   */
  private int rootQuaEqn(int numClassesL) {
    // The positive root of quadratic equation x^2+x-numClassesL=0.
    return (int)((-1 + Math.sqrt(1.0 + numClassesL * 4)) / 2.0);
  }

  /** Writting the meta information about the learning into the model file. */
  public void writeTrainingMetaData(BufferedWriter modelsBuff, int numClasses,
    int numNull, int numTrainingDocs, long totalFeatures, String modelFile,
    SupervisedLearner learner) throws IOException {
    modelsBuff.append(numTrainingDocs + " #numTrainingDocs\n");
    modelsBuff.append(numClasses + " #numClasses\n");
    modelsBuff.append(numNull + " #numNullLabelInstances\n");
    long actualNum = totalFeatures - 5; // because added 5 in DataForLearning
                                        // class
    modelsBuff.append(actualNum + " #totalFeatures\n");
    modelsBuff.append(modelFile + " #modelFile\n");
    modelsBuff.append(learner.getLearnerName() + " #learnerName\n");
    modelsBuff.append(learner.getLearnerExecutable() + " #learnerExecutable\n");
    modelsBuff.append(learner.getLearnerParams() + " #learnerParams\n");
    return;
  }

  /** Read the meta data from the header of the file. */
  public int ReadTrainingMetaData(BufferedReader modelsBuff,
    String learnerNameFromModel) throws IOException {
    int totalFeatures;
    String line;
    modelsBuff.readLine(); // read the traing documents
    line = modelsBuff.readLine(); // read the number of classes
    numClasses = new Integer(line.substring(0, line.indexOf(" ")));
    line = modelsBuff.readLine(); // read the number of classes
    numNull = new Integer(line.substring(0, line.indexOf(" ")));
    line = modelsBuff.readLine(); // read the total number of features
    totalFeatures = new Integer(line.substring(0, line.indexOf(" ")));
    totalFeatures += 5;
    modelsBuff.readLine(); // read the model file name
    line = modelsBuff.readLine(); // read the learner's name
    learnerNameFromModel = line.substring(0, line.indexOf(" "));
    modelsBuff.readLine(); // read the learnerExecutable string
    modelsBuff.readLine(); // read the learnerParams string
    return totalFeatures;
  }

  /** Obtain the unqilabels from the training data. */
  int obtainUniqueLabels(DataForLearning dataFVinDoc,
    Map<Integer,Integer> class2NumberInstances) {
    int numN = 0;
    for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i)
      for(int j = 0; j < dataFVinDoc.labelsFVDoc[i].multiLabels.length; ++j) {
        // int label = dataFVinDoc.labelsFVDoc[i].labels[j];
        LabelsOfFV multiLabel = dataFVinDoc.labelsFVDoc[i].multiLabels[j];
        if(multiLabel.num == 0) ++numN;
        for(int j1 = 0; j1 < multiLabel.num; ++j1) {
          if(multiLabel.labels[j1] > 0) {
            if(class2NumberInstances.containsKey(multiLabel.labels[j1]))
              class2NumberInstances.put(multiLabel.labels[j1],
                class2NumberInstances.get(multiLabel.labels[j1])
                   + 1);
            else class2NumberInstances.put(multiLabel.labels[j1], 1);
          }
        }
      }
    return numN;
  }

  /** Learn the models and write them into a file -- not use thread*/
  public void trainingNoThread(SupervisedLearner learner, File modelFile, boolean isUsingTempDataFile, File tempFVDataFile) {
    final int totalNumFeatures = dataFVinDoc.getTotalNumFeatures();
    Set<Integer> classesName = class2NumberInstances.keySet();
    final List<Integer> array1 = new ArrayList<Integer>(classesName);
    LongCompactor c = new LongCompactor();
    Collections.sort(array1, c);
    if(LogService.minVerbosityLevel>1)
      System.out.println("total Number of classes for learning is "
        + array1.size());
    LogService.logMessage("total Number of classes for learning is "
      + array1.size(), 1);
    //Open the mode file for writing the model into it
    try {
      if(modelFile.exists() && !modelFile.isDirectory()) {
        if(!modelFile.delete()) { throw new IOException(
          "Existing single-file model " + modelFile + " could not be deleted."); }
      }
      if(!modelFile.exists()) {
        if(!modelFile.mkdirs()) { throw new IOException(
          "Couldn't create directory for model files"); }
      }
      // create a temporary directory for the new learned models
      File tmpDirFile = new File(modelFile, "tmp");
      if(tmpDirFile.exists()) {
        deleteRecursively(tmpDirFile);
      }
      if(!tmpDirFile.mkdir()) { throw new IOException(
        "Couldn't create temporary directory for training"); }

      File metaDataFile = new File(tmpDirFile,
        ConstantParameters.FILENAMEOFModelMetaData);
      BufferedWriter metaDataBuff = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(metaDataFile), "UTF-8"));
      int classIndex = 1; //class index for model's file name

      // Open the mode file for writing the model into it
      // convert the multi-class to binary class -- labels conversion
      labelsTrainingNT = new short[dataFVinDoc.numTraining];
      fvsTrainingNT = new SparseFeatureVector[dataFVinDoc.numTraining];
      switch(multi2BinaryMode){
        case 1: // if using the one vs all others appoach
          // Write some meta information into the model as a header
          LogService.logMessage(
            "One against others for multi to binary class conversion.", 1);
          writeTrainingMetaData(metaDataBuff, numClasses, numNull, dataFVinDoc
            .getNumTrainingDocs(), dataFVinDoc.getTotalNumFeatures(), modelFile
            .getAbsolutePath(), learner);
          metaDataBuff.close();

          for(int iCounter = 0; iCounter < array1.size(); ++iCounter) {
            final int i = iCounter;
            final File thisClassModelFile = new File(tmpDirFile, String.format(
              ConstantParameters.FILENAMEOFPerClassModel, Integer
                .valueOf(classIndex++)));
            BufferedWriter modelBuff = new BufferedWriter(
              new OutputStreamWriter(new FileOutputStream(
                thisClassModelFile), "UTF-8"));
            Multi2BinaryClass.oneVsOthers(dataFVinDoc,
              array1.get(i).toString(), labelsTrainingNT, fvsTrainingNT);
            int numTraining = labelsTrainingNT.length;
            int numP = 0;
            for(int i1 = 0; i1 < numTraining; ++i1)
              if(labelsTrainingNT[i1] > 0) ++numP;
            modelBuff.append("Class=" + array1.get(i).toString()
              + " numTraining=" + numTraining + " numPos=" + numP + "\n");
            long time1 = new Date().getTime();
            if(isUsingTempDataFile) {//using the data file
              BufferedReader fvTempRd = new BomStrippingInputStreamReader(
                new FileInputStream(tempFVDataFile), "UTF-8");
              learner.trainingWithDataFile(modelBuff, fvTempRd, totalNumFeatures,
                labelsTrainingNT, numTraining);
              fvTempRd.close();
            } else
              learner.training(modelBuff, fvsTrainingNT, totalNumFeatures,
                labelsTrainingNT, numTraining);
            modelBuff.close();
            long time2 = new Date().getTime();
            time2 -= time1;
            LogService.logMessage("Training time for class "
              + array1.get(i).toString() + ": " + time2 + "ms", 1);
          }
          break;
        case 2: // if using the one vs another appoach
          // new numClasses
          int numClasses0;
          if(numNull > 0)
            numClasses0 = (numClasses + 1) * numClasses / 2;
          else numClasses0 = (numClasses - 1) * numClasses / 2;
          LogService.logMessage(
            "One against another for multi to binary class conversion.\n"
              + "So actually we have " + numClasses0 + " binary classes.", 1);
          writeTrainingMetaData(metaDataBuff, numClasses0, numNull, dataFVinDoc
            .getNumTrainingDocs(), dataFVinDoc.getTotalNumFeatures(), modelFile
            .getAbsolutePath(), learner);
          metaDataBuff.close();
          // first for null vs label
          if(numNull > 0) {
            for(int jCounter = 0; jCounter < array1.size(); ++jCounter) {
              final int j = jCounter;
              final File thisClassModelFile = new File(tmpDirFile, String
                .format(ConstantParameters.FILENAMEOFPerClassModel, Integer
                  .valueOf(classIndex++)));
              BufferedWriter modelBuff = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(
                  thisClassModelFile), "UTF-8"));
              int numTraining;
              numTraining = Multi2BinaryClass.oneVsNull(dataFVinDoc, array1
                .get(j).toString(), labelsTrainingNT, fvsTrainingNT);
              int numP = 0;
              for(int i1 = 0; i1 < numTraining; ++i1) {
                if(labelsTrainingNT[i1] > 0) ++numP;
              }
              modelBuff.append("Class1=_NULL" + " Class2="
                + array1.get(j).toString() + " numTraining=" + numTraining
                + " numPos=" + numP + "\n");
              long time1 = new Date().getTime();
              if(isUsingTempDataFile) {//using the data file
                BufferedReader fvTempRd = new BomStrippingInputStreamReader(
                  new FileInputStream(tempFVDataFile), "UTF-8");
                learner.trainingWithDataFile(modelBuff, fvTempRd, totalNumFeatures,
                  labelsTrainingNT, numTraining);
                fvTempRd.close();
              } else
                learner.training(modelBuff, fvsTrainingNT, totalNumFeatures,
                  labelsTrainingNT, numTraining);
              modelBuff.close();
              long time2 = new Date().getTime();
              time2 -= time1;
              LogService.logMessage("Training time for class null against "
                + array1.get(j).toString() + ": " + time2 + "ms", 1);
            }
          }
          // then for one vs. another
          for(int iCounter = 0; iCounter < array1.size(); ++iCounter) {
            final int i = iCounter;
            for(int jCounter = i + 1; jCounter < array1.size(); ++jCounter) {
              final int j = jCounter;
              final File thisClassModelFile = new File(tmpDirFile, String
                .format(ConstantParameters.FILENAMEOFPerClassModel, Integer
                  .valueOf(classIndex++)));
              BufferedWriter modelBuff = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(
                  thisClassModelFile), "UTF-8"));
              int numTraining;
              numTraining = Multi2BinaryClass.oneVsAnother(dataFVinDoc, array1
                .get(i).toString(), array1.get(j).toString(), labelsTrainingNT,
                fvsTrainingNT);
              int numP = 0;
              for(int i1 = 0; i1 < numTraining; ++i1) {
                if(labelsTrainingNT[i1] > 0) ++numP;
              }
              modelBuff.append("Class1="+ array1.get(i).toString()+ " Class2="
                + array1.get(j).toString() + " numTraining=" + numTraining
                + " numPos=" + numP + "\n");
              long time1 = new Date().getTime();
              if(isUsingTempDataFile) { //using the data file
                BufferedReader fvTempRd = new BomStrippingInputStreamReader(
                  new FileInputStream(tempFVDataFile), "UTF-8");
                learner.trainingWithDataFile(modelBuff, fvTempRd, totalNumFeatures,
                  labelsTrainingNT, numTraining);
                fvTempRd.close();
              } else
                learner.training(modelBuff, fvsTrainingNT, totalNumFeatures,
                  labelsTrainingNT, numTraining);
              modelBuff.close();
              long time2 = new Date().getTime();
              time2 -= time1;
              LogService.logMessage("Training time for class "
                + array1.get(i).toString() + " against "
                + array1.get(j).toString() + ": " + time2 + "ms", 1);
              // }
              }
          }
          break;
        default:
          System.out.println("Incorrect multi2BinaryMode value="
            + multi2BinaryMode);
          LogService.logMessage("Incorrect multi2BinaryMode value="
            + multi2BinaryMode, 0);
      }
      //    replace the old model with the new one
      moveAllFiles(tmpDirFile, modelFile);
      deleteRecursively(tmpDirFile);
      //delete the temp data file
      tempFVDataFile.delete();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /** Apply the model to the data - not use thread. */
  public void applyNoThread(SupervisedLearner learner, File modelFile) {
//  Open the mode file and read the model
    try {
      if(modelFile.exists() && !modelFile.isDirectory()) {
        // see whether we're trying to apply an old-style model
        // stored all in one file
        BufferedReader buff = new BomStrippingInputStreamReader(
                new FileInputStream(modelFile), "UTF-8");
        String firstLine = buff.readLine();
        buff.close();
        if(firstLine != null && firstLine.endsWith("#numTrainingDocs")) {
          // this is an old-style model, so try and transparently upgrade it to
          // the new format
          upgradeSingleFileModelToDirectory(modelFile);
        } else {
          throw new IOException("Unrecognised model file format for file "
            + modelFile);
        }
      }
      if(!modelFile.exists()) { throw new IllegalStateException(
        "Model directory " + modelFile + " does not exist"); }
      File metaDataFile = new File(modelFile,
        ConstantParameters.FILENAMEOFModelMetaData);
      BufferedReader metaDataBuff = new BomStrippingInputStreamReader(
        new FileInputStream(metaDataFile), "UTF-8");
       //    Read the training meta information from the model file's header
      // include the total number of features and number of tags (numClasses)
      int totalNumFeatures;
      String learnerNameFromModel = learner.getLearnerName();
      // note that reading the training meta data also read the number of class
      // in the model, e.g. changing the numClasses.
      totalNumFeatures = ReadTrainingMetaData(metaDataBuff,
        learnerNameFromModel);
      if(LogService.minVerbosityLevel > 1)
        System.out.println(" *** numClasses=" + numClasses + " totalfeatures="
          + totalNumFeatures);
      metaDataBuff.close();
      // compare with the meta data of test data
      if(totalNumFeatures < dataFVinDoc.getTotalNumFeatures())
        totalNumFeatures = dataFVinDoc.getTotalNumFeatures();
      final int finalTotalNumFeatures = totalNumFeatures;
      //    Apply the model to test feature vectors
      long time1 = new Date().getTime();
      int classIndex = 1;
      switch(multi2BinaryMode){
        case 1:
          LogService.logMessage(
            "One against others for multi to binary class conversion.\n"
              + "Number of classes in model: " + numClasses, 1);
          // Use the tau modification in all cases
          learner.isUseTauALLCases = true;
          for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i)
            for(int j = 0; j < dataFVinDoc.trainingFVinDoc[i].getNumInstances(); ++j) {
              dataFVinDoc.labelsFVDoc[i].multiLabels[j] = new LabelsOfFV(
                numClasses);
              dataFVinDoc.labelsFVDoc[i].multiLabels[j].probs = new float[numClasses];
            }
          // for each class
          for(int iClassCounter = 0; iClassCounter < numClasses; ++iClassCounter) {
            final int iClass = iClassCounter;
            final File thisClassModelFile = new File(modelFile, String.format(
              ConstantParameters.FILENAMEOFPerClassModel, Integer
                .valueOf(classIndex++)));
            BufferedReader modelBuff = new BomStrippingInputStreamReader(
                    new FileInputStream(thisClassModelFile), "UTF-8");
            learner.applying(modelBuff, dataFVinDoc, finalTotalNumFeatures,
              iClass);
            modelBuff.close();
          }
          if(LogService.minVerbosityLevel > 1)
            System.out.println("**** One against all others, numNull=" + numNull);
          break;
        case 2:
          LogService.logMessage(
            "One against another for multi to binary class conversion.", 1);
          // not use the tau modification in all cases
          learner.isUseTauALLCases = false;
          //set the multi class number and allocate the memory
          for(int i = 0; i < dataFVinDoc.getNumTrainingDocs(); ++i)
            for(int j = 0; j < dataFVinDoc.trainingFVinDoc[i].getNumInstances(); ++j) {
              dataFVinDoc.labelsFVDoc[i].multiLabels[j] = new LabelsOfFV(
                numClasses);
              dataFVinDoc.labelsFVDoc[i].multiLabels[j].probs = new float[numClasses];
            }
          // for each class
          for(int iClassCounter = 0; iClassCounter < numClasses; ++iClassCounter) {
            final int iClass = iClassCounter;
            final File thisClassModelFile = new File(modelFile, String.format(
              ConstantParameters.FILENAMEOFPerClassModel, Integer
                .valueOf(classIndex++)));
            BufferedReader modelBuff = new BomStrippingInputStreamReader(new FileInputStream(thisClassModelFile), "UTF-8");
            learner.applying(modelBuff, dataFVinDoc, finalTotalNumFeatures,
              iClass);
            modelBuff.close();
          }

          PostProcessing postProc = new PostProcessing();
          // Get the number of classes of the problem, since the numClasses
          // refers to the number
          // of classes in the one against another method.
          int numClassesL = numClasses * 2;
          numClassesL = rootQuaEqn(numClassesL);
          if(numNull == 0) numClassesL += 1;
          LogService.logMessage("Number of classes in training data: "
            + numClassesL + "\nActuall number of binary classes in model: "
            + numClasses, 1);
          if(LogService.minVerbosityLevel > 1)
            System.out.println("**** One against another, numNull=" + numNull);
          if(numNull > 0)
            postProc.voteForOneVSAnotherNull(dataFVinDoc, numClassesL);
          else postProc.voteForOneVSAnother(dataFVinDoc, numClassesL);
          // Set the number of classes with the correct value.
          numClasses = numClassesL;
          break;
        default:
          System.out.println("Incorrect multi2BinaryMode value="
            + multi2BinaryMode);
          LogService.logMessage("Incorrect multi2BinaryMode value="
            + multi2BinaryMode, 1);
      }
      long time2 = new Date().getTime();
      time2 -= time1;
      LogService.logMessage("Application time for class: " + time2 + "ms", 1);
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Obtain the learner from the learner's name speficied by the learning
   * configuration file.
   *
   * @throws GateException
   */
  public static SupervisedLearner obtainLearnerFromName(String learnerName,
    String commandLine, String dataFilesName) throws GateException {
    SupervisedLearner learner = null;
    if(learnerName.equalsIgnoreCase("SVMLibSvmJava")) {
      learner = new SvmLibSVM();
      learner.setLearnerName(learnerName);
      learner.setCommandLine(commandLine + " " + dataFilesName);
      learner.getParametersFromCommmand();
    } else if(learnerName.equalsIgnoreCase("SVMExec")) {
      learner = new SvmForExec();
      learner.setLearnerName(learnerName);
      learner.setCommandLine(commandLine);
      learner.getParametersFromCommmand();
    } else if(learnerName.equalsIgnoreCase("PAUM")) {
      learner = new Paum();
      learner.setCommandLine(commandLine);
      learner.getParametersFromCommmand();
    } else if(learnerName.equalsIgnoreCase("PAUMExec")) {
      learner = new PaumForExec();
      learner.setLearnerName(learnerName);
      learner.setCommandLine(commandLine);
      learner.getParametersFromCommmand();
    } else {
      throw new GateException("The learner's name \"" + learnerName
        + "\" is not defined!");
    }
    return learner;
  }
}
