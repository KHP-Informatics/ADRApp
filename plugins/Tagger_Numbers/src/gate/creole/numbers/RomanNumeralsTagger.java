/*
 * Copyright (c) 2009-2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 * 
 * Parts of this file were copied from
 * http://www.faqs.org/docs/javap/c9/ex-9-3-answer.html. This is part of the
 * website at http://www.faqs.org/docs/javap/index.html, which is open source
 * published under the GNU Free Documentation Licence
 * (http://www.faqs.org/docs/javap/license.txt)
 * 
 * The icon used for this PR is an edited version of the SVG file from
 * http://commons.wikimedia.org/wiki/File:Centurion_icon.svg which was released,
 * by it's author Woudloper, under the GNU Free Documentation Licence
 */
package gate.creole.numbers;

import static gate.creole.numbers.AnnotationConstants.NUMBER_ANNOTATION_NAME;
import static gate.creole.numbers.AnnotationConstants.TYPE_FEATURE_NAME;
import static gate.creole.numbers.AnnotationConstants.VALUE_FEATURE_NAME;
import gate.AnnotationSet;
import gate.Factory;
import gate.FeatureMap;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ExecutionInterruptedException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.InvalidOffsetException;

import java.text.NumberFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A GATE PR which annotates Roman Numerals with their numeric value.
 * 
 * @see <a href="http://gate.ac.uk/userguide/sec:misc-creole:numbers:roman">The GATE User Guide</a>
 * @author Mark A. Greenwood
 * @author Valentin Tablan
 */
@CreoleResource(name = "Roman Numerals Tagger", comment = "Finds and annotates Roman numerals", icon = "roman.png", helpURL="http://gate.ac.uk/userguide/sec:misc-creole:numbers:roman")
public class RomanNumeralsTagger extends AbstractLanguageAnalyser {

  private static final long serialVersionUID = 8568794158677464398L;

  private String outputAnnotationSetName;

  private boolean allowLowerCase = false;

  /**
   * The maximum numbers of characters allowed as a suffix. This is useful for
   * recognising references to document parts that also have a sub-part, such as
   * Table XIIa.
   */
  private int maxTailLength;

  /**
   * Converts a Roman letter into its value.
   * 
   * @param letter
   *          the Roman numeral character to convert into a number
   * @return returns the value of the character when treated as a Roman numeral,
   *         or 01 if the character isn't valid
   */
  private int letterToNumber(char letter) {
    // Find the integer value of letter considered as a Roman numeral. Return
    // -1 if letter is not a legal Roman numeral. The letter must be upper
    // case.
    switch(letter){
      case 'I':
        return 1;
      case 'V':
        return 5;
      case 'X':
        return 10;
      case 'L':
        return 50;
      case 'C':
        return 100;
      case 'D':
        return 500;
      case 'M':
        return 1000;
      default:
        return -1;
    }
  }

  /**
   * A utility method to convert a Roman numeral into a decimal value.
   * 
   * @param roman
   *          the {@link String} representing the Roman numeral.
   * @return the value of the Roman numeral provided
   * @throws NumberFormatException
   *           if the provided string is not a valid Roman numeral.
   */
  private int romanToInt(String roman) throws NumberFormatException {
    // Creates the Roman number with the given representation.
    // For example, RomanNumeral("xvii") is 17. If the parameter is not a
    // legal Roman numeral, a NumberFormatException is thrown. Both upper and
    // lower case letters are allowed.

    if(roman.length() == 0)
      throw new NumberFormatException(
              "An empty string does not define a Roman numeral.");

    // Convert to upper case letters.
    roman = roman.toUpperCase();

    // A position in the string;
    int i = 0;

    // Arabic numeral equivalent of the part of the string that has been
    // converted so far.
    int arabic = 0;

    while(i < roman.length()) {
      // Letter at current position in string.
      char letter = roman.charAt(i);

      // Numerical equivalent of letter.
      int number = letterToNumber(letter);

      if(number < 0) return number;

      // Move on to next position in the string
      i++;

      if(i == roman.length()) {
        // There is no letter in the string following the one we have just
        // processed. So just add the number corresponding to the single
        // letter to arabic.
        arabic += number;
      } else {
        // Look at the next letter in the string. If it has a larger Roman
        // numeral equivalent than number, then the two letters are counted
        // together as a Roman numeral with value (nextNumber - number).
        int nextNumber = letterToNumber(roman.charAt(i));
        if(nextNumber > number) {
          // Combine the two letters to get one value, and move on to next
          // position in string.
          arabic += (nextNumber - number);
          i++;
        } else {
          // Don't combine the letters. Just add the value of the one letter
          // onto the number.
          arabic += number;
        }
      }
    }

    if(arabic > 3999)
      throw new NumberFormatException(
              "Roman numeral must have value 3999 or less.");

    return arabic;
  }

  public String getOutputASName() {
    return outputAnnotationSetName;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "The name for annotation set used for the generated annotations")
  public void setOutputASName(String outputAnnotationSetName) {
    this.outputAnnotationSetName = outputAnnotationSetName;
  }

  public Integer getMaxTailLength() {
    return maxTailLength;
  }

  @RunTime
  @CreoleParameter(defaultValue = "0", comment = "The maximum numbers of characters allowed as a suffix. "
          + "This is useful for recognising references to document "
          + "parts that also have a sub-part, such as Table XIIa.")
  public void setMaxTailLength(Integer maxTailLength) {
    this.maxTailLength = maxTailLength;
  }

  public Boolean getAllowLowerCase() {
    return allowLowerCase;
  }

  @RunTime
  @CreoleParameter(defaultValue = "false", comment = "Should lower case Roman numerals, e.g. vi, be recognised?")
  public void setAllowLowerCase(Boolean allowLowerCase) {
    this.allowLowerCase = allowLowerCase;
  }

  @Override
  public void execute() throws ExecutionException {
    interrupted = false;

    if(document == null) throw new ExecutionException("No Document provided!");

    AnnotationSet outputAS = document.getAnnotations(outputAnnotationSetName);

    long startTime = System.currentTimeMillis();
    fireStatusChanged("Tagging Roman Numerals in " + document.getName());
    fireProgressChanged(0);

    // create the pattern for finding potential Roman Numerals
    // a sequence of Roman letters, that start a word.
    // the first capturing group matches the numeral, the second group
    // matches the remainder of the word.
    Pattern pattern;
    if(allowLowerCase) {
      if(maxTailLength > 0) {
        pattern =
                Pattern.compile("\\b((?:[mdclxvi]+)|(?:[MDCLCVI]+))(\\w{0,"
                        + maxTailLength + "})\\b");
      } else {
        pattern = Pattern.compile("\\b((?:[mdclxvi]+)|(?:[MDCLCVI]+))\\b");
      }
    } else {
      // no lower case option
      if(maxTailLength > 0) {
        pattern =
                Pattern.compile("\\b([MDCLCVI]+)(\\w{0," + maxTailLength
                        + "})\\b");
      } else {
        pattern = Pattern.compile("\\b([MDCLCVI]+)\\b");
      }
    }

    // run the regex matcher over the document text
    String content = document.getContent().toString();
    Matcher matcher = pattern.matcher(content);

    while(matcher.find()) {
      if(isInterrupted()) { throw new ExecutionInterruptedException(
              "The execution of the \""
                      + getName()
                      + "\" Roman Numerals Tagger has been abruptly interrupted!"); }

      int numStart = matcher.start(1);
      int numEnd = matcher.end(1);
      if(numStart >= 0 && numEnd > numStart) {
        String romanNumeral = content.substring(numStart, numEnd);

        int value = romanToInt(romanNumeral);

        if(value > 0) {
          String tail = null;
          if(maxTailLength > 0) {
            int tailStart = matcher.start(2);
            int tailEnd = matcher.end(2);
            if(tailStart < tailEnd) {
              tail = content.substring(tailStart, tailEnd);
              numEnd = tailEnd;
            }
          }

          // create the new annotation
          FeatureMap fm = Factory.newFeatureMap();
          fm.put(VALUE_FEATURE_NAME, Integer.valueOf(value).doubleValue());
          fm.put(TYPE_FEATURE_NAME, "roman");
          if(tail != null) fm.put("tail", tail);

          try {
            outputAS.add((long)numStart, (long)numEnd, NUMBER_ANNOTATION_NAME,
                    fm);
          } catch(InvalidOffsetException e) {
            // this should never be able to happen!
          }
        }
      }
    }

    fireProcessFinished();
    fireStatusChanged(document.getName()
            + " tagged with Roman Numerals in "
            + NumberFormat.getInstance().format(
                    (double)(System.currentTimeMillis() - startTime) / 1000)
            + " seconds!");
  }
}
