/*
 *  LogService.java
 * 
 *  Yaoyong Li 22/03/2007
 *
 *  $Id: LogService.java, v 1.0 2007-03-22 12:58:16 +0000 yaoyong $
 */
package gate.learning;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

/**
 * Write the learning information into a log file, which name is specified in
 * ConstantParameters.java. Also including a static variable debug determining
 * the level of output of the ML Api.
 */
public class LogService {
  //-------------------- Verbosity Level --------------------
  /** Absolutely no logging at all - don't even open the log file. */
  public static final int NONE        = -1;
  /** The Minimum level, only output the error and warning information. */
  public static final int MINIMUM     = 0;
  /** The normal level, outputing the setting information and results. */
  public static final int NORMAL          = 1;
  /** Output the debug information. */
  public static final int DEBUG      = 2;
  /**
   * Determine if or not printing the information. 0 -- no debug information; 1 --
   * usual information output; 2 -- all the information output, including some
   * warning information.
   */
  
  /** The minimal verbosity level. Message below this level are ignored. */
  public static int minVerbosityLevel=1;
  /** The PrintStream to write the messages to. */
  private static PrintWriter logFileIn = null;
  /** The last printed message. */
  private static String lastMessage;
  /** Counts how often a message was repeated. */
  private static int equalMessageCount;
  
  /** Open the log service and set the minimum verbosity level. */
  public static void init(File logfile, boolean append, int verboLevel) throws IOException {
    if(verboLevel > NONE) {
      // special case - for NONE we don't even want to open the log file
      logFileIn = new PrintWriter(new FileWriter(logfile, append));
    }
    minVerbosityLevel = verboLevel;
  }
  
  /** Closes the log stream. */
  public static void close() {
    //logMessage("Ended logging", INIT);
    logFileIn.close();
  }
  
  /** Writes the message to the output stream if the verbosity level is high enough. */
  public static void logMessage(String message, int verbosityLevel) {
    if (message == null || logFileIn == null) return;
    if (verbosityLevel < minVerbosityLevel) return;
    if (message.equals(lastMessage)) {
      equalMessageCount++;
      return;
    }
    if (equalMessageCount > 0) {
      logFileIn.println("Last message repeated " + equalMessageCount + " times.");
      equalMessageCount = 0;
    }
    lastMessage = message;
    logFileIn.println(getTime() + " " + message);
    logFileIn.flush();
  }
  
  //-------------------- private helper methods --------------------

  /** Returns the current system time nicely formatted. */
  private static String getTime() {
    return java.text.DateFormat.getDateTimeInstance().format(new Date()) + ":";
  }

}
