/*
 *  LearningEngineSettings.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: LearningEngineSettings.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import gate.creole.ResourceInstantiationException;
import gate.learning.learners.MultiClassLearning;
import gate.util.GateException;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * Reading and storing the learning settings from the configuration file.
 */
public class LearningEngineSettings {
  /** Storing date set definition. */
  public DataSetDefinition datasetDefinition;
  /** Number of threads used **/;
  int numThreadUsed = 1;
  /** Threshold of the probability for the boundary token of chunk. */
  float thrBoundaryProb = 0.4f;
  /** The threshold of the probability for the chunk. */
  float thrEntityProb = 0.2f;
  /** The threshold of the probability for classifation. */
  float thrClassificationProb = 0.2f;
  /** Name used in the configuration file for boundary token prob threshold. */
  final static String thrBoundaryProbStr = "thresholdProbabilityBoundary";
  /** Name used in the configuration file for entity prob threshold. */
  final static String thrEntityProbStr = "thresholdProbabilityEntity";
  /** Name used in the configuration file for classification prob. threshold. */
  final static String thrClassificationProbStr = "thresholdProbabilityClassification";
  /**
   * Two difference methods of converting multi-class problem into binary class
   * problems.
   */
  short multi2BinaryMode = 1;
  /** One against others. */
  public final static short OneVSOtherMode = 1;
  /** One against Another one. */
  public final static short OneVSAnotehrMode = 2;
  /**
   * Name used in the configuration file for the the method of multi to binary
   * mode conversion.
   */
  final static String multi2BinaryN = "multiClassification2BinaryMethod";
  /**
   * Executor used to run the individual binary training and classification
   * tasks in multi-binary mode. If this is not set, {@link MultiClassLearning}
   * will simply run the tasks sequentially in its main thread, but as the
   * binary tasks are independent they could instead be run in a thread pool.
   */
  public ExecutorService multiBinaryExecutor;
  /** The settings of learner specified. */
  public LearnerSettings learnerSettings;
  /** The surround mode. */
  boolean surround;
  /** The option if the label list is updatable. */
  public boolean isLabelListUpdatable = true;
  /** The option if the NLP Feature List is updatable. */
  public boolean isNLPFeatListUpdatable = true;
  /** The option if doing filtering training data or not. */
  public boolean fiteringTrainingData = false;
  /**
   * Ratio of negative examples filiterd out to the total number of negative
   * exampels in training set.
   */
  public float filteringRatio = 0.0f;
  /**
   * Filtering the negative examples which are nearest to classification
   * hyper-plane or furthest from.
   */
  public boolean filteringNear = false;
  /** The setting for evaluation. */
  public EvaluationConfiguration evaluationconfig = null;
  /** Number of document as interval between trainings in MI-learning mode. */
  public int miDocInterval = 1;
  /** The document number interval for one applicataion in batch learning mode. */
  public int docNumIntevalApp = 1;
  /**
   * Define the number of the NLP features with the biggest weights in linear
   * SVM model.
   */
  public int numPosSVMModel;
  /**
   * Define the number of the NLP features with the smallest weight in linear
   * SVM model.
   */
  public int numNegSVMModel;
  /** Active learning settings. */
  ActiveLearningSetting alSetting;
  /**
   * The verbosity level for writing information into log file. 0: no real
   * output. 1: normal output including results and setting information. 2:
   * warning information.
   */
  public int verbosityLogService = LogService.NORMAL;
  
  public org.jdom.Document jdomDocSaved = null;
  // After loading the learning settings from the URL this is either null
  // for a non-file URL or the File correspinding to the settings if it
  // is a file URL
  public File configFile = null;
  
  public String experimentId = "";

  /** Loading the learning settings from the configuration file. */
  public static LearningEngineSettings loadLearningSettingsFromFile(
    java.net.URL xmlengines) throws GateException {
    SAXBuilder saxBuilder = new SAXBuilder(false);
    org.jdom.Document jdomDoc = null;
    try {
      jdomDoc = saxBuilder.build(xmlengines);
    } catch(Exception e) {
      throw new GateException("Problem parsing config file",e);
    }
    Element rootElement = jdomDoc.getRootElement();
    if(!rootElement.getName().equals("ML-CONFIG"))
      throw new ResourceInstantiationException(
        "Root element of dataset defintion file is \"" + rootElement.getName()
          + "\" instead of \"ML-CONFIG\"!");
    // Create a learning setting object
    LearningEngineSettings learningSettings = new LearningEngineSettings();
    if(xmlengines.getProtocol().equalsIgnoreCase("file")) { 
      learningSettings.configFile = gate.util.Files.fileFromURL(xmlengines); 
    }
    learningSettings.jdomDocSaved = jdomDoc;
    if(rootElement.getChild("EXPERIMENT-ID") != null) {
      learningSettings.experimentId = rootElement.getChild("EXPERIMENT-ID").getTextTrim();
    }
    learningSettings.surround = false;
    if(rootElement.getChild("SURROUND") != null) {
      String value = rootElement.getChild("SURROUND").getAttribute("value")
        .getValue();
      learningSettings.surround = "true".equalsIgnoreCase(value);
    }
    /** Set the number of documents as training interval for mi-learning. */
    learningSettings.miDocInterval = 1;
    if(rootElement.getChild("MI-TRAINING-INTERVAL") != null) {
      String value = rootElement.getChild("MI-TRAINING-INTERVAL").getAttribute(
        "num").getValue();
      learningSettings.miDocInterval = Integer.parseInt(value);
    }
    /** Set the number of documents as interval for batch application. */
    learningSettings.docNumIntevalApp = 1;
    if(rootElement.getChild("BATCH-APP-INTERVAL") != null) {
      String value = rootElement.getChild("BATCH-APP-INTERVAL").getAttribute(
        "num").getValue();
      learningSettings.docNumIntevalApp = Integer.parseInt(value);
    }
    /** Get the setting for verbosity. */
    learningSettings.verbosityLogService = LogService.NORMAL;
    if(rootElement.getChild("VERBOSITY") != null) {
      String value = rootElement.getChild("VERBOSITY").getAttribute("level")
        .getValue();
      Integer logLevel = null;
      try {
        Field f = LogService.class.getField(value);
        if(Modifier.isStatic(f.getModifiers()) &&
            Modifier.isFinal(f.getModifiers()) &&
            Integer.TYPE == f.getType()) {
          logLevel = (Integer)f.get(null);
        }
      } catch(Exception e) {
        // Do nothing, try treating it as a number instead
      }
      if(logLevel == null) {
        logLevel = Integer.valueOf(value);
      }
      learningSettings.verbosityLogService = logLevel.intValue();
    }
    learningSettings.fiteringTrainingData = false;
    learningSettings.filteringRatio = 0.0f;
    learningSettings.filteringNear = false;
    if(rootElement.getChild("FILTERING") != null) {
      String value = rootElement.getChild("FILTERING").getAttribute("ratio")
        .getValue();
      learningSettings.filteringRatio = Float.parseFloat(value);
      value = rootElement.getChild("FILTERING").getAttribute("dis").getValue();
      learningSettings.filteringNear = "near".equalsIgnoreCase(value);
      learningSettings.fiteringTrainingData = true;
    }
    learningSettings.isLabelListUpdatable = true;
    if(rootElement.getChild("IS-LABEL-UPDATABLE") != null) {
      String value = rootElement.getChild("IS-LABEL-UPDATABLE").getAttribute(
        "value").getValue();
      learningSettings.isLabelListUpdatable = "true".equalsIgnoreCase(value);
    }
    learningSettings.isNLPFeatListUpdatable = true;
    if(rootElement.getChild("IS-NLPFEATURELIST-UPDATABLE") != null) {
      String value = rootElement.getChild("IS-NLPFEATURELIST-UPDATABLE")
        .getAttribute("value").getValue();
      learningSettings.isNLPFeatListUpdatable = "true".equalsIgnoreCase(value);
    }
    learningSettings.multi2BinaryMode = 1;
    if(rootElement.getChild("multiClassification2Binary") != null) {
      Element mc2b = rootElement.getChild("multiClassification2Binary");
      String value = mc2b.getAttribute("method").getValue();
      if(value.equalsIgnoreCase("one-vs-another"))
        learningSettings.multi2BinaryMode = 2;
      // thread-pool-size attribute causes multi-binary learning to use
      // a pool of threads to run the binary learning tasks, rather than
      // running them sequentially. This can give a big speedup on large
      // training sets with a multi-processor machine.
      String threadPoolSize = mc2b.getAttributeValue("thread-pool-size");
      if(threadPoolSize != null) {
        try {
          int poolSize = Integer.parseInt(threadPoolSize);
          learningSettings.numThreadUsed = poolSize;
          // override the default thread factory with one that returns daemon
          // threads
          // so as not to stop the VM from exiting
          learningSettings.multiBinaryExecutor = Executors.newFixedThreadPool(
            poolSize, new ThreadFactory() {
              private ThreadFactory fac = Executors.defaultThreadFactory();

              public Thread newThread(Runnable r) {
                Thread t = fac.newThread(r);
                t.setDaemon(true);
                return t;
              }
            });
        } catch(NumberFormatException nfe) {
          throw new ResourceInstantiationException(threadPoolSize
            + " is not a valid thread-pool-size: integer expected");
        }
      } else {
        learningSettings.numThreadUsed = 1;
      }
    }
    // Read the parameter for displaying the NLP features from linear SVM model
    learningSettings.numPosSVMModel = 10;
    learningSettings.numNegSVMModel = 0;
    if(rootElement.getChild("DISPLAY-NLPFEATURES-LINEARSVM") != null) {
      String value = rootElement.getChild("DISPLAY-NLPFEATURES-LINEARSVM")
        .getAttribute("numP").getValue();
      if(value != null)
        learningSettings.numPosSVMModel = Integer.parseInt(value);
      value = rootElement.getChild("DISPLAY-NLPFEATURES-LINEARSVM")
        .getAttribute("numN").getValue();
      if(value != null)
        learningSettings.numNegSVMModel = Integer.parseInt(value);
    }
    // for active learning setting
    learningSettings.alSetting = new ActiveLearningSetting();
    if(rootElement.getChild("ACTIVELEARNING") != null) {
      String value = rootElement.getChild("ACTIVELEARNING").getAttributeValue(
        "numTokensPerDoc");
      learningSettings.alSetting.numTokensSelect = Integer.parseInt(value);
    }
    // Read the evaluation method: k-fold CV or k-run hold-out
    try {
      Element evalelem = rootElement.getChild("EVALUATION");
      if(evalelem != null)
        learningSettings.evaluationconfig = EvaluationConfiguration
          .fromXML(evalelem);
      else {
        System.out
          .println("! Warning no evaluation scheme is specified. So it will use the default scheme.");
        learningSettings.evaluationconfig = new EvaluationConfiguration();
      }
    } catch(RuntimeException e) {
    }
    // Loading the dataset definition
    try {
      Element datasetElement = rootElement.getChild("DATASET");
      learningSettings.datasetDefinition = new DataSetDefinition(datasetElement);
    } catch(Exception e) {
      throw new GateException(
        "The DSD element in the configureation file is missing or invalid");
    }
    // Threshold settings
    Iterator<?> parameters = rootElement.getChildren("PARAMETER").iterator();
    while(parameters.hasNext()) {
      Element paramelem = (Element)parameters.next();
      String name = paramelem.getAttribute("name").getValue();
      String value = paramelem.getAttribute("value").getValue();
      if(name.equals(LearningEngineSettings.thrBoundaryProbStr))
        learningSettings.thrBoundaryProb = Float.parseFloat(value);
      if(name.equals(LearningEngineSettings.thrEntityProbStr))
        learningSettings.thrEntityProb = Float.parseFloat(value);
      if(name.equals(LearningEngineSettings.thrClassificationProbStr))
        learningSettings.thrClassificationProb = Float.parseFloat(value);
    }
    // read the setting for the engine by creating a learner subject
    learningSettings.learnerSettings = new LearnerSettings();
    Element UEelement = rootElement.getChild("ENGINE");
    if(UEelement == null)
      System.out
        .println("!! Warning: the Engine element in the configureation file is missing or invalid. "
          + "You CANNOT learn and apply model, but it's OK for producing the feature files.");
    else {
      if(UEelement.getAttribute("nickname") != null)
        learningSettings.learnerSettings.learnerNickName = UEelement
          .getAttribute("nickname").getValue();
      else learningSettings.learnerSettings.learnerNickName = "A_Learner";
      if(UEelement.getAttribute("implementationName") != null)
        learningSettings.learnerSettings.learnerName = UEelement.getAttribute(
          "implementationName").getValue();
      else throw new GateException("The ENGINE element in the configuration "
        + "does not specify the leaner's name!");
      if(UEelement.getAttribute("options") != null)
        learningSettings.learnerSettings.paramsOfLearning = UEelement
          .getAttribute("options").getValue();
      if(UEelement.getAttribute("executableTraining") != null)
        learningSettings.learnerSettings.executableTraining = UEelement
          .getAttribute("executableTraining").getValue();
    }
    return learningSettings;
  }
}
