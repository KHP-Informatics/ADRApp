/*
 *  TestLearningAPI.java
 *
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: TestLearningAPI.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import gate.Corpus;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.learning.ConstantParameters;
import gate.learning.EvaluationBasedOnDocs;
import gate.learning.LearningAPIMain;
import gate.learning.LogService;
import gate.learning.RunMode;
import gate.util.ExtensionFileFilter;
import gate.util.GateException;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
/**
 * Test the three types of NLP learning implemented in
 *  ML Api by using the test methods and small datasets.
 */
public class TestLearningAPI extends TestCase {
  /** Use it to do initialisation only once. */
  private static boolean initialized = false;
  /** Learning home for reading the data and configuration file. */
  private static File learningHome;
  /** Constructor, setting the home directory. */
  public TestLearningAPI(String arg0) throws GateException,
    MalformedURLException {
    super(arg0);
    if(!initialized) {
      Gate.init();
      learningHome = new File(new File(Gate.getGateHome(), "plugins"),
        "Learning");
      Gate.getCreoleRegister().registerDirectories(learningHome.toURI().toURL());
      initialized = true;
    }
  }
  /** The ML Api object to be tested. */
  LearningAPIMain learningApi;
  /** Corpus used for testing. */
  Corpus corpus;
  /** The controller include the ML Api as one PR. */
  gate.creole.SerialAnalyserController controller;
  /** Set up method (does nothing because it may have
   * different behaviour in different enviroment.
   */
  protected void setUp() throws Exception {
    super.setUp();
  }
  /**  Release some resources.*/
  protected void tearDown() throws Exception {
    super.tearDown();
  }
  /** Loading the configurationg file and corpus for testing.
   * And make settings as in the GATE Gui.
   */
  void loadSettings(String configFileName, String corpusDirName, String inputasN, String outputasN)
    throws GateException, IOException {
    LogService.minVerbosityLevel = 0;
    if(LogService.minVerbosityLevel>0)
      System.out.println("Learning Home : " + learningHome.getAbsolutePath());
    FeatureMap parameters = Factory.newFeatureMap();
    URL configFileURL = new File(configFileName).toURI().toURL();
    parameters.put("configFileURL", configFileURL);
    learningApi = (LearningAPIMain)Factory.createResource(
      "gate.learning.LearningAPIMain", parameters);
    // Load the corpus
    corpus = Factory.newCorpus("DataSet");
    ExtensionFileFilter fileFilter = new ExtensionFileFilter();
    fileFilter.addExtension("xml");
    File[] xmlFiles = new File(corpusDirName).listFiles(fileFilter);
    Arrays.sort(xmlFiles, new Comparator<File>() {
      public int compare(File a, File b) {
        return a.getName().compareTo(b.getName());
      }
    });
    for(File f : xmlFiles) {
      if(!f.isDirectory()) {
          Document doc = Factory.newDocument(f.toURI().toURL(), "UTF-8");
          doc.setName(f.getName());
          corpus.add(doc);
      }
    }
//    URL tempURL = new File(corpusDirName).toURI().toURL();
//    corpus.populate(tempURL, fileFilter, "UTF-8", false);
    // Set the inputAS
    learningApi.setInputASName(inputasN);
    learningApi.setOutputASName(outputasN);
    controller = (gate.creole.SerialAnalyserController)Factory
      .createResource("gate.creole.SerialAnalyserController");
    controller.setCorpus(corpus);
    controller.add(learningApi);
  }
  /** Clear up the resources used after one test. */
  private void clearOneTest() {
    corpus.clear();
    Factory.deleteResource(corpus);
    Factory.deleteResource(learningApi);
    controller.remove(learningApi);
    controller.cleanup();
    Factory.deleteResource(controller);
  }
  /** Test the chunk learning by using the SVM with linear kernel and
   * a small part of the OntoNews corpus.
   */
  
  public void testSVMChunkLearnngNumeric() throws IOException, GateException {
	    // Initialisation
	    System.out.print("Testing the SVM with liner kernel on chunk learning with numeric feature...");
	    File chunklearningHome = new File(new File(learningHome, "test"),
	      "chunklearning");
	    String configFileURL = new File(chunklearningHome, "engines-svm_numeric.xml")
	      .getAbsolutePath();
	    String corpusDirName = new File(chunklearningHome, "test_3")
	      .getAbsolutePath();
	    //Remove the label list file, feature list file and chunk length files.
	    String wdResults = new File(chunklearningHome,
	      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
	    emptySavedFiles(wdResults);
	    String inputASN = "Key";
	    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
	    // Set the evaluation mode
	    RunMode runM=RunMode.EVALUATION;
	    learningApi.setLearningMode(runM);
	    controller.execute();
	    // Using the evaluation mode for testing
	    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
	    
	    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
	    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
	    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
	    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
	    // Compare the overall results with the correct numbers 
	    assertEquals("Wrong value for correct: ", 0, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
	    assertEquals("Wrong value for partial: ",0, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
	    assertEquals("Wrong value for spurious: ", 2, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
	    assertEquals("Wrong value for missing: ", 7, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));

	    System.out.println(" completed");
	    // Remove the resources
	    clearOneTest();
	  }
  public void testSVMChunkLearnng() throws IOException, GateException {
    // Initialisation
    System.out.print("Testing the SVM with liner kernenl on chunk learning...");
    File chunklearningHome = new File(new File(learningHome, "test"),
      "chunklearning");
    String configFileURL = new File(chunklearningHome, "engines-svm.xml")
      .getAbsolutePath();
    String corpusDirName = new File(chunklearningHome, "data-ontonews")
      .getAbsolutePath();
    //Remove the label list file, feature list file and chunk length files.
    String wdResults = new File(chunklearningHome,
      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
    emptySavedFiles(wdResults);
    String inputASN = "Key";
    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
    // Set the evaluation mode
    RunMode runM=RunMode.EVALUATION;
    learningApi.setLearningMode(runM);
    controller.execute();
    // Using the evaluation mode for testing
    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
    
    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    // Compare the overall results with the correct numbers 
    assertEquals("Wrong value for correct: ", 44, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
    assertEquals("Wrong value for partial: ", 10, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
    assertEquals("Wrong value for spurious: ", 11, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
    assertEquals("Wrong value for missing: ", 40, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));

    System.out.println(" completed");
    // Remove the resources
    clearOneTest();
  }
  /** Test the chunk learning by using the Naive Bayes method and
   * a small part of the OntoNews corpus. */
  public void testNBChunkLearnng() throws IOException, GateException {
    // Initialisation
    System.out.print("Testing the Naive Bayes method on chunk learning...");
    File chunklearningHome = new File(new File(learningHome, "test"),
      "chunklearning");
    String configFileURL = new File(chunklearningHome,
      "engines-naivebayesweka.xml").getAbsolutePath();
    String corpusDirName = new File(chunklearningHome, "data-ontonews")
      .getAbsolutePath();
    //Remove the label list file, feature list file and chunk length files.
    String wdResults = new File(chunklearningHome,
      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
    emptySavedFiles(wdResults);
    String inputASN = "Key";
    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
    // Set the evaluation mode
    RunMode runM=RunMode.EVALUATION;
    learningApi.setLearningMode(runM);
    controller.execute();
    // Using the evaluation mode for testing
    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
    // Compare the overall results with the correct numbers
    //assertEquals(evaluation.macroMeasuresOfResults.correct, 3);
    //assertEquals(evaluation.macroMeasuresOfResults.partialCor, 1);
    //assertEquals(evaluation.macroMeasuresOfResults.spurious, 19);
    //assertEquals(evaluation.macroMeasuresOfResults.missing, 68);
    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    assertEquals("Wrong value for correct: ", 27, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
    assertEquals("Wrong value for partial: ", 3, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
    assertEquals("Wrong value for spurious: ", 26, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
    assertEquals("Wrong value for missing: ", 42, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    // Remove the resources
    clearOneTest();
    System.out.println(" completed");
  } //tempoary disabled
  /** Test the chunk learning by using the PAUM and
   * a small part of the OntoNews corpus. */
  public void testPAUMChunkLearnng() throws IOException, GateException {
    // Initialisation
    System.out.print("Testing the PAUM method on chunk learning...");
    File chunklearningHome = new File(new File(learningHome, "test"),
      "chunklearning");
    String configFileURL = new File(chunklearningHome,
      "engines-paum.xml").getAbsolutePath();
    String corpusDirName = new File(chunklearningHome, "data-ontonews")
      .getAbsolutePath();
    //Remove the label list file, feature list file and chunk length files.
    String wdResults = new File(chunklearningHome,
      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
    emptySavedFiles(wdResults);
    String inputASN = "Key";
    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
    // Set the evaluation mode
    RunMode runM=RunMode.EVALUATION;
    learningApi.setLearningMode(runM);
    controller.execute();
    // Using the evaluation mode for testing
    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
    // Compare the overall results with the correct numbers
    //assertEquals(evaluation.macroMeasuresOfResults.correct, 3);
    //assertEquals(evaluation.macroMeasuresOfResults.partialCor, 1);
    //assertEquals(evaluation.macroMeasuresOfResults.spurious, 19);
    //assertEquals(evaluation.macroMeasuresOfResults.missing, 68);
    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    assertEquals("Wrong value for correct: ", 52, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
    assertEquals("Wrong value for partial: ", 12, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
    assertEquals("Wrong value for spurious: ", 24, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
    assertEquals("Wrong value for missing: ", 30, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    // Remove the resources
    clearOneTest();
    System.out.println(" completed");
  }  //temporarily disabled
  /** Test the text classification by using the SVM with linear kernel
   * and the data for sentence classification.
   */
  public void testSVMClassification() throws GateException, IOException {
    // Initialisation
    System.out.print("Testing the SVM with linear kernel on text classification...");
    File scHome = new File(new File(learningHome, "test"),
      "sentence-classification");
    String configFileURL = new File(scHome, "engines-svm.xml")
      .getAbsolutePath();
    String corpusDirName = new File(scHome, "data-h").getAbsolutePath();
    //Remove the label list file, feature list file and chunk length files.
    String wdResults = new File(scHome,
      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
    emptySavedFiles(wdResults);
    String inputASN = null;
    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
    // Set the evaluation mode
    RunMode runM=RunMode.EVALUATION;
    learningApi.setLearningMode(runM);
    controller.execute();
    // Using the evaluation mode for testing
    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
    
    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    
    assertEquals("Wrong value for correct: ", 27, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
    assertEquals("Wrong value for partial: ", 0, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
    assertEquals("Wrong value for spurious: ", 45, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
    assertEquals("Wrong value for missing: ", 39, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    // Remove the resources
    clearOneTest();
    System.out.println(" completed");
  }
  
 public void testSVMClassification_numeric() throws GateException, IOException {
	    // Initialisation
	    System.out.print("Testing the SVM with linear kernel on text classification with numeric feature...");
	    File scHome = new File(new File(learningHome, "test"),
	      "sentence-classification");
	    String configFileURL = new File(scHome, "engines-svm_numeric.xml")
	      .getAbsolutePath();
	    String corpusDirName = new File(scHome, "data-h").getAbsolutePath();
	    //Remove the label list file, feature list file and chunk length files.
	    String wdResults = new File(scHome,
	      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
	    emptySavedFiles(wdResults);
	    String inputASN = null;
	    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
	    // Set the evaluation mode
	    RunMode runM=RunMode.EVALUATION;
	    learningApi.setLearningMode(runM);
	    controller.execute();
	    // Using the evaluation mode for testing
	    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
	    
	    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
	    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
	    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
	    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
	    
	    assertEquals("Wrong value for correct: ", 27, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
	    assertEquals("Wrong value for partial: ", 0, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
	    assertEquals("Wrong value for spurious: ", 44, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
	    assertEquals("Wrong value for missing: ", 39, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
	    // Remove the resources
	    clearOneTest();
	    System.out.println(" completed");
 }
  
  /** Test the text classification by using the SVM with quadratic kernel
   * and the data for sentence classification.
   */
  public void testSVMKernelClassification() throws GateException, IOException {
    System.out.print("Testing the SVM with quadratic kernel on text classification...");
    // Initialisation
    File scHome = new File(new File(learningHome, "test"),
      "sentence-classification");
    String configFileURL = new File(scHome, "engines-svm-quadratickernel.xml")
      .getAbsolutePath();
    String corpusDirName = new File(scHome, "data-h").getAbsolutePath();
    //Remove the label list file, feature list file and chunk length files.
    String wdResults = new File(scHome,
      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
    emptySavedFiles(wdResults);
    String inputASN = null;
    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
    // Set the evaluation mode
    RunMode runM=RunMode.EVALUATION;
    learningApi.setLearningMode(runM);
    controller.execute();
    // Using the evaluation mode for testing
    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
    // Compare the overall results with the correct numbers
    
    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    
    assertEquals("Wrong value for correct: ", 27, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
    assertEquals("Wrong value for partial: ", 0, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
    assertEquals("Wrong value for spurious: ", 44, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
    assertEquals("Wrong value for missing: ", 38, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    // Remove the resources
    clearOneTest();
    System.out.println(" completed");
  }
  /** Test the text classification by using the KNN
   * and the data for sentence classification.
   */
  public void testKNNClassification() throws GateException, IOException {
    System.out.print("Testing the KNN on text classification...");
    // Initialisation
    File scHome = new File(new File(learningHome, "test"),
      "sentence-classification");
    String configFileURL = new File(scHome, "engines-knnweka.xml")
      .getAbsolutePath();
    String corpusDirName = new File(scHome, "data-h").getAbsolutePath();
    //Remove the label list file, feature list file and chunk length files.
    String wdResults = new File(scHome,
      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
    emptySavedFiles(wdResults);
    String inputASN = null;
    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
    // Set the evaluation mode
    RunMode runM=RunMode.EVALUATION;
    learningApi.setLearningMode(runM);
    controller.execute();
    // Using the evaluation mode for testing
    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
    
    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    // Compare the overall results with the correct numbers
    assertEquals("Wrong value for correct: ", 13, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
    assertEquals("Wrong value for partial: ", 0, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
    assertEquals("Wrong value for spurious: ", 60, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
    assertEquals("Wrong value for missing: ", 52, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    
    // Remove the resources
    clearOneTest();
    System.out.println(" completed");
  }
  /** Test the text classification by using the C4.5 algorithm
   * and the data for sentence classification.
   */
  public void testC45Classification() throws GateException, IOException {
    System.out.print("Testing the C4.5 on text classification...");
    // Initialisation
    File scHome = new File(new File(learningHome, "test"),
      "sentence-classification");
    String configFileURL = new File(scHome, "engines-c45weka.xml")
      .getAbsolutePath();
    String corpusDirName = new File(scHome, "data-h").getAbsolutePath();
    //Remove the label list file, feature list file and chunk length files.
    String wdResults = new File(scHome,
      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
    emptySavedFiles(wdResults);
    String inputASN = null;
    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
    // Set the evaluation mode
    RunMode runM=RunMode.EVALUATION;
    learningApi.setLearningMode(runM);
    controller.execute();
    // Using the evaluation mode for testing
    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
    
    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    // Compare the overall results with the correct numbers
    assertEquals("Wrong value for correct: ", 25, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
    assertEquals("Wrong value for partial: ", 0, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
    assertEquals("Wrong value for spurious: ", 63, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
    assertEquals("Wrong value for missing: ", 40, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    
    // Remove the resources
    clearOneTest();
    System.out.println(" completed");
  }
  /** Test the relation extraction by using the SVM with linear kernel
   * and a small part of data from ACE-04 relation extraction.
   * This test has been disabled (02/Apr/2014, IR) as it produces different
   * numbers when run on Java 6 vs Java 7.
   */
  public void dont_testSVMRelationLearning() throws GateException, IOException {
    System.out.print("Testing the SVM with linear kernel on relation extraction...");
    // Initialisation relation-learning
    File scHome = new File(new File(learningHome, "test"), "relation-learning");
    String configFileURL = new File(scHome, "engines-svm.xml")
      .getAbsolutePath();
    String corpusDirName = new File(scHome, "data-acerelation")
      .getAbsolutePath();
    //Remove the label list file, feature list file and chunk length files.
    String wdResults = new File(scHome,
      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
    emptySavedFiles(wdResults);
    String inputASN = "Key";
    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
    // Set the evaluation mode
    RunMode runM=RunMode.EVALUATION;
    learningApi.setLearningMode(runM);
    controller.execute();
    // Using the evaluation mode for testing
    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
    // Compare the overall results with the correct numbers
    //assertEquals(evaluation.macroMeasuresOfResults.correct, 4);
    //assertEquals(evaluation.macroMeasuresOfResults.partialCor, 0);
    //assertEquals(evaluation.macroMeasuresOfResults.spurious, 27);
    //assertEquals(evaluation.macroMeasuresOfResults.missing, 110);
   // assertEquals(evaluation.macroMeasuresOfResults.correct, 1);
    //assertEquals(evaluation.macroMeasuresOfResults.partialCor, 0);
    //assertEquals(evaluation.macroMeasuresOfResults.spurious, 5);
    //assertEquals(evaluation.macroMeasuresOfResults.missing, 113);
    
    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    
    assertEquals("Wrong value for correct: ", 4, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
    assertEquals("Wrong value for partial: ", 0, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
    assertEquals("Wrong value for spurious: ", 7, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
    assertEquals("Wrong value for missing: ", 110, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    
    
    // Remove the resources
    clearOneTest();
    System.out.println(" completed");
  }
  /** Test the relation extraction by using the SVM with linear kernel
   * and a small part of data from ACE-04 relation extraction.
   */
  public void testSVMRelationLearningWithNgramFeatures() throws GateException, IOException {
    System.out.print("Testing the SVM with Ngram features on relation extraction ...");
    // Initialisation relation-learning
    File scHome = new File(new File(learningHome, "test"), "relation-learning");
    String configFileURL = new File(scHome, "engines-svm-ngram.xml")
      .getAbsolutePath();
    String corpusDirName = new File(scHome, "data-acerelation")
      .getAbsolutePath();
    //Remove the label list file, feature list file and chunk length files.
    String wdResults = new File(scHome,
      ConstantParameters.SUBDIRFORRESULTS).getAbsolutePath();
    emptySavedFiles(wdResults);
    String inputASN = "Key";
    loadSettings(configFileURL, corpusDirName, inputASN, inputASN);
    // Set the evaluation mode
    RunMode runM=RunMode.EVALUATION;
    learningApi.setLearningMode(runM);
    controller.execute();
    // Using the evaluation mode for testing
    EvaluationBasedOnDocs evaluation = learningApi.getEvaluation();
    // Compare the overall results with the correct numbers
    //assertEquals(evaluation.macroMeasuresOfResults.correct, 4);
    //assertEquals(evaluation.macroMeasuresOfResults.partialCor, 0);
    //assertEquals(evaluation.macroMeasuresOfResults.spurious, 27);
    //assertEquals(evaluation.macroMeasuresOfResults.missing, 110);
    System.out.print(" correct="+ (int)Math.floor(evaluation.macroMeasuresOfResults.correct) +
    		"; partial="+(int)Math.floor(evaluation.macroMeasuresOfResults.partialCor)+
    		"; spurious="+(int)Math.floor(evaluation.macroMeasuresOfResults.spurious) +
    				"; missing=" + (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    assertEquals("Wrong value for correct: ", 0, (int)Math.floor(evaluation.macroMeasuresOfResults.correct));
    assertEquals("Wrong value for partial: ", 0, (int)Math.floor(evaluation.macroMeasuresOfResults.partialCor));
    assertEquals("Wrong value for spurious: ", 1, (int)Math.floor(evaluation.macroMeasuresOfResults.spurious));
    assertEquals("Wrong value for missing: ", 114, (int)Math.floor(evaluation.macroMeasuresOfResults.missing));
    
    // Remove the resources
    clearOneTest();
    System.out.println(" completed");
  }
  /** Empty the label list, NLP feature list and the chunk lenght list file
   * before each test in order to obtain the consistent results of each test.
   */
  private void emptySavedFiles(String savedFilesDir) {
    (new File(savedFilesDir, ConstantParameters.FILENAMEOFNLPFeatureList)).delete();
    (new File(savedFilesDir, ConstantParameters.FILENAMEOFLabelList)).delete();
    (new File(savedFilesDir, ConstantParameters.FILENAMEOFChunkLenStats)).delete();
    (new File(savedFilesDir, ConstantParameters.FILENAMEOFFeatureVectorData)).delete();
    (new File(savedFilesDir, ConstantParameters.FILENAMEOFNLPFeaturesData)).delete();
  }

  /** Test suite routine for the test runner */
  public static Test suite() {
    return new TestSuite(TestLearningAPI.class);
  } // suite
}
