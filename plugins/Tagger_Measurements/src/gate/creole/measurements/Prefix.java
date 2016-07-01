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

import java.util.List;

/**
 * A prefix.
 */
class Prefix extends Factor {
  /**
   * Construct object for prefix 'nam' defined at 'loc'. The prefix is defined
   * by string 'df'.
   */
  Prefix(final String nam, final String df, MeasurementsParser gnuUnits) {
    super(nam, df, gnuUnits);
  }

  /**
   * Given is a line from units.dat file, parsed into name 'nam' and definition
   * 'df'. If this line defines a prefix, construct a Prefix object defined by
   * it, enter it into Prefix table, and return true. Otherwise return false.
   */
  static boolean accept(final String nam, final String df, Location loc,
          MeasurementsParser gnuUnits) {
    // If unitname ends with '-', we have a prefix definition.
    if(!nam.endsWith("-")) return false;
    String prefname = nam.substring(0, nam.length() - 1);
    // Is it redefinition?
    if(gnuUnits.prefixes.containsKey(prefname)) {
      System.err.println("Redefinition of prefix '" + prefname + "-' on line "
              + loc.lineNum + " is ignored.");
      return true;
    }
    // Install prefix in table.
    gnuUnits.prefixes.put(prefname, new Prefix(prefname, df, gnuUnits));
    return true;
  }

  /**
   * Check the prefix definition. Used in 'checkunits'.
   */
  @Override
  void check() {
    // check for bad '/' character in prefix
    int plevel = 0;
    for(int i = 0; i < def.length(); i++) {
      int ch = def.charAt(i);
      if(ch == ')')
        plevel--;
      else if(ch == '(')
        plevel++;
      else if(plevel == 0 && ch == '/') {
        System.err.println("'" + name + "-' defined as '" + def
                + "' contains bad '/'");
        return;
      }
    }
    // check if can be reduced
    Measurement v = Measurement.fromString(name, gnuUnits);
    if(v == null
            || !v.isCompatibleWith(new Measurement(gnuUnits), Ignore.PRIMITIVE))
      System.err.println("'" + name + "' defined as '" + def
              + "' is irreducible");
  }

  /**
   * These methods, defined in Entity class, are never called for a Prefix
   * object.
   */
  @Override
  void addtolist(final Measurement v, List<Entity> list) {
    throw new UnsupportedOperationException("Program Error");
  }

  @Override
  String desc() {
    throw new UnsupportedOperationException("Program Error");
  }

  /**
   * Find the longest prefix of 'name' that is in Prefix table, and return that
   * Prefix object. (The prefix may all of 'name'.) Return null if name does not
   * have a known prefix.
   */
  static Prefix find(final String name, MeasurementsParser gnuUnits) {
    int nlg = name.length();
    int plg;
    for(plg = nlg; plg > 0; plg--) {
      Prefix p = gnuUnits.prefixes.get(name.substring(0, plg));
      if(p != null) return p;
    }
    return null;
  }
}
