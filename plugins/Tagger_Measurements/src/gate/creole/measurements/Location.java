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

/**
 * Identifies location of a piece of text in a file.
 */
class Location {
  final String file; // File

  final int lineNum; // Line number

  final int beginChar; // Starting character index

  final int endChar; // Ending character index

  /**
   * Constructs dummy Location for built-in entity.
   */
  Location() {
    file = null;
    lineNum = -1;
    beginChar = -1;
    endChar = -1;
  }

  /**
   * Constructs Location object.
   * 
   * @param fil
   *          file name.
   * @param line
   *          line number.
   * @param begin
   *          starting character index.
   * @param end
   *          ending character index.
   */
  Location(final String fil, int line, int begin, int end) {
    file = fil;
    lineNum = line;
    beginChar = begin;
    endChar = end;
  }
}
