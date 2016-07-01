package gate.learning.learners;

import gate.learning.SparseFeatureVector;
import gate.learning.learners.svm.svm_parameter;
import gate.util.BomStrippingInputStreamReader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.apache.commons.io.IOUtils;

public class SvmForExec extends SupervisedLearner{
  /** The uneven margins parameter. */
  private float tau = (float)0.4;

  /** The data file for svm learning.*/
  private String svmDat;
  /** The model file for svm learning.*/
  private String modelSVMFile;

  /** The kernel type. */
  private int kernelType =0; //linear kernel as default value
  /** kernel_type */
  public static final int LINEAR = 0;
  public static final int POLY = 1;
  public static final int RBF = 2;
  public static final int SIGMOID = 3;
  public static final int PRECOMPUTED = 4;

  /**Parameters in the kernel function.*/
  int paramD = 3;
  double paramG = 1.0;
  double paramR = 0.0;

  /**Class constructor without parameter.*/
  public SvmForExec() {
  }
  /**Class constructor with tau parameter.*/
  public SvmForExec(float t) {
    this.tau = t;
  }

  /**Class constructor with tau parameter.*/
  public SvmForExec(float t, String command) {
    this.tau = t;
    this.commandLine = command;
  }

  /** Get the parameters from the command line.
   * @throws  */
  public void getParametersFromCommmand() {

    System.out.println("commandline=*"+commandLine+"*");

    if(commandLine == null) {
      System.out.println("Error: no command for executing!!");
      try {
        throw new Exception("no command to execute");
      } catch(Exception e) {
        e.printStackTrace();
      }
    } else {
      double coef1=1.0;
      String [] items = commandLine.split(" ");
      int len = items.length;
      modelSVMFile = items[len-1];
      svmDat = items[len-2];
      kernelType = 0;
      for(int i=0; i<len-2; ++i) {
        if(items[i].equals("-t") && i+1<len-2) {
          kernelType = new Integer(items[i+1]);
        }
        if(items[i].equals("-d") && i+1<len-2) {
          this.paramD = new Integer(items[i+1]);
        }
        if(items[i].equals("-g") && i+1<len-2) {
          this.paramG = new Double(items[i+1]);
        }
        if(items[i].equals("-r") && i+1<len-2) {
          this.paramR = new Double(items[i+1]);
        }
        if(items[i].equals("-s") && i+1<len-2) {
          coef1 = new Double(items[i+1]);
        }
        if(items[i].equals("-tau") && i+1<len-2) {
          this.tau = new Float(items[i+1]);
        }
      }
      if(coef1!=0)
        this.paramR /= coef1;
    }

  }

  /** Method for training, by reading from data file for feature vectors, and
   * with label as input. */
  public void trainingWithDataFile(BufferedWriter modelFile,
      BufferedReader dataFile, int totalNumFeatures,
      short[] classLabels, int numTraining) {
    try {
      //Write the data into the data file for svm learning
      BufferedWriter svmDataBuff = new BufferedWriter(new FileWriter(new File(svmDat)));
      for(int iCounter=0; iCounter<numTraining; ++iCounter) {
        final int i = iCounter;
        if(classLabels[i]>0)
          svmDataBuff.append("1 ");
        else
          svmDataBuff.append("-1 ");
        //int [] indexes = dataLearning[i].getIndexes();
        //float [] values = dataLearning[i].getValues();
        //for(int j=0; j<dataLearning[i].getLen(); ++j)
          //svmDataBuff.append(" "+dataLearning[i].nodes[j].index+":"+dataLearning[i].nodes[j].value);
        String line = dataFile.readLine();
        svmDataBuff.append(line);
        svmDataBuff.append("\n");
      }
      svmDataBuff.close();

      //Execute the command for the SVM  learning
      //Get the command line for the svm_learn, by getting rid of the tau parameter
      String commandLineSVM = obtainSVMCommandline(commandLine);
      //Run the external svm learn exectuable
      runExternalCommand(commandLineSVM);
      //runExternalCommandWithRedirect(commandLineSVM);

      //Read the model from the svm results file and write it into our model file
      writeSVMModelIntoFile(modelSVMFile, kernelType, modelFile, totalNumFeatures);


    } catch(IOException e) {
      e.printStackTrace();
    }
  }


  public void training(BufferedWriter modelFile, SparseFeatureVector [] dataLearning,
          int totalNumFeatures, short [] classLabels, int numTraining) {
    try {
      //Write the data into the data file for svm learning
      BufferedWriter svmDataBuff = new BufferedWriter(new FileWriter(new File(svmDat)));
      for(int iCounter=0; iCounter<numTraining; ++iCounter) {
        final int i = iCounter;
        if(classLabels[i]>0)
          svmDataBuff.append("1");
        else
          svmDataBuff.append("-1");
        //int [] indexes = dataLearning[i].getIndexes();
        //float [] values = dataLearning[i].getValues();
        for(int j=0; j<dataLearning[i].getLen(); ++j)
          svmDataBuff.append(" "+dataLearning[i].nodes[j].index+":"+dataLearning[i].nodes[j].value);
        svmDataBuff.append("\n");
      }
      svmDataBuff.close();

      //Execute the command for the SVM  learning
      //Get the command line for the svm_learn, by getting rid of the tau parameter
      String commandLineSVM = obtainSVMCommandline(commandLine);
      //Run the external svm learn exectuable
      runExternalCommand(commandLineSVM);
      //runExternalCommandWithRedirect(commandLineSVM);

      //Read the model from the svm results file and write it into our model file
      writeSVMModelIntoFile(modelSVMFile, kernelType, modelFile, totalNumFeatures);


    } catch(IOException e) {
      e.printStackTrace();
    }

  }

  /** Get the command line for the svm_learn, by getting rid of the tau parameter.*/
  public static String obtainSVMCommandline(String commandLine) {
    StringBuffer commandSVM = new StringBuffer();
    String [] items = commandLine.split("[ \t]+");
    int len=0;
    commandSVM.append(items[len++]);
    while(len<items.length) {
      if(items[len].equalsIgnoreCase("-tau")) {
        ++len;
      } else {
        commandSVM.append(" "+items[len]);
      }
      ++len;
    }

    return commandSVM.toString();
  }

  public static void writeSVMModelIntoFile(String modelSVMFile, int kernelType,
          BufferedWriter modelFile, int totalNumFeatures) {
    //Open the svm model file
    BufferedReader svmModelBuff;
    try {
      svmModelBuff = new BufferedReader(new FileReader(new File(modelSVMFile)));
      int numSV=0;
      float b=0;
      String line;
      line = svmModelBuff.readLine();
      while(!(line.contains("# threshold b,") || line.contains("nr_sv "))) {
        if(line.contains("# number of support vectors plus 1")) {
          numSV = new Integer(line.substring(0, line.indexOf(" ")));
          numSV -= 1; //since it's number of SVs plus 1 in svm_light
        }
        if(line.contains("rho ")) { //for the model from libsvm
          b = new Float(line.substring(line.indexOf(" ")+1)).floatValue();
          b = -b;
        }
        line = svmModelBuff.readLine();
      }
      if(line.contains("# threshold b,")) {
        b = new Float(line.substring(0, line.indexOf(" "))).floatValue();
        b = -b; //since the b in the svm model file is -b
      }
      if(line.contains("nr_sv ")) { //for the model from libsvm
        String [] items = line.split(" ");
        if(items.length>1) {
           numSV = new Integer(items[1]);
           if(items.length>2)
             numSV += new Integer(items[2]);
        } else {
          System.out.println("Error: no information for num_sv in the model file from libsvm!!");
          Exception e = new Exception();
          e.fillInStackTrace().printStackTrace();
        }
        line = svmModelBuff.readLine(); //read in one more line for the libsvm model file
      }

      System.out.println("b="+b+", num_sv="+numSV+"*");
      //Convert into primal form, if it is linear kernel
      if(kernelType == 0) {
        //Define the sparse vectors used for SVs
        SparseFeatureVector [] svFVs = new  SparseFeatureVector[numSV];
        double [] alphas = new double[numSV];
        SvmLibSVM.readOneSVMModel(svmModelBuff, numSV, svFVs, alphas);
        //Convert the dual form into primal form
        float [] w = new float[totalNumFeatures];
        for(int i=0; i<numSV; ++i) {
          //int [] indexes = svFVs[i].getIndexes();
          //float [] values = svFVs[i].getValues();
          for(int j=0; j<svFVs[i].getLen(); ++j)
            w[svFVs[i].nodes[j].index] += alphas[i]*svFVs[i].nodes[j].value;
        }
        SvmLibSVM.writeLinearModelIntoFile(modelFile, b, w, totalNumFeatures);
      } else {
        //just write the all the model into file in dual form
        modelFile.append(b+"\n");
        modelFile.append(numSV+"\n");
        for(int i=0; i<numSV; ++i) {
          line = svmModelBuff.readLine();
          modelFile.append(line+"\n");
        }
      }
    } catch(FileNotFoundException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }


  public void applying(BufferedReader modelFile, DataForLearning dataFVinDoc, int totalNumFeatures, int numClasses) {
    float optB;
    optB = (1-tau)/(1+tau);
    svm_parameter svmParam = setSvmParamByCommandline();
    SvmLibSVM.svmApplying(modelFile, dataFVinDoc, totalNumFeatures, numClasses, optB, svmParam, this.isUseTauALLCases);

  }
  /** Set the svm_parameter.*/
  svm_parameter setSvmParamByCommandline() {
    svm_parameter param = new svm_parameter();
    param.kernel_type = kernelType;
    param.degree = paramD;
    param.gamma = paramG;
    param.coef0 = paramR;

    return param;
  }


//a class used for execute an external command
  class StreamGobbler extends Thread {
      InputStream is;
      String type;

      StreamGobbler(InputStream is, String type)
      {
          this.is = is;
          this.type = type;
      }

      public void run()
      {
        BufferedReader br = null;  
        try
          {
              br = new BomStrippingInputStreamReader(is);
              String line=null;
              while ( (line = br.readLine()) != null)
                  System.out.println(type + ">" + line);
              } catch (IOException ioe)
                {
                  ioe.printStackTrace();
                }
        finally {
          IOUtils.closeQuietly(br);
        }
      }
  }


  public void runExternalCommand(String command) {

      try
      {
          String osName = System.getProperty("os.name" );
          String[] cmd = new String[3];
          boolean isWindows = false;

          if( osName.equals( "Windows XP") || osName.equals("Windows NT") )
          {
              cmd[0] = "cmd.exe" ;
              cmd[1] = "/C" ;
              cmd[2] = command;
              isWindows = true;
          }
          else if( osName.equals( "Windows 95" ) )
          {
              cmd[0] = "command.com" ;
              cmd[1] = "/C" ;
              cmd[2] = command;
              isWindows = true;
          } else {
            cmd[0] = " " ;
              cmd[1] = " " ;
              cmd[2] = command;
          }

          Runtime rt = Runtime.getRuntime();
          //System.out.println("Execing " + cmd[0] + " " + cmd[1]
              //               + " " + cmd[2]);
          Process proc = null;
          if(isWindows)
            proc = rt.exec(cmd);
          else
            proc = rt.exec(command); //linux cannot run the empty command
          // any error message?
          StreamGobbler errorGobbler = new
              StreamGobbler(proc.getErrorStream(), "ERROR");

          // any output?
          StreamGobbler outputGobbler = new
              StreamGobbler(proc.getInputStream(), "OUTPUT");

          // kick them off
          errorGobbler.start();
          outputGobbler.start();

          // any error???
          @SuppressWarnings("unused")
          int exitVal = proc.waitFor();
          //System.out.println("ExitValue: " + exitVal);
      } catch (Throwable t)
        {
          t.printStackTrace();
        }
  }

//a class used for execute an external command
  class StreamGobblerForDirect extends Thread {
      InputStream is;
      String type;
      OutputStream os;

      StreamGobblerForDirect(InputStream is, String type)
      {
          this.is = is;
          this.type = type;
      }

      StreamGobblerForDirect(InputStream is, String type, OutputStream redirect)
      {
          this.is = is;
          this.type = type;
          this.os = redirect;
      }

      public void run()
      {
        BufferedReader br = null;  
        try
          {
              PrintWriter pw = null;
              if (os != null)
                  pw = new PrintWriter(os);

              br = new BomStrippingInputStreamReader(is);
              String line=null;
              while ( (line = br.readLine()) != null)
              {
                  if (pw != null)
                      pw.println(line);
                  System.out.println(type + ">" + line);
              }
              if (pw != null)
                  pw.flush();
          } catch (IOException ioe)
              {
              ioe.printStackTrace();
              }
        finally {
          IOUtils.closeQuietly(br);
        }
      }

  }


  public void runExternalCommandWithRedirect(String command) {

      try
      {
          String osName = System.getProperty("os.name" );
          String[] cmd = new String[3];

          if( osName.equals( "Windows XP") || osName.equals("Windows NT") )
          {
              cmd[0] = "cmd.exe" ;
              cmd[1] = "/C" ;
              cmd[2] = command;
          }
          else if( osName.equals( "Windows 95" ) )
          {
              cmd[0] = "command.com" ;
              cmd[1] = "/C" ;
              cmd[2] = command;
          } else {
            cmd[0] = " " ;
              cmd[1] = " " ;
              cmd[2] = command;
          }

          Runtime rt = Runtime.getRuntime();
          System.out.println("Execing " + cmd[0] + " " + cmd[1]
                             + " " + cmd[2]);
          Process proc = rt.exec(cmd);
          // any error message?
          StreamGobblerForDirect errorGobbler = new
              StreamGobblerForDirect(proc.getErrorStream(), "ERROR");

          // any output?
          String outFileName = command.substring(command.lastIndexOf('>')+1);
          FileOutputStream fos = new FileOutputStream(outFileName);
          StreamGobblerForDirect outputGobbler = new
              StreamGobblerForDirect(proc.getInputStream(), "OUTPUT", fos);

          // kick them off
          errorGobbler.start();
          outputGobbler.start();

          // any error???
          int exitVal = proc.waitFor();
          System.out.println("ExitValue: " + exitVal);
          fos.flush();
          fos.close();
      } catch (Throwable t)
        {
          t.printStackTrace();
        }

  }


}
