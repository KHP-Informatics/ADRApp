/*
 * Units is a program for unit conversion originally written in C by Adrian
 * Mariano (adrian@cam.cornell.edu.). Copyright (C) 1996, 1997, 1999, 2000,
 * 2001, 2002, 2003, 2004, 2005, 2006 by Free Software Foundation, Inc.
 * 
 * Java version Copyright (C) 2003, 2004, 2005, 2006, 2007 by Roman R
 * Redziejowski (roman.redz@tele2.se).
 * 
 * GATE version is Copyright (c) 2009-2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * Licensed under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */
package gate.creole.measurements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds common methods used by other classes.
 */
class Util {

  /**
   * Finds occurrence of one of given characters in a string. <br>
   * Finds the first occurrence in String <code>s</code>, at or after index
   * <code>start</code>, of any of the characters contained in
   * <code>chars</code>.
   * 
   * @param s
   *          String to search.
   * @param start
   *          starting index for the search.
   * @param chars
   *          characters to look for.
   * @return index of the found occurrence, or length of <code>s</code> if none
   *         found.
   */
  static int indexOf(final String chars, final String s, int start) {
    for(int i = start; i < s.length(); i++)
      if(chars.indexOf(s.charAt(i)) >= 0) return i;
    return s.length();
  }

  /**
   * Converts <code>double</code> number to a printable representation.
   * 
   * @param d
   *          number to be converted.
   * @return String representation of <code>d</code>.
   */
  public static String shownumber(double d) {
    if(d == (int)d) return Integer.toString((int)d);
    return Float.toString((float)d);
  }

  private static final Pattern p =
          Pattern.compile("[ \t]*[+-]?((([0-9]+(\\.[0-9]*)?)|(\\.?[0-9]+))([Ee][+-]?[0-9]+)?)[ \t]*");

  /**
   * Emulates (part of) C/C++ library function <code>strtod</code>. <br>
   * Finds the longest substring of <code>s</code> starting at index
   * <code>i</code> that represents a <code>double</code> number in C/C++
   * format.
   * 
   * @param s
   *          String to be scanned.
   * @param i
   *          starting index for the scan.
   * @return index of the last recognized character plus 1, or <code>i</code> if
   *         nothing was recognized.
   */
  static int strtod(final String s, int i) {
    Matcher m = p.matcher(s);
    if(m.find(i)) return m.end();
    return i;
  }
}
