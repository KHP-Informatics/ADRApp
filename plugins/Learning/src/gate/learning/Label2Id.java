/*
 *  Label2Id.java
 *
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: Label2Id.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
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

/**
 * The list of unique labels, containing string labels and their numeric
 * indexes.
 */
public class Label2Id {
  /** Label to index map, for training. */
  public Map<String,Integer> label2Id;
  /** Index to label map for application. */
  public Map<Integer, String> id2Label;

  /** Constructor, create the two hash map. */
  public Label2Id() {
    label2Id = new HashMap<String,Integer>();
    id2Label = new HashMap<Integer,String>();
  }

  /** Load the label list and the indexes from a file. */
  public void loadLabelAndIdFromFile(File parentDir, String filename) {
    File file1 = new File(parentDir, filename);
    if(file1.exists()) {
      try {
        BufferedReader in = new BomStrippingInputStreamReader(new FileInputStream(
          new File(parentDir, filename)), "UTF-8");
        String line;
        while((line = in.readLine()) != null) {
          line.trim();
          int p = line.indexOf(' ');
          label2Id.put(line.substring(0, p).trim(), new Integer(line.substring(p + 1)
            .trim()));
          id2Label.put(new Integer(line.substring(p + 1).trim()), line.substring(0, p)
            .trim());
        }
        in.close();
      } catch(IOException e) {
      }
    } else {
      if(LogService.minVerbosityLevel > 0)
        System.out.println("No label list file in initialisation phrase.");
    }
  }

  /** Write the label list and indexes into a file. */
  public void writeLabelAndIdToFile(File parentDir, String filename) {
    try {
      PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(parentDir,
        filename)), "UTF-8"));
      List<String> keys = new ArrayList<String>(label2Id.keySet());
      Collections.sort(keys);
      Iterator<String> iterator = keys.iterator();
      while(iterator.hasNext()) {
        String key = iterator.next();
        out.println(key + " " + label2Id.get(key));
      }
      out.close();
    } catch(IOException e) {
    }
  }

  /** Update the label list from the new document. */
  public void updateMultiLabelFromDoc(String[] className) {
    int baseId = label2Id.size();
    for(int i = 0; i < className.length; ++i) {
      if(className[i] == null) continue;
      String[] items = className[i].split(ConstantParameters.ITEMSEPARATOR);
      for(int j = 0; j < items.length; ++j) {
        if(items[j].endsWith(ConstantParameters.SUFFIXSTARTTOKEN))
          items[j] = items[j].substring(0, items[j]
            .lastIndexOf(ConstantParameters.SUFFIXSTARTTOKEN));
        if(!label2Id.containsKey(items[j])) {
          ++baseId;
          label2Id.put(items[j], new Integer(baseId));
          id2Label.put(new Integer(baseId), items[j]);
        }
      }
    }
  }

  /** Clear the label list object for another run in evaluation. */
  public void clearAllData() {
    this.label2Id.clear();
    this.id2Label.clear();
  }
}
