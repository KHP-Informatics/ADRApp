/*
 *  WekaLearning.java
 *
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: WekaLearning.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning.learners.weka;

import gate.learning.ConstantParameters;
import gate.learning.LabelsOfFV;
import gate.learning.LabelsOfFeatureVectorDoc;
import gate.learning.NLPFeaturesList;
import gate.learning.SparseFeatureVector;
import gate.learning.learners.MultiClassLearning;
import gate.util.BomStrippingInputStreamReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SparseInstance;

/**
 * The interface between the Weka learner and the data defined in the ML Api,
 * which convert the data into the format a Weka learner can use.
 */
public class WekaLearning {
  /** The data in the Weka object for training or application. */
  public Instances instancesData;
  /** The labels in the form of instances of every doc. */
  public LabelsOfFeatureVectorDoc[] labelsFVDoc = null;
  /** For using the feature vector data. */
  public final static short SPARSEFVDATA = 2;
  /** For using the NLP feature data. */
  public final static short NLPFEATUREFVDATA = 1;

  /** Learn a model and save it into the model file. */
  public void train(WekaLearner wekaCl, File modelFile) {
    // Training.
    wekaCl.training(instancesData);
    // Write the learner class into the modelfile by class serialisation
    try {
      if(modelFile.exists()) {
        deleteRecursively(modelFile);
      }
      FileOutputStream modelOutFile = new FileOutputStream(modelFile);
      ObjectOutputStream modelOutputObjectFile = new ObjectOutputStream(
        modelOutFile);
      modelOutputObjectFile.writeObject(wekaCl);
      modelOutputObjectFile.flush();
      modelOutputObjectFile.close();
    } catch(FileNotFoundException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Recursively delete a file or directory (think "rm -rf file").
   */
  private void deleteRecursively(File file) throws IOException {
    if(!file.exists()) { return; }
    if(file.isDirectory()) {
      for(File f : file.listFiles()) {
        deleteRecursively(f);
      }
    }
    if(!file.delete()) { throw new IOException("Couldn't delete file " + file); }
  }

  /** Read the model from the file and apply it to the data. */
  public void apply(WekaLearner wekaCl, File modelFile,
    boolean distributionOutput) {
    // Read the learner class from the modelfile by class serialisation
    try {
      FileInputStream modelInFile = new FileInputStream(modelFile);
      ObjectInputStream modelInputObjectFile = new ObjectInputStream(
        modelInFile);
      wekaCl = (WekaLearner)modelInputObjectFile.readObject();
      modelInputObjectFile.close();
    } catch(FileNotFoundException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    } catch(ClassNotFoundException e) {
      e.printStackTrace();
    }
    // Apply the model to the data.
    wekaCl.applying(instancesData, labelsFVDoc, distributionOutput);
  }

  /**
   * Read the sparse feature vector data from the data file and convert it into
   * the Weka's instance format.
   */
  public void readSparseFVsFromFile(File dataFile, int numDocs,
    boolean trainingMode, int numLabels, boolean surroundMode) {
    int numFeats = 0;
    int numClasses = 0;
    labelsFVDoc = new LabelsOfFeatureVectorDoc[numDocs];
    // Read the sparse FVs by using the method in MultiClassLearning class
    MultiClassLearning multiClassL = new MultiClassLearning();
    boolean isUsingDataFile = false;
    File tempFVDataFile = null;
    multiClassL.getDataFromFile(numDocs, dataFile, isUsingDataFile, tempFVDataFile);
    // Create the attributes.
    numFeats = multiClassL.dataFVinDoc.getTotalNumFeatures();
    FastVector attributes = new FastVector(numFeats + 1);
    for(int i = 0; i < numFeats; ++i)
      attributes.addElement(new Attribute(new Integer(i + 1).toString()));
    // Add class attribute.
    if(surroundMode)
      numClasses = 2 * numLabels + 1; // count the null too, as value -1.
    else numClasses = numLabels + 1;
    FastVector classValues = new FastVector(numClasses);
    classValues.addElement("-1"); // The first class for null class
    for(int i = 1; i < numClasses; ++i)
      classValues.addElement(new Integer(i).toString());
    attributes.addElement(new Attribute("Class", classValues));
    // Create the dataset with capacity of all FVs (but actuall number of FVs
    // mabe be larger than the pre-specified, because possible multi-label) and
    // set index of class
    instancesData = new Instances("SparseFVsData", attributes,
      multiClassL.dataFVinDoc.getNumTraining());
    instancesData.setClassIndex(instancesData.numAttributes() - 1);
    // Copy the data into the instance;
    for(int iDoc = 0; iDoc < multiClassL.dataFVinDoc.getNumTrainingDocs(); ++iDoc) {
      SparseFeatureVector[] fvs = multiClassL.dataFVinDoc.trainingFVinDoc[iDoc]
        .getFvs();
      labelsFVDoc[iDoc] = new LabelsOfFeatureVectorDoc();
      labelsFVDoc[iDoc].multiLabels = multiClassL.dataFVinDoc.labelsFVDoc[iDoc].multiLabels;
      for(int i = 0; i < fvs.length; ++i) {
        // Object valueO = fvs[i].getValues();
        double[] values = new double[fvs[i].getLen()];
        int[] indexes = new int[fvs[i].getLen()];
        for(int j = 0; j < fvs[i].getLen(); ++j) {
          //values[j] = (double)fvs[i].values[j];
          values[j] = fvs[i].nodes[j].value;
          indexes[j] = fvs[i].nodes[j].index;
        }
        SparseInstance inst = new SparseInstance(1.0, values, indexes, 50000);
        inst.setDataset(instancesData);
        if(trainingMode && labelsFVDoc[iDoc].multiLabels[i].num > 0)
          for(int j1 = 0; j1 < labelsFVDoc[iDoc].multiLabels[i].num; ++j1) {
            inst.setClassValue((labelsFVDoc[iDoc].multiLabels[i].labels[j1])); // label
            // >0
            instancesData.add(inst);
          }
        else {
          inst.setClassValue("-1"); // set label as -1 for null
          instancesData.add(inst);
        }
      }
    }
    return;
  }

  /**
   * Read the NLP feature data from the data file and convert it into the Weka's
   * instance format.
   */
  public void readNLPFeaturesFromFile(File dataFile, int numDocs,
    NLPFeaturesList nlpFeatList, boolean trainingMode, int numLabels,
    boolean surroundMode) {
    labelsFVDoc = new LabelsOfFeatureVectorDoc[numDocs];
    try {
      BufferedReader inData;
      inData = new BomStrippingInputStreamReader(new FileInputStream(
        dataFile), "UTF-8");
      // Get the number of attributes in the data
      String[] items = inData.readLine()
        .split(ConstantParameters.ITEMSEPARATOR);
      Map<String,Set<String>> metaFeats = new HashMap<String,Set<String>>();
      int numFeats = 0;
      // Create an attribute for each meta feature
      Map<String,String> entityToPosition = new HashMap<String,String>();
      String entityTerm = "";
      int numEntity = 0;
      // Not include the class attribute
      for(int i = 1; i < items.length; ++i) {
        // Assume the name of NGRAM should end with "gram"!!
        if(!items[i].endsWith("gram")) {
          if(!metaFeats.containsKey(items[i])) {
            metaFeats.put(items[i], new HashSet<String>());
            ++numFeats; // counted as a new attribute
          }
          //System.out.println(i +" item="+items[i]);
          
          String feat = items[i].substring(0, items[i].lastIndexOf("("));
          String featNum = items[i].substring(items[i].lastIndexOf("("));
          if(!feat.equals(entityTerm)) {
            numEntity = 0;
            entityTerm = feat;
          } else ++numEntity;
          entityToPosition.put(feat + "_" + numEntity, featNum);
          if(!metaFeats.containsKey(feat)) {
            metaFeats.put(feat, new HashSet<String>());
            // just for collect the terms
          }
        }
      }
      List<String> allTerms = new ArrayList<String>(nlpFeatList.featuresList.keySet());
      Collections.sort(allTerms);
      for(int i = 0; i < allTerms.size(); ++i) {
        String feat = allTerms.get(i);
        if(isNgramFeat(feat)) {
          ++numFeats;
        } else {
          feat = feat.substring(feat.indexOf("_") + 1);
          // Name of the entity
          String feat1 = feat.substring(0, feat.indexOf("_"));
          // Term itself
          String feat2 = feat.substring(feat.indexOf("_") + 1);
          metaFeats.get(feat1).add(feat2);
        }
      }
      numFeats += 1; // include the class feature
      // Create the attributes.
      Map<String,Integer> featToAttr = new HashMap<String,Integer>(); // feat to attribute index
      FastVector attributes = new FastVector(numFeats);
      // First for the meta feature attribute.
      List<String> metaFeatTerms = new ArrayList<String>(metaFeats.keySet());
      int numMetaFeats = 0;
      for(int i = 0; i < metaFeatTerms.size(); ++i) {
        String featName = metaFeatTerms.get(i).toString();
        if(featName.endsWith(")")) {
          String featName0 = featName.substring(0, featName.lastIndexOf("("));
          Set<String> metaF = metaFeats.get(featName0);
          FastVector featFV = new FastVector(metaF.size());
          for(Object obj : metaF)
            featFV.addElement(obj.toString());
          attributes.addElement(new Attribute(featName, featFV));
          featToAttr.put(featName, numMetaFeats);
          ++numMetaFeats;
        }
      }
      // Then the terms from ngram features
      for(int i = 0; i < allTerms.size(); ++i) {
        String feat = allTerms.get(i).toString();
        if(isNgramFeat(feat)) {
          FastVector featFV = new FastVector(1);
          featFV.addElement(feat);
          attributes.addElement(new Attribute(feat, featFV));// Nominal form
          featToAttr.put(feat, i + numMetaFeats);
        }
      }
      // Add class attribute.
      int numClasses;
      if(surroundMode)
        numClasses = 2 * numLabels + 1; // count the null too, as value -1.
      else numClasses = numLabels + 1;
      FastVector classValues = new FastVector(numClasses);
      classValues.addElement("-1"); // The first class for null class
      for(int i = 1; i < numClasses; ++i)
        classValues.addElement(new Integer(i).toString());
      attributes.addElement(new Attribute("Class", classValues));
      // Create the dataset with capacity of all FVs, and set index of class
      instancesData = new Instances("NLPFeatureData", attributes, numDocs * 10);
      // The first attribute is for class.
      instancesData.setClassIndex(attributes.size() - 1);
      // Read data from file and copy the data into the instance;
      for(int iDoc = 0; iDoc < numDocs; ++iDoc) { // For each document
        items = inData.readLine().split(ConstantParameters.ITEMSEPARATOR);
        // The third item is for number of instances in the doc.
        int num = Integer.parseInt(items[2]);
        labelsFVDoc[iDoc] = new LabelsOfFeatureVectorDoc();
        labelsFVDoc[iDoc].multiLabels = new LabelsOfFV[num];
        for(int i = 0; i < num; ++i) { // For each instance
          items = inData.readLine().split(ConstantParameters.ITEMSEPARATOR);
          Instance inst = new Instance(numFeats);
          inst.setDataset(instancesData);
          int numLabel = Integer.parseInt(items[0]); // number of labels for
          // the instance
          entityTerm = "";
          numEntity = 0;
         
          // For each NLP feature term
          for(int j = numLabel + 1; j < items.length; ++j) {
        	  if(items[j].endsWith("[0]"))
        		  items[j] = items[j].substring(0, items[j].lastIndexOf("["));
        	  //System.out.println(j+", "+items[j]);
            // Skip the feature if it is not in the list
            if(!allTerms.contains(items[j])) continue;
            if(isNgramFeat(items[j])) {// if it's a ngram
              items[j] = items[j].substring(0, items[j]
                .lastIndexOf(NLPFeaturesList.SYMBOLNGARM));
              inst.setValue(Integer.parseInt(featToAttr.get(items[j])
                .toString()), items[j]);
            } else {// if not a ngram
              // For real features, not "_NA"
            	//System.out.println(j+", items="+items[j]);
            	//items[j] = items[j].substring(0, items[j].lastIndexOf("["));
            	
              if(!items[j].equals(ConstantParameters.NAMENONFEATURE)) {
                // Get the feature term
                items[j] = items[j].substring(items[j].indexOf("_") + 1);
                // Entity name
                String feat1 = items[j].substring(0, items[j].indexOf("_"));
                // Feature name
                String feat2 = items[j].substring(items[j].indexOf("_") + 1);
                if(!feat1.equals(entityTerm)) {
                  numEntity = 0;
                  entityTerm = feat1;
                } else ++numEntity;
                feat1 = feat1
                  + entityToPosition.get(feat1 + "_" + numEntity).toString();
                inst.setValue(Integer
                  .parseInt(featToAttr.get(feat1).toString()), feat2);
              }
            }
          }
          if(trainingMode && numLabel > 0) {
            labelsFVDoc[iDoc].multiLabels[i] = new LabelsOfFV(numLabel);
            for(int j = 1; j <= numLabel; ++j) {
              inst.setClassValue(items[j]);
              instancesData.add(inst);
            }
          } else {
            labelsFVDoc[iDoc].multiLabels[i] = new LabelsOfFV(0);
            inst.setClassValue("-1"); // set as null class
            instancesData.add(inst);
          }
        }// end of the loop i
      }
      inData.close();
    } catch(FileNotFoundException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
    return;
  }

  /** Check if the item is a n-gram or not. */
  private boolean isNgramFeat(String item) {
    if(item.contains(NLPFeaturesList.SYMBOLNGARM))
      return true;
    else return false;
  }

  /**
   * Determining a learner from Weka using the NLP. feature data or the feature
   * vector data.
   */
  public static short obtainWekaLeanerDataType(String learnerName) {
    if(learnerName.contains("C4.5") || learnerName.contains("NaiveBayes")) {
      return NLPFEATUREFVDATA;
    } else {
      return SPARSEFVDATA;
    }
  }

  /** Obtaining the Weka learners. */
  public static WekaLearner obtainWekaLearner(String learnerName,
    String learningOpts) {
    WekaLearner wekaL = null;
    if(learnerName.contains("KNN")) {
      if(learningOpts != null) {
        wekaL = new KNNIBK(learningOpts);
      } else wekaL = new KNNIBK();
    } else if(learnerName.contains("NaiveBayes")) {
      wekaL = new NaiveBayesC();
    } else if(learnerName.contains("C4.5")) {
      wekaL = new C45();
    }
    if(learningOpts != null) wekaL.getParametersFromOptionsLine(learningOpts);
    return wekaL;
  }

  /** Determing the output type of a Weka learner. */
  public static boolean obtainWekaLearnerOutputType(String learnerName) {
    /*
     * if(learnerName.contains("KNN")) { return true; } else
     * if(learnerName.contains("NaiveBayes")) { return true; } else
     * if(learnerName.contains("C45")) { return true; }
     */
    return true;
    // return false;
  }
}
