package gate.learning;

import gate.util.BomStrippingInputStreamReader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

public class MiLearningInformation {
  /** Number of documents got so far for training in MI-learning mode. */
  int miNumDocsTraining;
  /** Number of document got since last training. */
  int miNumDocsFromLast;

  MiLearningInformation(){
    this.miNumDocsTraining = 0;
    this.miNumDocsFromLast = 0;
  }

  MiLearningInformation(int numTraining, int numFromLast){
    this.miNumDocsTraining = numTraining;
    this.miNumDocsFromLast = numFromLast;
  }

  /** Read information from the data file. */
  public void readDataFromFile(File miInforFile) {
    if(!miInforFile.exists()) {
      this.miNumDocsTraining = 0;
      this.miNumDocsFromLast = 0;
      return;
    }
    try {
      BufferedReader in = new BomStrippingInputStreamReader(new FileInputStream(
        miInforFile), "UTF-8");
      String [] line = in.readLine().split(ConstantParameters.ITEMSEPARATOR);
      miNumDocsTraining = Integer.parseInt(line[0]);
      line = in.readLine().split(ConstantParameters.ITEMSEPARATOR);
      miNumDocsFromLast = Integer.parseInt(line[0]);
      in.close();
    } catch(UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch(FileNotFoundException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }

  }

  /** Write the information into the data file. */
  public void writeDataIntoFile(File miInforFile) {
    try {
      BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
        miInforFile), "UTF-8"));
      out.append(miNumDocsTraining+ConstantParameters.ITEMSEPARATOR+"#miNumDocsTraining");
      out.newLine();
      out.append(miNumDocsFromLast+ConstantParameters.ITEMSEPARATOR+"#miNumDocsFromLast");
      out.newLine();
      out.flush();
      out.close();
    } catch(UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch(FileNotFoundException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }

  }
}
