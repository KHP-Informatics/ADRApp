/*
 *  LightWeightLearningApi.java
 *
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: LightWeightLearningApi.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Corpus;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Node;
import gate.learning.learners.ChunkOrEntity;
import gate.learning.learners.MultiClassLearning;
import gate.learning.learners.PostProcessing;
import gate.learning.learners.SupervisedLearner;
import gate.learning.learners.SvmLibSVM;
import gate.learning.learners.weka.WekaLearner;
import gate.learning.learners.weka.WekaLearning;
import gate.util.Benchmark;
import gate.util.Benchmarkable;
import gate.util.BomStrippingInputStreamReader;
import gate.util.GateException;
import gate.util.InvalidOffsetException;
import gate.util.OffsetComparator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * Do all the main learning tasks, such as obtaining the feature vectors from
 * GATE annotations, training and application. Also filtering out some negative
 * examples if want.
 */
public class LightWeightLearningApi extends Object implements Benchmarkable {
  /** This is where the model(s) should be saved */
  private File wd;
  /**
   * The annotationSet containing annotations considered in the DATASET element
   * of configuration file.
   */
  public String inputASName;
  /**
   * The annotationSet for the resulting annotations by application of models.
   */
  public String outputASName;
  /** Object of the NLP feature list. */
  public NLPFeaturesList featuresList;
  /** Object of the label list. */
  public Label2Id labelsAndId;
  /** Position of all features specified in the configuration file. */
  int[] featurePositionTotal;
  /** The left-most position among all features. */
  int maxNegPositionTotal = 0;
  /** The right-most position among all features. */
  int maxPosPositionTotal = 0;
  /** The weight for the Ngram features */
  float ngramWeight = 1.0f;
  /**
   * Map for the chunkLenStats, for post-processing of chunk learning.
   */
  Map<Integer,ChunkLengthStats> chunkLenHash;

  /** Constructor, with working directory setting. */
  public LightWeightLearningApi(File wd) {
    this.wd = wd;
  }

  /**
   * Further initialisation for the main object LearningAPIMain().
   */
  public void furtherInit(File wdResults, LearningEngineSettings engineSettings) {
    // read the NLP feature list
    featuresList = new NLPFeaturesList();
    featuresList.loadFromFile(wdResults,
      ConstantParameters.FILENAMEOFNLPFeatureList, "UTF-8");
    labelsAndId = new Label2Id();
    labelsAndId.loadLabelAndIdFromFile(wdResults,
      ConstantParameters.FILENAMEOFLabelList);
    chunkLenHash = ChunkLengthStats.loadChunkLenStats(wdResults,
      ConstantParameters.FILENAMEOFChunkLenStats);
    // Get the feature position of all features
    // Keep the order of the three types of features as that in
    // NLPFeaturesOfDoc.obtainDocNLPFeatures()
    int num;
    num = engineSettings.datasetDefinition.arrs.featurePosition.length;
    if(engineSettings.datasetDefinition.dataType == DataSetDefinition.RelationData) {
      num += engineSettings.datasetDefinition.arg1.arrs.featurePosition.length
        + engineSettings.datasetDefinition.arg2.arrs.featurePosition.length;
    }
    this.featurePositionTotal = new int[num];
    num = 0;
    if(engineSettings.datasetDefinition.dataType == DataSetDefinition.RelationData) {
      for(int i = 0; i < engineSettings.datasetDefinition.arg1.arrs.featurePosition.length; ++i)
        this.featurePositionTotal[num++] = engineSettings.datasetDefinition.arg1.arrs.featurePosition[i];
      for(int i = 0; i < engineSettings.datasetDefinition.arg2.arrs.featurePosition.length; ++i)
        this.featurePositionTotal[num++] = engineSettings.datasetDefinition.arg2.arrs.featurePosition[i];
    }
    for(int i = 0; i < engineSettings.datasetDefinition.arrs.featurePosition.length; ++i)
      this.featurePositionTotal[num++] = engineSettings.datasetDefinition.arrs.featurePosition[i];
    maxNegPositionTotal = 0;
    if(maxNegPositionTotal < engineSettings.datasetDefinition.arrs.maxNegPosition)
      maxNegPositionTotal = engineSettings.datasetDefinition.arrs.maxNegPosition;
    if(engineSettings.datasetDefinition.dataType == DataSetDefinition.RelationData) {
      if(maxNegPositionTotal < engineSettings.datasetDefinition.arg1.arrs.maxNegPosition)
        maxNegPositionTotal = engineSettings.datasetDefinition.arg1.arrs.maxNegPosition;
      if(maxNegPositionTotal < engineSettings.datasetDefinition.arg2.arrs.maxNegPosition
        + engineSettings.datasetDefinition.arg1.maxTotalPosition + 2)
        maxNegPositionTotal = engineSettings.datasetDefinition.arg2.arrs.maxNegPosition
          + engineSettings.datasetDefinition.arg1.maxTotalPosition + 2;
    }
    // Get the ngram weight from the datasetdefintion.
    ngramWeight = 1.0f;
    if(engineSettings.datasetDefinition.ngrams != null
      && engineSettings.datasetDefinition.ngrams.size() > 0
      && engineSettings.datasetDefinition.ngrams.get(0).weight != 1.0)
      ngramWeight = engineSettings.datasetDefinition.ngrams.get(0).weight;
    if(engineSettings.datasetDefinition.dataType == DataSetDefinition.RelationData) {
      if(engineSettings.datasetDefinition.arg1.ngrams != null
        && engineSettings.datasetDefinition.arg1.ngrams.size() > 0
        && engineSettings.datasetDefinition.arg1.ngrams.get(0).weight != 1.0)
        ngramWeight = engineSettings.datasetDefinition.arg1.ngrams
          .get(0).weight;
      if(engineSettings.datasetDefinition.arg2.ngrams != null
        && engineSettings.datasetDefinition.arg2.ngrams.size() > 0
        && engineSettings.datasetDefinition.arg2.ngrams.get(0).weight != 1.0)
        ngramWeight = engineSettings.datasetDefinition.arg2.ngrams
          .get(0).weight;
    }
  }

  /**
   * Obtain the features and labels and form feature vectors from the GATE
   * annotation of each document.
   */
  public void annotations2NLPFeatures(Document doc, int numDocs,
    BufferedWriter outNLPFeatures, boolean isTraining,
    LearningEngineSettings engineSettings) {
    AnnotationSet annotations = null;
    if(inputASName == null || inputASName.trim().length() == 0) {
      annotations = doc.getAnnotations();
    } else {
      annotations = doc.getAnnotations(inputASName);
    }
    /*
     * if(numDocs == 0) { try { outNLPFeatures = new BufferedWriter(new
     * OutputStreamWriter(new FileOutputStream(new File(wdResults,
     * ConstantParameters.FILENAMEOFNLPFeaturesData)), "UTF-8")); }
     * catch(IOException e) {
     * e.printStackTrace(); } }
     */
    // obtain the NLP features for the document
    String docName = doc.getName().replaceAll(ConstantParameters.ITEMSEPARATOR,
      "_");
    if(docName.contains("_"))
      docName = docName.substring(0, docName.lastIndexOf("_"));
    if(LogService.minVerbosityLevel > 1)
      System.out.println(numDocs + " docname=" + docName + ".");
    NLPFeaturesOfDoc nlpFeaturesDoc = new NLPFeaturesOfDoc(annotations,
      engineSettings.datasetDefinition.getInstanceType(), docName);
    nlpFeaturesDoc.obtainDocNLPFeatures(annotations,
      engineSettings.datasetDefinition);
    // update the NLP features list
    if(isTraining && engineSettings.isNLPFeatListUpdatable) {
      featuresList.addFeaturesFromDoc(nlpFeaturesDoc);
    }
    if(isTraining && engineSettings.isLabelListUpdatable) {
      // update the class name list
      labelsAndId.updateMultiLabelFromDoc(nlpFeaturesDoc.classNames);
    }
    // Only after the label list was updated, update the chunk length
    // list for each label
    if(isTraining && engineSettings.surround)
      ChunkLengthStats.updateChunkLensStats(annotations,
        engineSettings.datasetDefinition, chunkLenHash, labelsAndId);
    nlpFeaturesDoc.writeNLPFeaturesToFile(outNLPFeatures, docName, numDocs,
      featurePositionTotal);
    return;
  }

  /** Normalising the feature vectors. */
  static void normaliseFVs(DocFeatureVectors docFV) {
    for(int i = 0; i < docFV.numInstances; ++i) {
      double sum = 0;
      for(int j = 0; j < docFV.fvs[i].len; ++j)
        sum += docFV.fvs[i].nodes[j].value * docFV.fvs[i].nodes[j].value;
      sum = Math.sqrt(sum);
      for(int j = 0; j < docFV.fvs[i].len; ++j)
        docFV.fvs[i].nodes[j].value /= sum;
    }
  }

  /**
   * Finishing the conversion from annotations to feature vectors by writing
   * back the label and nlp feature list into files, and closing the java
   * writers.
   */
  public void finishFVs(File wdResults, int numDocs, boolean isTraining,
    LearningEngineSettings engineSettings) {
    if(isTraining && engineSettings.isNLPFeatListUpdatable)
      featuresList.writeListIntoFile(wdResults,
        ConstantParameters.FILENAMEOFNLPFeatureList, "UTF-8");
    if(isTraining && engineSettings.isLabelListUpdatable)
      labelsAndId.writeLabelAndIdToFile(wdResults,
        ConstantParameters.FILENAMEOFLabelList);
    if(isTraining & engineSettings.surround)
      ChunkLengthStats.writeChunkLensStatsIntoFile(wdResults,
        ConstantParameters.FILENAMEOFChunkLenStats, chunkLenHash);
  }

  /** transfer the feature list to ngram (language model). */
  public void featureList2LM(File wdResults, int nGram) {
    featuresList.writeToLM(wdResults, ConstantParameters.FILENAMEOFNgramLM,
      nGram);
  }

  /** Convert the NLP features into feature vectors and write them into file. */
  public void nlpfeatures2FVs(File wdResults, BufferedReader inNLPFeatures,
    BufferedWriter outFeatureVectors, int numDocs, boolean isTraining,
    LearningEngineSettings engineSettings) {
    try {
      // BufferedWriter outFeatureVectors = new BufferedWriter(new
      // OutputStreamWriter(new FileOutputStream(
      // new File(wdResults,ConstantParameters.FILENAMEOFFeatureVectorData),
      // true), "UTF-8"));
      // Read the first line out which is about feature names
      inNLPFeatures.readLine();
      for(int i = 0; i < numDocs; ++i) {
        NLPFeaturesOfDoc nlpFeatDoc = new NLPFeaturesOfDoc();
        nlpFeatDoc.readNLPFeaturesFromFile(inNLPFeatures);
        DocFeatureVectors docFV = new DocFeatureVectors();
        docFV.obtainFVsFromNLPFeatures(nlpFeatDoc, featuresList,
          featurePositionTotal, maxNegPositionTotal, featuresList.totalNumDocs,
          ngramWeight, engineSettings.datasetDefinition.valueTypeNgram);
        /*if(engineSettings.datasetDefinition.isSameWinSize) {
          // expand the feature vector to include the context words
          docFV.expandFV(engineSettings.datasetDefinition.windowSizeLeft,
            engineSettings.datasetDefinition.windowSizeRight);
        }*/
        if(isTraining) {
          LabelsOfFeatureVectorDoc labelsDoc = new LabelsOfFeatureVectorDoc();
          labelsDoc.obtainMultiLabelsFromNLPDocSurround(nlpFeatDoc,
            labelsAndId, engineSettings.surround);
          //addDocFVsMultiLabelToFile(i, outFeatureVectors,
            //labelsDoc.multiLabels, docFV);
          docFV.addDocFVsMultiLabelToFile(i, outFeatureVectors,
            labelsDoc.multiLabels);
        } else {
          int[] labels = new int[nlpFeatDoc.numInstances];
          //addDocFVsToFile(i, outFeatureVectors, labels, docFV);
          docFV.addDocFVsToFile(i, outFeatureVectors, labels);
        }
      }
      // outFeatureVectors.flush();
      // outFeatureVectors.close();
    } catch(IOException e) {
      System.out
        .println("Error occured in reading the NLP data from file for converting to FVs"
          + "or writing the FVs data into file!");
    }
  }

  /** Write the FVs of one document into file. */
  /*void addDocFVsToFile(int index, BufferedWriter out, int[] labels,
    DocFeatureVectors docFV) {
    try {
      out.write(new Integer(index) + ConstantParameters.ITEMSEPARATOR
        + new Integer(docFV.numInstances) + ConstantParameters.ITEMSEPARATOR
        + docFV.docId);
      out.newLine();
      for(int i = 0; i < docFV.numInstances; ++i) {
        StringBuffer line = new StringBuffer();
        line.append(new Integer(i + 1) + ConstantParameters.ITEMSEPARATOR
          + new Integer(labels[i]));
        for(int j = 0; j < docFV.fvs[i].len; ++j)
          line.append(ConstantParameters.ITEMSEPARATOR
            + docFV.fvs[i].nodes[j].index + ConstantParameters.INDEXVALUESEPARATOR
            + docFV.fvs[i].nodes[j].value);
        out.write(line.toString());
        out.newLine();
      }
    } catch(IOException e) {
    }
  }*/

  /** Write the FVs with labels of one document into file. */
  /*void addDocFVsMultiLabelToFile(int index, BufferedWriter out,
    LabelsOfFV[] multiLabels, DocFeatureVectors docFV) {
    try {
      out.write(new Integer(index) + ConstantParameters.ITEMSEPARATOR
        + new Integer(docFV.numInstances) + ConstantParameters.ITEMSEPARATOR
        + docFV.docId);
      out.newLine();
      for(int i = 0; i < docFV.numInstances; ++i) {
        StringBuffer line = new StringBuffer();
        line.append(new Integer(i + 1) + ConstantParameters.ITEMSEPARATOR
          + multiLabels[i].num);
        for(int j = 0; j < multiLabels[i].num; ++j)
          line.append(ConstantParameters.ITEMSEPARATOR
            + multiLabels[i].labels[j]);
        for(int j = 0; j < docFV.fvs[i].len; ++j)
          line.append(ConstantParameters.ITEMSEPARATOR
            + docFV.fvs[i].nodes[j].index + ConstantParameters.INDEXVALUESEPARATOR
            + docFV.fvs[i].nodes[j].value);
        out.write(line.toString());
        out.newLine();
      }
    } catch(IOException e) {
    }
  }*/

  /** Order and select unlabelled documents for active learning. */
  public void orderDocsWithModels(File wdResults,
    LearningEngineSettings engineSettings) {
    try {
      // Reading the names of all documents and the total number of them
      int numDocs = 0;
      // Read the names of documents from a file
      BufferedReader inDocsName = new BomStrippingInputStreamReader(
        new FileInputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFDocsName)), "UTF-8");
      String str = inDocsName.readLine();
      numDocs = Integer.parseInt(str.substring(str.indexOf("=") + 1));
      String[] docsName = new String[numDocs];
      for(int i = 0; i < numDocs; ++i)
        docsName[i] = inDocsName.readLine();
      inDocsName.close();
      // Read the selected document
      int numDocsSelected;
      List<String> selectedDocs = new ArrayList<String>();
      File docsFile = new File(wdResults,
        ConstantParameters.FILENAMEOFSelectedDOCForAL);
      if(docsFile.exists())
        numDocsSelected = obtainDocsForAL(docsFile, selectedDocs);
      else {
        System.out.println("!!! Warning: Cannot get the information about the "
          + "number of documents for selecting!!");
        return;
      }
      boolean[] countedDocs = new boolean[numDocs];
      int[] indexSortedDocs = new int[numDocs];
      // first apply the current models to the data
      File dataFile = new File(wdResults,
        ConstantParameters.FILENAMEOFFVDataSelecting);
      if(!dataFile.exists()) {
        System.out
          .println("!!! Warning: The data file named "
            + ConstantParameters.FILENAMEOFFVDataSelecting
            + " doesn't exist. The file should be used to store the fv data for selecting doc!");
        return;
      }
      File modelFile = new File(wdResults, ConstantParameters.FILENAMEOFModels);
      int learnerType;
      learnerType = obtainLearnerType(engineSettings.learnerSettings.learnerName);
      int numClasses = 0;
      switch(learnerType){
        case 1: // for weka learner
          LogService.logMessage("Use weka learner.", 1);
          System.out
            .println("!! Warning: Currently there is no implementation for the Weka's learner "
              + "to select document for active learning.");
          LogService.logMessage(
            "!! Warning: Currently there is no implementation for the Weka's learner "
              + "to select document for active learning.", 1);
          break;
        case 2: // for learner of multi to binary conversion
          LogService.logMessage("Multi to binary conversion.", 1);
          // System.out.println("** multi to binary:");
          String dataSetFile = null;
          //get a learner
          String learningCommand = engineSettings.learnerSettings.paramsOfLearning;
          learningCommand = learningCommand.trim();
          learningCommand = learningCommand.replaceAll("[ \t]+", " ");
          SupervisedLearner paumLearner = MultiClassLearning
            .obtainLearnerFromName(engineSettings.learnerSettings.learnerName,
              learningCommand, dataSetFile);
          paumLearner
            .setLearnerExecutable(engineSettings.learnerSettings.executableTraining);
          paumLearner
            .setLearnerParams(engineSettings.learnerSettings.paramsOfLearning);
          LogService.logMessage(
            "The learners: " + paumLearner.getLearnerName(), 1);
          MultiClassLearning chunkLearning = new MultiClassLearning(
            engineSettings.multi2BinaryMode);
          // read data
          File  tempDataFile= new File(wdResults,
            ConstantParameters.TempFILENAMEofFVData);
          boolean isUsingTempDataFile = false;
          //if(paumLearner.getLearnerName().equals("SVMExec"))
            //isUsingTempDataFile = true;
          chunkLearning.getDataFromFile(numDocs, dataFile, isUsingTempDataFile, tempDataFile);
          // Apply the model to the data which was read already.
          chunkLearning.apply(paumLearner, modelFile);
          // labelsFVDoc = chunkLearning.dataFVinDoc.labelsFVDoc;
          numClasses = chunkLearning.numClasses;
          if(engineSettings.multi2BinaryMode == 2) // for one vs another mode
            if(chunkLearning.numNull > 0)
              numClasses = (numClasses + 1) * numClasses / 2;
            else numClasses = (numClasses - 1) * numClasses / 2;
          // Compute the margins for the instances in the document
          // first set which documents were selected, which were not
          for(int i = 0; i < numDocs; ++i) {
            if(selectedDocs.contains(docsName[i]))
              countedDocs[i] = false;
            else countedDocs[i] = true;
          }
          // then compute the margins for documents
          float[] marginsD = new float[numDocs];
          float optB = (1 - ((SvmLibSVM)paumLearner).tau)
            / (1 + ((SvmLibSVM)paumLearner).tau);
          computeDocBasedMargins(chunkLearning.dataFVinDoc.labelsFVDoc,
            numClasses, engineSettings.multi2BinaryMode, optB,
            engineSettings.alSetting, marginsD);
          // get the biggest ones
          // setting the smallest value to remove those not counted
          for(int i = 0; i < numDocs; ++i) {
            if(!countedDocs[i]) marginsD[i] = 99999;
          }
          LightWeightLearningApi.sortFloatAscIndex(marginsD, indexSortedDocs,
            numDocs, numDocs);
          // write the ranked documents into a file
          BufferedWriter outDocs = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(new File(wdResults,
              ConstantParameters.FILENAMEOFRankedDOCForAL)), "UTF-8"));
          // String [] str =
          // inDocs.readLine().split(ConstantParameters.ITEMSEPARATOR);
          int numRanked = numDocs - numDocsSelected;
          outDocs.append("##numDocsRanked=" + numRanked);
          outDocs.newLine();
          for(int i = 0; i < numDocs; ++i) {
            int kk = numDocs - i - 1;
            if(countedDocs[indexSortedDocs[kk]]) {
              outDocs.append(docsName[indexSortedDocs[kk]]
                + ConstantParameters.ITEMSEPARATOR
                + marginsD[indexSortedDocs[kk]]);
              outDocs.newLine();
            }
          }
          outDocs.flush();
          outDocs.close();
          break;
        default:
          System.out.println("Error! Wrong learner type.");
          LogService.logMessage("Error! Wrong learner type.", 0);
      }
    } catch(GateException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * obtained the total number of docs and the selected docs for AL.
   *
   * @throws IOException
   */
  private int obtainDocsForAL(File docsFile, List<String> selectedDocs)
    throws IOException {
    int numDocsSelected = 0;
    BufferedReader inDocs = new BomStrippingInputStreamReader(
      new FileInputStream(docsFile), "UTF-8");
    String str = inDocs.readLine();
    while(str != null && str != "") {
      selectedDocs.add(str);
      str = inDocs.readLine();
      ++numDocsSelected;
    }
    inDocs.close();
    return numDocsSelected;
  }

  /**
   * compute the margins of tokens to class hyperplane and averaged over tokens
   * in document
   */
  private void computeDocBasedMargins(LabelsOfFeatureVectorDoc[] labelsFVDoc,
    int numClasses, int multi2BinaryMode, float optB,
    ActiveLearningSetting als, float[] marginsD) {
    int numDoc = labelsFVDoc.length;
    for(int nd = 0; nd < numDoc; ++nd) {
      int numInstances = labelsFVDoc[nd].multiLabels.length;
      float[] valueInst = new float[numInstances];
      for(int i = 0; i < numClasses; ++i) {
        for(int ni = 0; ni < numInstances; ++ni) {
          valueInst[ni] = (float)UsefulFunctions
            .inversesigmoid(labelsFVDoc[nd].multiLabels[ni].probs[i]);
          if(multi2BinaryMode == 1) valueInst[ni] -= optB;
          if(valueInst[ni] > 0) valueInst[ni] = -valueInst[ni]; // get the
          // negative
          // values to be
          // used the
          // sorting
          // method
        }// end of loop over all instances
        // select the biggest
        if(als.numTokensSelect > 0) {
          int numExamples = als.numTokensSelect;
          if(numExamples > numInstances) numExamples = numInstances;
          int[] indexSort = new int[numExamples];
          LightWeightLearningApi.sortFloatAscIndex(valueInst, indexSort,
            numInstances, numExamples);
          float sum = 0;
          for(int j = 0; j < numExamples; ++j)
            sum += valueInst[indexSort[j]]; // still use the negative values
          sum /= numExamples;
          marginsD[nd] -= sum; // because values are negative.
        } else {
          float sum = 0;
          int k = 0;
          for(int j = 0; j < numInstances; ++j)
            if(valueInst[j] > -0.5) {
              sum += valueInst[j];
              ++k;
            }
          if(k > 0)
            sum /= k;
          else sum = -1.0f;
          marginsD[nd] -= sum; // because values are negative.
        }
      }// end of loop over all classes
    }// end of loop over all documents
  }

  /**
   * Obtain the term-frequence matrix for each documents from the feature vector
   * files
   */
  public void termfrequenceMatrix(File wdResults, int numDocs) {
    try {
      BufferedWriter outTFs = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFTermFreqMatrix), true), "UTF-8"));
      BufferedReader inFVs = new BomStrippingInputStreamReader(
        new FileInputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFFeatureVectorData)), "UTF-8");
      Map<Integer, String> indexTerm = new HashMap<Integer, String>();
      for(String obj : featuresList.featuresList.keySet()) {
        indexTerm.put(
          featuresList.featuresList.get(obj).intValue(), obj);
      }
      for(int nd = 0; nd < numDocs; ++nd) {
        String[] ts = inFVs.readLine().split(ConstantParameters.ITEMSEPARATOR);
        outTFs.append(ts[0] + " Documentname=\"" + ts[2] + "\", has " + ts[1]
          + " parts:");
        outTFs.newLine();
        int npart = Integer.parseInt(ts[1]);
        for(int i = 0; i < npart; ++i) {
          String[] ts1 = inFVs.readLine().split(
            ConstantParameters.ITEMSEPARATOR);
          HashMap<String, Integer> termFreq = new HashMap<String, Integer>();
          int bindex = 2 + Integer.parseInt(ts1[1]);
          for(int j = bindex; j < ts1.length; ++j) {
            int isep = ts1[j].indexOf(ConstantParameters.INDEXVALUESEPARATOR);
            Integer index = new Integer(ts1[j].substring(0, isep));
            Integer valI = (int)Float.parseFloat((ts1[j]
              .substring(isep + 1)));
            termFreq.put(indexTerm.get(index), valI);
          }
          List<String> keys = new ArrayList<String>(termFreq.keySet());
          Collections.sort(keys);
          StringBuffer sb = new StringBuffer();
          for(int j = 0; j < keys.size(); ++j) {
            String ks = keys.get(j);
            String str = ks;
            if(str.contains("<>")) { // if it is a ngram feature
              str = str.substring(str.indexOf("_", 1) + 1, str
                .lastIndexOf("<>"));
              sb.append(str + ConstantParameters.INDEXVALUESEPARATOR
                + termFreq.get(ks) + " ");
            }
          }
          outTFs.append(sb.toString().trim());
          outTFs.newLine();
        }
      }
      inFVs.close();
      outTFs.flush();
      outTFs.close();
    } catch(UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch(FileNotFoundException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /** Copy the NLP features from tempory file to normal file. */
  public void copyNLPFeat2NormalFile(File wdResults, int miNumDocsTraining) {
    try {
      BufferedWriter outNLPFeatures = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFNLPFeaturesData), true), "UTF-8"));
      BufferedReader inNLPFeaturesTemp = new BomStrippingInputStreamReader(new FileInputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFNLPFeaturesDataTemp)), "UTF-8");
      String line = inNLPFeaturesTemp.readLine();
      if(miNumDocsTraining == 0) {
        outNLPFeatures.append(line);
        outNLPFeatures.newLine();
      }
      line = inNLPFeaturesTemp.readLine();
      while(line != null) {
        outNLPFeatures.append(line);
        outNLPFeatures.newLine();
        line = inNLPFeaturesTemp.readLine();
      }
      inNLPFeaturesTemp.close();
      outNLPFeatures.flush();
      outNLPFeatures.close();
    } catch(UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch(FileNotFoundException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Training using the Java implementatoin of learning algorithms.
   */
  public void trainingJava(int numDocs, LearningEngineSettings engineSettings)
    throws GateException {
    LogService.logMessage("\nTraining starts.\n", 1);
    // The files for training data and model
    File wdResults = new File(wd, ConstantParameters.SUBDIRFORRESULTS);
    String fvFileName = wdResults.toString() + File.separator
      + ConstantParameters.FILENAMEOFFeatureVectorData;
    String nlpFileName = wdResults.toString() + File.separator
      + ConstantParameters.FILENAMEOFNLPFeaturesData;
    String modelFileName = wdResults.toString() + File.separator
      + ConstantParameters.FILENAMEOFModels;
    String labelInDataFileName = wdResults.toString() + File.separator
      + ConstantParameters.FILENAMEOFLabelsInData;
    String nlpDataLabelFileName = wdResults.toString() + File.separator
      + ConstantParameters.FILENAMEOFNLPDataLabel;
    File dataFile = new File(fvFileName);
    File nlpDataFile = new File(nlpFileName);
    File modelFile = new File(modelFileName);
    File labelInData = new File(labelInDataFileName);
    File nlpDataLabelFile = new File(nlpDataLabelFileName);
    int learnerType = obtainLearnerType(engineSettings.learnerSettings.learnerName);
    // benchmarking features
    Map<Object,Object> benchmarkingFeatures = new HashMap<Object,Object>();
    switch(learnerType){
      case 1: // for weka learner
        LogService.logMessage("Use weka learner.", 1);
        WekaLearning wekaL = new WekaLearning();
        short featureType = WekaLearning
          .obtainWekaLeanerDataType(engineSettings.learnerSettings.learnerName);
        // Convert and read training data
        switch(featureType){
          case WekaLearning.NLPFEATUREFVDATA:
            // Transfer the labels in nlpDataFile into
            // the label in the sparse data
            // and collect the labels and write them into a file
            long startTime = Benchmark.startPoint();
            convertNLPLabelsTDataLabel(nlpDataFile, dataFile, labelInData,
              nlpDataLabelFile, numDocs, engineSettings.surround);
            Benchmark.checkPoint(startTime, benchmarkID + "."
              + "nlpLabelsToDataLabels", this, benchmarkingFeatures);
            startTime = Benchmark.startPoint();
            benchmarkingFeatures.put("nlpFeaturesFile", nlpDataLabelFile
              .getAbsolutePath());
            wekaL.readNLPFeaturesFromFile(nlpDataLabelFile, numDocs,
              this.featuresList, true, labelsAndId.label2Id.size(),
              engineSettings.surround);
            Benchmark.checkPoint(startTime, benchmarkID + "."
              + "readingNlpFeatures", this, benchmarkingFeatures);
            benchmarkingFeatures.remove("nlpFeaturesFile");
            break;
          case WekaLearning.SPARSEFVDATA:
            startTime = Benchmark.startPoint();
            benchmarkingFeatures.put("featureVectorFile", dataFile
              .getAbsolutePath());
            wekaL.readSparseFVsFromFile(dataFile, numDocs, true,
              labelsAndId.label2Id.size(), engineSettings.surround);
            Benchmark.checkPoint(startTime, benchmarkID + "." + "readingFVs",
              this, benchmarkingFeatures);
            benchmarkingFeatures.remove("featureVectorFile");
            break;
        }
        // Get the wekaLearner from the learnername
        WekaLearner wekaLearner = WekaLearning.obtainWekaLearner(
          engineSettings.learnerSettings.learnerName,
          engineSettings.learnerSettings.paramsOfLearning);
        LogService.logMessage("Weka learner name: "
          + wekaLearner.getLearnerName(), 1);
        long startTime = Benchmark.startPoint();
        benchmarkingFeatures.put("modelFile", modelFile.getAbsolutePath());
        // Training.
        wekaL.train(wekaLearner, modelFile);
        Benchmark.checkPoint(startTime,
          benchmarkID + "." + "wekaModelTraining", this, benchmarkingFeatures);
        benchmarkingFeatures.remove("modelFile");
        break;
      case 2: // for learner of multi to binary conversion
        LogService.logMessage("Multi to binary conversion.", 1);
         //      get a learner
        String learningCommand = "";
        if(engineSettings.learnerSettings.executableTraining != null)
          learningCommand = engineSettings.learnerSettings.executableTraining+ " ";
        learningCommand += engineSettings.learnerSettings.paramsOfLearning;
        learningCommand = learningCommand.trim();
        learningCommand = learningCommand.replaceAll("[ \t]+", " ");
        String dataSetFile = null;
        SupervisedLearner paumLearner = MultiClassLearning
          .obtainLearnerFromName(engineSettings.learnerSettings.learnerName,
            learningCommand, dataSetFile);
        paumLearner
          .setLearnerExecutable(engineSettings.learnerSettings.executableTraining);
        paumLearner
          .setLearnerParams(engineSettings.learnerSettings.paramsOfLearning);
        LogService.logMessage("The learners: " + paumLearner.getLearnerName(),
          1);
        if(LogService.minVerbosityLevel > 1)
          System.out.println("Using the "+ paumLearner.getLearnerName());
        MultiClassLearning chunkLearning = new MultiClassLearning(
          engineSettings.multi2BinaryMode);
        if(engineSettings.multiBinaryExecutor != null) {
          chunkLearning.setExecutor(engineSettings.multiBinaryExecutor);
        }
        startTime = Benchmark.startPoint();
        benchmarkingFeatures.put("dataFile", dataFile.getAbsolutePath());
        //      read data
        File  tempDataFile= new File(wdResults,
          ConstantParameters.TempFILENAMEofFVData);
        boolean isUsingTempDataFile = false;
        if(paumLearner.getLearnerName().equals("SVMExec") ||
          paumLearner.getLearnerName().equals("PAUMExec") )
          isUsingTempDataFile = true; //using the temp data file
        chunkLearning.getDataFromFile(numDocs, dataFile, isUsingTempDataFile, tempDataFile);
        Benchmark.checkPoint(startTime, benchmarkID + "."
          + "readingChunkLearningData", this, benchmarkingFeatures);
        benchmarkingFeatures.remove("dataFile");
        LogService.logMessage("The number of classes in dataset: "
          + chunkLearning.numClasses, 1);

        // training
        startTime = Benchmark.startPoint();
        benchmarkingFeatures.put("modelFile", modelFile.getAbsolutePath());
        //using different method for one thread or multithread
        if(engineSettings.numThreadUsed >1 )//for using thread
          chunkLearning.training(paumLearner, modelFile);
        else //for not using thread
          chunkLearning.trainingNoThread(paumLearner, modelFile, isUsingTempDataFile, tempDataFile);

        Benchmark.checkPoint(startTime,
          benchmarkID + "." + "paumModelTraining", this, benchmarkingFeatures);
        benchmarkingFeatures.remove("modelFile");
        break;
      default:
        System.out.println("Error! Wrong learner type.");
        LogService.logMessage("Error! Wrong learner type.", 0);
    }
  }

  /**
   * Apply the model to data, also using the learning algorithm implemented in
   * Java.
   */
  public void applyModelInJava(Corpus corpus, int startDocId, int endDocId,
    String labelName, LearningEngineSettings engineSettings, File tempFilesDir)
    throws GateException {
    int numDocs = endDocId - startDocId;
    LogService.logMessage("\nApplication starts.", 1);
    // The files for training data and model
    File wdResults = new File(wd, ConstantParameters.SUBDIRFORRESULTS);
    String fvFileName = tempFilesDir.toString() + File.separator
     + ConstantParameters.FILENAMEOFFeatureVectorDataApp;
    String nlpFileName = tempFilesDir.toString() + File.separator
      + ConstantParameters.FILENAMEOFNLPFeaturesData;
    String modelFileName = wdResults.toString() + File.separator
      + ConstantParameters.FILENAMEOFModels;
    // String labelInDataFileName = wdResults.toString() + File.separator
    // + ConstantParameters.FILENAMEOFLabelsInData;
    File dataFile = new File(fvFileName);
    File nlpDataFile = new File(nlpFileName);
    File modelFile = new File(modelFileName);
    int learnerType;
    learnerType = obtainLearnerType(engineSettings.learnerSettings.learnerName);
    int numClasses = 0;
    // Store the label information from the model application
    LabelsOfFeatureVectorDoc[] labelsFVDoc = null;
    short featureType = WekaLearning.SPARSEFVDATA;
    Map<Object,Object> benchmarkingFeatures = new HashMap<Object,Object>();
    switch(learnerType){
      case 1: // for weka learner
        LogService.logMessage("Use weka learner.", 1);
        WekaLearning wekaL = new WekaLearning();
        // Check if the learner uses the sparse feaature vectors or NLP
        // features
        featureType = WekaLearning
          .obtainWekaLeanerDataType(engineSettings.learnerSettings.learnerName);
        long startTime = Benchmark.startPoint();
        switch(featureType){
          case WekaLearning.NLPFEATUREFVDATA:
            wekaL.readNLPFeaturesFromFile(nlpDataFile, numDocs,
              this.featuresList, false, labelsAndId.label2Id.size(),
              engineSettings.surround);
            break;
          case WekaLearning.SPARSEFVDATA:
            wekaL.readSparseFVsFromFile(dataFile, numDocs, false,
              labelsAndId.label2Id.size(), engineSettings.surround);
            break;
        }
        Benchmark.checkPoint(startTime, benchmarkID + "."
          + "readingNlpFeatures", this, benchmarkingFeatures);
        // Check if the weka learner has distribute output of classify
        boolean distributionOutput = WekaLearning
          .obtainWekaLearnerOutputType(engineSettings.learnerSettings.learnerName);
        // Get the wekaLearner from the learnername
        WekaLearner wekaLearner = WekaLearning.obtainWekaLearner(
          engineSettings.learnerSettings.learnerName,
          engineSettings.learnerSettings.paramsOfLearning);
        LogService.logMessage("Weka learner name: "
          + wekaLearner.getLearnerName(), 1);
        // Application
        startTime = Benchmark.startPoint();
        benchmarkingFeatures.put("modelFile", modelFile.getAbsolutePath());
        wekaL.apply(wekaLearner, modelFile, distributionOutput);
        Benchmark.checkPoint(startTime, benchmarkID + "."
          + "wekaModelApplication", this, benchmarkingFeatures);
        benchmarkingFeatures.remove("modelFile");
        labelsFVDoc = wekaL.labelsFVDoc;
        numClasses = labelsAndId.label2Id.size() * 2; // subtract the
        // null class
        break;
      case 2: // for learner of multi to binary conversion
        LogService.logMessage("Multi to binary conversion.", 1);
        // System.out.println("** multi to binary:");
        //      get a learner
        String learningCommand = engineSettings.learnerSettings.paramsOfLearning;
        learningCommand = learningCommand.trim();
        learningCommand = learningCommand.replaceAll("[ \t]+", " ");
        String dataSetFile = null;
        SupervisedLearner paumLearner = MultiClassLearning
          .obtainLearnerFromName(engineSettings.learnerSettings.learnerName,
            learningCommand, dataSetFile);
        paumLearner
          .setLearnerExecutable(engineSettings.learnerSettings.executableTraining);
        paumLearner
          .setLearnerParams(engineSettings.learnerSettings.paramsOfLearning);
        LogService.logMessage("The learners: " + paumLearner.getLearnerName(),
          1);
        MultiClassLearning chunkLearning = new MultiClassLearning(
          engineSettings.multi2BinaryMode);
        // read data
        startTime = Benchmark.startPoint();
        //get the fv data
        File  tempDataFile= new File(wdResults,
          ConstantParameters.TempFILENAMEofFVData);
        boolean isUsingTempDataFile = false;
        //if(paumLearner.getLearnerName().equals("SVMExec"))
          //isUsingTempDataFile = true;
        chunkLearning.getDataFromFile(numDocs, dataFile, isUsingTempDataFile, tempDataFile);
        Benchmark.checkPoint(startTime, benchmarkID + "."
          + "readingChunkLearningData", this, benchmarkingFeatures);
        // apply
        startTime = Benchmark.startPoint();
        benchmarkingFeatures.put("modelFile", modelFile.getAbsolutePath());
        //      using different method for one thread or multithread
        if(engineSettings.numThreadUsed>1) //for using thread
          chunkLearning.apply(paumLearner, modelFile);
        else //for not using thread
          chunkLearning.applyNoThread(paumLearner, modelFile);

        Benchmark.checkPoint(startTime, benchmarkID + "."
          + "paumModelApplication", this, benchmarkingFeatures);
        benchmarkingFeatures.remove("modelFile");
        labelsFVDoc = chunkLearning.dataFVinDoc.labelsFVDoc;
        numClasses = chunkLearning.numClasses;
        break;
      default:
        System.out.println("Error! Wrong learner type.");
        LogService.logMessage("Error! Wrong learner type.", 0);
    }
    if(engineSettings.surround) {
      String featName = engineSettings.datasetDefinition.arrs.classFeature;
      String instanceType = engineSettings.datasetDefinition.getInstanceType();
      labelsAndId = new Label2Id();
      labelsAndId.loadLabelAndIdFromFile(wdResults,
        ConstantParameters.FILENAMEOFLabelList);
      // post-processing and add new annotation to the text
      PostProcessing postPr = new PostProcessing(
        engineSettings.thrBoundaryProb, engineSettings.thrEntityProb,
        engineSettings.thrClassificationProb);
      // System.out.println("** Application mode:");
      long startTime = Benchmark.startPoint();
      for(int i = 0; i < numDocs; ++i) {
        Set<ChunkOrEntity> chunks = new HashSet<ChunkOrEntity>();
        postPr.postProcessingChunk((short)3, labelsFVDoc[i].multiLabels,
          numClasses, chunks, chunkLenHash);
        // System.out.println("**
        // documentName="+((Document)corpus.get(i)).getName());
        boolean wasLoaded = corpus.isDocumentLoaded(i+startDocId);
        Document toProcess = corpus.get(i + startDocId);
        addAnnsInDoc(toProcess, chunks, instanceType, featName, labelName,
          labelsAndId);
        if(toProcess.getDataStore() != null && corpus.getDataStore() != null) {
          corpus.getDataStore().sync(corpus);
        }
        if(!wasLoaded) {
          corpus.unloadDocument(toProcess);
          Factory.deleteResource(toProcess);
        }
      }
      Benchmark.checkPoint(startTime, benchmarkID + "." + "postProcessing",
        this, benchmarkingFeatures);
    } else {
      String featName = engineSettings.datasetDefinition.arrs.classFeature;
      String instanceType = engineSettings.datasetDefinition.getInstanceType();
      labelsAndId = new Label2Id();
      labelsAndId.loadLabelAndIdFromFile(wdResults,
        ConstantParameters.FILENAMEOFLabelList);
      // post-processing and add new annotation to the text
      // PostProcessing postPr = new PostProcessing(0.42, 0.2);
      PostProcessing postPr = new PostProcessing(
        engineSettings.thrBoundaryProb, engineSettings.thrEntityProb,
        engineSettings.thrClassificationProb);
      for(int i = 0; i < numDocs; ++i) {
        int[] selectedLabels = new int[labelsFVDoc[i].multiLabels.length];
        float[] valuesLabels = new float[labelsFVDoc[i].multiLabels.length];
        postPr.postProcessingClassification((short)3,
          labelsFVDoc[i].multiLabels, selectedLabels, valuesLabels);
        boolean wasLoaded = corpus.isDocumentLoaded(i+startDocId);
        Document toProcess = corpus.get(i + startDocId);
        // Add the ranked label list and their scores, not just a single label
        // addLabelListInDocClassification(toProcess,
        // labelsFVDoc[i].multiLabels,
        // instanceType, featName, labelName, labelsAndId, engineSettings);
        addAnnsInDocClassification(toProcess, selectedLabels, valuesLabels,
          instanceType, featName, labelName, labelsAndId, engineSettings);
        if(toProcess.getDataStore() != null && corpus.getDataStore() != null) {
          corpus.getDataStore().sync(corpus);
        }
        if(!wasLoaded) {
          corpus.unloadDocument(toProcess);
          Factory.deleteResource(toProcess);
        }
      }
    }
  }

  /**
   * Apply the model to one document, also using the learning algorithm
   * implemented in Java.
   */
  public void applyModelInJavaPerDoc(Document doc, String labelName,
    LearningEngineSettings engineSettings, File tempFilesDir) throws GateException {
    LogService.logMessage("\nApplication starts.", 1);
    // The files for training data and model
    File wdResults = new File(wd, ConstantParameters.SUBDIRFORRESULTS);
    String fvFileName = tempFilesDir.toString() + File.separator
      + ConstantParameters.FILENAMEOFFeatureVectorData;
    String nlpFileName = tempFilesDir.toString() + File.separator
      + ConstantParameters.FILENAMEOFNLPFeaturesData;
    String modelFileName = wdResults.toString() + File.separator
      + ConstantParameters.FILENAMEOFModels;
    // String labelInDataFileName = wdResults.toString() + File.separator
    // + ConstantParameters.FILENAMEOFLabelsInData;
    File dataFile = new File(fvFileName);
    File nlpDataFile = new File(nlpFileName);
    File modelFile = new File(modelFileName);
    int learnerType;
    learnerType = obtainLearnerType(engineSettings.learnerSettings.learnerName);
    int numClasses = 0;
    int numDocs = 1; // corpus.size();
    // Store the label information from the model application
    LabelsOfFeatureVectorDoc[] labelsFVDoc = null;
    short featureType = WekaLearning.SPARSEFVDATA;
    switch(learnerType){
      case 1: // for weka learner
        LogService.logMessage("Use weka learner.", 1);
        WekaLearning wekaL = new WekaLearning();
        // Check if the learner uses the sparse feaature vectors or NLP
        // features
        featureType = WekaLearning
          .obtainWekaLeanerDataType(engineSettings.learnerSettings.learnerName);
        switch(featureType){
          case WekaLearning.NLPFEATUREFVDATA:
            wekaL.readNLPFeaturesFromFile(nlpDataFile, numDocs,
              this.featuresList, false, labelsAndId.label2Id.size(),
              engineSettings.surround);
            break;
          case WekaLearning.SPARSEFVDATA:
            wekaL.readSparseFVsFromFile(dataFile, numDocs, false,
              labelsAndId.label2Id.size(), engineSettings.surround);
            break;
        }
        // Check if the weka learner has distribute output of classify
        boolean distributionOutput = WekaLearning
          .obtainWekaLearnerOutputType(engineSettings.learnerSettings.learnerName);
        // Get the wekaLearner from the learnername
        WekaLearner wekaLearner = WekaLearning.obtainWekaLearner(
          engineSettings.learnerSettings.learnerName,
          engineSettings.learnerSettings.paramsOfLearning);
        LogService.logMessage("Weka learner name: "
          + wekaLearner.getLearnerName(), 1);
        // Training.
        wekaL.apply(wekaLearner, modelFile, distributionOutput);
        labelsFVDoc = wekaL.labelsFVDoc;
        numClasses = labelsAndId.label2Id.size() * 2; // subtract the
        // null class
        break;
      case 2: // for learner of multi to binary conversion
        LogService.logMessage("Multi to binary conversion.", 1);
        // get a learner
        String learningCommand = engineSettings.learnerSettings.paramsOfLearning;
        learningCommand = learningCommand.trim();
        learningCommand = learningCommand.replaceAll("[ \t]+", " ");
        String dataSetFile = null;
        SupervisedLearner paumLearner = MultiClassLearning
          .obtainLearnerFromName(engineSettings.learnerSettings.learnerName,
            learningCommand, dataSetFile);
        paumLearner
          .setLearnerExecutable(engineSettings.learnerSettings.executableTraining);
        paumLearner
          .setLearnerParams(engineSettings.learnerSettings.paramsOfLearning);
        LogService.logMessage("The learners: " + paumLearner.getLearnerName(),
          1);
        // System.out.println("** multi to binary:");
        MultiClassLearning chunkLearning = new MultiClassLearning(
          engineSettings.multi2BinaryMode);
        // read data
         //      get the fv data
        File  tempDataFile= new File(tempFilesDir,
          ConstantParameters.TempFILENAMEofFVData);
        boolean isUsingTempDataFile = false;
        //if(paumLearner.getLearnerName().equals("SVMExec"))
          //isUsingTempDataFile = true;
        chunkLearning.getDataFromFile(numDocs, dataFile, isUsingTempDataFile, tempDataFile);
        // dataFile);

        // apply
        //      using different method for one thread or multithread
        if(engineSettings.numThreadUsed>1) //for using thread
          chunkLearning.apply(paumLearner, modelFile);
        else //for not using thread
          chunkLearning.applyNoThread(paumLearner, modelFile);
        labelsFVDoc = chunkLearning.dataFVinDoc.labelsFVDoc;
        numClasses = chunkLearning.numClasses;
        break;
      default:
        System.out.println("Error! Wrong learner type.");
        LogService.logMessage("Error! Wrong learner type.", 0);
    }
    if(engineSettings.surround) {
      String featName = engineSettings.datasetDefinition.arrs.classFeature;
      String instanceType = engineSettings.datasetDefinition.getInstanceType();
      labelsAndId = new Label2Id();
      labelsAndId.loadLabelAndIdFromFile(wdResults,
        ConstantParameters.FILENAMEOFLabelList);
      // post-processing and add new annotation to the text
      PostProcessing postPr = new PostProcessing(
        engineSettings.thrBoundaryProb, engineSettings.thrEntityProb,
        engineSettings.thrClassificationProb);
      // System.out.println("** Application mode:");
      for(int i = 0; i < numDocs; ++i) {
        Set<ChunkOrEntity> chunks = new HashSet<ChunkOrEntity>();
        postPr.postProcessingChunk((short)3, labelsFVDoc[i].multiLabels,
          numClasses, chunks, chunkLenHash);
        // System.out.println("**
        // documentName="+((Document)corpus.get(i)).getName());
        addAnnsInDoc(doc, chunks, instanceType, featName, labelName,
          labelsAndId);
      }
    } else {
      String featName = engineSettings.datasetDefinition.arrs.classFeature;
      String instanceType = engineSettings.datasetDefinition.getInstanceType();
      labelsAndId = new Label2Id();
      labelsAndId.loadLabelAndIdFromFile(wdResults,
        ConstantParameters.FILENAMEOFLabelList);
      // post-processing and add new annotation to the text
      // PostProcessing postPr = new PostProcessing(0.42, 0.2);
      PostProcessing postPr = new PostProcessing(
        engineSettings.thrBoundaryProb, engineSettings.thrEntityProb,
        engineSettings.thrClassificationProb);
      for(int i = 0; i < numDocs; ++i) { // numDocs is always 1
        int[] selectedLabels = new int[labelsFVDoc[i].multiLabels.length];
        float[] valuesLabels = new float[labelsFVDoc[i].multiLabels.length];
        postPr.postProcessingClassification((short)3,
          labelsFVDoc[i].multiLabels, selectedLabels, valuesLabels);
        addAnnsInDocClassification(doc, selectedLabels, valuesLabels,
          instanceType, featName, labelName, labelsAndId, engineSettings);
      }
    }
  }

  /**
   * Add the annotation into documents for chunk learning.
   *
   * @throws InvalidOffsetException
   */
  private void addAnnsInDoc(Document doc, Set<ChunkOrEntity> chunks, String instanceType,
    String featName, String labelName, Label2Id labelsAndId)
    throws InvalidOffsetException {
    AnnotationSet annsDoc = null;
    if(inputASName == null || inputASName.trim().length() == 0) {
      annsDoc = doc.getAnnotations();
    } else {
      annsDoc = doc.getAnnotations(inputASName);
    }
    AnnotationSet annsDocResults = null;
    if(outputASName == null || outputASName.trim().length() == 0) {
      annsDocResults = doc.getAnnotations();
    } else {
      annsDocResults = doc.getAnnotations(outputASName);
    }
    AnnotationSet anns = annsDoc.get(instanceType);
    List<Annotation> annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    for(ChunkOrEntity entity : chunks) {
      FeatureMap features = Factory.newFeatureMap();
      features.put(featName, labelsAndId.id2Label.get(entity.name));
      features.put("prob", entity.prob);
      Annotation token1 = annotationArray.get(entity.start);
      Annotation token2 = annotationArray.get(entity.end);
      Node entityS = token1.getStartNode();
      Node entityE = token2.getEndNode();
      if(entityS != null && entityE != null)
        annsDocResults.add(entityS.getOffset(), entityE.getOffset(), labelName,
          features);
    }
  }

  /**
   * Add the annotation into documents for classification.
   *
   * @throws InvalidOffsetException
   */
  private void addAnnsInDocClassification(Document doc, int[] selectedLabels,
    float[] valuesLabels, String instanceType, String featName,
    String labelName, Label2Id labelsAndId,
    LearningEngineSettings engineSettings) throws InvalidOffsetException {
    AnnotationSet annsDoc = null;
    if(inputASName == null || inputASName.trim().length() == 0) {
      annsDoc = doc.getAnnotations();
    } else {
      annsDoc = doc.getAnnotations(inputASName);
    }
    AnnotationSet annsDocResults = null;
    if(outputASName == null || outputASName.trim().length() == 0) {
      annsDocResults = doc.getAnnotations();
    } else {
      annsDocResults = doc.getAnnotations(outputASName);
    }
    //AnnotationSet annsLabel = annsDoc.get(labelName);
    AnnotationSet anns = annsDoc.get(instanceType);
    List<Annotation> annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    // For the relation extraction
    String arg1F = null;
    String arg2F = null;
    if(engineSettings.datasetDefinition.dataType == DataSetDefinition.RelationData) {
      AttributeRelation relAtt = (AttributeRelation)engineSettings.datasetDefinition.classAttribute;
      arg1F = relAtt.getArg1();
      arg2F = relAtt.getArg2();
    }
    for(int i = 0; i < annotationArray.size(); ++i) {
      if(selectedLabels[i] < 0) continue;
      
      if (labelsAndId.id2Label.get(selectedLabels[i] + 1) == null) {
        throw new RuntimeException("Required feature '"+featName+"' is missing");
      }
      
      FeatureMap features = Factory.newFeatureMap();
      Annotation ann = annotationArray.get(i);
      features.putAll(ann.getFeatures()); //GG: put all instance features on output
      features.put(featName, labelsAndId.id2Label.get(
        selectedLabels[i] + 1).toString());
      features.put("prob", valuesLabels[i]);
      // For relation data, need the argument features
      if(engineSettings.datasetDefinition.dataType == DataSetDefinition.RelationData) {
        String arg1V = ann.getFeatures().get(
          engineSettings.datasetDefinition.arg1Feat).toString();
        String arg2V = ann.getFeatures().get(
          engineSettings.datasetDefinition.arg2Feat).toString();
        features.put(arg1F, arg1V);
        features.put(arg2F, arg2V);
      }
      // FeatureMap featO = ann.getFeatures();
      // for(Object obj:features.keySet()) {
      // if(featO.containsKey(obj))
      // featO.put(obj.toString()+"_results", features.get(obj));
      // else featO.put(obj, features.get(obj));
      // }
      annsDocResults.add(ann.getStartNode().getOffset(), ann.getEndNode()
        .getOffset(), labelName, features);
    }
  }

  /**
   * Add a ranked list of label for each example in documents for
   * classification, not just a single label.
   *
   * @throws InvalidOffsetException
   */
  @SuppressWarnings("unused")
  private void addLabelListInDocClassification(Document doc,
    LabelsOfFV[] multiLabels, String instanceType, String featName,
    String labelName, Label2Id labelsAndId,
    LearningEngineSettings engineSettings) throws InvalidOffsetException {
    AnnotationSet annsDoc = null;
    if(inputASName == null || inputASName.trim().length() == 0) {
      annsDoc = doc.getAnnotations();
    } else {
      annsDoc = doc.getAnnotations(inputASName);
    }
    AnnotationSet annsDocResults = null;
    if(outputASName == null || outputASName.trim().length() == 0) {
      annsDocResults = doc.getAnnotations();
    } else {
      annsDocResults = doc.getAnnotations(outputASName);
    }
    AnnotationSet anns = annsDoc.get(instanceType);
    List<Annotation> annotationArray = (anns == null || anns.isEmpty())
      ? new ArrayList<Annotation>()
      : new ArrayList<Annotation>(anns);
    Collections.sort(annotationArray, new OffsetComparator());
    // For the relation extraction
    String arg1F = null;
    String arg2F = null;
    if(engineSettings.datasetDefinition.dataType == DataSetDefinition.RelationData) {
      AttributeRelation relAtt = (AttributeRelation)engineSettings.datasetDefinition.classAttribute;
      arg1F = relAtt.getArg1();
      arg2F = relAtt.getArg2();
    }
    for(int i = 0; i < annotationArray.size(); ++i) {
      int len = multiLabels[i].num;
      int[] indexSort = new int[len];
      sortFloatAscIndex(multiLabels[i].probs, indexSort, len, len);
      // get the labels and their scores
      StringBuffer strB = new StringBuffer();
      for(int j = 0; j < len; ++j) {
        String label = labelsAndId.id2Label.get(
          indexSort[j] + 1).toString();
        strB.append(label + ":" + multiLabels[i].probs[indexSort[j]] + " ");
      }
      FeatureMap features = Factory.newFeatureMap();
      Annotation ann = annotationArray.get(i);
      features.putAll(ann.getFeatures()); //GG: put all instance features on output
      features.put(featName, strB.toString().trim());
      // features.put("prob", valuesLabels[i]);
      // For relation data, need the argument features
      if(engineSettings.datasetDefinition.dataType == DataSetDefinition.RelationData) {
        String arg1V = ann.getFeatures().get(
          engineSettings.datasetDefinition.arg1Feat).toString();
        String arg2V = ann.getFeatures().get(
          engineSettings.datasetDefinition.arg2Feat).toString();
        features.put(arg1F, arg1V);
        features.put(arg2F, arg2V);
      }
      FeatureMap featO = ann.getFeatures();
      for(Object obj : features.keySet()) {
        // if(featO.containsKey(obj))
        featO.put(obj.toString() + "_resultsList", features.get(obj));
        // else featO.put(obj, features.get(obj));
      }
      // FeatureMap featAdd = ann.
      // for(Object obj:featO.keySet()) {
      // annsDocResults.add(ann.).getFeatures().put(obj, featO.get(obj));
      // }
      annsDocResults.add(ann.getStartNode().getOffset(), ann.getEndNode()
        .getOffset(), labelName, featO);
      // annsDoc.add(ann.getStartNode(), ann.getEndNode(), labelName, features);
    }
  }

  /** Convert the string labels in the nlp data file into the index labels. */
  public void convertNLPLabelsTDataLabel(File nlpDataFile, File dataFile,
    File labelInDataFile, File nlpDataLabelFile, int numDocs,
    boolean surroundingMode) {
    try {
      BufferedReader inData = new BomStrippingInputStreamReader(
        new FileInputStream(dataFile), "UTF-8");
      BufferedReader inNlpData = new BomStrippingInputStreamReader(
        new FileInputStream(nlpDataFile), "UTF-8");
      BufferedWriter outNlpDataLabel = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(nlpDataLabelFile), "UTF-8"));
      Set<String> uniqueLabels = new HashSet<String>();
      // The head line of NLP feature file
      String line = inNlpData.readLine();
      outNlpDataLabel.append(line);
      outNlpDataLabel.newLine();
      String[] items;
      // For each document
      for(int iDoc = 0; iDoc < numDocs; ++iDoc) {
        int numLabels;
        line = inData.readLine();
        items = line.split(ConstantParameters.ITEMSEPARATOR);
        int num = Integer.parseInt(items[1]);
        line = inNlpData.readLine();
        items = line.split(ConstantParameters.ITEMSEPARATOR);
        line = items[0]+ ConstantParameters.ITEMSEPARATOR+items[1]+ ConstantParameters.ITEMSEPARATOR+
           +num;
        outNlpDataLabel.append(line);
        outNlpDataLabel.newLine();
        // For each instance
        for(int i = 0; i < num; ++i) {
          // Read the line from data file and get the data label
          line = inData.readLine();
          items = line.split(ConstantParameters.ITEMSEPARATOR);
          numLabels = Integer.parseInt(items[1]);
          StringBuffer labels = new StringBuffer();
          labels.append(items[1]);

          //System.out.println("line="+line+", items[1]="+items[1]);

          for(int j = 0; j < numLabels; ++j) {
            labels.append(ConstantParameters.ITEMSEPARATOR);
            labels.append(items[j + 2]);
            if(!uniqueLabels.contains(items[j + 2]))
              uniqueLabels.add(items[j + 2]);
          }
          outNlpDataLabel.append(labels.toString());
          // Read the line from NLP feature and get the features
          line = inNlpData.readLine();
          items = line.split(ConstantParameters.ITEMSEPARATOR);
          numLabels = Integer.parseInt(items[0]);
          StringBuffer nlpFeats = new StringBuffer();
          for(int j = numLabels + 1; j < items.length; ++j) {
            nlpFeats.append(ConstantParameters.ITEMSEPARATOR);
            nlpFeats.append(items[j]);
          }
          outNlpDataLabel.append(nlpFeats);
          outNlpDataLabel.newLine();
        }
      }
      outNlpDataLabel.flush();
      outNlpDataLabel.close();
      inData.close();
      inNlpData.close();
      BufferedWriter labelInData = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(labelInDataFile), "UTF-8"));
      labelInData.append(uniqueLabels.size() + " #total_labels");
      labelInData.newLine();
      for(Object obj : uniqueLabels) {
        labelInData.append(obj.toString());
        labelInData.newLine();
      }
      labelInData.flush();
      labelInData.close();
    } catch(FileNotFoundException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Flitering out the negative examples of the training data using the SVM.
   *
   * @throws GateException
   */
  public void FilteringNegativeInstsInJava(int numDocs,
    LearningEngineSettings engineSettings) throws GateException {
    LogService.logMessage("\nFiltering starts.", 1);
    // The files for training data and model
    File wdResults = new File(wd, ConstantParameters.SUBDIRFORRESULTS);
    String fvFileName = wdResults.toString() + File.separator
      + ConstantParameters.FILENAMEOFFeatureVectorData;
    String modelFileName = wdResults.toString() + File.separator
      + ConstantParameters.FILENAMEOFModels;
    File dataFile = new File(fvFileName);
    File modelFile = new File(modelFileName);
    // for learner of multi to binary conversion
    LogService.logMessage("Multi to binary conversion.", 1);
    MultiClassLearning chunkLearning = new MultiClassLearning(
      engineSettings.multi2BinaryMode);
    // read data
    File  tempDataFile= new File(wdResults,
      ConstantParameters.TempFILENAMEofFVData);
    boolean isUsingTempDataFile = false;
    //if(paumLearner.getLearnerName().equals("SVMExec"))
      //isUsingTempDataFile = true; //using the temp data file
    chunkLearning.getDataFromFile(numDocs, dataFile, isUsingTempDataFile, tempDataFile);
    // Back up the label data before it is reset.
    LabelsOfFeatureVectorDoc[] labelsFVDocB = new LabelsOfFeatureVectorDoc[numDocs];
    for(int i = 0; i < numDocs; ++i) {
      labelsFVDocB[i] = new LabelsOfFeatureVectorDoc();
      labelsFVDocB[i].multiLabels = new LabelsOfFV[chunkLearning.dataFVinDoc.labelsFVDoc[i].multiLabels.length];
      for(int j = 0; j < chunkLearning.dataFVinDoc.labelsFVDoc[i].multiLabels.length; ++j)
        labelsFVDocB[i].multiLabels[j] = chunkLearning.dataFVinDoc.labelsFVDoc[i].multiLabels[j];
    }
    // Reset the class label of data for binary class for filtering
    // purpose
    int numNeg; // number of negative example in the training data
    numNeg = chunkLearning.resetClassInData();
    if(numNeg == 0) {// No negative example (null lable) at all in training
                      // data
      LogService.logMessage(
        "!Cannot do the filtering, because there is no negative examples"
          + "in the training data for filtering!", 1);
      if(LogService.minVerbosityLevel > 0)
        System.out
          .println("!Cannot do the filtering, because there is no negative examples"
            + "in the training data for filtering!");
      return;
    }
    LogService.logMessage("The number of classes in dataset: "
      + chunkLearning.numClasses, 1);
    // Use the SVM only for filtering
    String dataSetFile = null;
    SupervisedLearner paumLearner = MultiClassLearning.obtainLearnerFromName(
      "SVMLibSvmJava", "-c 1.0 -t 0 -m 100 -tau 1.0 ", dataSetFile);
    paumLearner
      .setLearnerExecutable(engineSettings.learnerSettings.executableTraining);
    paumLearner
      .setLearnerParams(engineSettings.learnerSettings.paramsOfLearning);
    LogService.logMessage("The learners: " + paumLearner.getLearnerName(), 1);
    // training
    // if number of classes is zero, not filtering at all
    if(chunkLearning.numClasses == 0) {
      LogService.logMessage(
        "!Cannot do the filtering, because there is no positive examples"
          + "in the training data!", 1);
      if(LogService.minVerbosityLevel > 0)
        System.out
          .println("!Cannot do the filtering, because there is no positive examples"
            + "in the training data!");
      return;
    }
    //  using different method for one thread or multithread
    if(engineSettings.numThreadUsed>1) {//using thread
      chunkLearning.training(paumLearner, modelFile);
      // applying the learning model to training example and get the
      // confidence score for each example
      chunkLearning.apply(paumLearner, modelFile);
    } else { //not using thread
      chunkLearning.trainingNoThread(paumLearner, modelFile, isUsingTempDataFile, tempDataFile);
      chunkLearning.applyNoThread(paumLearner, modelFile);
    }

    // Store the scores of negative examples.
    float[] scoresNegB = new float[numNeg];
    float[] scoresNeg = new float[numNeg];
    int kk = 0;
    for(int i = 0; i < labelsFVDocB.length; ++i) {
      for(int j = 0; j < labelsFVDocB[i].multiLabels.length; ++j)
        if(labelsFVDocB[i].multiLabels[j].num == 0) {
          // System.out.println("(i, j, kk)="+i+","+j+","+kk+"*");
          scoresNeg[kk++] = chunkLearning.dataFVinDoc.labelsFVDoc[i].multiLabels[j].probs[0];
        }
    }
    // If want to remove the negative that are close to positive one,
    // reverse the scores.
    if(engineSettings.filteringNear) for(int i = 0; i < numNeg; ++i)
      scoresNeg[i] = -scoresNeg[i];
    // Back up the score before sorting
    for(int i = 0; i < numNeg; ++i)
      scoresNegB[i] = scoresNeg[i];
    // Sort those scores
    Arrays.sort(scoresNeg);
    // int index = numNeg -
    // (int)Math.floor(numNeg*engineSettings.filteringRatio);
    int index = (int)Math.floor(numNeg * engineSettings.filteringRatio);
    if(index >= numNeg) index = numNeg - 1;
    if(index < 0) index = 0;
    float thrFiltering = scoresNeg[index];
    boolean[] isFiltered = new boolean[numNeg];
    for(int i = 0; i < numNeg; ++i)
      if(scoresNegB[i] < thrFiltering)
        isFiltered[i] = true;
      else isFiltered[i] = false;
    // Write the filtered data into the data file
    BufferedWriter out;
    try {
      out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
        dataFile), "UTF-8"));
      numNeg = 0;
      for(int i = 0; i < labelsFVDocB.length; ++i) {
        int kk1 = 0;
        int numK = 0; // num of instances in the doc to be kept
        for(int j = 0; j < labelsFVDocB[i].multiLabels.length; ++j)
          if(labelsFVDocB[i].multiLabels[j].num == 0) {
            if(!isFiltered[numNeg + kk1]) ++numK;
            ++kk1;
          } else {
            ++numK;
          }
        out.write(i + ConstantParameters.ITEMSEPARATOR + numK
          + ConstantParameters.ITEMSEPARATOR
          + chunkLearning.dataFVinDoc.trainingFVinDoc[i].docId);
        out.newLine();
        kk1 = 0;
        for(int j = 0; j < labelsFVDocB[i].multiLabels.length; ++j) {
          if(labelsFVDocB[i].multiLabels[j].num > 0
            || !isFiltered[numNeg + kk1]) {
            StringBuffer line = new StringBuffer();
            line.append(j + ConstantParameters.ITEMSEPARATOR
              + labelsFVDocB[i].multiLabels[j].num);
            for(int j1 = 0; j1 < labelsFVDocB[i].multiLabels[j].num; ++j1) {
              line.append(ConstantParameters.ITEMSEPARATOR
                + labelsFVDocB[i].multiLabels[j].labels[j1]);
            }
            SparseFeatureVector fv = chunkLearning.dataFVinDoc.trainingFVinDoc[i].fvs[j];
            for(int j1 = 0; j1 < fv.len; ++j1)
              line.append(ConstantParameters.ITEMSEPARATOR + fv.nodes[j1].index
                + ConstantParameters.INDEXVALUESEPARATOR + fv.nodes[j1].value);
            out.write(line.toString());
            out.newLine();
          }
          if(labelsFVDocB[i].multiLabels[j].num == 0) ++kk1;
        }
        numNeg += kk1;
      }
      out.flush();
      out.close();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Determining the type of the learner, it is from Weka or not.
   */
  public static int obtainLearnerType(String learnerName) throws GateException {
    if(learnerName.equals("SVMLibSvmJava") || learnerName.equals("C4.5Weka")
      || learnerName.equals("KNNWeka") || learnerName.equals("NaiveBayesWeka")
      || learnerName.equals("PAUM") || learnerName.equals("SVMExec") ||
      learnerName.equals("PAUMExec")) {
      if(learnerName.endsWith("Weka")) {
        return 1;
      } else {
        return 2;
      }
    } else {
      throw new GateException("The learning engine named as \"" + learnerName
        + "\" is not defined!");
    }
  }

  /** Convert and view the SVM models in term of NLP features. */
  public void viewSVMmodelsInNLPFeatures(File modelFile,
    LearningEngineSettings engineSettings) {
    try {
      // First decide if the learning model is linear one or not
      if(!(engineSettings.learnerSettings.learnerName
        .equalsIgnoreCase("SVMLibSvmJava") && (!engineSettings.learnerSettings.paramsOfLearning
        .contains("-t ") || engineSettings.learnerSettings.paramsOfLearning
        .contains("-t 0")))) {
        System.out
          .println("According to the configuration file, "
            + "the model file is not linear svm model. Hence it cannot be displayed in the "
            + "current implementation!");
        return;
      }
      int numP0, numN0;
      boolean surroundMode;
      numP0 = engineSettings.numPosSVMModel;
      numN0 = engineSettings.numNegSVMModel;
      surroundMode = engineSettings.surround;
      // Open the mode file and read the model
      Map<Integer,String> featId2Form = new HashMap<Integer,String>();
      for(String obj : featuresList.featuresList.keySet()) {
        int k =  featuresList.featuresList.get(obj).intValue();
        featId2Form.put(k, obj);
      }
      // Need some methods from MultiClassLearning
      MultiClassLearning mulL = new MultiClassLearning();
      // Open the mode file and read the model
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
          mulL.upgradeSingleFileModelToDirectory(modelFile);
        } else {
          throw new IOException("Unrecognised model file format for file "
            + modelFile);
        }
      }
      if(!modelFile.exists()) { throw new IllegalStateException(
        "Model directory " + modelFile + " does not exist");
      }
      //Read the training meta information from the meta data file
      // include the total number of features and number of tags (numClasses)
      File metaDataFile = new File(modelFile, ConstantParameters.FILENAMEOFModelMetaData);
      BufferedReader metaDataBuff = new BufferedReader(new InputStreamReader(
              new FileInputStream(metaDataFile), "UTF-8"));
      int totalNumFeatures;
      String learnerNameFromModel = null;
      // note that reading the training meta data also read the number of class
      // in the model, e.g. changing the numClasses.
      totalNumFeatures = mulL.ReadTrainingMetaData(metaDataBuff,
        learnerNameFromModel);
      metaDataBuff.close();
      // for each class
      int classIndex = 1;
      for(int iClass = 0; iClass < mulL.numClasses; ++iClass) {
        @SuppressWarnings("unused")
        float b;
        float[] w = new float[totalNumFeatures];
        //Read the model file
        final File thisClassModelFile = new File(modelFile, String.format(
          ConstantParameters.FILENAMEOFPerClassModel, Integer
            .valueOf(classIndex++)));
        BufferedReader modelBuff = new BomStrippingInputStreamReader(
            new FileInputStream(thisClassModelFile), "UTF-8");
        //Read the header line
        @SuppressWarnings("unused")
        String items[] = modelBuff.readLine().split(" ");
        // Get the weight vector
        b = SvmLibSVM.readWeightVectorFromFile(modelBuff, w);
        modelBuff.close();
        int numT;
        int numP;
        if(numP0 > 0) {
          numT = 0;
          for(int i = 1; i < totalNumFeatures; ++i)
            if(w[i] >= 0) ++numT;
          float[] wP = new float[numT];
          int[] indexW = new int[numT];
          numT = 0;
          for(int i = 1; i < totalNumFeatures; ++i)
            if(w[i] >= 0) {
              wP[numT] = w[i];
              indexW[numT] = i;
              ++numT;
            }
          if(numP0 > numT)
            numP = numT;
          else numP = numP0;
          int[] indexSort = new int[numP];
          sortFloatAscIndex(wP, indexSort, numT, numP);
          String st1 = null;
          if(surroundMode) {
            st1 = labelsAndId.id2Label.get(
              iClass / 2 + 1).toString();
            if(iClass % 2 == 0)
              st1 += "-StartToken";
            else st1 += "-LastToken";
          } else {
            st1 = labelsAndId.id2Label.get(iClass + 1)
              .toString();
          }
          System.out.println("The " + numP
            + " most significiant positive NLP feature for class:   " + st1
            + ":");
          for(int i = 0; i < numP; ++i) {
            int k1 = indexW[indexSort[i]]
              / (int)ConstantParameters.MAXIMUMFEATURES;
            int k2 = indexW[indexSort[i]]
              % (int)ConstantParameters.MAXIMUMFEATURES;
            String st = null;
            if(k1 == 0) {
              st = "0";
            } else {
              if(k1 <= maxNegPositionTotal)
                st = "-" + k1;
              else {
                k1 -= maxNegPositionTotal;
                st = "+" + k1;
              }
            }
            st += " " + featId2Form.get(k2).toString();
            System.out.println(i + ": " + st + " -- " + wP[indexSort[i]]);
          }
        }
        // For the negative weight
        if(numN0 > 0) {
          System.out.println("Negative weight:");
          numT = 0;
          for(int i = 0; i < totalNumFeatures; ++i)
            if(w[i] < 0) ++numT;
          float[] wN = new float[numT];
          int[] indexWN = new int[numT];
          numT = 0;
          for(int i = 0; i < totalNumFeatures; ++i)
            if(w[i] < 0) {
              wN[numT] = -w[i];
              indexWN[numT] = i;
              ++numT;
            }
          if(numN0 > numT)
            numP = numT;
          else numP = numN0;
          int[] indexSortN = new int[numP];
          sortFloatAscIndex(wN, indexSortN, numT, numP);
          String st1 = null;
          if(surroundMode) {
            st1 = labelsAndId.id2Label.get(
              iClass / 2 + 1).toString();
            if(iClass % 2 == 0)
              st1 += "-StartToken";
            else st1 += "-LastToken";
          } else {
            st1 = labelsAndId.id2Label.get(iClass + 1)
              .toString();
          }
          System.out.println("The " + numP
            + " most significiant negative NLP feature for class:   " + st1
            + ":");
          for(int i = 0; i < numP; ++i) {
            int k1 = indexWN[indexSortN[i]]
              / (int)ConstantParameters.MAXIMUMFEATURES;
            int k2 = indexWN[indexSortN[i]]
              % (int)ConstantParameters.MAXIMUMFEATURES;
            String st = null;
            if(k1 == 0) {
              st = "0";
            } else {
              if(k1 <= maxNegPositionTotal)
                st = "-" + k1;
              else {
                k1 -= maxNegPositionTotal;
                st = "+" + k1;
              }
            }
            st += " " + featId2Form.get(k2).toString();
            float wN0 = -wN[indexSortN[i]];
            System.out.println(i + ": " + st + " -- " + wN0);
          }
        }
      }
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Get the biggest numK components from an array. wP: the array storing the
   * numbers for sorting, indexSort stores the indices of the numK biggest
   * numbers numT, the number of those numbers.
   */
  public static void sortFloatAscIndex(float[] wP, int[] indexSort, int numT,
    int numK) {
    int i, j, k, j1;
    float[] rp1 = new float[numK];
    float one;
    if(numT <= 0) return;
    rp1[0] = wP[0];
    indexSort[0] = 0;
    for(i = 1; i < numT; ++i) {
      one = wP[i];
      if(i >= numK)
        j1 = numK - 1;
      else j1 = i - 1;
      k = j1;
      for(j = j1; j > -1; --j) {
        if(one > rp1[j])
          --k;
        else break;
      }
      if(i < numK) {
        rp1[i] = rp1[i - 1];
        indexSort[i] = indexSort[i - 1];
      }
      ++k;
      for(j = j1; j > k; --j) {
        rp1[j] = rp1[j - 1];
        indexSort[j] = indexSort[j - 1];
      }
      if(k < numK) {
        rp1[k] = one;
        indexSort[k] = i;
      }
    }
  }

  // /////// Benchmarkable ////////////////
  private String parentBenchmarkID;
  private String benchmarkID;

  /**
   * Returns the benchmark ID of the parent of this resource.
   *
   * @return
   */
  public String getParentBenchmarkId() {
    return this.parentBenchmarkID;
  }

  /**
   * Returns the benchmark ID of this resource.
   *
   * @return
   */
  public String getBenchmarkId() {
    if(this.benchmarkID == null) {
      benchmarkID = "LightWeightLearningApi";
    }
    return this.benchmarkID;
  }

  /**
   * Given an ID of the parent resource, this method is responsible for
   * producing the Benchmark ID, unique to this resource.
   *
   * @param parentID
   */
  public void createBenchmarkId(String parentID) {
    parentBenchmarkID = parentID;
    benchmarkID = Benchmark.createBenchmarkId("LightWeightLearningApi",
      parentID);
  }

  /**
   * This method sets the benchmarkID for this resource.
   *
   * @param benchmarkID
   */
  public void setParentBenchmarkId(String benchmarkID) {
    parentBenchmarkID = benchmarkID;
  }

  /**
   * Returns the logger object being used by this resource.
   *
   * @return
   */
  public Logger getLogger() {
    return Benchmark.logger;
  }

  public void setBenchmarkId(String arg0) {
    // stub
  }
}
