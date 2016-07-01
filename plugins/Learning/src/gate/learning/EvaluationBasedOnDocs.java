/*
 *  EvaluationBasedOnDocs.java
 *
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: EvaluationBasedOnDocs.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Corpus;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.creole.ResourceInstantiationException;
import gate.util.AnnotationDiffer;
import gate.util.BomStrippingInputStreamReader;
import gate.util.GateException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Do evaluation by splitting the documents into training and testing datasets.
 * Two methods of splitting are implemented, namely k-fold and hold-out test.
 */
public class EvaluationBasedOnDocs {
  /** Corpus referring to the corpus used as data. */
  Corpus corpusOn;
  /** Number of documents in the corpus. */
  int numDoc;
  /**
   * Showing if one document used for training or testing in one evaluation.
   */
  boolean[] isUsedForTraining;
  /** The sub-directory for storing the data file produced by ML api. */
  File wdResults;
  /** Name of the annotation set as input. */
  String inputASName;
  /** Storing the macro averaged overall F-measure results of the evaluation. */
  public EvaluationMeasuresComputation macroMeasuresOfResults = new EvaluationMeasuresComputation();
  public List<EvaluationMeasuresComputation> macroMeasuresOfResultsPerRun =
          new ArrayList<EvaluationMeasuresComputation>();
  /** Storing the macro averaged results of every label. */
  Map<String,EvaluationMeasuresComputation> labels2MMR = new HashMap<String,EvaluationMeasuresComputation>();
  /**
   * Label to number of the runs having that label, used for macro-averaged for
   * each label.
   */
  Map<String,Integer> labels2RunsNum = new HashMap<String,Integer>();
  /**
   * Label to number of the instances with that label, averaged over the number
   * of runs for that label.
   */
  Map<String,Integer> labels2InstNum = new HashMap<String,Integer>();

  /** Constructor. */
  public EvaluationBasedOnDocs(Corpus corpus, File wdRes, String inputAsN) {
    corpusOn = corpus;
    numDoc = corpus.size();
    isUsedForTraining = new boolean[numDoc];
    wdResults = wdRes;
    inputASName = inputAsN;
  }

  /**
   * Main method for evaluation.
   *
   * @throws IOException
   */
  public void evaluation(LearningEngineSettings learningSettings,
    LightWeightLearningApi lightWeightApi) throws GateException, IOException {
    // first obtain the feature vectors from documents
    /*
     * BufferedWriter outNLPFeatures=null; BufferedReader inNLPFeatures = null;
     * BufferedWriter outFeatureVectors = null; boolean isTraining = true;
     * outNLPFeatures = new BufferedWriter(new OutputStreamWriter(new
     * FileOutputStream(new File(wdResults,
     * ConstantParameters.FILENAMEOFNLPFeaturesData)), "UTF-8")); for(int i = 0;
     * i < corpusOn.size(); ++i) { Document toProcess=
     * (Document)corpusOn.get(i);
     * lightWeightApi.annotations2NLPFeatures(toProcess, i, outNLPFeatures,
     * isTraining, learningSettings); if(toProcess.getDataStore() != null &&
     * corpusOn.getDataStore() != null) Factory.deleteResource(toProcess);
     * //++numDoc; } outNLPFeatures.flush(); outNLPFeatures.close();
     * lightWeightApi.finishFVs(wdResults, numDoc, isTraining,
     * learningSettings); // Open the normal NLP feature file. inNLPFeatures =
     * new BufferedReader(new InputStreamReader(new FileInputStream(new
     * File(wdResults, ConstantParameters.FILENAMEOFNLPFeaturesData)),
     * "UTF-8")); outFeatureVectors = new BufferedWriter(new
     * OutputStreamWriter(new FileOutputStream( new
     * File(wdResults,ConstantParameters.FILENAMEOFFeatureVectorTotalData)),
     * "UTF-8")); lightWeightApi.nlpfeatures2FVs(wdResults, inNLPFeatures,
     * outFeatureVectors, numDoc, isTraining, learningSettings);
     * inNLPFeatures.close(); outFeatureVectors.flush();
     * outFeatureVectors.close();
     */
    // outFeatureVectors.close();
    // k-fold
    if(learningSettings.evaluationconfig.mode == EvaluationConfiguration.kfold)
      kfoldEval(learningSettings, lightWeightApi);
    // Hold-out testing
    else if(learningSettings.evaluationconfig.mode == EvaluationConfiguration.split)
      holdoutEval(learningSettings, lightWeightApi);
    else throw new GateException("The evaluation configuration mode as "
      + learningSettings.evaluationconfig.mode + " is not implemented!");
    StringBuffer logMes = new StringBuffer();
    logMes.append("\n*** Averaged results for each label over "
      + learningSettings.evaluationconfig.kk + " runs as:\n");
    logMes.append(printFmeasureForEachLabel(labels2InstNum, labels2MMR));
    logMes.append("\nOverall results as:\n");
    logMes.append(macroMeasuresOfResults.printResults());
    LogService.logMessage(logMes.toString(), 1);
    if(LogService.minVerbosityLevel > 0) System.out.println(logMes);
  }

  /** K-fold evaluation. */
  public void kfoldEval(LearningEngineSettings learningSettings,
    LightWeightLearningApi lightWeightApi) throws GateException {
    int k = learningSettings.evaluationconfig.kk;
    LogService.logMessage("K-fold evaluation: k=" + k, 1);
    int lenPerFold = (new Double(Math.floor((double)numDoc / k))).intValue();
    if(lenPerFold < 1) lenPerFold = 1;
    int beginIndex, endIndex;
    if(LogService.minVerbosityLevel > 0) {
      System.out.println("Kfold k=" + k + ", numDoc="
        + numDoc + ", len=" + lenPerFold + ".");
    }
    LogService.logMessage("Kfold k=" + k + ", numDoc="
      + numDoc + ", len=" + lenPerFold + ".", 1);
    for(int nr = 0; nr < k; ++nr) {
      EvaluationMeasuresComputation measuresOfResults = new EvaluationMeasuresComputation();
      // Label to measure of result of the label
      Map<String,EvaluationMeasuresComputation> labels2MR = new HashMap<String,EvaluationMeasuresComputation>();
      beginIndex = nr * lenPerFold;
      endIndex = (nr + 1) * lenPerFold;
      if(endIndex > numDoc) endIndex = numDoc;
      if(beginIndex > endIndex) beginIndex = endIndex;
      int i;
      for(i = 0; i < beginIndex; ++i)
        isUsedForTraining[i] = true;
      for(i = beginIndex; i < endIndex; ++i)
        isUsedForTraining[i] = false;
      for(i = endIndex; i < numDoc; ++i)
        isUsedForTraining[i] = true;
      StringBuffer logMes = new StringBuffer();
      int nr0 = nr + 1;
      logMes.append("\n*** Fold " + nr0 + "\n");
      logMes.append("Number of docs for training: "
        + (numDoc - endIndex + beginIndex) + "\n");
      int ik = 0;
      for(i = 0; i < numDoc; ++i) {
        if(isUsedForTraining[i]) {
          ++ik;
          Document toProcess = corpusOn.get(i);
          logMes
            .append(ik + " " + toProcess.getName() + "\n");
          if(toProcess.getDataStore() != null
            && corpusOn.getDataStore() != null)
            Factory.deleteResource(toProcess);
        }
      }
      logMes.append("Number of docs for application: "
        + (endIndex - beginIndex) + "\n");
      ik = 0;
      for(i = 0; i < numDoc; ++i) {
        if(!isUsedForTraining[i]) {
          ++ik;
          Document toProcess = corpusOn.get(i);
          logMes
            .append(ik + " " + toProcess.getName() + "\n");
          if(toProcess.getDataStore() != null
            && corpusOn.getDataStore() != null)
            Factory.deleteResource(toProcess);
        }
      }
      LogService.logMessage(logMes.toString(), 1);
      if(LogService.minVerbosityLevel > 1) System.out.println(logMes);
      // call the training or application
      oneRun(learningSettings, lightWeightApi, isUsedForTraining,
        measuresOfResults, labels2MR, labels2InstNum);
      // Add to the macro averaged figures
      add2MacroMeasure(measuresOfResults, labels2MR, macroMeasuresOfResults,
        labels2MMR, labels2RunsNum);
      // calculate fold-specific measures
      EvaluationMeasuresComputation foldMeasures = 
              createRunMeasures(measuresOfResults, labels2MR);
      macroMeasuresOfResultsPerRun.add(foldMeasures);
      // show fold-specific measures if verbosity is not 0
      if(LogService.minVerbosityLevel > 0) {
        System.out.println("XVAL Fold "+nr+": "+foldMeasures.printResults());
      }
    }
    macroMeasuresOfResults.macroAverage(k);
    for(String obj : labels2MMR.keySet()) {
      int num = labels2RunsNum.get(obj);
      labels2MMR.get(obj).macroAverage(num);
      num = (int)(labels2InstNum.get(obj).floatValue() / num);
      labels2InstNum.put(obj, num);
    }
  }
  
  private EvaluationMeasuresComputation createRunMeasures(
          EvaluationMeasuresComputation curResults, Map<String,EvaluationMeasuresComputation> labels2MR) { 
    
      Map<String,EvaluationMeasuresComputation> foldLabels2MMR = new HashMap<String,EvaluationMeasuresComputation>();
      Map<String,Integer> foldLabels2RunsNum = new HashMap<String,Integer>();
      Map<String,Integer> foldLabels2InstNum = new HashMap<String,Integer>();
      foldLabels2InstNum.putAll(labels2InstNum);
      EvaluationMeasuresComputation retResults = 
              new EvaluationMeasuresComputation();
      add2MacroMeasure(curResults, labels2MR, retResults,
              foldLabels2MMR, foldLabels2RunsNum);
      for(String obj : foldLabels2MMR.keySet()) {
        int num = foldLabels2RunsNum.get(obj);
        foldLabels2MMR.get(obj).macroAverage(num);
        num = (int)(foldLabels2InstNum.get(obj).floatValue() / num);
        foldLabels2InstNum.put(obj, num);
      }
      return retResults;
  }

  /** Hold-out testing. */
  public void holdoutEval(LearningEngineSettings learningSettings,
    LightWeightLearningApi lightWeightApi) throws GateException {
    int k = learningSettings.evaluationconfig.kk;
    LogService.logMessage("Hold-out test: runs=" + k
      + ", ratio of training docs is "
      + learningSettings.evaluationconfig.ratio, 1);
    int trainingNum = (new Double(Math
      .floor((numDoc * learningSettings.evaluationconfig.ratio))).intValue());
    if(trainingNum > numDoc) trainingNum = numDoc;
    LogService.logMessage("Split, k=" + k + ", trainingNum="
      + trainingNum + ".", 1);
    if(LogService.minVerbosityLevel > 0) {
      System.out.println("Hold-out test: runs=" + k
        + ", ratio of training docs is "
        + learningSettings.evaluationconfig.ratio);
      System.out.println("Split, k=" + k + ", trainingNum="
        + trainingNum + ".");
    }
    int testNum = numDoc - trainingNum;
    Random randGenerator = new Random(1000);
    for(int nr = 0; nr < k; ++nr) {
      EvaluationMeasuresComputation measuresOfResults = new EvaluationMeasuresComputation();
      // Label to measure of result of the label
      Map<String,EvaluationMeasuresComputation> labels2MR = new HashMap<String,EvaluationMeasuresComputation>();
      // Select the training examples randomly from the data
      int[] indexRand = new int[testNum];
      for(int i = 0; i < testNum; ++i) {
        int newNum = 0;
        boolean isDuplicate = true;
        int maxNumSel = 0;
        while(isDuplicate && maxNumSel < 1000) {
          newNum = randGenerator.nextInt(numDoc);
          isDuplicate = false;
          for(int j = 0; j < i; ++j)
            if(indexRand[j] == newNum) {
              isDuplicate = true;
              break;
            }
        }
        if(isDuplicate) {
          // If cannot select a non-duplicate index in
          // 1000 times, selected the one
          boolean isOk = false;
          for(int j = 0; j < numDoc; ++j) {
            for(int j1 = 0; j1 < i; ++j1)
              if(indexRand[j1] != j) {
                newNum = j;
                isOk = true;
                break;
              }
            if(isOk) break;
          }
        }
        if(LogService.minVerbosityLevel > 1) {
          System.out.println("i=" + i + ", newNum="
            + newNum);
        }
        indexRand[i] = newNum;
      }
      for(int i = 0; i < numDoc; ++i)
        isUsedForTraining[i] = true;
      for(int i = 0; i < testNum; ++i)
        isUsedForTraining[indexRand[i]] = false;
      StringBuffer logMes = new StringBuffer();
      int nr0 = nr + 1;
      logMes.append("\n*** Run " + nr0 + "\n");
      logMes.append("Number of docs for training: " + (numDoc - testNum)
        + "\n");
      int ik = 0;
      for(int i = 0; i < numDoc; ++i) {
        if(isUsedForTraining[i]) {
          ++ik;
          Document toProcess = corpusOn.get(i);
          logMes
            .append(ik + " " + toProcess.getName() + "\n");
          if(toProcess.getDataStore() != null
            && corpusOn.getDataStore() != null)
            Factory.deleteResource(toProcess);
        }
      }
      logMes.append("Number of docs for application: " + testNum + "\n");
      ik = 0;
      for(int i = 0; i < numDoc; ++i) {
        if(!isUsedForTraining[i]) {
          ++ik;
          Document toProcess = corpusOn.get(i);
          logMes
            .append(ik + " " + toProcess.getName() + "\n");
          if(toProcess.getDataStore() != null
            && corpusOn.getDataStore() != null)
            Factory.deleteResource(toProcess);
        }
      }
      LogService.logMessage(logMes.toString(), 1);
      if(LogService.minVerbosityLevel > 1) System.out.println(logMes);
      // One run, call the training and application and do evaluation
      oneRun(learningSettings, lightWeightApi, isUsedForTraining,
        measuresOfResults, labels2MR, labels2InstNum);
      // Add to the macro averaged figures
      add2MacroMeasure(measuresOfResults, labels2MR, macroMeasuresOfResults,
        labels2MMR, labels2RunsNum);
      // calculate fold-specific measures
      EvaluationMeasuresComputation foldMeasures = 
              createRunMeasures(measuresOfResults, labels2MR);
      macroMeasuresOfResultsPerRun.add(foldMeasures);
      // show fold-specific measures if verbosity is not 0
      if(LogService.minVerbosityLevel > 0) {
        System.out.println("HOLDOUT Fold "+nr+": "+foldMeasures.printResults());
      }
    }
    macroMeasuresOfResults.macroAverage(k);
    for(String obj : labels2MMR.keySet()) {
      int num = labels2RunsNum.get(obj);
      labels2MMR.get(obj).macroAverage(num);
      // Averaged the number of instances for all the runs
      num = (int)(labels2InstNum.get(obj).floatValue() / num);
      labels2InstNum.put(obj, num);
    }
  }

  /**
   * One run of the evaluation: training, testing and measuring results.
   *
   * @throws
   * @throws UnsupportedEncodingException
   */
  private void oneRun(LearningEngineSettings learningSettings,
    LightWeightLearningApi lightWeightApi, boolean isUsedForTraining[],
    EvaluationMeasuresComputation measuresOfResults, Map<String,EvaluationMeasuresComputation> labels2MR,
    Map<String,Integer> labels2InstNum) throws GateException {
    // first learning using the document for training
    // empty the data file
    emptyDatafile(wdResults, true);
    lightWeightApi.labelsAndId.clearAllData();
    lightWeightApi.featuresList.clearAllData();
    boolean isTraining = true;
    int numDoc = 0;
    BufferedWriter outNLPFeatures = null;
    BufferedReader inNLPFeatures = null;
    BufferedWriter outFeatureVectors = null;
    try {
      outNLPFeatures = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFNLPFeaturesData)), "UTF-8"));
      for(int i = 0; i < corpusOn.size(); ++i)
        if(isUsedForTraining[i]) {
          Document toProcess = corpusOn.get(i);
          lightWeightApi.annotations2NLPFeatures(toProcess, numDoc,
            outNLPFeatures, isTraining, learningSettings);
          if(toProcess.getDataStore() != null
            && corpusOn.getDataStore() != null)
            Factory.deleteResource(toProcess);
          ++numDoc;
        }
      outNLPFeatures.flush();
      outNLPFeatures.close();
      lightWeightApi.finishFVs(wdResults, numDoc, isTraining, learningSettings);
      // Open the normal NLP feature file.
      inNLPFeatures = new BomStrippingInputStreamReader(
        new FileInputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFNLPFeaturesData)), "UTF-8");
      outFeatureVectors = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFFeatureVectorData)), "UTF-8"));
      lightWeightApi.nlpfeatures2FVs(wdResults, inNLPFeatures,
        outFeatureVectors, numDoc, isTraining, learningSettings);
      inNLPFeatures.close();
      outFeatureVectors.flush();
      outFeatureVectors.close();
      // outFeatureVectors.close();
      // read the fv data from total file and write them into fv files for
      // training and application, respectively
      /*
       * BufferedReader inFVs = new BufferedReader(new InputStreamReader(new
       * FileInputStream(new File(wdResults,
       * ConstantParameters.FILENAMEOFFeatureVectorTotalData)), "UTF-8"));
       * BufferedWriter outFVs = new BufferedWriter(new OutputStreamWriter(new
       * FileOutputStream( new
       * File(wdResults,ConstantParameters.FILENAMEOFFeatureVectorData)),
       * "UTF-8")); BufferedWriter outFVsApp = new BufferedWriter(new
       * OutputStreamWriter(new FileOutputStream( new
       * File(wdResults,ConstantParameters.FILENAMEOFFeatureVectorDataApp)),
       * "UTF-8")); for(int i = 0; i < corpusOn.size(); ++i) { String str =
       * inFVs.readLine(); int numLines =
       * Integer.parseInt(str.substring(str.indexOf(ConstantParameters.ITEMSEPARATOR)+1,
       * str.lastIndexOf(ConstantParameters.ITEMSEPARATOR)));
       * if(isUsedForTraining[i]) { outFVs.append(str); outFVs.newLine();
       * for(int j=0; j<numLines; ++j) { outFVs.append(inFVs.readLine());
       * outFVs.newLine(); } ++numDoc; } else { outFVsApp.append(str);
       * outFVsApp.newLine(); for(int j=0; j<numLines; ++j) {
       * outFVsApp.append(inFVs.readLine()); outFVsApp.newLine(); } } }
       * inFVs.close(); outFVs.flush(); outFVs.close(); outFVsApp.flush();
       * outFVsApp.close();
       */
      // if fitering the training data
      if(learningSettings.fiteringTrainingData
        && learningSettings.filteringRatio > 0.0)
        lightWeightApi.FilteringNegativeInstsInJava(numDoc, learningSettings);
      // lightWeightApi.trainDirect();
      lightWeightApi.trainingJava(numDoc, learningSettings);
      // then application to the test set
      // First we empty the NLP feature file and feature vector file; but not
      // the list files.
      emptyDatafile(wdResults, false);
      // We have to use two class types for the evaluation purpose
      String classTypeOriginal = null;
      String classTypeTest = null;
      String classFeature = null;
      classTypeOriginal = learningSettings.datasetDefinition
        .getClassAttribute().getType();
      classTypeTest = classTypeOriginal.concat("Test");
      classFeature = learningSettings.datasetDefinition.getClassAttribute()
        .getFeature();
      learningSettings.datasetDefinition.getClassAttribute().setType(
        classTypeTest);
      if(LogService.minVerbosityLevel > 1)
        System.out.println("classType=" + classTypeOriginal + ", testType="
          + classTypeTest + ".");
      isTraining = false;
      // numDoc = isUsedForTraining.length - numDoc;
      numDoc = 0;
      outNLPFeatures = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFNLPFeaturesData)), "UTF-8"));
      for(int i = 0; i < corpusOn.size(); ++i)
        if(!isUsedForTraining[i]) {
          Document toProcess = corpusOn.get(i);
          lightWeightApi.annotations2NLPFeatures(toProcess,
            numDoc, outNLPFeatures, isTraining, learningSettings);
          ++numDoc;
          if(toProcess.getDataStore() != null
            && corpusOn.getDataStore() != null) {
            corpusOn.getDataStore().sync(corpusOn);
            Factory.deleteResource(toProcess);
          }
        }
      outNLPFeatures.flush();
      outNLPFeatures.close();
      lightWeightApi.finishFVs(wdResults, numDoc, isTraining, learningSettings);
      inNLPFeatures = new BomStrippingInputStreamReader(
        new FileInputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFNLPFeaturesData)), "UTF-8");
      outFeatureVectors = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(new File(wdResults,
          ConstantParameters.FILENAMEOFFeatureVectorDataApp)), "UTF-8"));
      lightWeightApi.nlpfeatures2FVs(wdResults, inNLPFeatures,
        outFeatureVectors, numDoc, isTraining, learningSettings);
      inNLPFeatures.close();
      outFeatureVectors.flush();
      outFeatureVectors.close();
      // lightWeightApi.finishDocAnnotation();
      Corpus corpusTest;
      corpusTest = Factory.newCorpus("testCorpus");
      if(corpusOn.getDataStore() != null) {
        Corpus corpusTestTemp = (Corpus)corpusOn.getDataStore().adopt(corpusTest);
        Factory.deleteResource(corpusTest);
        corpusTest = corpusTestTemp;
        corpusTest.getDataStore().sync(corpusTest);
      }
      numDoc = 0;
      for(int i = 0; i < corpusOn.size(); ++i)
        if(!isUsedForTraining[i]) {
          Document d = corpusOn.get(i);
          corpusTest.add(d);
          if(corpusTest.getDataStore() != null) {
            corpusTest.getDataStore().sync(corpusTest);
            Factory.deleteResource(d);
          }
          ++numDoc;
        }
      lightWeightApi.applyModelInJava(corpusTest, 0, corpusTest.size(),
        classTypeTest, learningSettings, wdResults);
      //unload the documents in the test corpus from memory
      /*for(int i=0; i<corpusTest.size(); ++i) {
        Document toProcess = (Document)corpusOn.get(i);
        if(corpusOn.getDataStore() != null) {
          corpusOn.getDataStore().sync(corpusOn);
          Factory.deleteResource(toProcess);
        }
      }*/
      corpusTest.clear();
      Factory.deleteResource(corpusTest);
      if(corpusTest.getDataStore() != null) {
        corpusTest.getDataStore().delete(corpusTest.getClass().getName(), corpusTest.getLRPersistenceId());
      }

      // Do the evaluation on test using the AnnotationDiff
      // First get all the labels in the training data,
      // so that we can do evaluation on each single label
      // as well as on all labels
      Map<String,Integer> uniqueLabels = new HashMap<String,Integer>();
      for(int i = 0; i < corpusOn.size(); ++i)
        if(isUsedForTraining[i]) {
          Document toProcess = corpusOn.get(i);
          AnnotationSet keyAnns = getInputAS(toProcess).get(classTypeOriginal);
          for(Object obj : keyAnns) {
            if(((Annotation)obj).getFeatures().get(classFeature) != null) {
              String label = ((Annotation)obj).getFeatures().get(classFeature)
                .toString();
              if(uniqueLabels.containsKey(label))
                uniqueLabels.put(label, uniqueLabels
                  .get(label) + 1);
              else uniqueLabels.put(label, 1); //MAG was "1" instead of 1
            }
          }
          if(toProcess.getDataStore() != null
            && corpusOn.getDataStore() != null) {
            corpusOn.getDataStore().sync(corpusOn);
            Factory.deleteResource(toProcess);
          }
        }
      // Then create one evaluationMeasure object for each label
      for(String obj : uniqueLabels.keySet()) {
        EvaluationMeasuresComputation emc = new EvaluationMeasuresComputation();
        labels2MR.put(obj, emc);
      }
      // Copy the number of instances for each label into the macro one
      for(String obj : uniqueLabels.keySet()) {
        if(labels2InstNum.containsKey(obj)) {
          int num = uniqueLabels.get(obj);//new Integer(uniqueLabels.get(obj).toString()).intValue();
          labels2InstNum.put(obj, labels2InstNum.get(obj) + num);
        } else labels2InstNum.put(obj, uniqueLabels.get(obj));
      }
      // Do the evaluation on the test set
      if(learningSettings.datasetDefinition.dataType == DataSetDefinition.RelationData) {
        // For relation type, we cannot use the evaluation method AnnDiff
        // of Gate
        AttributeRelation relAttr = (AttributeRelation)learningSettings.datasetDefinition
          .getClassAttribute();
        String arg1F = relAttr.getArg1();
        String arg2F = relAttr.getArg2();
        for(int i = 0; i < corpusOn.size(); ++i) {
          if(!isUsedForTraining[i]) {
            Document toProcess = corpusOn.get(i);
            evaluateAnnotationsRel(toProcess,
              classTypeOriginal, classTypeTest, classFeature, arg1F, arg2F,
              uniqueLabels.keySet(), labels2MR);
            if(toProcess.getDataStore() != null
              && corpusOn.getDataStore() != null) {
              corpusOn.getDataStore().sync(corpusOn);
              Factory.deleteResource(toProcess);
            }
          }
        }
      } else {
        /*
         * for(int i=0; i<corpusOn.size(); ++i) { if(! isUsedForTraining[i]) {
         * evaluateAnnotations((Document)corpusOn.get(i), classTypeOriginal,
         * classTypeTest, classFeature, uniqueLabels.keySet(), labels2MR); } }
         */
        // evaluation on each label using the AnnDiff method of Gate
        for(int i = 0; i < corpusOn.size(); ++i)
          if(!isUsedForTraining[i]) {
            Document toProcess = corpusOn.get(i);
            evaluateAnnDiff(toProcess, classTypeOriginal,
              classTypeTest, classFeature, uniqueLabels.keySet(), labels2MR);
            if(toProcess.getDataStore() != null
              && corpusOn.getDataStore() != null) {
              corpusOn.getDataStore().sync(corpusOn);
              Factory.deleteResource(toProcess);
            }
          }
      }
      // Pool the results of all labels together
      for(Object obj : uniqueLabels.keySet())
        measuresOfResults
          .add(labels2MR.get(obj));
      measuresOfResults.computeFmeasure();
      measuresOfResults.computeFmeasureLenient();
      for(Object obj : uniqueLabels.keySet()) {
        EvaluationMeasuresComputation emc = labels2MR
          .get(obj);
        emc.computeFmeasure();
        emc.computeFmeasureLenient();
      }
      StringBuffer logMes = new StringBuffer();
      logMes.append("Results of this run:\n\n");
      logMes.append(printFmeasureForEachLabel(uniqueLabels, labels2MR));
      logMes.append("\nOverall results(micro-averaged over all labels):\n");
      logMes.append(measuresOfResults.printResults());
      if(LogService.minVerbosityLevel > 1) {
        System.out.println(logMes);
      }
      LogService.logMessage(logMes.toString(), 1);
      // finally, change the class type back for training,
      // and remove the test annotations
      learningSettings.datasetDefinition.getClassAttribute().setType(
        classTypeOriginal);
      for(int i = 0; i < corpusOn.size(); ++i) {
        if(!isUsedForTraining[i]) {
          Document toProcess = corpusOn.get(i);
          AnnotationSet annsInput = getInputAS(toProcess);
          AnnotationSet anns = annsInput.get(classTypeTest);
          Iterator<Annotation> iter = anns.iterator();
          while(iter.hasNext())
            annsInput.remove(iter.next());
          if(toProcess.getDataStore() != null
            && corpusOn.getDataStore() != null) {
            corpusOn.getDataStore().sync(corpusOn);
            Factory.deleteResource(toProcess);
          }
        }
      }
    } catch(ResourceInstantiationException e) {
      e.printStackTrace();
    } catch(UnsupportedEncodingException e1) {
      e1.printStackTrace();
    } catch(FileNotFoundException e1) {
      e1.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /** Empty the label list and feature list. */
  public static void emptyDatafile(File wdResults, boolean isRemoveList) {
    if(isRemoveList) {
      (new File(wdResults, ConstantParameters.FILENAMEOFNLPFeatureList))
        .delete();
      (new File(wdResults, ConstantParameters.FILENAMEOFLabelList)).delete();
      (new File(wdResults, ConstantParameters.FILENAMEOFChunkLenStats))
        .delete();
      (new File(wdResults, ConstantParameters.FILENAMEOFLabelsInData)).delete();
      try {
        (new File(wdResults, ConstantParameters.FILENAMEOFNLPFeatureList))
          .createNewFile();
      } catch(IOException e) {
        e.printStackTrace();
      }
    }
    (new File(wdResults, ConstantParameters.FILENAMEOFNLPFeaturesData))
      .delete();
    (new File(wdResults, ConstantParameters.FILENAMEOFFeatureVectorData))
      .delete();
    (new File(wdResults, ConstantParameters.FILENAMEOFNLPDataLabel)).delete();
    System.gc(); // to make effort to delete the files.
  }

  /** Add the F-measure of each label to the overal F-measure */
  public void add2MacroMeasure(EvaluationMeasuresComputation measuresOfResults,
    Map<String,EvaluationMeasuresComputation> labels2MR, EvaluationMeasuresComputation macroMeasuresOfResults,
    Map<String,EvaluationMeasuresComputation> labels2MMR, Map<String,Integer> labels2RunsNum) {
    macroMeasuresOfResults.add(measuresOfResults);
    // for each label
    for(String label : labels2MR.keySet()) {
      if(labels2MMR.containsKey(label)) {// the label is in the macro
        // Array already
        labels2MMR.get(label)
          .add((labels2MR.get(label)));
        labels2RunsNum.put(label, labels2RunsNum.get(label) + 1);
      } else {
        // labels2MMR.put(label, new EvaluationMeasuresComputation());
        EvaluationMeasuresComputation emc = new EvaluationMeasuresComputation();
        emc.add((labels2MR.get(label)));
        labels2MMR.put(label, emc);
        labels2RunsNum.put(label, 1); //MAG: was "1" instead of 1
      }
    }
  }

  /** Print the F-measure results for each label. */
  public String printFmeasureForEachLabel(Map<String,Integer> uniqueLabels,
    Map<String,EvaluationMeasuresComputation> labels2MR) {
    StringBuffer logMes = new StringBuffer();
    logMes.append("\nResults of single label:\n");
    // LogService.logMessage("\nResults of single label", 1);
    List<String> labels = new ArrayList<String>(uniqueLabels.keySet());
    Collections.sort(labels);
    for(int i = 0; i < labels.size(); ++i) {
      String labelName = labels.get(i).toString();
      logMes.append(i + " LabelName=" + labelName + ", number of instances="
        + uniqueLabels.get(labelName) + "\n");
      logMes.append(labels2MR.get(labelName)
        .printResults());
      // ((EvaluationMeasuresComputation)labels2MR.get(labelName))
      // .printResults(logFileIn);
    }
    return logMes.toString();
  }

  /** Get the measures per label */
  public Map<String,EvaluationMeasuresComputation> getMeasuresForEachLabel() {
    List<String> labels = new ArrayList<String>(labels2InstNum.keySet());
    Map<String,EvaluationMeasuresComputation> ret = new LinkedHashMap<String,EvaluationMeasuresComputation>();
    Collections.sort(labels);
    for(int i = 0; i < labels.size(); ++i) {
      String labelName = labels.get(i).toString();
      ret.put(labelName, labels2MMR.get(labelName));
    }
    return ret;
  }
  
  
  /** Evaluate the test document by using the AnnotationDiff class. */
  public void evaluateAnnDiff(Document doc, String classTypeOriginal,
    String classTypeTest, String classFeat, Set<String> labelSet, Map<String,EvaluationMeasuresComputation> labels2MR) {
    // for each label
    AnnotationSet annsOriginal = getInputAS(doc).get(classTypeOriginal);
    AnnotationSet annsTest = getInputAS(doc).get(classTypeTest);
    Set<Annotation> annsKey = new HashSet<Annotation>();
    Set<Annotation> annsRes = new HashSet<Annotation>();
    Set<String> signSet = new HashSet<String>();
    signSet.add(classFeat);
    for(String label : labelSet) {
      annsKey.clear();
      annsRes.clear();
      for(Annotation objAnn : annsOriginal)
        if(objAnn.getFeatures().get(classFeat) != null)
          if(objAnn.getFeatures().get(classFeat)
            .toString().equals(label)) annsKey.add(objAnn);
      for(Annotation objAnn : annsTest)
        if(objAnn.getFeatures().get(classFeat) != null)
          if(objAnn.getFeatures().get(classFeat)
            .toString().equals(label)) annsRes.add(objAnn);
      AnnotationDiffer annDiff = new AnnotationDiffer();
      annDiff.setSignificantFeaturesSet(signSet);
      annDiff.calculateDiff(annsKey, annsRes);
      EvaluationMeasuresComputation emc = labels2MR
        .get(label);
      emc.correct += annDiff.getCorrectMatches();
      emc.partialCor += annDiff.getPartiallyCorrectMatches();
      emc.missing += annDiff.getMissing();
      emc.spurious += annDiff.getSpurious();
    }
  }

  /**
   * Evaluate the test document by comparing the annotations with the golden
   * standard
   */
  public void evaluateAnnotations(Document doc, String classTypeOriginal,
    String classTypeTest, String classFeat, Set<String> labelSet, Map<String,EvaluationMeasuresComputation> labels2MR) {
    AnnotationSet annsOriginal = getInputAS(doc).get(classTypeOriginal);
    AnnotationSet annsTest = getInputAS(doc).get(classTypeTest);
    Set<Annotation> annsKey = new HashSet<Annotation>();
    Set<Annotation> annsRes = new HashSet<Annotation>();
    // For each label
    for(String label : labelSet) {
      annsKey.clear();
      annsRes.clear();
      for(Annotation objAnn : annsOriginal) {
        Object label0 = objAnn.getFeatures().get(classFeat);
        if(label0 != null && label0.equals(label)) annsKey.add(objAnn);
      }
      for(Annotation objAnn : annsTest) {
        Object label0 = objAnn.getFeatures().get(classFeat);
        if(label0 != null && label0.equals(label)) annsRes.add(objAnn);
      }
      EvaluationMeasuresComputation measuresOfResults = labels2MR
        .get(label);
      for(Object annOrig : annsKey) {
        boolean isMatch = false;
        for(Object annTest : annsRes)
          if(((Annotation)annTest).coextensive(((Annotation)annOrig))) {
            measuresOfResults.correct++;
            isMatch = true;
            break;
          }
        if(!isMatch) measuresOfResults.missing++;
      }
      for(Object annTest : annsRes) {
        boolean isMatch = false;
        for(Object annOrig : annsKey)
          if(((Annotation)annTest).coextensive(((Annotation)annOrig))) {
            isMatch = true;
            break;
          }
        if(!isMatch) measuresOfResults.spurious++;
      }
    }
  }

  /**
   * Evaluate the test document by comparing the annotations with the golden
   * standard, for Relation learning
   */
  public void evaluateAnnotationsRel(Document doc, String classTypeOriginal,
    String classTypeTest, String classFeat, String arg1F, String arg2F,
    Set<String> labelSet, Map<String,EvaluationMeasuresComputation> labels2MR) {
    AnnotationSet annsOriginal = getInputAS(doc).get(classTypeOriginal);
    AnnotationSet annsTest = getInputAS(doc).get(classTypeTest);
    Set<Annotation> annsKey = new HashSet<Annotation>();
    Set<Annotation> annsRes = new HashSet<Annotation>();
    // For each label
    for(String label : labelSet) {
      annsKey.clear();
      annsRes.clear();
      for(Annotation objAnn : annsOriginal) {
        Object label0 = objAnn.getFeatures().get(classFeat);
        if(label0 != null && label0.equals(label)) annsKey.add(objAnn);
      }
      for(Annotation objAnn : annsTest) {
        Object label0 = objAnn.getFeatures().get(classFeat);
        if(label0 != null && label0.equals(label)) annsRes.add(objAnn);
      }
      EvaluationMeasuresComputation measuresOfResults = labels2MR
        .get(label);
      for(Object annOrig : annsKey) {
        Object arg1VO = ((Annotation)annOrig).getFeatures().get(arg1F);
        Object arg2VO = ((Annotation)annOrig).getFeatures().get(arg2F);
        boolean isMatch = false;
        for(Object annTest : annsRes) {
          FeatureMap feats = ((Annotation)annTest).getFeatures();
          if(arg1VO.equals(feats.get(arg1F)) && arg2VO.equals(feats.get(arg2F))) {
            measuresOfResults.correct++;
            isMatch = true;
            break;
          }
        }
        if(!isMatch) measuresOfResults.missing++;
      }
      for(Object annTest : annsRes) {
        Object arg1VO = ((Annotation)annTest).getFeatures().get(arg1F);
        Object arg2VO = ((Annotation)annTest).getFeatures().get(arg2F);
        boolean isMatch = false;
        for(Object annOrig : annsKey) {
          FeatureMap feats = ((Annotation)annOrig).getFeatures();
          if(arg1VO.equals(feats.get(arg1F)) && arg2VO.equals(feats.get(arg2F))) {
            isMatch = true;
            break;
          }
        }
        if(!isMatch) measuresOfResults.spurious++;
      }
    }
  }

  private AnnotationSet getInputAS(Document doc) {
    if(inputASName == null || inputASName.trim().length() == 0) {
      return doc.getAnnotations();
    } else {
      return doc.getAnnotations(inputASName);
    }
  }
}
