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
 * A unit.
 */
class Unit extends Factor {
  /**
   * Construct object for unit 'nam' defined at 'loc'. The unit is defined by
   * string 'df'.
   */
  Unit(final String nam, final String df, MeasurementsParser gnuUnits) {
    super(nam, df, gnuUnits);
  }

  /**
   * Given is a line number 'lin' from units.dat file, parsed into name 'nam'
   * and definition 'df'. It should be a unit definition. Construct a Unit
   * object defined by the line, enter it into Units table, and return true.
   */
  static boolean accept(final String nam, final String df, Location loc,
          MeasurementsParser gnuUnits) {
    // Units that end in [2-9] can never be accessed.
    if("23456789".indexOf(nam.charAt(nam.length() - 1)) >= 0) {
      System.err.println("Unit '" + nam + "' on line " + loc.lineNum
              + " ignored. It ends with a digit 2-9.");
      return true;
    }
    // Units that start with a digit can never be accessed.
    if("0123456789".indexOf(nam.charAt(0)) >= 0) {
      System.err.println("Unit '" + nam + "' on line " + loc.lineNum
              + " ignored. It starts with a digit.");
      return true;
    }
    // Is it a redefinition?
    if(gnuUnits.units.containsKey(nam)) {
      System.err.println("Redefinition of unit '" + nam + "' on line "
              + loc.lineNum + " is ignored.");
      return true;
    }
    // Install unit in table.
    gnuUnits.units.put(nam, new Unit(nam, df, gnuUnits));
    return true;
  }

  @Override
  void check() {
    // check if can be reduced
    Measurement v = Measurement.fromString(name, gnuUnits);
    if(v == null
            || !v.isCompatibleWith(new Measurement(gnuUnits), Ignore.PRIMITIVE))
      System.err.println("'" + name + "' defined as '" + def
              + "' is irreducible");
    // check if not hidden by function
    if(gnuUnits.functions.containsKey(name))
      System.err.println("unit '" + name + "' is hidden by function '" + name
              + "'");
  }

  /**
   * If unit defined by this object is compatible with Value 'v', add its
   * description to 'list'. Used in 'tryallunits'.
   */
  @Override
  void addtolist(final Measurement v, List<Entity> list) {
    Measurement thisvalue = Measurement.fromString(name, gnuUnits);
    if(thisvalue == null) return;
    if(thisvalue.isCompatibleWith(v, Ignore.DIMLESS)) insertAlph(list);
  }

  /**
   * Return short description of this object to be shown by 'tryallunits'.
   */
  @Override
  String desc() {
    return (isPrimitive ? "<primitive unit>" : "= " + def);
  }

  /**
   * Find out if 'name' is the name of a known unit, possibly in plural. Return
   * the Unit object if so, or null otherwise. (Originally part of
   * 'lookupunit'.)
   */
  static Unit find(final String name, MeasurementsParser gnuUnits) {
    // ---------------------------------------------------------------
    // If 'name' appears as unit name in table,
    // return object from the table.
    // ---------------------------------------------------------------
    if(gnuUnits.units.containsKey(name)) return gnuUnits.units.get(name);
    // ---------------------------------------------------------------
    // Plural rules for English: add -s
    // after x, sh, ch, ss add -es
    // -y becomes -ies except after a vowel when you just add -s
    // Try removing 's'.
    // ---------------------------------------------------------------
    int ulg = name.length();
    if(ulg > 3 && name.charAt(ulg - 1) == 's') {
      String temp = name.substring(0, ulg - 1);
      if(gnuUnits.units.containsKey(temp)) return gnuUnits.units.get(temp);
      // -------------------------------------------------------------
      // Removing the suffix 's' did not help. It could still be
      // a plural form ending on 'es'. Try this.
      // -------------------------------------------------------------
      if(ulg > 4 && name.charAt(ulg - 2) == 'e') {
        temp = name.substring(0, ulg - 2);
        if(gnuUnits.units.containsKey(temp)) return gnuUnits.units.get(temp);
        // -----------------------------------------------------------
        // Removing the suffix 'es' did not help. It could still be
        // a plural form ending on 'ies'. Try this.
        // -----------------------------------------------------------
        if(ulg > 5 && name.charAt(ulg - 3) == 'i') {
          temp = name.substring(0, ulg - 3) + "y";
          if(gnuUnits.units.containsKey(temp)) return gnuUnits.units.get(temp);
        }
      }
    }
    return null;
  }
}
