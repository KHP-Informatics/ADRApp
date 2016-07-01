/*
 *  NLPFeaturesList.java
 *
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: NLPFeaturesList.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import gate.util.BomStrippingInputStreamReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * NLP feature list. Read it from a file, update it using the new documents, and
 * write back into the file.
 */
public class NLPFeaturesList {
  /** the features with ids, can be accessed by multiple threads. */
  public Map<String,Long> featuresList = null;
  /**
   * Document frequence of each term, useful for document or passage
   * classification
   */
  public Map<String,Long> idfFeatures = null;
  /** Total number of documents used for forming the list. */
  int totalNumDocs;
  /** The unique sysmbol used for the N-gram feature. */
  public final static String SYMBOLNGARM = "<>";

  /** Constructor, get the two hashtables. */
  public NLPFeaturesList() {
    featuresList = new HashMap<String,Long>();
    idfFeatures = new HashMap<String,Long>();
    totalNumDocs = 0;
  }

  /** Loading the list from a file. */
  public void loadFromFile(File parentDir, String filename, String tcode) {
    File fileFeaturesList = new File(parentDir, filename);
    if(fileFeaturesList.exists()) {
      try {
        BufferedReader in = new BomStrippingInputStreamReader(new FileInputStream
          (fileFeaturesList), tcode);
        // featuresList = new Hashtable();
        String line;
        if((line = in.readLine()) != null)
          totalNumDocs = new Integer(line.substring(line.lastIndexOf("=") + 1));
        while((line = in.readLine()) != null) {
          String[] st = line.split(" ");
          featuresList.put(st[0], new Long(st[1]));
          idfFeatures.put(st[0], new Long(st[2]));
        }
        in.close();
      } catch(IOException e) {
      }
    } else {
      if(LogService.minVerbosityLevel > 0)
        System.out.println("No feature list file in initialisation phrase.");
    }
  }

  /** Write back the list into the file, with updated information. */
  public void writeListIntoFile(File parentDir, String filename, String tcode) {
    File fileFeaturesList = new File(parentDir, filename);
    if(LogService.minVerbosityLevel > 1)
      System.out.println("Lengh of List = " + featuresList.size());
    try {
      PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(
        fileFeaturesList), tcode));
      // for the total number of docs
      out.println("totalNumDocs=" + totalNumDocs);
      List<String> keys = new ArrayList<String>(featuresList.keySet());
      Collections.sort(keys);
      // write the features list into the output file
      Iterator<String> iterator = keys.iterator();
      while(iterator.hasNext()) {
        String key = iterator.next();
        out.println(key + " " + featuresList.get(key) + " "
          + idfFeatures.get(key));
         //System.out.println("*"+key+ "* " + featuresList.get(key));
      }
      out.close();
    } catch(IOException e) {
    }
  }

  /** Update the NLP features from new documents. */
  public void addFeaturesFromDoc(NLPFeaturesOfDoc fd) {
    long size = featuresList.size();
    for(int i = 0; i < fd.numInstances; ++i) {
      String[] features = fd.featuresInLine[i].toString().trim().split(
        ConstantParameters.ITEMSEPARATOR);
      for(int j = 0; j < features.length; ++j) {
        if(features[j] != null && Pattern.matches((".+\\[[-0-9]+\\]$"), features[j])) {
          int ind = features[j].lastIndexOf('[');
          features[j] = features[j].substring(0,ind);
        }
        //if the feature is an empty, don't count it as a feature at all.
        if(features[j].equals(""))
          continue;
        String feat = features[j];
 
        if(feat.contains(SYMBOLNGARM))
          feat = feat.substring(0, feat.lastIndexOf(SYMBOLNGARM));
        else {//for the attribute type
          //System.out.println("feat="+feat);
          if(!feat.equals(ConstantParameters.NAMENONFEATURE)) {
        	  
        	  char semanticTypeNow=feat.charAt(ConstantParameters.ITEMSEPREPLACEMENT.length());
            //feat =feat.substring(ConstantParameters.ITEMSEPREPLACEMENT.length()+1);//remove the semantic type, which is char.
            //char semanticTypeNow=feat.charAt(ConstantParameters.ITEMSEPREPLACEMENT.length());
            
            if(semanticTypeNow != Attribute.NOMINAL) 
              feat = feat.substring(0, feat.lastIndexOf(ConstantParameters.ITEMSEPREPLACEMENT));
          }
        }
        if(!feat.equals(ConstantParameters.NAMENONFEATURE)) {
          // If the featureName is not in the feature list
          if(size < ConstantParameters.MAXIMUMFEATURES) {
            if(!featuresList.containsKey(feat)) {
              ++size;
              // features is from 1 (not zero), in the SVM-light
              // format
              featuresList.put(feat, size);
              idfFeatures.put(feat, 1L);
            } else {
              idfFeatures.put(feat, idfFeatures.get(feat) + 1);
            }
          } else {
            String msg = 
              "There are more NLP features from the training docuements" +
              " than the pre-defined maximal number" + 
              ConstantParameters.MAXIMUMFEATURES;
            if(!gate.Utils.isLoggedOnce(msg)) {
              System.out.println(msg);
            }
            return;
          }
        }
      }
    }// end of the loop on the instances
    // update the total number of docs
    totalNumDocs += fd.numInstances;
  }

  /** Clear the label list object for another run in evaluation. */
  public void clearAllData() {
    featuresList.clear();
    idfFeatures.clear();
  }
  /** convert the NLP list into Ngram language model and write it into a file. */
 public void writeToLM(File parentDir, String filename, int nGram) {
   File ngramList = new File(parentDir, filename);
   if(LogService.minVerbosityLevel > 1)
     System.out.println("Lengh of List = " + featuresList.size());
   try {
     PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(
       ngramList), "UTF-8"));
     // for the total number of docs
     //out.println("totalNumDocs=" + new Integer(totalNumDocs));
     out.println("## The following "+nGram+"-gram were obtained from " + totalNumDocs+ " documents or examples");
     List<String> keys = new ArrayList<String>(featuresList.keySet());
     //Collections.sort(keys);
     int numT = keys.size();
     float [] freqs = new float[numT];
     for(int i=0; i<numT; ++i)
       freqs[i] = Float.parseFloat(idfFeatures.get(keys.get(i)).toString());
     int [] indexSort = new int[numT];
     LightWeightLearningApi.sortFloatAscIndex(freqs, indexSort, numT, numT);
     // write the features list into the output file
     //Iterator iterator = keys.iterator();
     //while(iterator.hasNext()) {
     for(int i=0; i<numT; ++i) {
       Object key = keys.get(indexSort[i]);
       String str = key.toString();
       if(str.contains("<>")) { //if it is a ngram feature
         str = str.substring(str.indexOf("_",1)+1, str.lastIndexOf("<>"));
         out.println(str + " " + (int)freqs[indexSort[i]]);
        //System.out.println("*"+key+ "* " + featuresList.get(key));
       }
     }
     out.close();
   } catch(IOException e) {
   }
 }
}
